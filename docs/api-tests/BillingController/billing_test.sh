#!/bin/bash
# BillingController pass-1 end-to-end tests. Prints PASS/FAIL per scenario.
B=localhost:8080/api/v1
PASS=0; FAIL=0
BASE=$(( $(date +%s) % 1000000 ))  # run-unique 6-digit phone base
ph(){ printf "98%06d%02d" $BASE $1; }  # 10-digit unique phone
jget(){ python3 -c "import sys,json;d=json.load(sys.stdin);print(eval(\"d$1\"))" 2>/dev/null; }
post(){ curl -s -X POST "$B$1" -H 'Content-Type: application/json' -d "$2"; }
put(){ curl -s -X PUT "$B$1" -H 'Content-Type: application/json' -d "$2"; }
del(){ curl -s -X DELETE "$B$1"; }
get(){ curl -s "$B$1"; }
code(){ curl -s -o /dev/null -w "%{http_code}" -X "$1" "$B$2" -H 'Content-Type: application/json' ${3:+-d "$3"}; }
check(){ if [ "$2" == "$3" ]; then echo "  PASS: $1 ($2)"; PASS=$((PASS+1)); else echo "  FAIL: $1 — got [$2] expected [$3]"; FAIL=$((FAIL+1)); fi; }
ncheck(){ if python3 -c "import sys;sys.exit(0 if abs(float('$2')-float('$3'))<0.005 else 1)" 2>/dev/null; then echo "  PASS: $1 ($2)"; PASS=$((PASS+1)); else echo "  FAIL: $1 — got [$2] expected [$3]"; FAIL=$((FAIL+1)); fi; }

echo "############ FIXTURE ############"
OWNER=$(post /users "{\"phone\":\"$(ph 1)\",\"fullName\":\"BillOwner\",\"role\":\"LANDLORD\"}" | jget "['data']['id']")
echo "owner=$OWNER"
# Property: MAIN_METER_ONLY electricity, FIXED water, EQUAL split, grace 7, WHOLE_NUMBER
PROP=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"MainMeterBldg\",\"electricityBillingMode\":\"MAIN_METER_ONLY\",\"waterMode\":\"FIXED_PER_TENANT\",\"defaultSplitRule\":\"EQUAL\",\"neaTariffMode\":\"FLAT_RATE\",\"billingDay\":1,\"gracePeriodDays\":7,\"roundingMethod\":\"WHOLE_NUMBER\"}" | jget "['data']['id']")
echo "prop=$PROP"
FLOOR=$(post /floors "{\"propertyId\":\"$PROP\",\"name\":\"G\",\"floorNumber\":1}" | jget "['data']['id']")
R1=$(post /rooms "{\"floorId\":\"$FLOOR\",\"name\":\"R1\"}" | jget "['data']['id']")
R2=$(post /rooms "{\"floorId\":\"$FLOOR\",\"name\":\"R2\"}" | jget "['data']['id']")
R3=$(post /rooms "{\"floorId\":\"$FLOOR\",\"name\":\"R3\"}" | jget "['data']['id']")
mk_tenant(){ # $1 phone -> membership id, assigns room $2 rent $3 start $4
  local pid=$(post /tenant-profiles "{\"fullName\":\"T$1\",\"phone\":\"$1\"}" | jget "['data']['id']")
  local mid=$(post /memberships "{\"tenantProfileId\":\"$pid\",\"propertyId\":\"$PROP\",\"startedAtBs\":\"$4\"}" | jget "['data']['id']")
  post /memberships/$mid/room-assignments "{\"roomId\":\"$2\",\"effectiveFromBs\":\"$4\",\"monthlyRent\":$3}" >/dev/null
  echo "$mid"
}
M1=$(mk_tenant $(ph 11) $R1 10000 2082-04-01)
M2=$(mk_tenant $(ph 12) $R2 10000 2082-04-01)
M3=$(mk_tenant $(ph 13) $R3 8000 2082-04-16)   # mid-month join -> proration
echo "M1=$M1 M2=$M2 M3=$M3"

