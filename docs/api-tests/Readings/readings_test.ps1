# =============================================================================
# Readings due and tenant readings - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\Readings\readings_test.ps1
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

$png = New-Object byte[] 512
$sig = 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
for ($i = 0; $i -lt 8; $i++) { $png[$i] = [byte]$sig[$i] }
function Join([string]$code, [string]$tok, [string]$ownerTok) {
    $jrq = (Call POST ('/join/' + $code) @{ } $tok).Body.data
    return (Call POST ('/join-requests/' + $jrq.id + '/accept') @{ startedAtBs = '2082-04-01' } $ownerTok).Body.data.id
}
function Meter([string]$label, [string]$resp, [string]$roomId) {
    $mt = (Call POST '/meters' @{ propertyId = $prop.id; label = $label; meterPurpose = 'ELECTRICITY'; meterType = 'TENANT_SUPPLY'; readingResponsibility = $resp } $A).Body.data
    Call POST ('/meters/' + $mt.id + '/coverage') @{ roomId = $roomId; effectiveFromBs = '2082-04-01' } $A | Out-Null
    return $mt.id
}
function Initial([string]$meterId, [int]$value) {
    $rd = (Call POST ('/meters/' + $meterId + '/readings') @{ readingType = 'INITIAL'; readingValue = $value; readingDateBs = '2082-04-01' } $A).Body.data
    return (Call POST ('/readings/' + $rd.id + '/confirm') @{ } $A)
}

# --- Setup: C lives in 101 (metered), D in 102 (no meter) ------------------------------
$regA = Register (NewPhone) ('ra' + $run + '@test.np') 'LANDLORD'
$regC = Register (NewPhone) ('rc' + $run + '@test.np') 'TENANT'
$regD = Register (NewPhone) ('rd' + $run + '@test.np') 'TENANT'
$A = $regA.Body.data.accessToken; $C = $regC.Body.data.accessToken; $D = $regD.Body.data.accessToken
$prop = (Call POST '/properties' @{ name = 'Meters ' + $run; city = 'Bhaktapur' } $A).Body.data
$floorId = (Call POST '/floors' @{ propertyId = $prop.id; name = 'Ground'; floorNumber = 0 } $A).Body.data.id
$r101 = (Call POST '/rooms' @{ floorId = $floorId; name = '101' } $A).Body.data.id
$r102 = (Call POST '/rooms' @{ floorId = $floorId; name = '102' } $A).Body.data.id
$mC = Join $prop.joinCode $C $A
$mD = Join $prop.joinCode $D $A
Call POST ('/memberships/' + $mC + '/room-assignments') @{ roomId = $r101; effectiveFromBs = '2082-04-01'; monthlyRent = 9000 } $A | Out-Null
Call POST ('/memberships/' + $mD + '/room-assignments') @{ roomId = $r102; effectiveFromBs = '2082-04-01'; monthlyRent = 9000 } $A | Out-Null
$m101 = Meter 'M-101' 'FIRST_SUBMISSION_WINS' $r101
$mOwner = Meter 'M-OWNER' 'LANDLORD_ONLY' $r101
$mFresh = Meter 'M-NEW' 'FIRST_SUBMISSION_WINS' $r101
Check 'setup: first reading of M-101 (1000) confirmed' (Initial $m101 1000) 200
Check 'setup: first reading of M-OWNER confirmed' (Initial $mOwner 500) 200

# --- Owner: what is due ----------------------------------------------------------------
$due = Call GET ('/properties/' + $prop.id + '/readings/due') $null $A
Check 'owner reads readings due' $due 200
$d101 = @($due.Body.data) | Where-Object { $_.label -eq 'M-101' }
Assert 'three meters listed; M-101 due, last 1000, room 101' ((@($due.Body.data).Count -eq 3) -and ($d101.thisMonth -eq 'NONE') -and ($d101.lastValue -eq 1000) -and ($d101.rooms -contains '101')) $due.Raw

