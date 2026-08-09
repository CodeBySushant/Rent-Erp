#!/bin/bash
# BillingController pass-2 end-to-end tests (SUB_METERED, NEA blended, segment engine T9,
# KUKL/boring water, CUSTOM split, M17 overage, B8 correction, B15 async). PASS/FAIL per line.
# Requires the app on :8080 and Postgres. Idempotent — uses a run-unique phone base.
B=localhost:8080/api/v1
PASS=0; FAIL=0
BASE=$(( $(date +%s) % 1000000 ))
ph(){ printf "97%06d%02d" $BASE $1; }
jget(){ python3 -c "import sys,json;d=json.load(sys.stdin);print(eval(\"d$1\"))" 2>/dev/null; }
post(){ curl -s -X POST "$B$1" -H 'Content-Type: application/json' -d "$2"; }
get(){ curl -s "$B$1"; }
code(){ curl -s -o /dev/null -w "%{http_code}" -X "$1" "$B$2" -H 'Content-Type: application/json' ${3:+-d "$3"}; }
check(){ if [ "$2" == "$3" ]; then echo "  PASS: $1 ($2)"; PASS=$((PASS+1)); else echo "  FAIL: $1 — got [$2] expected [$3]"; FAIL=$((FAIL+1)); fi; }
ncheck(){ if python3 -c "import sys;sys.exit(0 if abs(float('$2')-float('$3'))<0.005 else 1)" 2>/dev/null; then echo "  PASS: $1 ($2)"; PASS=$((PASS+1)); else echo "  FAIL: $1 — got [$2] expected [$3]"; FAIL=$((FAIL+1)); fi; }
elec_of(){ echo "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);print([b for b in d['data'] if b['membershipId']=='$2'][0]['electricityAmount'])"; }
water_of(){ echo "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);print([b for b in d['data'] if b['membershipId']=='$2'][0]['waterAmount'])"; }
sum_field(){ echo "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);print(sum(float(b['$2']) for b in d['data']))"; }

OWNER=$(post /users "{\"phone\":\"$(ph 1)\",\"fullName\":\"P2Owner\",\"role\":\"LANDLORD\"}" | jget "['data']['id']")
echo "owner=$OWNER  base=$BASE"

mk_tenant(){ # $1 phone $2 propId $3 roomId $4 rent $5 start -> membershipId
  local pid=$(post /tenant-profiles "{\"fullName\":\"T$1\",\"phone\":\"$1\"}" | jget "['data']['id']")
  local mid=$(post /memberships "{\"tenantProfileId\":\"$pid\",\"propertyId\":\"$2\",\"startedAtBs\":\"$5\"}" | jget "['data']['id']")
  post /memberships/$mid/room-assignments "{\"roomId\":\"$3\",\"effectiveFromBs\":\"$5\",\"monthlyRent\":$4}" >/dev/null
  echo "$mid"
}
mk_meter(){ # $1 propId $2 label $3 type -> meterId  (ELECTRICITY, LANDLORD_ONLY)
  post /meters "{\"propertyId\":\"$1\",\"label\":\"$2\",\"meterPurpose\":\"ELECTRICITY\",\"meterType\":\"$3\",\"readingResponsibility\":\"LANDLORD_ONLY\"}" | jget "['data']['id']"
}
cover(){ post /meters/$1/coverage "{\"roomId\":\"$2\",\"effectiveFromBs\":\"$3\"}" >/dev/null; }
reading(){ # $1 meterId $2 type $3 value $4 dateBs  (submits + confirms)
  local rid=$(post /meters/$1/readings "{\"readingType\":\"$2\",\"readingValue\":$3,\"readingDateBs\":\"$4\",\"photoUrl\":\"http://x/p.jpg\"}" | jget "['data']['id']")
  post /readings/$rid/confirm '{}' >/dev/null
}