echo "############ 1. TARIFF CRUD ############"
T=$(post /tariffs '{"name":"NEA 2082","effectiveFromBs":"2082-01-01","slabs":[{"uptoUnits":20,"ratePerUnit":8.5},{"uptoUnits":null,"ratePerUnit":11.0}],"demandCharge":75,"vatPercent":13}')
TID=$(echo "$T" | jget "['data']['id']")
check "tariff create success" "$(echo "$T" | jget "['success']")" "True"
check "tariff get" "$(get /tariffs/$TID | jget "['data']['id']")" "$TID"
# asOf 2082-01-03 isolates this run's tariff (2082-01-01) from other global/national tariffs
# created by the pass-2 suite (tariff_versions is not property-scoped).
check "tariff effective on 2082-01-03" "$(get "/tariffs/effective?asOfBs=2082-01-03" | jget "['data']['id']")" "$TID"
check "tariff list has >=1" "$(get '/tariffs?size=5' | jget "['data']['totalElements']>=1")" "True"
check "tariff duplicate effectiveFrom -> 409" "$(code POST /tariffs '{"name":"dup","effectiveFromBs":"2082-01-01","slabs":[],"vatPercent":13}')" "409"
check "tariff update ok (not referenced)" "$(put /tariffs/$TID '{"name":"NEA 2082 rev","effectiveFromBs":"2082-01-01","slabs":[{"uptoUnits":null,"ratePerUnit":12}],"demandCharge":80,"serviceCharge":0,"minimumCharge":0,"vatPercent":13}' | jget "['data']['name']")" "NEA 2082 rev"

echo "############ 2. GENERATE RUN (MAIN_METER_ONLY, B9 reconciliation) ############"
RUN=$(post /properties/$PROP/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01","electricityTotalAmount":1000,"waterFixedAmount":300,"idempotencyKey":"key-apr-1"}')
RID=$(echo "$RUN" | jget "['data']['id']")
check "run generated DRAFT" "$(echo "$RUN" | jget "['data']['status']")" "DRAFT"
check "run tenantCount=3" "$(echo "$RUN" | jget "['data']['tenantCount']")" "3"
BILLS=$(get /billing-runs/$RID/bills)
ELECSUM=$(echo "$BILLS" | python3 -c "import sys,json;d=json.load(sys.stdin);print(sum(float(b['electricityAmount']) for b in d['data']))")
check "B9: electricity shares sum == 1000 exactly" "$ELECSUM" "1000.0"
# M3 prorated rent: 8000 * 16/31 (days 16..31 inclusive=16) = 4129.03
M3BILL=$(echo "$BILLS" | python3 -c "import sys,json;d=json.load(sys.stdin);print([b for b in d['data'] if b['membershipId']=='$M3'][0]['rentAmount'],[b for b in d['data'] if b['membershipId']=='$M3'][0]['prorated'],[b for b in d['data'] if b['membershipId']=='$M3'][0]['daysOccupied'])")
echo "  M3 rent/prorated/days = $M3BILL"
check "M3 prorated flag" "$(echo $M3BILL | cut -d' ' -f2)" "True"
check "M3 daysOccupied=16" "$(echo $M3BILL | cut -d' ' -f3)" "16"
check "due date = generated + grace(7) = 2082-05-08" "$(echo "$BILLS" | jget "['data'][0]['dueDateBs']")" "2082-05-08"

echo "############ 3. IDEMPOTENCY (B10) ############"
RUN2=$(post /properties/$PROP/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01","electricityTotalAmount":1000,"waterFixedAmount":300,"idempotencyKey":"key-apr-1"}')
check "idempotent replay returns same run id" "$(echo "$RUN2" | jget "['data']['id']")" "$RID"

echo "############ 4. DUPLICATE PERIOD (B10) ############"
check "duplicate live run same period -> 409" "$(code POST /properties/$PROP/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01","electricityTotalAmount":500,"waterFixedAmount":300,"idempotencyKey":"other"}')" "409"

echo "############ 5. B13 OLDEST-FIRST ############"
# Create a later-period draft (2082-05), then try to confirm it while 2082-04 draft exists
RUN_MAY=$(post /properties/$PROP/billing-runs '{"billingMonthBs":"2082-05","periodStartBs":"2082-05-01","periodEndBs":"2082-05-31","generatedAtBs":"2082-06-01","electricityTotalAmount":900,"waterFixedAmount":300}')
RID_MAY=$(echo "$RUN_MAY" | jget "['data']['id']")
check "confirm May while Apr draft exists -> 400 (B13)" "$(code POST /billing-runs/$RID_MAY/confirm)" "400"

echo "############ 6. CONFIRM (oldest Apr first) ############"
CONF=$(post /billing-runs/$RID/confirm '{}')
check "confirm Apr run -> CONFIRMED" "$(echo "$CONF" | jget "['data']['status']")" "CONFIRMED"
check "Apr bills now ISSUED" "$(get /billing-runs/$RID/bills | jget "['data'][0]['status']")" "ISSUED"
check "now confirm May ok" "$(post /billing-runs/$RID_MAY/confirm '{}' | jget "['data']['status']")" "CONFIRMED"
check "re-confirm Apr -> 400" "$(code POST /billing-runs/$RID/confirm)" "400"

echo "############ 7. SEGMENTS ############"
# Pass-2 segment engine (T9): M3 joins day 16, so M1 and M2 each split into a pre-join
# (denominator 2) and post-join (denominator 3) segment, plus M3's single segment = 5.
check "segments count = 5 (T9 split at M3 join)" "$(get /billing-runs/$RID/segments | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data']))")" "5"

