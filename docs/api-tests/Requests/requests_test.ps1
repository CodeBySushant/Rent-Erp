# =============================================================================
# Tenant requests - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\Requests\requests_test.ps1
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

# --- Setup: C in 101, D in 103, 102 vacant --------------------------------------------
$regA = Register (NewPhone) ('qa' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('qb' + $run + '@test.np') 'LANDLORD'
$regC = Register (NewPhone) ('qc' + $run + '@test.np') 'TENANT'
$regD = Register (NewPhone) ('qd' + $run + '@test.np') 'TENANT'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken; $C = $regC.Body.data.accessToken; $D = $regD.Body.data.accessToken
$prop = (Call POST '/properties' @{ name = 'Req ' + $run; city = 'Butwal' } $A).Body.data
$floorId = (Call POST '/floors' @{ propertyId = $prop.id; name = 'Ground'; floorNumber = 0 } $A).Body.data.id
$r101 = (Call POST '/rooms' @{ floorId = $floorId; name = '101' } $A).Body.data.id
$r102 = (Call POST '/rooms' @{ floorId = $floorId; name = '102' } $A).Body.data.id
$r103 = (Call POST '/rooms' @{ floorId = $floorId; name = '103' } $A).Body.data.id
function JoinRoom([string]$tok, [string]$roomId) {
    $jrq = (Call POST ('/join/' + $prop.joinCode) @{ } $tok).Body.data
    $mid = (Call POST ('/join-requests/' + $jrq.id + '/accept') @{ startedAtBs = '2082-04-01' } $A).Body.data.id
    Call POST ('/memberships/' + $mid + '/room-assignments') @{ roomId = $roomId; effectiveFromBs = '2082-04-01'; monthlyRent = 9000 } $A | Out-Null
    return $mid
}
$mC = JoinRoom $C $r101
$mD = JoinRoom $D $r103
$today = (Call GET '/dashboard' $null $A).Body.data.todayBs
$rpath = '/memberships/' + $mC + '/requests'

# --- Tenant raises requests --------------------------------------------------------------
$photo = (Upload $png 'leak.png' 'REQUEST_PHOTO' $prop.id $C).Body.data.id
$m1 = Call POST $rpath @{ type = 'MAINTENANCE'; title = 'Bathroom tap leaking'; description = 'Since yesterday'; photoFileId = $photo } $C
Check 'C reports a leak with a photo' $m1 201
Assert 'pending, from the tenant, photo url' (($m1.Body.data.status -eq 'PENDING') -and $m1.Body.data.byTenant -and ($m1.Body.data.photoUrl -match '/files/')) $m1.Raw
$m2 = (Call POST $rpath @{ type = 'MAINTENANCE'; title = 'Light not working' } $C).Body.data
Assert 'a second maintenance request is allowed' ($m2.status -eq 'PENDING')
$rc = Call POST $rpath @{ type = 'ROOM_CHANGE'; title = 'Need a quieter room'; preferredDateBs = $today } $C
Check 'C asks for a room change' $rc 201
Check 'second open room change -> 409' (Call POST $rpath @{ type = 'ROOM_CHANGE'; title = 'Again' } $C) 409 'REQUEST_ALREADY_OPEN'
Check 'vacate without a date -> 400' (Call POST $rpath @{ type = 'VACATE'; title = 'Leaving' } $C) 400 'DATE_REQUIRED'
Check 'date in the past -> 400' (Call POST $rpath @{ type = 'VACATE'; title = 'Leaving'; preferredDateBs = '2080-01-01' } $C) 400 'DATE_IN_PAST'
Check 'other tenant cannot raise for C -> 403' (Call POST $rpath @{ type = 'OTHER'; title = 'x' } $D) 403 'FORBIDDEN'
Check 'other owner cannot raise for C -> 403' (Call POST $rpath @{ type = 'OTHER'; title = 'x' } $B) 403 'FORBIDDEN'
Assert 'owner sees 3 pending requests' (@((Call GET ('/properties/' + $prop.id + '/requests?status=PENDING') $null $A).Body.data).Count -eq 3)
Assert 'D does not see C requests' (@((Call GET '/me/requests' $null $D).Body.data).Count -eq 0)
Check 'other owner cannot list -> 403' (Call GET ('/properties/' + $prop.id + '/requests') $null $B) 403 'FORBIDDEN'

# --- Owner decides -------------------------------------------------------------------------
Check 'reject without a note -> 400' (Call POST ('/requests/' + $m2.id + '/reject') @{ } $A) 400 'NOTE_REQUIRED'
$rj = Call POST ('/requests/' + $m2.id + '/reject') @{ note = 'Bulb is the tenant''s to replace' } $A
Assert 'rejected with the note' (($rj.Status -eq 200) -and ($rj.Body.data.status -eq 'REJECTED')) $rj.Raw
Check 'deciding twice -> 409' (Call POST ('/requests/' + $m2.id + '/reject') @{ note = 'x' } $A) 409 'REQUEST_ALREADY_DECIDED'
Check 'tenant cannot approve -> 403' (Call POST ('/requests/' + $m1.Body.data.id + '/approve') @{ } $C) 403 'FORBIDDEN'
Check 'owner approves the leak' (Call POST ('/requests/' + $m1.Body.data.id + '/approve') @{ note = 'Plumber tomorrow' } $A) 200
$done = Call POST ('/requests/' + $m1.Body.data.id + '/complete') @{ note = 'Fixed' } $A
Assert 'leak marked completed' (($done.Status -eq 200) -and ($done.Body.data.status -eq 'COMPLETED')) $done.Raw

# --- Room change: approving with a room moves the tenant -------------------------------------
Check 'approve into an occupied room -> 409' (Call POST ('/requests/' + $rc.Body.data.id + '/approve') @{ toRoomId = $r103; effectiveDateBs = $today } $A) 409 'ROOM_OCCUPIED'
Assert 'request still pending, C still in 101' (((Call GET $rpath $null $A).Raw -match 'PENDING') -and ((@((Call GET ('/properties/' + $prop.id + '/tenants') $null $A).Body.data) | Where-Object { $_.membershipId -eq $mC }).rooms[0].roomName -eq '101'))
$ok = Call POST ('/requests/' + $rc.Body.data.id + '/approve') @{ toRoomId = $r102; effectiveDateBs = $today } $A
Assert 'approved into 102: completed, C now in 102' (($ok.Body.data.status -eq 'COMPLETED') -and ((@((Call GET ('/properties/' + $prop.id + '/tenants') $null $A).Body.data) | Where-Object { $_.membershipId -eq $mC }).rooms[0].roomName -eq '102')) $ok.Raw

# --- Vacate: approving opens the move-out notice --------------------------------------------
$va = (Call POST $rpath @{ type = 'VACATE'; title = 'Moving to Kathmandu'; preferredDateBs = $today } $C).Body.data
Check 'owner approves the vacate request' (Call POST ('/requests/' + $va.id + '/approve') @{ } $A) 200
$mo = (Call GET ('/memberships/' + $mC + '/move-out') $null $A).Body.data
Assert 'move-out notice opened for that date' (($mo.status -eq 'NOTICE_GIVEN') -and ($mo.plannedMoveOutBs -eq $today)) ($mo | ConvertTo-Json)

# --- Withdraw ----------------------------------------------------------------------------------
$ot = (Call POST $rpath @{ type = 'OTHER'; title = 'Parking space?' } $C).Body.data
Check 'other tenant cannot withdraw it -> 403' (Call POST ('/requests/' + $ot.id + '/cancel') @{ } $D) 403 'FORBIDDEN'
$cw = Call POST ('/requests/' + $ot.id + '/cancel') @{ } $C
Assert 'tenant withdraws it' (($cw.Status -eq 200) -and ($cw.Body.data.status -eq 'CANCELLED')) $cw.Raw
Assert 'C sees all 5 of their requests' (@((Call GET '/me/requests' $null $C).Body.data).Count -eq 5)

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