echo "############ 1. SUB_METERED FLAT_RATE ############"
P1=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"SubFlat\",\"electricityBillingMode\":\"SUB_METERED\",\"waterMode\":\"INCLUDED_IN_RENT\",\"neaTariffMode\":\"FLAT_RATE\",\"defaultSplitRule\":\"EQUAL\",\"gracePeriodDays\":7,\"roundingMethod\":\"WHOLE_NUMBER\"}" | jget "['data']['id']")
F1=$(post /floors "{\"propertyId\":\"$P1\",\"name\":\"G\",\"floorNumber\":1}" | jget "['data']['id']")
R1=$(post /rooms "{\"floorId\":\"$F1\",\"name\":\"R1\"}" | jget "['data']['id']")
R2=$(post /rooms "{\"floorId\":\"$F1\",\"name\":\"R2\"}" | jget "['data']['id']")
M1=$(mk_tenant $(ph 11) $P1 $R1 8000 2082-04-01)
M2=$(mk_tenant $(ph 12) $P1 $R2 8000 2082-04-01)
SM1=$(mk_meter $P1 "SM-R1" TENANT_SUPPLY); cover $SM1 $R1 2082-04-01
SM2=$(mk_meter $P1 "SM-R2" TENANT_SUPPLY); cover $SM2 $R2 2082-04-01
reading $SM1 INITIAL 100 2082-04-01;  reading $SM1 BILLING_RUN 250 2082-04-31   # 150 units
reading $SM2 INITIAL 500 2082-04-01;  reading $SM2 BILLING_RUN 560 2082-04-31   # 60 units
RUN1=$(post /properties/$P1/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01","electricityRatePerUnit":15}')
RID1=$(echo "$RUN1" | jget "['data']['id']")
check "sub-metered run DRAFT" "$(echo "$RUN1" | jget "['data']['status']")" "DRAFT"
B1=$(get /billing-runs/$RID1/bills)
ncheck "M1 elec = 150u x 15 = 2250" "$(elec_of "$B1" $M1)" "2250"
ncheck "M2 elec = 60u x 15 = 900" "$(elec_of "$B1" $M2)" "900"

echo "############ 2. NEA BLENDED_RATE ############"
# Tariff: slab1 <=20u @8, slab2 rest @12; demand 75, vat 13
post /tariffs "{\"name\":\"NEA blend $BASE\",\"effectiveFromBs\":\"2082-01-05\",\"slabs\":[{\"uptoUnits\":20,\"ratePerUnit\":8},{\"uptoUnits\":null,\"ratePerUnit\":12}],\"demandCharge\":75,\"vatPercent\":13}" >/dev/null
P2=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"SubBlend\",\"electricityBillingMode\":\"SUB_METERED\",\"waterMode\":\"INCLUDED_IN_RENT\",\"neaTariffMode\":\"BLENDED_RATE\",\"defaultSplitRule\":\"EQUAL\",\"roundingMethod\":\"STANDARD\",\"commonUnitsChargedToTenants\":true}" | jget "['data']['id']")
F2=$(post /floors "{\"propertyId\":\"$P2\",\"name\":\"G\",\"floorNumber\":1}" | jget "['data']['id']")
BR1=$(post /rooms "{\"floorId\":\"$F2\",\"name\":\"B1\"}" | jget "['data']['id']")
BR2=$(post /rooms "{\"floorId\":\"$F2\",\"name\":\"B2\"}" | jget "['data']['id']")
BM1=$(mk_tenant $(ph 21) $P2 $BR1 9000 2082-04-01)
BM2=$(mk_tenant $(ph 22) $P2 $BR2 9000 2082-04-01)
MAIN=$(mk_meter $P2 "MAIN" MAIN)
BSM1=$(mk_meter $P2 "BSM1" TENANT_SUPPLY); cover $BSM1 $BR1 2082-04-01
BSM2=$(mk_meter $P2 "BSM2" TENANT_SUPPLY); cover $BSM2 $BR2 2082-04-01
reading $MAIN INITIAL 0 2082-04-01;   reading $MAIN BILLING_RUN 100 2082-04-31    # main 100 units
reading $BSM1 INITIAL 0 2082-04-01;   reading $BSM1 BILLING_RUN 40 2082-04-31     # 40 units
reading $BSM2 INITIAL 0 2082-04-01;   reading $BSM2 BILLING_RUN 40 2082-04-31     # 40 units
# energy = 20*8 + 80*12 = 1120; +demand 75 = 1195; vat13% = 155.35; total = 1350.35; blended = 13.5035
RUN2=$(post /properties/$P2/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01"}')
RID2=$(echo "$RUN2" | jget "['data']['id']")
check "blended run tariffMode" "$(echo "$RUN2" | jget "['data']['tariffMode']")" "BLENDED_RATE"
ncheck "nea blended rate = 13.5035" "$(echo "$RUN2" | jget "['data']['neaBlendedRate']")" "13.5035"
ncheck "nea total bill = 1350.35" "$(echo "$RUN2" | jget "['data']['neaTotalBill']")" "1350.35"
B2=$(get /billing-runs/$RID2/bills)
ncheck "B9: sum electricity reconciles to NEA total 1350.35" "$(sum_field "$B2" electricityAmount)" "1350.35"

echo "############ 3. SEGMENT ENGINE (T9 mid-month join) ############"
P3=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"SegBldg\",\"electricityBillingMode\":\"MAIN_METER_ONLY\",\"waterMode\":\"INCLUDED_IN_RENT\",\"defaultSplitRule\":\"EQUAL\",\"roundingMethod\":\"STANDARD\"}" | jget "['data']['id']")
F3=$(post /floors "{\"propertyId\":\"$P3\",\"name\":\"G\",\"floorNumber\":1}" | jget "['data']['id']")
SR1=$(post /rooms "{\"floorId\":\"$F3\",\"name\":\"S1\"}" | jget "['data']['id']")
SR2=$(post /rooms "{\"floorId\":\"$F3\",\"name\":\"S2\"}" | jget "['data']['id']")
SM_1=$(mk_tenant $(ph 31) $P3 $SR1 5000 2082-04-01)
SM_2=$(mk_tenant $(ph 32) $P3 $SR2 5000 2082-04-16)   # joins day 16
RUN3=$(post /properties/$P3/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01","electricityTotalAmount":3100}')
RID3=$(echo "$RUN3" | jget "['data']['id']")
B3=$(get /billing-runs/$RID3/bills)
# daily=100; days1-15 solo M1=1500; days16-31 (16d) split -> +800 each
ncheck "T9 M1 elec = 2300 (pre-join days fully on M1)" "$(elec_of "$B3" $SM_1)" "2300"
ncheck "T9 M2 elec = 800 (only post-join days)" "$(elec_of "$B3" $SM_2)" "800"
ncheck "T9 electricity reconciles to 3100" "$(sum_field "$B3" electricityAmount)" "3100"
SEGS=$(get /billing-runs/$RID3/segments)
check "segment count = 3" "$(echo "$SEGS" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data']))")" "3"
check "M2 has a MID_MONTH_JOIN segment" "$(echo "$SEGS" | python3 -c "import sys,json;d=json.load(sys.stdin);print(any(s['membershipId']=='$SM_2' and s['reason']=='MID_MONTH_JOIN' for s in d['data']))")" "True"

