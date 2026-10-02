# =============================================================================
# Room transfer - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\RoomTransfer\transfer_test.ps1
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

# --- Setup: Ram in 101 (Rs 10000), Sita in 103; 102 vacant ------------------------------
$regA = Register (NewPhone) ('xa' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('xb' + $run + '@test.np') 'LANDLORD'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken
$propId = (Call POST '/properties' @{ name = 'Transfer ' + $run; electricityBillingMode = 'FIXED_PER_TENANT' } $A).Body.data.id
$floorId = (Call POST '/floors' @{ propertyId = $propId; name = 'First'; floorNumber = 1 } $A).Body.data.id
$r101 = (Call POST '/rooms' @{ floorId = $floorId; name = '101' } $A).Body.data.id
$r102 = (Call POST '/rooms' @{ floorId = $floorId; name = '102' } $A).Body.data.id
$r103 = (Call POST '/rooms' @{ floorId = $floorId; name = '103' } $A).Body.data.id
$propB = (Call POST '/properties' @{ name = 'Other ' + $run; electricityBillingMode = 'FIXED_PER_TENANT' } $B).Body.data.id
$floorB = (Call POST '/floors' @{ propertyId = $propB; name = 'G'; floorNumber = 0 } $B).Body.data.id
$roomB = (Call POST '/rooms' @{ floorId = $floorB; name = 'B1' } $B).Body.data.id
$tpath = '/properties/' + $propId + '/tenants'
$ram = (Call POST $tpath @{ fullName = 'Ram Thapa'; phone = (NewPhone); roomId = $r101; moveInDateBs = '2082-04-01'; monthlyRent = 10000 } $A).Body.data.membershipId
$sita = (Call POST $tpath @{ fullName = 'Sita Rai'; phone = (NewPhone); roomId = $r103; moveInDateBs = '2082-04-01'; monthlyRent = 9000 } $A).Body.data.membershipId
Assert 'setup: two tenants added' ([bool]$ram -and [bool]$sita)
$xpath = '/memberships/' + $ram + '/room-transfer'
function RamRow { return @((Call GET $tpath $null $A).Body.data) | Where-Object { $_.membershipId -eq $ram } }

# --- Transfer ------------------------------------------------------------------------------
$t = Call POST $xpath @{ fromRoomId = $r101; toRoomId = $r102; effectiveDateBs = '2082-06-01' } $A
Check 'Ram moves 101 -> 102' $t 200
Assert 'old assignment ended, new one opened, rent kept' ([bool]$t.Body.data.endedAssignmentId -and [bool]$t.Body.data.newAssignmentId -and ($t.Body.data.monthlyRent -eq 10000)) $t.Raw
$row = RamRow
Assert 'tenant list: Ram in 102 at Rs 10000' ((@($row.rooms).Count -eq 1) -and ($row.rooms[0].roomName -eq '102') -and ($row.monthlyRent -eq 10000)) ($row | ConvertTo-Json -Depth 4)
$hist = @((Call GET ('/memberships/' + $ram + '/room-assignments') $null $A).Body.data)
Assert 'history kept: 101 closed on 2082-06-01, 102 open' ((@($hist).Count -eq 2) -and (@($hist | Where-Object { $_.roomId -eq $r101 -and $_.effectiveToBs -eq '2082-06-01' }).Count -eq 1)) ($hist | ConvertTo-Json -Depth 3)
Assert 'summary: still 2 occupied, 1 vacant' (((Call GET ('/properties/' + $propId + '/summary') $null $A).Body.data.occupiedRooms -eq 2))

# --- Refusals leave the tenant where they were ---------------------------------------------
Check 'to an occupied room -> 409' (Call POST $xpath @{ fromRoomId = $r102; toRoomId = $r103; effectiveDateBs = '2082-07-01' } $A) 409 'ROOM_OCCUPIED'
Assert 'Ram still in 102' ((RamRow).rooms[0].roomName -eq '102')
Check 'from a room the tenant is not in -> 400' (Call POST $xpath @{ fromRoomId = $r101; toRoomId = $r103; effectiveDateBs = '2082-07-01' } $A) 400 'NOT_IN_ROOM'
Check 'same room -> 400' (Call POST $xpath @{ fromRoomId = $r102; toRoomId = $r102; effectiveDateBs = '2082-07-01' } $A) 400 'SAME_ROOM'
Check 'date not after the current move-in -> 400' (Call POST $xpath @{ fromRoomId = $r102; toRoomId = $r101; effectiveDateBs = '2082-06-01' } $A) 400 'INVALID_DATE'
Check 'room of another property -> 400' (Call POST $xpath @{ fromRoomId = $r102; toRoomId = $roomB; effectiveDateBs = '2082-07-01' } $A) 400 'ROOM_NOT_IN_PROPERTY'
Check 'other owner -> 403' (Call POST $xpath @{ fromRoomId = $r102; toRoomId = $r101; effectiveDateBs = '2082-07-01' } $B) 403 'FORBIDDEN'

# --- New rent, and the freed room can be let ---------------------------------------------
$t2 = Call POST $xpath @{ fromRoomId = $r102; toRoomId = $r101; effectiveDateBs = '2082-07-01'; monthlyRent = 12000 } $A
Check 'Ram moves back to 101 at Rs 12000' $t2 200
Assert 'tenant list: Ram in 101 at Rs 12000' (((RamRow).rooms[0].roomName -eq '101') -and ((RamRow).monthlyRent -eq 12000))
Check 'room 102 can now take a new tenant' (Call POST $tpath @{ fullName = 'Hari KC'; phone = (NewPhone); roomId = $r102; moveInDateBs = '2082-07-01'; monthlyRent = 8000 } $A) 201

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