echo "############ 8. CANCEL + REGENERATE (B8) ############"
# June run, cancel it, then regenerate same period
RUN_JUN=$(post /properties/$PROP/billing-runs '{"billingMonthBs":"2082-06","periodStartBs":"2082-06-01","periodEndBs":"2082-06-30","generatedAtBs":"2082-07-01","electricityTotalAmount":600,"waterFixedAmount":300}')
RID_JUN=$(echo "$RUN_JUN" | jget "['data']['id']")
check "cancel Jun -> CANCELLED" "$(post /billing-runs/$RID_JUN/cancel '{"reason":"wrong reading"}' | jget "['data']['status']")" "CANCELLED"
check "Jun bills CANCELLED" "$(get /billing-runs/$RID_JUN/bills | jget "['data'][0]['status']")" "CANCELLED"
RUN_JUN2=$(post /properties/$PROP/billing-runs '{"billingMonthBs":"2082-06","periodStartBs":"2082-06-01","periodEndBs":"2082-06-30","generatedAtBs":"2082-07-02","electricityTotalAmount":650,"waterFixedAmount":300}')
check "regenerate same period after cancel -> DRAFT" "$(echo "$RUN_JUN2" | jget "['data']['status']")" "DRAFT"

echo "############ 9. GUARDRAILS ############"
check "invalid BS date -> 400" "$(code POST /properties/$PROP/billing-runs '{"billingMonthBs":"2082-07","periodStartBs":"2082-07-99","periodEndBs":"2082-07-30","generatedAtBs":"2082-08-01","electricityTotalAmount":100,"waterFixedAmount":0}')" "400"
check "MAIN_METER without total -> 400" "$(code POST /properties/$PROP/billing-runs '{"billingMonthBs":"2082-08","periodStartBs":"2082-08-01","periodEndBs":"2082-08-29","generatedAtBs":"2082-09-01","waterFixedAmount":0}')" "400"
# SUB_METERED property WITH a tenant but no TENANT_SUPPLY meter → 400 (pass-2 engine guard).
# (An empty SUB_METERED property short-circuits to a B11 empty run before electricity, so a
#  tenant must exist for the sub-meter guard to fire.)
PROP_SUB=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"SubBldg\",\"electricityBillingMode\":\"SUB_METERED\",\"waterMode\":\"INCLUDED_IN_RENT\"}" | jget "['data']['id']")
FSUB=$(post /floors "{\"propertyId\":\"$PROP_SUB\",\"name\":\"G\",\"floorNumber\":1}" | jget "['data']['id']")
RSUB=$(post /rooms "{\"floorId\":\"$FSUB\",\"name\":\"S1\"}" | jget "['data']['id']")
PSUB=$(post /tenant-profiles "{\"fullName\":\"SubT\",\"phone\":\"$(ph 91)\"}" | jget "['data']['id']")
MSUB=$(post /memberships "{\"tenantProfileId\":\"$PSUB\",\"propertyId\":\"$PROP_SUB\",\"startedAtBs\":\"2082-04-01\"}" | jget "['data']['id']")
post /memberships/$MSUB/room-assignments "{\"roomId\":\"$RSUB\",\"effectiveFromBs\":\"2082-04-01\",\"monthlyRent\":5000}" >/dev/null
check "SUB_METERED w/ tenant but no sub-meter -> 400" "$(code POST /properties/$PROP_SUB/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01","electricityRatePerUnit":10}')" "400"

