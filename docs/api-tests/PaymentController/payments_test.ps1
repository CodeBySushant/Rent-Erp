# =============================================================================
# Payments - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\PaymentController\payments_test.ps1
# Uses fresh random phone numbers, so it can be re-run without resetting the DB.
# =============================================================================
param(
    [string]$BaseUrl = 'http://localhost:8080/api/v1',
    [string]$LogFile = (Join-Path $PSScriptRoot '..\..\..\backend\logs\rent-erp.log')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$script:pass = 0
$script:fail = 0

function Call([string]$method, [string]$path, $body = $null, [string]$token = $null, [string]$raw = $null) {
    $req = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::new($method)), ($BaseUrl + $path)
    if ($token) { $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token) }
    if ($raw) {
        $req.Content = New-Object System.Net.Http.StringContent($raw, [System.Text.Encoding]::UTF8, 'application/json')
    } elseif ($body -ne $null) {
        $json = $body | ConvertTo-Json -Depth 6 -Compress
        $req.Content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, 'application/json')
    }
    $res = $null
    $why = ''
    try { $res = $client.SendAsync($req).Result } catch { $e = $_.Exception; while ($e.InnerException) { $e = $e.InnerException }; $why = $e.Message }
    if ($res -eq $null) { throw ('No response from ' + $BaseUrl + ' (' + $why + '). Start the backend in its own window (cd backend; mvn spring-boot:run), wait for "Started RentErpApplication", then run this script in a second window.') }
    $text = $res.Content.ReadAsStringAsync().Result
    $parsed = $null
    if ($text) { try { $parsed = $text | ConvertFrom-Json } catch { } }
    return [pscustomobject]@{ Status = [int]$res.StatusCode; Body = $parsed; Raw = $text }
}

function Check([string]$name, $res, [int]$expected, [string]$code = $null) {
    $ok = ($res.Status -eq $expected)
    if ($ok -and $code) { $ok = ($res.Body.code -eq $code) }
    if ($ok) { $script:pass++; Write-Host ('PASS  ' + $name) -ForegroundColor Green }
    else {
        $script:fail++
        Write-Host ('FAIL  ' + $name + '  (expected ' + $expected + ' ' + $code + ', got ' + $res.Status + ' ' + $res.Body.code + ')') -ForegroundColor Red
        if ($res.Raw) { Write-Host ('      ' + $res.Raw) }
    }
}

# A check on a value rather than a response.
function Assert([string]$name, [bool]$condition, [string]$detail = '') {
    if ($condition) { $script:pass++; Write-Host ('PASS  ' + $name) -ForegroundColor Green }
    else { $script:fail++; Write-Host ('FAIL  ' + $name + '  ' + $detail) -ForegroundColor Red }
}

function NewPhone { return '98' + (Get-Random -Minimum 10000000 -Maximum 99999999) }

function OtpFromLog([string]$phone, [string]$purpose) {
    Start-Sleep -Milliseconds 400
    $pattern = 'DEV OTP for ' + $phone + ' \(' + $purpose + '\): (\d{6})'
    $hit = Select-String -Path $LogFile -Pattern $pattern | Select-Object -Last 1
    if (-not $hit) { throw ('No OTP for ' + $phone + ' in ' + $LogFile + ' - is APP_ENV=local?') }
    return $hit.Matches[0].Groups[1].Value
}

function Register([string]$phone, [string]$email, [string]$role) {
    $c = Call POST '/auth/otp/request' @{ phone = $phone; purpose = 'SIGNUP' }
    $code = OtpFromLog $phone 'SIGNUP'
    $v = Call POST '/auth/otp/verify' @{ verificationId = $c.Body.data.verificationId; code = $code }
    return Call POST '/auth/register' @{ name = 'Test ' + $role; phone = $phone; email = $email; password = 'rentlo123';
        role = $role; preferredLanguage = 'en'; verificationToken = $v.Body.data.verificationToken }
}

if (-not (Test-Path $LogFile)) { throw ('Log file not found: ' + $LogFile) }
$run = Get-Random -Minimum 100000 -Maximum 999999
# Wait for the backend (it can take a minute after 'mvn spring-boot:run').
$healthUrl = ($BaseUrl -replace '/api/v1$', '') + '/actuator/health'
$up = $false
for ($t = 0; $t -lt 45 -and -not $up; $t++) {
    try { $h = $client.GetAsync($healthUrl).Result; if ($h -and [int]$h.StatusCode -eq 200) { $up = $true } } catch { }
    if (-not $up) { if ($t -eq 0) { Write-Host 'Waiting for the backend at' $healthUrl '...' }; Start-Sleep -Seconds 2 }
}
if (-not $up) { throw ('The backend is not answering at ' + $healthUrl + ' after 90 seconds. Start it in its own window (cd backend; mvn spring-boot:run) and keep that window open.') }
Write-Host ('Run ' + $run + ' against ' + $BaseUrl)