echo "############ 4. KUKL / BORING WATER ############"
P4=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"Kukl\",\"electricityBillingMode\":\"INCLUDED_IN_RENT\",\"waterMode\":\"KUKL_AND_BORING\",\"defaultSplitRule\":\"EQUAL\",\"roundingMethod\":\"STANDARD\"}" | jget "['data']['id']")
F4=$(post /floors "{\"propertyId\":\"$P4\",\"name\":\"G\",\"floorNumber\":1}" | jget "['data']['id']")
KR1=$(post /rooms "{\"floorId\":\"$F4\",\"name\":\"K1\"}" | jget "['data']['id']")
KR2=$(post /rooms "{\"floorId\":\"$F4\",\"name\":\"K2\"}" | jget "['data']['id']")
KM1=$(mk_tenant $(ph 41) $P4 $KR1 6000 2082-04-01)
KM2=$(mk_tenant $(ph 42) $P4 $KR2 6000 2082-04-01)
RUN4=$(post /properties/$P4/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01","waterKuklAmount":800,"waterBoringAmount":200}')
RID4=$(echo "$RUN4" | jget "['data']['id']")
B4=$(get /billing-runs/$RID4/bills)
ncheck "KUKL+boring water each = 500" "$(water_of "$B4" $KM1)" "500"
ncheck "KUKL+boring water reconciles to 1000" "$(sum_field "$B4" waterAmount)" "1000"
check "missing kukl amount -> 400" "$(code POST /properties/$P4/billing-runs '{"billingMonthBs":"2082-05","periodStartBs":"2082-05-01","periodEndBs":"2082-05-31","generatedAtBs":"2082-06-01","waterBoringAmount":200}')" "400"

echo "############ 5. CUSTOM SPLIT ############"
P5=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"Custom\",\"electricityBillingMode\":\"MAIN_METER_ONLY\",\"waterMode\":\"INCLUDED_IN_RENT\",\"defaultSplitRule\":\"CUSTOM\",\"roundingMethod\":\"STANDARD\"}" | jget "['data']['id']")
F5=$(post /floors "{\"propertyId\":\"$P5\",\"name\":\"G\",\"floorNumber\":1}" | jget "['data']['id']")
CR1=$(post /rooms "{\"floorId\":\"$F5\",\"name\":\"C1\"}" | jget "['data']['id']")
CR2=$(post /rooms "{\"floorId\":\"$F5\",\"name\":\"C2\"}" | jget "['data']['id']")
CM1=$(mk_tenant $(ph 51) $P5 $CR1 5000 2082-04-01)
CM2=$(mk_tenant $(ph 52) $P5 $CR2 5000 2082-04-01)
RUN5=$(post /properties/$P5/billing-runs "{\"billingMonthBs\":\"2082-04\",\"periodStartBs\":\"2082-04-01\",\"periodEndBs\":\"2082-04-31\",\"generatedAtBs\":\"2082-05-01\",\"electricityTotalAmount\":1000,\"customWeights\":{\"$CM1\":3,\"$CM2\":1}}")
RID5=$(echo "$RUN5" | jget "['data']['id']")
B5=$(get /billing-runs/$RID5/bills)
ncheck "CUSTOM 3:1 -> M1 elec = 750" "$(elec_of "$B5" $CM1)" "750"
ncheck "CUSTOM 3:1 -> M2 elec = 250" "$(elec_of "$B5" $CM2)" "250"
check "CUSTOM missing weights -> 400" "$(code POST /properties/$P5/billing-runs '{"billingMonthBs":"2082-05","periodStartBs":"2082-05-01","periodEndBs":"2082-05-31","generatedAtBs":"2082-06-01","electricityTotalAmount":1000}')" "400"