echo "############ 10. EMPTY RUN (B11) ############"
PROP_EMPTY=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"EmptyBldg\",\"electricityBillingMode\":\"INCLUDED_IN_RENT\",\"waterMode\":\"INCLUDED_IN_RENT\"}" | jget "['data']['id']")
ER=$(post /properties/$PROP_EMPTY/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01"}')
check "empty run tenantCount=0" "$(echo "$ER" | jget "['data']['tenantCount']")" "0"
ncheck "empty run totalBilled=0" "$(echo "$ER" | jget "['data']['totalBilled']")" "0"

echo "############ 11. OPENING BALANCE (T7) + ADVANCE (T8) + ADJUSTMENT (P9) ############"
# New property FIXED_PER_TENANT elec, single tenant, with opening balance + advance + one-time charge
PROP2=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"FixedBldg\",\"electricityBillingMode\":\"FIXED_PER_TENANT\",\"waterMode\":\"INCLUDED_IN_RENT\",\"gracePeriodDays\":7,\"roundingMethod\":\"WHOLE_NUMBER\"}" | jget "['data']['id']")
F2=$(post /floors "{\"propertyId\":\"$PROP2\",\"name\":\"G\",\"floorNumber\":1}" | jget "['data']['id']")
RA=$(post /rooms "{\"floorId\":\"$F2\",\"name\":\"A\"}" | jget "['data']['id']")
P2=$(post /tenant-profiles "{\"fullName\":\"Solo\",\"phone\":\"$(ph 21)\"}" | jget "['data']['id']")
MS=$(post /memberships "{\"tenantProfileId\":\"$P2\",\"propertyId\":\"$PROP2\",\"startedAtBs\":\"2082-04-01\"}" | jget "['data']['id']")
post /memberships/$MS/room-assignments "{\"roomId\":\"$RA\",\"effectiveFromBs\":\"2082-04-01\",\"monthlyRent\":10000}" >/dev/null
# opening balance owed 500
post /memberships/$MS/opening-balance '{"amount":500,"direction":"OWED_BY_TENANT","asOfBs":"2082-03-30"}' >/dev/null
# advance rent 3000 covering the period
post /memberships/$MS/advance-rent '{"amount":3000,"monthsCovered":1,"coveredFromBs":"2082-04-01","coveredToBs":"2082-04-31"}' >/dev/null
# one-time charge 200
ADJ=$(post /memberships/$MS/adjustments '{"adjustmentType":"CHARGE","amount":200,"reason":"broken window"}')
check "adjustment created PENDING" "$(echo "$ADJ" | jget "['data']['status']")" "PENDING"
RUN3=$(post /properties/$PROP2/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01","electricityFixedAmount":1500}')
RID3=$(echo "$RUN3" | jget "['data']['id']")
BILL3=$(get /billing-runs/$RID3/bills | python3 -c "import sys,json;print(json.dumps(json.load(sys.stdin)['data'][0]))")
echo "  bill3: $(echo $BILL3 | python3 -c "import sys,json;d=json.load(sys.stdin);print('rent',d['rentAmount'],'elec',d['electricityAmount'],'prev',d['previousBalance'],'adj',d['adjustmentsAmount'],'adv',d['advanceAppliedAmount'],'total',d['totalDue'])")"
# expected: rent10000 + elec1500 + prev500 + adj200 - advance(min(rent,3000)=3000) = 10000+1500+500+200-3000=9200
ncheck "T7 previousBalance=500" "$(echo $BILL3 | jget "['previousBalance']")" "500"
ncheck "P9 adjustmentsAmount=200" "$(echo $BILL3 | jget "['adjustmentsAmount']")" "200"
ncheck "T8 advanceApplied=3000" "$(echo $BILL3 | jget "['advanceAppliedAmount']")" "3000"
ncheck "total_due=9200" "$(echo $BILL3 | jget "['totalDue']")" "9200"
check "adjustment now APPLIED" "$(get /memberships/$MS/adjustments | jget "['data'][0]['status']")" "APPLIED"

echo "############ 12. TARIFF DELETE ############"
check "tariff soft delete" "$(code DELETE /tariffs/$TID)" "200"

echo ""
echo "############ RESULTS: PASS=$PASS FAIL=$FAIL ############"
