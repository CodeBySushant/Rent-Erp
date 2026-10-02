# =============================================================================
# Move-out (notice and settlement) - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\MoveOut\moveout_test.ps1
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

# --- Setup: C in 101 (Rs 10000, deposit 15000), D in 102 (Rs 8000, no deposit); May billed --
$regA = Register (NewPhone) ('oa' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('ob' + $run + '@test.np') 'LANDLORD'
$regC = Register (NewPhone) ('oc' + $run + '@test.np') 'TENANT'
$regD = Register (NewPhone) ('od' + $run + '@test.np') 'TENANT'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken; $C = $regC.Body.data.accessToken; $D = $regD.Body.data.accessToken
$prop = (Call POST '/properties' @{ name = 'Move ' + $run; city = 'Dharan'; electricityBillingMode = 'FIXED_PER_TENANT' } $A).Body.data
$floorId = (Call POST '/floors' @{ propertyId = $prop.id; name = 'Ground'; floorNumber = 0 } $A).Body.data.id
$r101 = (Call POST '/rooms' @{ floorId = $floorId; name = '101' } $A).Body.data.id
$r102 = (Call POST '/rooms' @{ floorId = $floorId; name = '102' } $A).Body.data.id
function JoinRoom([string]$tok, [string]$roomId, [int]$rent) {
    $jrq = (Call POST ('/join/' + $prop.joinCode) @{ } $tok).Body.data
    $mid = (Call POST ('/join-requests/' + $jrq.id + '/accept') @{ startedAtBs = '2082-04-01' } $A).Body.data.id
    Call POST ('/memberships/' + $mid + '/room-assignments') @{ roomId = $roomId; effectiveFromBs = '2082-04-01'; monthlyRent = $rent } $A | Out-Null
    return $mid
}
$mC = JoinRoom $C $r101 10000
$mD = JoinRoom $D $r102 8000
Check 'setup: C deposit Rs 15000' (Call POST ('/memberships/' + $mC + '/deposit') @{ amount = 15000; currency = 'NPR'; receivedAtBs = '2082-04-01' } $A) 201
$runId = (CallKey ('/properties/' + $prop.id + '/billing-runs') @{ billingMonthBs = '2082-05'; periodStartBs = '2082-05-01'; periodEndBs = '2082-05-29'; generatedAtBs = '2082-05-29'; electricityFixedAmount = 500 } $A ([guid]::NewGuid().ToString())).Body.data.id
Check 'setup: May bills sent' (Call POST ('/billing-runs/' + $runId + '/confirm') @{ } $A) 200
$mayBills = @((Call GET ('/billing-runs/' + $runId + '/bills') $null $A).Body.data)
$billC = ($mayBills | Where-Object { $_.membershipId -eq $mC }).id
$billD = ($mayBills | Where-Object { $_.membershipId -eq $mD }).id
$today = (Call GET '/dashboard' $null $A).Body.data.todayBs
Assert 'setup: bills Rs 10500 and Rs 8500' (((BillNow $billC $A).totalDue -eq 10500) -and ((BillNow $billD $A).totalDue -eq 8500))

# --- Notice ----------------------------------------------------------------------------
$n = Call POST ('/memberships/' + $mC + '/move-out') @{ plannedMoveOutBs = $today; reason = 'Moving to Pokhara' } $C
Check 'tenant gives notice' $n 201
Assert 'by the tenant, short notice (under 30 days)' ($n.Body.data.requestedByTenant -and $n.Body.data.shortNotice -and ($n.Body.data.status -eq 'NOTICE_GIVEN')) $n.Raw
Check 'second open notice -> 409' (Call POST ('/memberships/' + $mC + '/move-out') @{ plannedMoveOutBs = $today } $C) 409 'MOVE_OUT_PENDING'
Check 'other owner cannot give notice -> 403' (Call POST ('/memberships/' + $mD + '/move-out') @{ plannedMoveOutBs = $today } $B) 403 'FORBIDDEN'
Check 'other tenant cannot give notice for C -> 403' (Call POST ('/memberships/' + $mC + '/move-out') @{ plannedMoveOutBs = $today } $D) 403 'FORBIDDEN'
Check 'date in the past -> 400' (Call POST ('/memberships/' + $mD + '/move-out') @{ plannedMoveOutBs = '2080-01-01' } $D) 400 'DATE_IN_PAST'
$pv = (Call GET ('/memberships/' + $mC + '/move-out') $null $A).Body.data
Assert 'owner preview: owes 10500, deposit 15000' (($pv.previewOutstanding -eq 10500) -and ($pv.previewDeposit -eq 15000) -and ($pv.tenantName -eq 'Test TENANT') -and ($pv.rooms -eq '101')) ($pv | ConvertTo-Json)
Check 'tenant withdraws the notice' (Call POST ('/move-outs/' + $n.Body.data.id + '/cancel') @{ } $C) 200
$n2 = Call POST ('/memberships/' + $mC + '/move-out') @{ plannedMoveOutBs = $today } $C
Check 'tenant gives notice again' $n2 201
$moC = $n2.Body.data.id

# --- Settlement guards --------------------------------------------------------------------
Check 'tenant cannot settle -> 403' (Call POST ('/move-outs/' + $moC + '/settle') @{ movedOutBs = $today } $C) 403 'FORBIDDEN'
Check 'other owner cannot settle -> 403' (Call POST ('/move-outs/' + $moC + '/settle') @{ movedOutBs = $today } $B) 403 'FORBIDDEN'
$proof = (Upload $png 'r.png' 'PAYMENT_PROOF' $prop.id $C).Body.data.id
$pp = (CallKey ('/tenant-bills/' + $billC + '/payment-proofs') @{ amount = 500; method = 'WALLET'; paidAtBs = $today; proofFileId = $proof } $C ([guid]::NewGuid().ToString())).Body.data
Check 'a waiting payment blocks settlement -> 409' (Call POST ('/move-outs/' + $moC + '/settle') @{ movedOutBs = $today } $A) 409 'PENDING_PAYMENTS'
Call POST ('/payments/' + $pp.id + '/reject') @{ reason = 'Not received' } $A | Out-Null

# --- Settle C: deposit pays the bill, then final charges and damages, rest refunded -------
$s = Call POST ('/move-outs/' + $moC + '/settle') @{ movedOutBs = $today; finalCharges = 800; finalChargesNote = 'Last days electricity'; deductions = 1000; deductionsNote = 'Broken window' } $A
Check 'owner settles C' $s 200
$sd = $s.Body.data
Assert 'refund 2700 (15000 - 10500 - 800 - 1000), nothing still owed' (($sd.refundAmount -eq 2700) -and ($sd.tenantStillOwes -eq 0) -and ($sd.depositApplied -eq 12300) -and ($sd.outstandingBefore -eq 10500)) $s.Raw
Assert 'C bill paid by the deposit' ((BillNow $billC $A).paymentStatus -eq 'PAID')
$hist = @((Call GET ('/tenant-bills/' + $billC + '/payments') $null $A).Body.data)
Assert 'bill history shows the deposit payment' (@($hist | Where-Object { $_.note -eq 'Deposit applied at move-out' -and $_.status -eq 'APPROVED' }).Count -eq 1) ($hist | ConvertTo-Json -Depth 4)
Assert 'deposit marked partly refunded' ((Call GET ('/memberships/' + $mC + '/deposit') $null $A).Body.data.status -eq 'REFUNDED_PARTIAL')
Assert 'tenancy ended, room 101 free' (((Call GET ('/memberships/' + $mC) $null $A).Body.data.status -eq 'TERMINATED') -and ((Call GET ('/properties/' + $prop.id + '/summary') $null $A).Body.data.occupiedRooms -eq 1))
Assert 'My Stay shows the ended tenancy' ((Call GET '/me/stay' $null $C).Body.data.stays[0].status -eq 'TERMINATED')
Check 'settling again -> 409' (Call POST ('/move-outs/' + $moC + '/settle') @{ movedOutBs = $today } $A) 409 'MOVE_OUT_CLOSED'
Check 'notice on an ended tenancy -> 400' (Call POST ('/memberships/' + $mC + '/move-out') @{ plannedMoveOutBs = $today } $A) 400 'TENANCY_ENDED'

# --- Settle D: no deposit, the bill stays owed ------------------------------------------------
$nD = (Call POST ('/memberships/' + $mD + '/move-out') @{ plannedMoveOutBs = $today } $A).Body.data
Assert 'owner gives notice for D' (($nD.status -eq 'NOTICE_GIVEN') -and (-not $nD.requestedByTenant))
$sD = (Call POST ('/move-outs/' + $nD.id + '/settle') @{ movedOutBs = $today } $A).Body.data
Assert 'D: no refund, still owes the Rs 8500 bill' (($sD.refundAmount -eq 0) -and ($sD.tenantStillOwes -eq 8500)) ($sD | ConvertTo-Json)
Assert 'D bill still unpaid (history kept)' ((BillNow $billD $A).balanceDue -eq 8500)
Assert 'both rooms free' ((Call GET ('/properties/' + $prop.id + '/summary') $null $A).Body.data.occupiedRooms -eq 0)
Assert 'owner list shows two settled move-outs' (@((Call GET ('/properties/' + $prop.id + '/move-outs?status=SETTLED') $null $A).Body.data).Count -eq 2)

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