echo "############ 6. M17 OVERAGE (BLOCK) ############"
P6=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"Overage\",\"electricityBillingMode\":\"SUB_METERED\",\"waterMode\":\"INCLUDED_IN_RENT\",\"neaTariffMode\":\"FLAT_RATE\",\"defaultSplitRule\":\"EQUAL\",\"roundingMethod\":\"STANDARD\",\"overageAction\":\"BLOCK\",\"overageThresholdPercent\":3}" | jget "['data']['id']")
F6=$(post /floors "{\"propertyId\":\"$P6\",\"name\":\"G\",\"floorNumber\":1}" | jget "['data']['id']")
OR1=$(post /rooms "{\"floorId\":\"$F6\",\"name\":\"O1\"}" | jget "['data']['id']")
OR2=$(post /rooms "{\"floorId\":\"$F6\",\"name\":\"O2\"}" | jget "['data']['id']")
OM1=$(mk_tenant $(ph 61) $P6 $OR1 5000 2082-04-01)
OM2=$(mk_tenant $(ph 62) $P6 $OR2 5000 2082-04-01)
OMAIN=$(mk_meter $P6 "OMAIN" MAIN)
OSM1=$(mk_meter $P6 "OSM1" TENANT_SUPPLY); cover $OSM1 $OR1 2082-04-01
OSM2=$(mk_meter $P6 "OSM2" TENANT_SUPPLY); cover $OSM2 $OR2 2082-04-01
reading $OMAIN INITIAL 0 2082-04-01;  reading $OMAIN BILLING_RUN 100 2082-04-31   # main 100
reading $OSM1 INITIAL 0 2082-04-01;   reading $OSM1 BILLING_RUN 60 2082-04-31     # 60
reading $OSM2 INITIAL 0 2082-04-01;   reading $OSM2 BILLING_RUN 50 2082-04-31     # 50 -> sum 110 > 103
check "M17 sub>main+3% with BLOCK -> 400" "$(code POST /properties/$P6/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01","electricityRatePerUnit":10}')" "400"

