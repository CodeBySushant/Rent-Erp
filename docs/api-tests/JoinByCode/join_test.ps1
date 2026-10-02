# =============================================================================
# Join by property code - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\JoinByCode\join_test.ps1
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

# --- Setup --------------------------------------------------------------------------
$regA = Register (NewPhone) ('ja' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('jb' + $run + '@test.np') 'LANDLORD'
$regC = Register (NewPhone) ('jc' + $run + '@test.np') 'TENANT'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken; $C = $regC.Body.data.accessToken
$prop = Call POST '/properties' @{ name = 'Shrestha Residency'; city = 'Kathmandu'; electricityBillingMode = 'FIXED_PER_TENANT' } $A
Check 'A creates property' $prop 201
$propId = $prop.Body.data.id
$code = $prop.Body.data.joinCode
Assert 'join code built from name and city' ($code -match '^SR-KTH-\d{4}$') $prop.Raw
Assert 'join code is not the internal id' (-not ($propId -like ('*' + $code.Substring(7) + '*') -and $code -eq $propId))
Check 'property read includes the join code' (Call GET ('/properties/' + $propId) $null $A) 200

# --- Look-up ("Property found") -------------------------------------------------------
$look = Call GET ('/join/' + $code) $null $C
Check 'tenant looks the code up' $look 200
Assert 'shows name, city and landlord' (($look.Body.data.name -eq 'Shrestha Residency') -and ($look.Body.data.city -eq 'Kathmandu') -and ($look.Body.data.landlordName -eq 'Test LANDLORD')) $look.Raw
Assert 'not a member, nothing pending yet' ((-not $look.Body.data.alreadyMember) -and (-not $look.Body.data.requestPending)) $look.Raw
Assert 'landlord phone is not shared' (-not ($look.Raw -match $regA.Body.data.user.phone)) $look.Raw
Check 'lower-case code works' (Call GET ('/join/' + $code.ToLower()) $null $C) 200
Check 'unknown code -> 404' (Call GET '/join/ZZ-ZZZ-0000' $null $C) 404 'JOIN_CODE_NOT_FOUND'
Check 'look-up needs a login -> 401' (Call GET ('/join/' + $code)) 401 'UNAUTHENTICATED'

# --- Ask to join ------------------------------------------------------------------------
$j = Call POST ('/join/' + $code) @{ message = 'Room 101 please' } $C
Check 'tenant asks to join (profile made from the account)' $j 201
Assert 'request is pending' ($j.Body.data.status -eq 'PENDING') $j.Raw
Check 'asking again -> 409' (Call POST ('/join/' + $code) @{ } $C) 409 'REQUEST_PENDING'
Assert 'look-up now shows the pending request' ((Call GET ('/join/' + $code) $null $C).Body.data.requestPending)
Check 'owner cannot join own property -> 400' (Call POST ('/join/' + $code) @{ } $A) 400 'OWN_PROPERTY'

# --- Owner sees and accepts --------------------------------------------------------------
$list = Call GET ('/join-requests?propertyId=' + $propId) $null $A
Assert 'owner sees the request' ($list.Raw -match $j.Body.data.id) $list.Raw
Check 'other owner cannot accept -> 403' (Call POST ('/join-requests/' + $j.Body.data.id + '/accept') @{ startedAtBs = '2082-06-01' } $B) 403 'FORBIDDEN'
Check 'owner accepts' (Call POST ('/join-requests/' + $j.Body.data.id + '/accept') @{ startedAtBs = '2082-06-01' } $A) 201
Assert 'look-up now shows membership' ((Call GET ('/join/' + $code) $null $C).Body.data.alreadyMember)
Check 'joining again as a member -> 409' (Call POST ('/join/' + $code) @{ } $C) 409 'ALREADY_MEMBER'

# --- Guessing codes is limited -----------------------------------------------------------
$last = $null
for ($i = 0; $i -lt 21; $i++) { $last = Call GET ('/join/QQ-QQQ-' + (1000 + $i)) $null $B }
Check '21 look-ups in a row -> 429' $last 429 'JOIN_LOOKUP_LIMIT'

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
