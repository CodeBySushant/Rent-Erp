# =============================================================================
# Add Tenant (one transaction) - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\TenantOnboarding\add_tenant_test.ps1
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

# --- Setup ------------------------------------------------------------------------
$regA = Register (NewPhone) ('ta' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('tb' + $run + '@test.np') 'LANDLORD'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken
$day = '2082-06-01'
$propId = (Call POST '/properties' @{ name = 'Onboard ' + $run; electricityBillingMode = 'FIXED_PER_TENANT' } $A).Body.data.id
$floorId = (Call POST '/floors' @{ propertyId = $propId; name = 'First'; floorNumber = 1 } $A).Body.data.id
$room101 = (Call POST '/rooms' @{ floorId = $floorId; name = '101' } $A).Body.data.id
$room102 = (Call POST '/rooms' @{ floorId = $floorId; name = '102' } $A).Body.data.id
$propB = (Call POST '/properties' @{ name = 'Other ' + $run; electricityBillingMode = 'FIXED_PER_TENANT' } $B).Body.data.id
$floorB = (Call POST '/floors' @{ propertyId = $propB; name = 'Ground'; floorNumber = 0 } $B).Body.data.id
$roomB = (Call POST '/rooms' @{ floorId = $floorB; name = 'B1' } $B).Body.data.id
Assert 'setup: two properties with rooms' ([bool]$room102 -and [bool]$roomB)
$path = '/properties/' + $propId + '/tenants'
$ramPhone = NewPhone
$sitaPhone = NewPhone

function TenantCount { return @((Call GET $path $null $A).Body.data).Count }
function ProfileWithPhone([string]$phone) { return ((Call GET '/tenant-profiles?size=500' $null $A).Raw -match $phone) }

# --- Happy path: profile + membership + room + deposit in one call -----------------
$add = Call POST $path @{ fullName = 'Ram Thapa'; phone = $ramPhone; roomId = $room101; moveInDateBs = $day; monthlyRent = 12000; depositAmount = 20000 } $A
Check 'add Ram to 101 with a deposit' $add 201
$mRam = $add.Body.data.membershipId
Assert 'response has membership, profile, assignment and deposit ids' ([bool]$add.Body.data.tenantProfileId -and [bool]$add.Body.data.roomAssignmentId -and [bool]$add.Body.data.depositId) $add.Raw
$rows = @((Call GET $path $null $A).Body.data)
Assert 'Ram listed in room 101 at Rs 12000' (($rows.Count -eq 1) -and ($rows[0].rooms[0].roomName -eq '101') -and ($rows[0].monthlyRent -eq 12000)) ($rows | ConvertTo-Json -Depth 5)
$dep = Call GET ('/memberships/' + $mRam + '/deposit') $null $A
Assert 'deposit of Rs 20000 recorded' (($dep.Status -eq 200) -and ($dep.Body.data.amount -eq 20000)) $dep.Raw
$sum = (Call GET ('/properties/' + $propId + '/summary') $null $A).Body.data
Assert 'summary: 1 occupied, 1 vacant, 1 tenant' (($sum.occupiedRooms -eq 1) -and ($sum.vacantRooms -eq 1) -and ($sum.activeTenants -eq 1))

# --- Refusals leave nothing behind ---------------------------------------------------
Check 'Sita into occupied 101 -> 409' (Call POST $path @{ fullName = 'Sita Rai'; phone = $sitaPhone; roomId = $room101; moveInDateBs = $day; monthlyRent = 9000; depositAmount = 5000 } $A) 409 'ROOM_OCCUPIED'
Assert 'still one tenant after the refusal' ((TenantCount) -eq 1)
Assert 'no stray profile for Sita' (-not (ProfileWithPhone $sitaPhone))
Check 'room of another owner property -> 400' (Call POST $path @{ fullName = 'Sita Rai'; phone = $sitaPhone; roomId = $roomB; moveInDateBs = $day; monthlyRent = 9000 } $A) 400 'ROOM_NOT_IN_PROPERTY'
Check 'unknown room -> 400' (Call POST $path @{ fullName = 'Sita Rai'; phone = $sitaPhone; roomId = [guid]::NewGuid().ToString(); moveInDateBs = $day; monthlyRent = 9000 } $A) 400 'ROOM_NOT_FOUND'
Check 'same phone already living here -> 409' (Call POST $path @{ fullName = 'Ram Again'; phone = $ramPhone; roomId = $room102; moveInDateBs = $day; monthlyRent = 9000 } $A) 409 'TENANT_ALREADY_ACTIVE'
Check 'impossible BS date -> 400' (Call POST $path @{ fullName = 'Sita Rai'; phone = $sitaPhone; roomId = $room102; moveInDateBs = '2082-13-45'; monthlyRent = 9000 } $A) 400 'INVALID_DATE'
Check 'negative rent -> 400' (Call POST $path @{ fullName = 'Sita Rai'; phone = $sitaPhone; roomId = $room102; moveInDateBs = $day; monthlyRent = -1 } $A) 400 'VALIDATION_FAILED'
Check 'bad phone -> 400' (Call POST $path @{ fullName = 'Sita Rai'; phone = '12345'; roomId = $room102; moveInDateBs = $day; monthlyRent = 9000 } $A) 400 'VALIDATION_FAILED'
Check 'missing room -> 400' (Call POST $path @{ fullName = 'Sita Rai'; phone = $sitaPhone; moveInDateBs = $day; monthlyRent = 9000 } $A) 400 'VALIDATION_FAILED'
Check 'other owner adds to A property -> 403' (Call POST $path @{ fullName = 'Intruder'; phone = (NewPhone); roomId = $room102; moveInDateBs = $day; monthlyRent = 1 } $B) 403 'FORBIDDEN'
Assert 'still one tenant, no stray Sita profile' (((TenantCount) -eq 1) -and (-not (ProfileWithPhone $sitaPhone)))

# --- Without a deposit ---------------------------------------------------------------
$add2 = Call POST $path @{ fullName = 'Sita Rai'; phone = $sitaPhone; roomId = $room102; moveInDateBs = $day; monthlyRent = 9000 } $A
Check 'add Sita to 102 without a deposit' $add2 201
Assert 'no deposit id' ($null -eq $add2.Body.data.depositId) $add2.Raw
Check 'Sita has no deposit -> 404' (Call GET ('/memberships/' + $add2.Body.data.membershipId + '/deposit') $null $A) 404
$sum2 = (Call GET ('/properties/' + $propId + '/summary') $null $A).Body.data
Assert 'summary: 2 occupied, 0 vacant, 2 tenants' (($sum2.occupiedRooms -eq 2) -and ($sum2.vacantRooms -eq 0) -and ($sum2.activeTenants -eq 2))

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