echo "############ 7. B8 CORRECTION — unpaid cancel+regenerate ############"
P7=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"CorrectBldg\",\"electricityBillingMode\":\"FIXED_PER_TENANT\",\"waterMode\":\"INCLUDED_IN_RENT\",\"defaultSplitRule\":\"EQUAL\",\"gracePeriodDays\":7,\"roundingMethod\":\"WHOLE_NUMBER\"}" | jget "['data']['id']")
F7=$(post /floors "{\"propertyId\":\"$P7\",\"name\":\"G\",\"floorNumber\":1}" | jget "['data']['id']")
XR1=$(post /rooms "{\"floorId\":\"$F7\",\"name\":\"X1\"}" | jget "['data']['id']")
XM1=$(mk_tenant $(ph 71) $P7 $XR1 10000 2082-04-01)
RUN7=$(post /properties/$P7/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01","electricityFixedAmount":1500}')
RID7=$(echo "$RUN7" | jget "['data']['id']")
post /billing-runs/$RID7/confirm '{}' >/dev/null
BILL7=$(get /billing-runs/$RID7/bills | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
OLDTOTAL=$(get /tenant-bills/$BILL7 | jget "['data']['totalDue']")
CORR=$(post /tenant-bills/$BILL7/correct "{\"correctedTotalDue\":$(python3 -c "print(float('$OLDTOTAL')+500)"),\"reason\":\"missed a charge\"}")
check "unpaid correction -> CANCEL_REGENERATE" "$(echo "$CORR" | jget "['data']['correctionType']")" "CANCEL_REGENERATE"
check "original bill now CANCELLED" "$(get /tenant-bills/$BILL7 | jget "['data']['status']")" "CANCELLED"
NEWBILL=$(echo "$CORR" | jget "['data']['regeneratedBillId']")
ncheck "replacement total = old + 500" "$(get /tenant-bills/$NEWBILL | jget "['data']['totalDue']")" "$(python3 -c "print(float('$OLDTOTAL')+500)")"
check "replacement supersedes original" "$(get /tenant-bills/$NEWBILL | jget "['data']['status']")" "ISSUED"

echo "############ 8. B8 CORRECTION — paid/partial next-bill adjustment (psql-assisted) ############"
RUN8=$(post /properties/$P7/billing-runs '{"billingMonthBs":"2082-05","periodStartBs":"2082-05-01","periodEndBs":"2082-05-31","generatedAtBs":"2082-06-01","electricityFixedAmount":1500}')
RID8=$(echo "$RUN8" | jget "['data']['id']")
post /billing-runs/$RID8/confirm '{}' >/dev/null
BILL8=$(get /billing-runs/$RID8/bills | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
# Simulate a partial payment (Payment phase not built yet) so the paid-path branch is exercised.
PGPASSWORD="$DB_PASSWORD" psql -h "${DB_HOST:-localhost}" -p "${DB_PORT:-5432}" -U "$DB_USERNAME" -d "$DB_NAME" -c \
  "UPDATE tenant_bills SET payment_status='PARTIAL', amount_paid=1000, balance_due=total_due-1000 WHERE id='$BILL8';" >/dev/null 2>&1
CORR8=$(post /tenant-bills/$BILL8/correct '{"deltaAmount":300,"reason":"extra service"}')
check "paid/partial correction -> NEXT_BILL_ADJUSTMENT" "$(echo "$CORR8" | jget "['data']['correctionType']")" "NEXT_BILL_ADJUSTMENT"
check "original paid bill stays ISSUED" "$(get /tenant-bills/$BILL8 | jget "['data']['status']")" "ISSUED"
ADJS=$(get /memberships/$XM1/adjustments)
check "BILL_CORRECTION adjustment created PENDING" "$(echo "$ADJS" | python3 -c "import sys,json;d=json.load(sys.stdin);print(any(a['source']=='BILL_CORRECTION' and a['status']=='PENDING' for a in d['data']))")" "True"

echo "############ 9. B15 ASYNC + PROGRESS POLLING ############"
P9=$(post /properties "{\"ownerUserId\":\"$OWNER\",\"name\":\"AsyncBldg\",\"electricityBillingMode\":\"INCLUDED_IN_RENT\",\"waterMode\":\"INCLUDED_IN_RENT\",\"defaultSplitRule\":\"EQUAL\",\"roundingMethod\":\"STANDARD\"}" | jget "['data']['id']")
F9=$(post /floors "{\"propertyId\":\"$P9\",\"name\":\"G\",\"floorNumber\":1}" | jget "['data']['id']")
AR1=$(post /rooms "{\"floorId\":\"$F9\",\"name\":\"A1\"}" | jget "['data']['id']")
AR2=$(post /rooms "{\"floorId\":\"$F9\",\"name\":\"A2\"}" | jget "['data']['id']")
AM1=$(mk_tenant $(ph 81) $P9 $AR1 5000 2082-04-01)
AM2=$(mk_tenant $(ph 82) $P9 $AR2 5000 2082-04-01)
RUN9=$(post /properties/$P9/billing-runs '{"billingMonthBs":"2082-04","periodStartBs":"2082-04-01","periodEndBs":"2082-04-31","generatedAtBs":"2082-05-01","async":true}')
RID9=$(echo "$RUN9" | jget "['data']['id']")
check "async run flagged async=true" "$(echo "$RUN9" | jget "['data']['async']")" "True"
# Poll progress until COMPLETED (worker fires ~2s after enqueue).
PSTATUS=""
for i in $(seq 1 20); do
  PSTATUS=$(get /billing-runs/$RID9/progress | jget "['data']['status']")
  if [ "$PSTATUS" = "COMPLETED" ]; then break; fi
  sleep 1
done
check "async progress reached COMPLETED" "$PSTATUS" "COMPLETED"
check "async progress processed 2/2" "$(get /billing-runs/$RID9/progress | jget "['data']['processedTenants']")" "2"
check "async run built 2 bills" "$(get /billing-runs/$RID9/bills | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data']))")" "2"
check "async run confirmable after completion" "$(post /billing-runs/$RID9/confirm '{}' | jget "['data']['status']")" "CONFIRMED"

echo ""
echo "############ PASS=$PASS FAIL=$FAIL ############"