function Upload([byte[]]$bytes, [string]$name, [string]$purpose, [string]$propertyId, [string]$token, [string]$part = 'file') {
    $url = $BaseUrl + '/files?purpose=' + $purpose
    if ($propertyId) { $url += '&propertyId=' + $propertyId }
    $req = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::Post), $url
    if ($token) { $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token) }
    $form = New-Object System.Net.Http.MultipartFormDataContent
    $content = New-Object System.Net.Http.ByteArrayContent(,$bytes)
    $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/octet-stream')
    $form.Add($content, $part, $name)
    $req.Content = $form
    $res = $null
    $why = ''
    try { $res = $client.SendAsync($req).Result } catch { $e = $_.Exception; while ($e.InnerException) { $e = $e.InnerException }; $why = $e.Message }
    if ($res -eq $null) { throw ('No response from ' + $BaseUrl + ' (' + $why + '). Start the backend in its own window (cd backend; mvn spring-boot:run), wait for "Started RentErpApplication", then run this script in a second window.') }
    $text = $res.Content.ReadAsStringAsync().Result
    $parsed = $null
    if ($text) { try { $parsed = $text | ConvertFrom-Json } catch { } }
    return [pscustomobject]@{ Status = [int]$res.StatusCode; Body = $parsed; Raw = $text }
}

function CallKey([string]$path, $body, [string]$token, [string]$key) {
    $req = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::Post), ($BaseUrl + $path)
    $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token)
    $req.Headers.Add('Idempotency-Key', $key)
    $req.Content = New-Object System.Net.Http.StringContent(($body | ConvertTo-Json -Depth 6), [System.Text.Encoding]::UTF8, 'application/json')
    $res = $client.SendAsync($req).Result
    $text = $res.Content.ReadAsStringAsync().Result
    $parsed = $null
    if ($text) { try { $parsed = $text | ConvertFrom-Json } catch { } }
    return [pscustomobject]@{ Status = [int]$res.StatusCode; Body = $parsed; Raw = $text }
}
function BillNow([string]$id, [string]$token) { return (Call GET ('/tenant-bills/' + $id) $null $token).Body.data }
$png = New-Object byte[] 512
$sig = 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
for ($i = 0; $i -lt 8; $i++) { $png[$i] = [byte]$sig[$i] }

