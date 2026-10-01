# =============================================================================
# AuthController + authorization - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\AuthController\auth_test.ps1
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
    try { $res = $client.SendAsync($req).Result } catch { }
    if ($res -eq $null) { throw ('No response from ' + $BaseUrl + ' - is the backend running?') }
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

# --- Sign-up ------------------------------------------------------------------
$phoneA = NewPhone
$emailA = 'a' + $run + '@test.np'
$c = Call POST '/auth/otp/request' @{ phone = $phoneA; purpose = 'SIGNUP' }
Check 'otp/request SIGNUP' $c 200
Check 'otp/request again at once -> 429' (Call POST '/auth/otp/request' @{ phone = $phoneA; purpose = 'SIGNUP' }) 429 'OTP_RESEND_TOO_SOON'
Check 'otp/request bad phone -> 400' (Call POST '/auth/otp/request' @{ phone = '12345'; purpose = 'SIGNUP' }) 400 'VALIDATION_FAILED'
$codeA = OtpFromLog $phoneA 'SIGNUP'
$wrong = if ($codeA -eq '000000') { '111111' } else { '000000' }
Check 'otp/verify wrong code -> 400' (Call POST '/auth/otp/verify' @{ verificationId = $c.Body.data.verificationId; code = $wrong }) 400 'OTP_INCORRECT'
$v = Call POST '/auth/otp/verify' @{ verificationId = $c.Body.data.verificationId; code = $codeA }
Check 'otp/verify right code' $v 200
Check 'otp/verify reuse -> 410' (Call POST '/auth/otp/verify' @{ verificationId = $c.Body.data.verificationId; code = $codeA }) 410 'OTP_EXPIRED'
Check 'register weak password -> 400' (Call POST '/auth/register' @{ name = 'Owner A'; phone = $phoneA; email = $emailA;
    password = 'abc'; role = 'LANDLORD'; verificationToken = $v.Body.data.verificationToken }) 400 'PASSWORD_WEAK'
$regA = Call POST '/auth/register' @{ name = 'Owner A'; phone = $phoneA; email = $emailA; password = 'rentlo123';
    role = 'LANDLORD'; preferredLanguage = 'hi'; verificationToken = $v.Body.data.verificationToken }
Check 'register owner A' $regA 201
Assert 'register response has no password or hash' (-not ($regA.Raw -match 'password'))
Check 'register again with spent verification -> 409' (Call POST '/auth/register' @{ name = 'Owner A'; phone = $phoneA; email = ('x' + $emailA);
    password = 'rentlo123'; role = 'LANDLORD'; verificationToken = $v.Body.data.verificationToken }) 409 'PHONE_TAKEN'
Check 'otp/request SIGNUP for taken phone -> 409' (Call POST '/auth/otp/request' @{ phone = $phoneA; purpose = 'SIGNUP' }) 409 'PHONE_TAKEN'
$tokA = $regA.Body.data.accessToken
$userA = $regA.Body.data.user

# --- Me / tokens --------------------------------------------------------------
$me = Call GET '/auth/me' $null $tokA
Check 'me with token' $me 200
Assert 'me returns the Hindi preference' ($me.Body.data.preferredLanguage -eq 'hi') $me.Raw
Check 'me without token -> 401' (Call GET '/auth/me') 401 'UNAUTHENTICATED'
Check 'me with tampered token -> 401' (Call GET '/auth/me' $null ($tokA.Substring(0, $tokA.Length - 2) + 'xx')) 401 'TOKEN_INVALID'
Check 'protected route without token -> 401' (Call GET '/properties') 401 'UNAUTHENTICATED'

# --- Password login -----------------------------------------------------------
Check 'login/password wrong -> 401' (Call POST '/auth/login/password' @{ email = $emailA; password = 'wrong1234' }) 401 'INVALID_CREDENTIALS'
Check 'login/password unknown email -> 401' (Call POST '/auth/login/password' @{ email = ('nobody' + $emailA); password = 'rentlo123' }) 401 'INVALID_CREDENTIALS'
$pl = Call POST '/auth/login/password' @{ email = $emailA.ToUpper(); password = 'rentlo123' }
Check 'login/password right (email case-insensitive)' $pl 200

# --- Refresh rotation ---------------------------------------------------------
$r1 = Call POST '/auth/refresh' @{ refreshToken = $pl.Body.data.refreshToken }
Check 'refresh' $r1 200
Check 'old refresh token after rotation -> 401' (Call POST '/auth/refresh' @{ refreshToken = $pl.Body.data.refreshToken }) 401 'REFRESH_INVALID'