# --- Tenant: my meters ---------------------------------------------------------------
$myC = @((Call GET '/me/meters' $null $C).Body.data)
$c101 = $myC | Where-Object { $_.label -eq 'M-101' }
$cOwn = $myC | Where-Object { $_.label -eq 'M-OWNER' }
$cNew = $myC | Where-Object { $_.label -eq 'M-NEW' }
Assert 'C sees the three meters of room 101' ($myC.Count -eq 3) ($myC | ConvertTo-Json -Depth 4)
Assert 'C may read M-101' ($c101.canSubmit) ($c101 | ConvertTo-Json)
Assert 'M-OWNER is owner-only' ((-not $cOwn.canSubmit) -and ($cOwn.reason -match 'owner')) ($cOwn | ConvertTo-Json)
Assert 'M-NEW waits for its first reading' ((-not $cNew.canSubmit) -and ($cNew.reason -match 'first reading')) ($cNew | ConvertTo-Json)
Assert 'D (room 102) has no meters' (@((Call GET '/me/meters' $null $D).Body.data).Count -eq 0)

# --- Tenant submits ---------------------------------------------------------------------
Check 'D cannot read a meter that does not cover their room -> 403' (Call POST ('/me/meters/' + $m101 + '/readings') @{ readingValue = 1100 } $D) 403 'FORBIDDEN'
Check 'owner-only meter -> 403' (Call POST ('/me/meters/' + $mOwner + '/readings') @{ readingValue = 600 } $C) 403 'FORBIDDEN'
Check 'meter without a first reading -> 400' (Call POST ('/me/meters/' + $mFresh + '/readings') @{ readingValue = 10 } $C) 400 'NO_FIRST_READING'
$ownerPhoto = (Upload $png 'meter.png' 'METER_PHOTO' $prop.id $A).Body.data.id
Check 'someone else''s photo -> 400' (Call POST ('/me/meters/' + $m101 + '/readings') @{ readingValue = 1100; photoFileId = $ownerPhoto } $C) 400 'PHOTO_INVALID'
$photo = (Upload $png 'meter.png' 'METER_PHOTO' $prop.id $C).Body.data.id
$sub = Call POST ('/me/meters/' + $m101 + '/readings') @{ readingValue = 1100; photoFileId = $photo; notes = 'Evening reading' } $C
Check 'C submits 1100 with a photo' $sub 201
Assert 'reading is pending' ($sub.Body.data.status -eq 'PENDING') $sub.Raw
Check 'second reading this month -> 409' (Call POST ('/me/meters/' + $m101 + '/readings') @{ readingValue = 1105 } $C) 409 'READING_ALREADY_SUBMITTED'
$c101b = @((Call GET '/me/meters' $null $C).Body.data) | Where-Object { $_.label -eq 'M-101' }
Assert 'C sees it pending and cannot submit again' (($c101b.thisMonth -eq 'PENDING') -and (-not $c101b.canSubmit)) ($c101b | ConvertTo-Json)
Check 'no tenant profile (owner) -> 403' (Call POST ('/me/meters/' + $m101 + '/readings') @{ readingValue = 1100 } $A) 403 'FORBIDDEN'

# --- Owner reviews --------------------------------------------------------------------------
$d2 = @((Call GET ('/properties/' + $prop.id + '/readings/due') $null $A).Body.data) | Where-Object { $_.label -eq 'M-101' }
Assert 'owner sees the tenant reading, value and photo' (($d2.thisMonth -eq 'PENDING') -and ($d2.thisMonthValue -eq 1100) -and $d2.submittedByTenant -and ($d2.thisMonthPhotoUrl -match '/files/')) ($d2 | ConvertTo-Json)
Check 'owner confirms it' (Call POST ('/readings/' + $d2.thisMonthReadingId + '/confirm') @{ } $A) 200
$d3 = @((Call GET ('/properties/' + $prop.id + '/readings/due') $null $A).Body.data) | Where-Object { $_.label -eq 'M-101' }
Assert 'M-101 confirmed for this month' ($d3.thisMonth -eq 'CONFIRMED') ($d3 | ConvertTo-Json)

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
