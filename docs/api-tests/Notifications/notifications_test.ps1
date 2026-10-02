# =============================================================================
# Notifications - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\Notifications\notifications_test.ps1
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

function Inbox([string]$tok) { return @((Call GET '/me/notifications?size=50' $null $tok).Body.data.content) }
function Unread([string]$tok) { return (Call GET '/me/notifications/unread-count' $null $tok).Body.data.count }
function Has([string]$tok, [string]$type) { return (@(Inbox $tok | Where-Object { $_.type -eq $type }).Count -ge 1) }

# --- Setup ----------------------------------------------------------------------------
$regA = Register (NewPhone) ('na' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('nb' + $run + '@test.np') 'LANDLORD'
$regC = Register (NewPhone) ('nc' + $run + '@test.np') 'TENANT'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken; $C = $regC.Body.data.accessToken
$prop = (Call POST '/properties' @{ name = 'Notify ' + $run; city = 'Janakpur'; electricityBillingMode = 'FIXED_PER_TENANT' } $A).Body.data
$floorId = (Call POST '/floors' @{ propertyId = $prop.id; name = 'Ground'; floorNumber = 0 } $A).Body.data.id
$r101 = (Call POST '/rooms' @{ floorId = $floorId; name = '101' } $A).Body.data.id
Assert 'nothing to start with' (((Unread $A) -eq 0) -and ((Unread $C) -eq 0))

# --- Join ---------------------------------------------------------------------------------
$jrq = (Call POST ('/join/' + $prop.joinCode) @{ } $C).Body.data
Assert 'owner: new join request' (Has $A 'JOIN_REQUESTED')
Assert 'tenant is not told about their own request' (-not (Has $C 'JOIN_REQUESTED'))
$mC = (Call POST ('/join-requests/' + $jrq.id + '/accept') @{ startedAtBs = '2082-04-01' } $A).Body.data.id
Assert 'tenant: join accepted' (Has $C 'JOIN_ACCEPTED')
Call POST ('/memberships/' + $mC + '/room-assignments') @{ roomId = $r101; effectiveFromBs = '2082-04-01'; monthlyRent = 10000 } $A | Out-Null

# --- Bill -------------------------------------------------------------------------------------
$runId = (CallKey ('/properties/' + $prop.id + '/billing-runs') @{ billingMonthBs = '2082-05'; periodStartBs = '2082-05-01'; periodEndBs = '2082-05-29'; generatedAtBs = '2082-05-29'; electricityFixedAmount = 500 } $A ([guid]::NewGuid().ToString())).Body.data.id
Assert 'no bill notice while it is a draft' (-not (Has $C 'BILL_ISSUED'))
Call POST ('/billing-runs/' + $runId + '/confirm') @{ } $A | Out-Null
$bn = Inbox $C | Where-Object { $_.type -eq 'BILL_ISSUED' } | Select-Object -First 1
Assert 'tenant: bill ready, Rs 10,500 for 2082-05' (($null -ne $bn) -and ($bn.body -match 'Rs 10,500') -and ($bn.body -match '2082-05') -and ($bn.entityType -eq 'BILL')) ($bn | ConvertTo-Json)
$billId = $bn.entityId

# --- Payments -----------------------------------------------------------------------------------
$proof = (Upload $png 'r.png' 'PAYMENT_PROOF' $prop.id $C).Body.data.id
$pp = (CallKey ('/tenant-bills/' + $billId + '/payment-proofs') @{ amount = 3000; method = 'WALLET'; paidAtBs = '2082-05-30'; proofFileId = $proof } $C ([guid]::NewGuid().ToString())).Body.data
Assert 'owner: payment waiting for approval' (Has $A 'PAYMENT_PROOF_WAITING')
Call POST ('/payments/' + $pp.id + '/approve') @{ } $A | Out-Null
Assert 'tenant: payment approved' (Has $C 'PAYMENT_APPROVED')
CallKey ('/tenant-bills/' + $billId + '/payments') @{ amount = 2000; method = 'CASH'; paidAtBs = '2082-05-30' } $A ([guid]::NewGuid().ToString()) | Out-Null
Assert 'tenant: payment recorded by the owner' (Has $C 'PAYMENT_RECORDED')
$proof2 = (Upload $png 'r.png' 'PAYMENT_PROOF' $prop.id $C).Body.data.id
$pp2 = (CallKey ('/tenant-bills/' + $billId + '/payment-proofs') @{ amount = 1000; method = 'WALLET'; paidAtBs = '2082-05-30'; proofFileId = $proof2 } $C ([guid]::NewGuid().ToString())).Body.data
Call POST ('/payments/' + $pp2.id + '/reject') @{ reason = 'Not received' } $A | Out-Null
$rej = Inbox $C | Where-Object { $_.type -eq 'PAYMENT_REJECTED' } | Select-Object -First 1
Assert 'tenant: payment rejected, with the reason' (($null -ne $rej) -and ($rej.body -match 'Not received')) ($rej | ConvertTo-Json)

# --- Requests ------------------------------------------------------------------------------------
$rq = (Call POST ('/memberships/' + $mC + '/requests') @{ type = 'MAINTENANCE'; title = 'Fan broken' } $C).Body.data
Assert 'owner: new request' (Has $A 'REQUEST_CREATED')
Call POST ('/requests/' + $rq.id + '/reject') @{ note = 'Use the spare fan' } $A | Out-Null
Assert 'tenant: request decided' (Has $C 'REQUEST_DECIDED')

# --- Move-out -----------------------------------------------------------------------------------
$today = (Call GET '/dashboard' $null $A).Body.data.todayBs
$mo = (Call POST ('/memberships/' + $mC + '/move-out') @{ plannedMoveOutBs = $today } $C).Body.data
Assert 'owner: move-out notice' (Has $A 'MOVE_OUT_NOTICE')
Call POST ('/move-outs/' + $mo.id + '/settle') @{ movedOutBs = $today } $A | Out-Null
$st = Inbox $C | Where-Object { $_.type -eq 'MOVE_OUT_SETTLED' } | Select-Object -First 1
Assert 'tenant: move-out settled, still owes Rs 5,500' (($null -ne $st) -and ($st.body -match 'Rs 5,500')) ($st | ConvertTo-Json)

# --- Inbox ---------------------------------------------------------------------------------------
$cAll = Inbox $C
Assert 'tenant inbox newest first' ($cAll[0].type -eq 'MOVE_OUT_SETTLED') ($cAll | ConvertTo-Json -Depth 3)
$before = Unread $C
$one = Call POST ('/me/notifications/' + $cAll[0].id + '/read') @{ } $C
Assert 'mark one read' (($one.Status -eq 200) -and $one.Body.data.read -and ((Unread $C) -eq ($before - 1)))
Check 'another user cannot mark it -> 404' (Call POST ('/me/notifications/' + $cAll[1].id + '/read') @{ } $A) 404 'NOTIFICATION_NOT_FOUND'
Check 'mark all read' (Call POST '/me/notifications/read-all' @{ } $C) 200
Assert 'nothing unread' ((Unread $C) -eq 0)
Assert 'other owner got nothing' ((Unread $B) -eq 0)
Check 'needs a login -> 401' (Call GET '/me/notifications') 401

# --- Device token --------------------------------------------------------------------------------
Check 'register a device token' (Call POST '/me/devices' @{ token = ('tok-' + $run); platform = 'ANDROID' } $C) 200
Check 'bad platform -> 400' (Call POST '/me/devices' @{ token = ('tok2-' + $run); platform = 'NOKIA' } $C) 400

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