# --- Logout -------------------------------------------------------------------
Check 'logout' (Call POST '/auth/logout' $null $r1.Body.data.accessToken) 200
Check 'access token after logout -> 401' (Call GET '/auth/me' $null $r1.Body.data.accessToken) 401 'SESSION_REVOKED'
Check 'refresh after logout -> 401' (Call POST '/auth/refresh' @{ refreshToken = $r1.Body.data.refreshToken }) 401 'REFRESH_INVALID'
Check 'other session still works' (Call GET '/auth/me' $null $tokA) 200

# --- OTP login ----------------------------------------------------------------
$lc = Call POST '/auth/otp/request' @{ phone = $phoneA; purpose = 'LOGIN' }
Check 'otp/request LOGIN' $lc 200
Check 'otp/request LOGIN unknown phone -> 404' (Call POST '/auth/otp/request' @{ phone = (NewPhone); purpose = 'LOGIN' }) 404 'ACCOUNT_NOT_FOUND'
Check 'login with OTP' (Call POST '/auth/login' @{ verificationId = $lc.Body.data.verificationId; code = (OtpFromLog $phoneA 'LOGIN') }) 200

# --- Authorization (IDOR) -----------------------------------------------------
$regB = Register (NewPhone) ('b' + $run + '@test.np') 'LANDLORD'
Check 'register owner B' $regB 201
$tokB = $regB.Body.data.accessToken
$userB = $regB.Body.data.user
$prop = Call POST '/properties' @{ name = ('A House ' + $run); city = 'Kathmandu'; ownerUserId = $userB.id } $tokA
Check 'owner A creates property' $prop 201
Assert 'property owner is the caller, not ownerUserId from the body' ($prop.Body.data.ownerUserId -eq $userA.id) $prop.Raw
$propId = $prop.Body.data.id
Check 'A reads own property' (Call GET ('/properties/' + $propId) $null $tokA) 200
Check 'B reads A property -> 403' (Call GET ('/properties/' + $propId) $null $tokB) 403 'FORBIDDEN'
Check 'B updates A property -> 403' (Call PUT ('/properties/' + $propId) @{ name = 'Hijacked' } $tokB) 403 'FORBIDDEN'
Check 'B deletes A property -> 403' (Call DELETE ('/properties/' + $propId) $null $tokB) 403 'FORBIDDEN'
$listB = Call GET ('/properties?ownerUserId=' + $userA.id) $null $tokB
Assert 'B list ignores ownerUserId and shows none of A' ($listB.Status -eq 200 -and $listB.Body.data.totalElements -eq 0) $listB.Raw
$listA = Call GET '/properties' $null $tokA
Assert 'A list shows A property' ($listA.Status -eq 200 -and $listA.Body.data.totalElements -eq 1) $listA.Raw
Check 'B reads A account -> 403' (Call GET ('/users/' + $userA.id) $null $tokB) 403 'FORBIDDEN'
Check 'B updates A account -> 403' (Call PUT ('/users/' + $userA.id) @{ name = 'Hijacked' } $tokB) 403 'FORBIDDEN'
Check 'B lists all users -> 403' (Call GET '/users' $null $tokB) 403 'FORBIDDEN'
Check 'B updates own account to Hindi' (Call PUT ('/users/' + $userB.id) @{ preferredLanguage = 'hi' } $tokB) 200
Check 'B grants itself access to A property -> 403' (Call POST '/property-access' @{ propertyId = $propId; userId = $userB.id; role = 'MANAGER' } $tokB) 403 'FORBIDDEN'

# --- Error shapes -------------------------------------------------------------
Check 'malformed JSON -> 400' (Call POST '/auth/login/password' $null $null '{"email":') 400 'MALFORMED_REQUEST'
Check 'bad UUID in path -> 400' (Call GET '/properties/not-a-uuid' $null $tokA) 400 'INVALID_PARAMETER'

# --- Password lockout (separate account) --------------------------------------
$emailC = 'c' + $run + '@test.np'
$regC = Register (NewPhone) $emailC 'TENANT'
Check 'register tenant C' $regC 201
for ($i = 1; $i -le 5; $i++) { $null = Call POST '/auth/login/password' @{ email = $emailC; password = 'wrongpass1' } }
Check 'login locked after 5 wrong passwords -> 429' (Call POST '/auth/login/password' @{ email = $emailC; password = 'rentlo123' }) 429 'LOGIN_LOCKED'

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
