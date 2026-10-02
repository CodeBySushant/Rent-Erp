# =============================================================================
# My Stay (tenant home) - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\MyStay\mystay_test.ps1
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

# --- Setup ---------------------------------------------------------------------------
$regA = Register (NewPhone) ('ma' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('mb' + $run + '@test.np') 'LANDLORD'
$regC = Register (NewPhone) ('mc' + $run + '@test.np') 'TENANT'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken; $C = $regC.Body.data.accessToken
$day = '2082-06-01'
$prop = (Call POST '/properties' @{ name = 'Shrestha Residency'; city = 'Kathmandu'; address = 'Baneshwor'; electricityBillingMode = 'FIXED_PER_TENANT' } $A).Body.data
$floorId = (Call POST '/floors' @{ propertyId = $prop.id; name = 'First'; floorNumber = 1 } $A).Body.data.id
$room101 = (Call POST '/rooms' @{ floorId = $floorId; name = '101' } $A).Body.data.id
Assert 'setup: property and room' ([bool]$room101)

# --- Before joining --------------------------------------------------------------------
$s0 = Call GET '/me/stay' $null $C
Check 'tenant with no profile gets an empty stay' $s0 200
Assert 'no stays, no requests' ((@($s0.Body.data.stays).Count -eq 0) -and (@($s0.Body.data.pendingRequests).Count -eq 0)) $s0.Raw
Check 'needs a login -> 401' (Call GET '/me/stay') 401 'UNAUTHENTICATED'

$jr = (Call POST ('/join/' + $prop.joinCode) @{ } $C).Body.data
$s1 = (Call GET '/me/stay' $null $C).Body.data
Assert 'pending request shows with the property name' ((@($s1.pendingRequests).Count -eq 1) -and ($s1.pendingRequests[0].propertyName -eq 'Shrestha Residency') -and (@($s1.stays).Count -eq 0))

# --- After the owner accepts and sets the room ---------------------------------------
$mId = (Call POST ('/join-requests/' + $jr.id + '/accept') @{ startedAtBs = $day } $A).Body.data.id
Check 'owner assigns room 101 at Rs 15000' (Call POST ('/memberships/' + $mId + '/room-assignments') @{ roomId = $room101; effectiveFromBs = $day; monthlyRent = 15000 } $A) 201
Check 'owner records a Rs 30000 deposit' (Call POST ('/memberships/' + $mId + '/deposit') @{ amount = 30000; currency = 'NPR'; receivedAtBs = $day } $A) 201
$res = Call GET '/me/stay' $null $C
Check 'tenant reads My Stay' $res 200
$st = $res.Body.data.stays[0]
Assert 'one active stay, no pending request' ((@($res.Body.data.stays).Count -eq 1) -and ($st.status -eq 'ACTIVE') -and (@($res.Body.data.pendingRequests).Count -eq 0)) $res.Raw
Assert 'property name, address, city and join code' (($st.property.name -eq 'Shrestha Residency') -and ($st.property.address -eq 'Baneshwor') -and ($st.property.joinCode -eq $prop.joinCode)) $res.Raw
Assert 'landlord name and phone (active tenancy)' (($st.landlordName -eq 'Test LANDLORD') -and ($st.landlordPhone -eq $regA.Body.data.user.phone)) $res.Raw
Assert 'room 101 on First, rent 15000' (($st.rooms[0].roomName -eq '101') -and ($st.rooms[0].floorName -eq 'First') -and ($st.monthlyRent -eq 15000)) $res.Raw
Assert 'deposit 30000 held' (($st.deposit.amount -eq 30000) -and ($st.deposit.status -eq 'HELD')) $res.Raw
Assert 'notice period and billing day from the property' (($st.noticePeriodDays -eq 30) -and ($st.billingDay -ge 1)) $res.Raw
Assert 'no bill yet, nothing owed' (($null -eq $st.currentBill) -and ($st.amountOwed -eq 0) -and (-not $st.overdue)) $res.Raw
Assert 'today is a BS date' ($res.Body.data.todayBs -match '^\d{4}-\d{2}-\d{2}$')

# --- Only ever the caller's own data ------------------------------------------------------
$sB = (Call GET '/me/stay' $null $B).Body.data
Assert 'owner B (not a tenant) sees nothing' ((@($sB.stays).Count -eq 0) -and (-not ((Call GET '/me/stay' $null $B).Raw -match 'Shrestha')))

# --- After moving out ------------------------------------------------------------------------
Check 'owner ends the tenancy' (Call POST ('/memberships/' + $mId + '/terminate') @{ endedAtBs = '2082-07-01'; reason = 'Moved out' } $A) 200
$st2 = (Call GET '/me/stay' $null $C).Body.data.stays[0]
Assert 'stay is terminated, rooms released' (($st2.status -eq 'TERMINATED') -and (@($st2.rooms).Count -eq 0) -and ($st2.endedAtBs -eq '2082-07-01'))
Assert 'landlord phone no longer shared' ($null -eq $st2.landlordPhone)

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
