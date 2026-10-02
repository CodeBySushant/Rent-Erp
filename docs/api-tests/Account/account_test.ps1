# =============================================================================
# Account self-service (password, phone, email, devices, delete) - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\Account\account_test.ps1
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

function Login([string]$email, [string]$password) { return Call POST '/auth/login/password' @{ email = $email; password = $password } }
function OtpFor([string]$dest, [string]$purpose) {
    Start-Sleep -Milliseconds 400
    $pattern = 'DEV OTP for ' + [regex]::Escape($dest) + ' \(' + $purpose + '\): (\d{6})'
    $hit = Select-String -Path $LogFile -Pattern $pattern | Select-Object -Last 1
    if (-not $hit) { throw ('No OTP for ' + $dest + ' in ' + $LogFile) }
    return $hit.Matches[0].Groups[1].Value
}

# --- Setup: owner A (property, tenant C living there), tenant C, user E with no ties --
$emailA = 'aa' + $run + '@test.np'; $emailC = 'ac' + $run + '@test.np'; $emailE = 'ae' + $run + '@test.np'
$regA = Register (NewPhone) $emailA 'LANDLORD'
$regC = Register (NewPhone) $emailC 'TENANT'
$regE = Register (NewPhone) $emailE 'TENANT'
$A = $regA.Body.data.accessToken; $C = $regC.Body.data.accessToken; $E = $regE.Body.data.accessToken
$phoneC = $regC.Body.data.user.phone
$prop = (Call POST '/properties' @{ name = 'Acct ' + $run } $A).Body.data
$jrq = (Call POST ('/join/' + $prop.joinCode) @{ } $C).Body.data
Check 'setup: C lives at A property' (Call POST ('/join-requests/' + $jrq.id + '/accept') @{ startedAtBs = '2082-04-01' } $A) 201

# --- Password ---------------------------------------------------------------------------
Check 'wrong current password -> 400' (Call POST '/me/password' @{ currentPassword = 'nope1234'; newPassword = 'newpass123' } $A) 400 'PASSWORD_INCORRECT'
Check 'weak new password -> 400' (Call POST '/me/password' @{ currentPassword = 'rentlo123'; newPassword = 'short' } $A) 400 'WEAK_PASSWORD'
Check 'same as current -> 400' (Call POST '/me/password' @{ currentPassword = 'rentlo123'; newPassword = 'rentlo123' } $A) 400 'PASSWORD_UNCHANGED'
$A2 = (Login $emailA 'rentlo123').Body.data.accessToken
$pw = Call POST '/me/password' @{ currentPassword = 'rentlo123'; newPassword = 'newpass123' } $A
Check 'A changes the password' $pw 200
Assert 'the other device was signed out' ($pw.Body.data.otherDevicesSignedOut -ge 1) $pw.Raw
Check 'other device is logged out -> 401' (Call GET '/auth/me' $null $A2) 401
Check 'this device still works' (Call GET '/auth/me' $null $A) 200
Check 'old password no longer works -> 401' (Login $emailA 'rentlo123') 401
Check 'new password works' (Login $emailA 'newpass123') 200

# --- Devices ----------------------------------------------------------------------------
$A3 = (Login $emailA 'newpass123').Body.data.accessToken
$list = @((Call GET '/me/sessions' $null $A).Body.data)
Assert 'devices listed, exactly one is this one' (($list.Count -ge 2) -and (@($list | Where-Object { $_.current }).Count -eq 1)) ($list | ConvertTo-Json -Depth 3)
$other = ($list | Where-Object { -not $_.current } | Select-Object -First 1).id
Check 'C cannot sign out A device -> 404' (Call DELETE ('/me/sessions/' + $other) $null $C) 404 'SESSION_NOT_FOUND'
Check 'A signs out one device' (Call DELETE ('/me/sessions/' + $other) $null $A) 200
Check 'that device is logged out -> 401' (Call GET '/auth/me' $null $A3) 401
$A4 = (Login $emailA 'newpass123').Body.data.accessToken
$eo = Call POST '/me/sessions/end-others' @{ } $A
Assert 'sign out all other devices' (($eo.Status -eq 200) -and ($eo.Body.data.ended -ge 1)) $eo.Raw
Check 'A4 is logged out -> 401' (Call GET '/auth/me' $null $A4) 401

