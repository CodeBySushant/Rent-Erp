# =============================================================================
# DashboardController (summaries, tenant list) - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\DashboardController\dashboard_test.ps1
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
Write-Host ('Run ' + $run + ' against ' + $BaseUrl)

# --- Accounts and data ------------------------------------------------------------
$regA = Register (NewPhone) ('da' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('db' + $run + '@test.np') 'LANDLORD'
$regC = Register (NewPhone) ('dc' + $run + '@test.np') 'TENANT'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken; $C = $regC.Body.data.accessToken
$day = '2082-06-01'
$propId = (Call POST '/properties' @{ name = 'Dash ' + $run; city = 'Lalitpur'; electricityBillingMode = 'FIXED_PER_TENANT' } $A).Body.data.id
$floorId = (Call POST '/floors' @{ propertyId = $propId; name = 'Ground'; floorNumber = 0 } $A).Body.data.id
$room1 = (Call POST '/rooms' @{ floorId = $floorId; name = '101' } $A).Body.data.id
$room2 = (Call POST '/rooms' @{ floorId = $floorId; name = '102' } $A).Body.data.id
$room3 = (Call POST '/rooms' @{ floorId = $floorId; name = '103' } $A).Body.data.id
Assert 'setup: property, floor, three rooms' ([bool]$propId -and [bool]$room3)

$ram = (Call POST '/tenant-profiles' @{ fullName = 'Ram Bahadur'; phone = (NewPhone) } $A).Body.data.id
$sita = (Call POST '/tenant-profiles' @{ fullName = 'Sita Kumari'; phone = (NewPhone) } $A).Body.data.id
$mRam = (Call POST '/memberships' @{ tenantProfileId = $ram; propertyId = $propId; startedAtBs = $day } $A).Body.data.id
$mSita = (Call POST '/memberships' @{ tenantProfileId = $sita; propertyId = $propId; startedAtBs = $day } $A).Body.data.id
Check 'Ram gets room 101 at Rs 10000' (Call POST ('/memberships/' + $mRam + '/room-assignments') @{ roomId = $room1; effectiveFromBs = $day; monthlyRent = 10000 } $A) 201

$tc = (Call POST '/tenant-profiles' @{ userId = $regC.Body.data.user.id; fullName = 'Hari Tenant'; phone = $regC.Body.data.user.phone } $C).Body.data.id
Check 'tenant C asks to join (pending)' (Call POST '/join-requests' @{ tenantProfileId = $tc; propertyId = $propId } $C) 201

# --- Property summary --------------------------------------------------------------
$s = Call GET ('/properties/' + $propId + '/summary') $null $A
Check 'A reads the property summary' $s 200
$d = $s.Body.data
Assert 'total rooms = 3' ($d.totalRooms -eq 3) $s.Raw
Assert 'occupied = 1, vacant = 2' (($d.occupiedRooms -eq 1) -and ($d.vacantRooms -eq 2)) $s.Raw
Assert 'active tenants = 2' ($d.activeTenants -eq 2) $s.Raw
Assert 'pending join requests = 1' ($d.pendingJoinRequests -eq 1) $s.Raw
Assert 'nothing owed or overdue yet' (($d.outstanding -eq 0) -and ($d.overdueTenants -eq 0)) $s.Raw
Assert 'no billing run yet' ($null -eq $d.currentBilling) $s.Raw
Check 'B reads A summary -> 403' (Call GET ('/properties/' + $propId + '/summary') $null $B) 403 'FORBIDDEN'
Check 'C reads A summary -> 403' (Call GET ('/properties/' + $propId + '/summary') $null $C) 403 'FORBIDDEN'

# --- Tenant list -----------------------------------------------------------------
$t = Call GET ('/properties/' + $propId + '/tenants') $null $A
Check 'A lists tenants' $t 200
$rows = @($t.Body.data)
Assert 'two active tenants, sorted by name' (($rows.Count -eq 2) -and ($rows[0].name -eq 'Ram Bahadur') -and ($rows[1].name -eq 'Sita Kumari')) $t.Raw
Assert 'Ram: room 101 on Ground, rent 10000' (($rows[0].rooms[0].roomName -eq '101') -and ($rows[0].rooms[0].floorName -eq 'Ground') -and ($rows[0].monthlyRent -eq 10000)) $t.Raw
Assert 'Sita: no room yet, rent 0' ((@($rows[1].rooms).Count -eq 0) -and ($rows[1].monthlyRent -eq 0)) $t.Raw
Assert 'unlinked tenants, nothing owed, no bill' ((-not $rows[0].linked) -and ($rows[0].amountOwed -eq 0) -and ($null -eq $rows[0].latestBill)) $t.Raw
Check 'status=ALL works' (Call GET ('/properties/' + $propId + '/tenants?status=ALL') $null $A) 200
Check 'bad status -> 400' (Call GET ('/properties/' + $propId + '/tenants?status=GONE') $null $A) 400 'INVALID_PARAMETER'
Check 'B lists A tenants -> 403' (Call GET ('/properties/' + $propId + '/tenants') $null $B) 403 'FORBIDDEN'

# --- Owner dashboard ---------------------------------------------------------------
$dash = Call GET '/dashboard' $null $A
Check 'A reads the dashboard' $dash 200
Assert 'A dashboard: one property, 3 rooms, 1 occupied, 2 tenants' (($dash.Body.data.properties -eq 1) -and ($dash.Body.data.totalRooms -eq 3) -and ($dash.Body.data.occupiedRooms -eq 1) -and ($dash.Body.data.activeTenants -eq 2)) $dash.Raw
Assert 'dashboard has today in BS' ($dash.Body.data.todayBs -match '^\d{4}-\d{2}-\d{2}$') $dash.Raw
$dashB = Call GET '/dashboard' $null $B
Assert 'B dashboard does not include A property' (-not ($dashB.Raw -match $propId)) $dashB.Raw
Check 'dashboard needs a login -> 401' (Call GET '/dashboard') 401 'UNAUTHENTICATED'

# --- Changes show up at once -----------------------------------------------------
Check 'Sita gets room 102' (Call POST ('/memberships/' + $mSita + '/room-assignments') @{ roomId = $room2; effectiveFromBs = $day; monthlyRent = 8000 } $A) 201
$s2 = (Call GET ('/properties/' + $propId + '/summary') $null $A).Body.data
Assert 'after assigning: occupied = 2, vacant = 1' (($s2.occupiedRooms -eq 2) -and ($s2.vacantRooms -eq 1))

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