# --- Setup: tenant C lives in room 101 (Rs 10000), May bill issued -------------------
$regA = Register (NewPhone) ('pa' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('pb' + $run + '@test.np') 'LANDLORD'
$regC = Register (NewPhone) ('pc' + $run + '@test.np') 'TENANT'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken; $C = $regC.Body.data.accessToken
$prop = (Call POST '/properties' @{ name = 'Pay ' + $run; city = 'Pokhara'; electricityBillingMode = 'FIXED_PER_TENANT' } $A).Body.data
$floorId = (Call POST '/floors' @{ propertyId = $prop.id; name = 'Ground'; floorNumber = 0 } $A).Body.data.id
$roomId = (Call POST '/rooms' @{ floorId = $floorId; name = '101' } $A).Body.data.id
$jr = (Call POST ('/join/' + $prop.joinCode) @{ } $C).Body.data
$mId = (Call POST ('/join-requests/' + $jr.id + '/accept') @{ startedAtBs = '2082-04-01' } $A).Body.data.id
Check 'setup: room 101 at Rs 10000' (Call POST ('/memberships/' + $mId + '/room-assignments') @{ roomId = $roomId; effectiveFromBs = '2082-04-01'; monthlyRent = 10000 } $A) 201
$runPath = '/properties/' + $prop.id + '/billing-runs'
$runId = (CallKey $runPath @{ billingMonthBs = '2082-05'; periodStartBs = '2082-05-01'; periodEndBs = '2082-05-29'; generatedAtBs = '2082-05-29'; electricityFixedAmount = 500 } $A ([guid]::NewGuid().ToString())).Body.data.id
Check 'setup: May bills sent' (Call POST ('/billing-runs/' + $runId + '/confirm') @{ } $A) 200
$billId = @((Call GET ('/billing-runs/' + $runId + '/bills') $null $A).Body.data)[0].id
$total = (BillNow $billId $A).totalDue
Assert 'setup: bill of Rs 10500' ($total -eq 10500) ('' + $total)
$payPath = '/tenant-bills/' + $billId + '/payments'
$proofPath = '/tenant-bills/' + $billId + '/payment-proofs'

# --- Owner records cash ---------------------------------------------------------------
$key = [guid]::NewGuid().ToString()
$cash = CallKey $payPath @{ amount = 3000; method = 'CASH'; paidAtBs = '2082-05-30' } $A $key
Check 'owner records Rs 3000 cash' $cash 201
Assert 'approved at once, bill partly paid, Rs 7500 left' (($cash.Body.data.status -eq 'APPROVED') -and ($cash.Body.data.bill.balanceDue -eq 7500) -and ($cash.Body.data.bill.paymentStatus -eq 'PARTIAL')) $cash.Raw
$again = CallKey $payPath @{ amount = 3000; method = 'CASH'; paidAtBs = '2082-05-30' } $A $key
Assert 'same key replays: same payment, bill reduced only once' (($again.Body.data.id -eq $cash.Body.data.id) -and ((BillNow $billId $A).balanceDue -eq 7500)) $again.Raw
Check 'more than owed -> 400' (CallKey $payPath @{ amount = 8000; method = 'CASH'; paidAtBs = '2082-05-30' } $A ([guid]::NewGuid().ToString())) 400 'AMOUNT_EXCEEDS_BALANCE'
Check 'impossible date -> 400' (CallKey $payPath @{ amount = 10; method = 'CASH'; paidAtBs = '2082-13-40' } $A ([guid]::NewGuid().ToString())) 400 'INVALID_DATE'
Check 'other owner cannot record -> 403' (CallKey $payPath @{ amount = 10; method = 'CASH'; paidAtBs = '2082-05-30' } $B ([guid]::NewGuid().ToString())) 403 'FORBIDDEN'
Check 'tenant cannot record as owner -> 403' (CallKey $payPath @{ amount = 10; method = 'CASH'; paidAtBs = '2082-05-30' } $C ([guid]::NewGuid().ToString())) 403 'FORBIDDEN'

# --- Tenant sends proof, owner approves --------------------------------------------------
$proofFile = (Upload $png 'receipt.png' 'PAYMENT_PROOF' $prop.id $C).Body.data.id
$ownerFile = (Upload $png 'other.png' 'PAYMENT_PROOF' $prop.id $A).Body.data.id
Check 'proof without a file -> 400' (CallKey $proofPath @{ amount = 5000; method = 'WALLET'; paidAtBs = '2082-05-31' } $C ([guid]::NewGuid().ToString())) 400 'PROOF_REQUIRED'
Check 'proof with someone else''s file -> 400' (CallKey $proofPath @{ amount = 5000; method = 'WALLET'; paidAtBs = '2082-05-31'; proofFileId = $ownerFile } $C ([guid]::NewGuid().ToString())) 400 'PROOF_INVALID'
Check 'owner cannot send tenant proof -> 403' (CallKey $proofPath @{ amount = 5000; method = 'WALLET'; paidAtBs = '2082-05-31'; proofFileId = $ownerFile } $A ([guid]::NewGuid().ToString())) 403 'FORBIDDEN'
$pf = CallKey $proofPath @{ amount = 5000; method = 'WALLET'; paidAtBs = '2082-05-31'; reference = 'ESW-123'; proofFileId = $proofFile } $C ([guid]::NewGuid().ToString())
Check 'tenant sends Rs 5000 proof' $pf 201
Assert 'pending; bill unchanged at Rs 7500' (($pf.Body.data.status -eq 'PENDING') -and ((BillNow $billId $A).balanceDue -eq 7500)) $pf.Raw
Check 'second proof while one is pending -> 409' (CallKey $proofPath @{ amount = 100; method = 'WALLET'; paidAtBs = '2082-05-31'; proofFileId = $proofFile } $C ([guid]::NewGuid().ToString())) 409 'PAYMENT_PENDING'
Assert 'owner summary counts 1 pending payment' ((Call GET ('/properties/' + $prop.id + '/summary') $null $A).Body.data.pendingPayments -eq 1)
Assert 'owner sees it in the pending list' ((Call GET ('/properties/' + $prop.id + '/payments?status=PENDING') $null $A).Raw -match $pf.Body.data.id)
Check 'other owner cannot approve -> 403' (Call POST ('/payments/' + $pf.Body.data.id + '/approve') @{ } $B) 403 'FORBIDDEN'
Check 'tenant cannot approve own proof -> 403' (Call POST ('/payments/' + $pf.Body.data.id + '/approve') @{ } $C) 403 'FORBIDDEN'
$ap = Call POST ('/payments/' + $pf.Body.data.id + '/approve') @{ } $A
Check 'owner approves' $ap 200
Assert 'bill now Rs 2500 left' (($ap.Body.data.bill.balanceDue -eq 2500) -and ((BillNow $billId $A).amountPaid -eq 8000)) $ap.Raw
Check 'approving again -> 409, bill not reduced twice' (Call POST ('/payments/' + $pf.Body.data.id + '/approve') @{ } $A) 409 'PAYMENT_ALREADY_DECIDED'
Assert 'still Rs 2500 left' ((BillNow $billId $A).balanceDue -eq 2500)

# --- Reject and withdraw ------------------------------------------------------------------
$pr = (CallKey $proofPath @{ amount = 2500; method = 'BANK'; paidAtBs = '2082-06-01'; proofFileId = $proofFile } $C ([guid]::NewGuid().ToString())).Body.data
$rj = Call POST ('/payments/' + $pr.id + '/reject') @{ reason = 'Amount not received in bank' } $A
Check 'owner rejects a proof with a reason' $rj 200
Assert 'rejected, reason kept, bill unchanged' (($rj.Body.data.status -eq 'REJECTED') -and ($rj.Body.data.rejectionReason -eq 'Amount not received in bank') -and ((BillNow $billId $A).balanceDue -eq 2500)) $rj.Raw
Check 'reject without a reason -> 400' (Call POST ('/payments/' + $pr.id + '/reject') @{ } $A) 400 'VALIDATION_FAILED'
$pw = (CallKey $proofPath @{ amount = 2500; method = 'BANK'; paidAtBs = '2082-06-01'; proofFileId = $proofFile } $C ([guid]::NewGuid().ToString())).Body.data
Check 'other tenant/owner cannot withdraw -> 403' (Call POST ('/payments/' + $pw.id + '/cancel') @{ } $A) 403 'FORBIDDEN'
$cw = Call POST ('/payments/' + $pw.id + '/cancel') @{ } $C
Assert 'tenant withdraws own pending proof' (($cw.Status -eq 200) -and ($cw.Body.data.status -eq 'CANCELLED')) $cw.Raw

# --- Paid in full -------------------------------------------------------------------------
$last = CallKey $payPath @{ amount = 2500; method = 'BANK'; paidAtBs = '2082-06-02' } $A ([guid]::NewGuid().ToString())
Assert 'owner records the rest: PAID, nothing left' (($last.Status -eq 201) -and ($last.Body.data.bill.paymentStatus -eq 'PAID') -and ($last.Body.data.bill.balanceDue -eq 0)) $last.Raw
Check 'paying a paid bill -> 409' (CallKey $payPath @{ amount = 1; method = 'CASH'; paidAtBs = '2082-06-02' } $A ([guid]::NewGuid().ToString())) 409 'BILL_ALREADY_PAID'
$row = @((Call GET ('/properties/' + $prop.id + '/tenants') $null $A).Body.data)[0]
Assert 'tenant list: nothing owed' ($row.amountOwed -eq 0) ($row | ConvertTo-Json -Depth 4)
Assert 'summary: nothing outstanding, no pending payment' (((Call GET ('/properties/' + $prop.id + '/summary') $null $A).Body.data.outstanding -eq 0) -and ((Call GET ('/properties/' + $prop.id + '/summary') $null $A).Body.data.pendingPayments -eq 0))
Assert 'My Stay: tenant owes nothing' ((Call GET '/me/stay' $null $C).Body.data.stays[0].amountOwed -eq 0)

# --- History and guards ---------------------------------------------------------------------
$hist = Call GET $payPath $null $C
Assert 'tenant sees the bill payment history (5 entries)' (($hist.Status -eq 200) -and (@($hist.Body.data).Count -eq 5)) $hist.Raw
Check 'other owner cannot see it -> 403' (Call GET $payPath $null $B) 403 'FORBIDDEN'
Assert 'tenant: my payments' ((Call GET '/me/payments' $null $C).Body.data.totalElements -eq 5)
Check 'cancelling a run with payments -> 409' (Call POST ('/billing-runs/' + $runId + '/cancel') @{ reason = 'test' } $A) 409 'RUN_HAS_PAYMENTS'
$junDraft = (CallKey $runPath @{ billingMonthBs = '2082-06'; periodStartBs = '2082-06-01'; periodEndBs = '2082-06-29'; generatedAtBs = '2082-06-29'; electricityFixedAmount = 500 } $A ([guid]::NewGuid().ToString())).Body.data.id
$junBill = @((Call GET ('/billing-runs/' + $junDraft + '/bills') $null $A).Body.data)[0].id
Check 'paying a draft bill -> 400' (CallKey ('/tenant-bills/' + $junBill + '/payments') @{ amount = 10; method = 'CASH'; paidAtBs = '2082-06-02' } $A ([guid]::NewGuid().ToString())) 400 'BILL_NOT_PAYABLE'

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