# --- Phone --------------------------------------------------------------------------------
Check 'same phone -> 400' (Call POST '/me/phone/otp' @{ phone = $regA.Body.data.user.phone } $A) 400 'SAME_PHONE'
Check 'phone of another account -> 409' (Call POST '/me/phone/otp' @{ phone = $phoneC } $A) 409 'PHONE_TAKEN'
Check 'bad phone -> 400' (Call POST '/me/phone/otp' @{ phone = '12345' } $A) 400
$newPhone = NewPhone
$ch = Call POST '/me/phone/otp' @{ phone = $newPhone } $A
Check 'code sent to the new number' $ch 200
$code = OtpFor $newPhone 'CHANGE_PHONE'
Check 'C cannot use A code -> 410' (Call POST '/me/phone' @{ verificationId = $ch.Body.data.verificationId; code = $code } $C) 410 'OTP_EXPIRED'
Check 'wrong code -> 400' (Call POST '/me/phone' @{ verificationId = $ch.Body.data.verificationId; code = '000000' } $A) 400 'OTP_INCORRECT'
$pc = Call POST '/me/phone' @{ verificationId = $ch.Body.data.verificationId; code = $code } $A
Assert 'phone changed' (($pc.Status -eq 200) -and ($pc.Body.data.phone -eq $newPhone)) $pc.Raw
Check 'the same code again -> 410' (Call POST '/me/phone' @{ verificationId = $ch.Body.data.verificationId; code = $code } $A) 410 'OTP_EXPIRED'

# --- Email ----------------------------------------------------------------------------------
Check 'email of another account -> 409' (Call POST '/me/email/otp' @{ email = $emailC } $A) 409 'EMAIL_TAKEN'
Check 'bad email -> 400' (Call POST '/me/email/otp' @{ email = 'not-an-email' } $A) 400
$newEmail = 'new' + $run + '@test.np'
$ec = Call POST '/me/email/otp' @{ email = $newEmail } $A
Check 'code sent to the new email' $ec 200
$ecode = OtpFor $newEmail 'CHANGE_EMAIL'
$em = Call POST '/me/email' @{ verificationId = $ec.Body.data.verificationId; code = $ecode } $A
Assert 'email changed' (($em.Status -eq 200) -and ($em.Body.data.email -eq $newEmail)) $em.Raw
Check 'log in with the new email' (Login $newEmail 'newpass123') 200
Check 'old email no longer logs in -> 401' (Login $emailA 'newpass123') 401

# --- Delete ---------------------------------------------------------------------------------
Check 'without DELETE -> 400' (Call DELETE '/me' @{ confirm = 'yes'; password = 'rentlo123' } $E) 400 'CONFIRM_REQUIRED'
Check 'wrong password -> 400' (Call DELETE '/me' @{ confirm = 'DELETE'; password = 'wrong1234' } $E) 400 'PASSWORD_INCORRECT'
Check 'tenant still living somewhere -> 409' (Call DELETE '/me' @{ confirm = 'DELETE'; password = 'rentlo123' } $C) 409 'ACTIVE_TENANCY'
Check 'owner with active tenants -> 409' (Call DELETE '/me' @{ confirm = 'DELETE'; password = 'newpass123' } $A) 409 'HAS_ACTIVE_TENANTS'
Check 'E deletes the account' (Call DELETE '/me' @{ confirm = 'DELETE'; password = 'rentlo123' } $E) 200
Check 'E is logged out -> 401' (Call GET '/auth/me' $null $E) 401
Check 'E cannot log in -> 401' (Login $emailE 'rentlo123') 401
Check 'C account untouched' (Call GET '/auth/me' $null $C) 200

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
