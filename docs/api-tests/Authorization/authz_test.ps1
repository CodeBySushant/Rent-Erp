# =============================================================================
# Authorization across every domain - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\Authorization\authz_test.ps1
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

# --- Accounts -------------------------------------------------------------------
$regA = Register (NewPhone) ('za' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('zb' + $run + '@test.np') 'LANDLORD'
$regC = Register (NewPhone) ('zc' + $run + '@test.np') 'TENANT'
Check 'register owner A' $regA 201
Check 'register owner B' $regB 201
Check 'register tenant C' $regC 201
$A = $regA.Body.data.accessToken
$B = $regB.Body.data.accessToken
$C = $regC.Body.data.accessToken
$userA = $regA.Body.data.user.id
$userC = $regC.Body.data.user.id
$day = '2082-06-01'

# --- Owner A builds a property ---------------------------------------------------
$p = Call POST '/properties' @{ name = 'Authz ' + $run; city = 'Kathmandu'; electricityBillingMode = 'FIXED_PER_TENANT' } $A
Check 'A creates property' $p 201
$P = $p.Body.data.id
$pb = Call POST '/properties' @{ name = 'Authz B ' + $run; city = 'Pokhara'; electricityBillingMode = 'FIXED_PER_TENANT' } $B
Check 'B creates own property' $pb 201
$PB = $pb.Body.data.id
$f = Call POST '/floors' @{ propertyId = $P; name = 'Ground'; floorNumber = 0 } $A
Check 'A creates floor' $f 201
$F = $f.Body.data.id
$r = Call POST '/rooms' @{ floorId = $F; name = '101' } $A
Check 'A creates room' $r 201
$R = $r.Body.data.id
$ct = Call POST '/charge-templates' @{ propertyId = $P; name = 'Internet'; amount = 500; splitBasis = 'FIXED_PER_TENANT' } $A
Check 'A creates charge template' $ct 201
$CT = $ct.Body.data.id
$t = Call POST '/tenant-profiles' @{ fullName = 'Ram Unlinked'; phone = (NewPhone) } $A
Check 'A creates unlinked tenant profile' $t 201
$T = $t.Body.data.id
Check 'A reads the profile it created' (Call GET ('/tenant-profiles/' + $T) $null $A) 200
$m = Call POST '/memberships' @{ tenantProfileId = $T; propertyId = $P; startedAtBs = $day } $A
Check 'A creates membership' $m 201
$M = $m.Body.data.id
$d = Call POST ('/memberships/' + $M + '/deposit') @{ amount = 5000; currency = 'NPR'; receivedAtBs = $day } $A
Check 'A records deposit' $d 201

# --- Owner A reads its own data ----------------------------------------------------
Check 'A reads floor' (Call GET ('/floors/' + $F) $null $A) 200
Check 'A reads room' (Call GET ('/rooms/' + $R) $null $A) 200
Check 'A lists rooms of its property' (Call GET ('/rooms?propertyId=' + $P) $null $A) 200
Check 'A reads charge template' (Call GET ('/charge-templates/' + $CT) $null $A) 200
Check 'A reads membership' (Call GET ('/memberships/' + $M) $null $A) 200
Check 'A reads deposit' (Call GET ('/memberships/' + $M + '/deposit') $null $A) 200

# --- Owner B cannot reach A's data -------------------------------------------------
Check 'B reads A floor -> 403' (Call GET ('/floors/' + $F) $null $B) 403 'FORBIDDEN'
Check 'B renames A floor -> 403' (Call PUT ('/floors/' + $F) @{ name = 'Hacked' } $B) 403 'FORBIDDEN'
Check 'B deletes A room -> 403' (Call DELETE ('/rooms/' + $R) $null $B) 403 'FORBIDDEN'
Check 'B adds a floor to A property -> 403' (Call POST '/floors' @{ propertyId = $P; name = 'Roof'; floorNumber = 9 } $B) 403 'FORBIDDEN'
Check 'B lists A floors -> 403' (Call GET ('/floors?propertyId=' + $P) $null $B) 403 'FORBIDDEN'
Check 'B lists floors without a property -> 400' (Call GET '/floors' $null $B) 400 'PROPERTY_ID_REQUIRED'
Check 'B lists rooms by A floor -> 403' (Call GET ('/rooms?floorId=' + $F) $null $B) 403 'FORBIDDEN'
Check 'B reads A charge template -> 403' (Call GET ('/charge-templates/' + $CT) $null $B) 403 'FORBIDDEN'
Check 'B lists meters without a property -> 400' (Call GET '/meters' $null $B) 400 'PROPERTY_ID_REQUIRED'
Check 'B reads A tenant profile -> 403' (Call GET ('/tenant-profiles/' + $T) $null $B) 403 'FORBIDDEN'
Check 'B edits A tenant profile -> 403' (Call PUT ('/tenant-profiles/' + $T) @{ fullName = 'Hacked' } $B) 403 'FORBIDDEN'
$bl = Call GET '/tenant-profiles?size=200' $null $B
Check 'B lists tenant profiles' $bl 200
Assert 'B profile list does not contain A tenant' (-not ($bl.Raw -match $T)) $bl.Raw
Check 'B attaches A tenant to B property -> 403' (Call POST '/memberships' @{ tenantProfileId = $T; propertyId = $PB; startedAtBs = $day } $B) 403 'FORBIDDEN'
Check 'B reads A membership -> 403' (Call GET ('/memberships/' + $M) $null $B) 403 'FORBIDDEN'
Check 'B lists A memberships -> 403' (Call GET ('/memberships?propertyId=' + $P) $null $B) 403 'FORBIDDEN'
Check 'B lists memberships of A tenant -> 403' (Call GET ('/memberships?tenantProfileId=' + $T) $null $B) 403 'FORBIDDEN'
Check 'B reads A deposit -> 403' (Call GET ('/memberships/' + $M + '/deposit') $null $B) 403 'FORBIDDEN'
Check 'B reads A bills -> 403' (Call GET ('/memberships/' + $M + '/bills') $null $B) 403 'FORBIDDEN'
Check 'B lists A billing runs -> 403' (Call GET ('/properties/' + $P + '/billing-runs') $null $B) 403 'FORBIDDEN'
Check 'B lists blocked tenants of A property -> 403' (Call GET ('/properties/' + $P + '/blocked-tenants') $null $B) 403 'FORBIDDEN'
Check 'tariff list needs a login -> 401' (Call GET '/tariffs') 401 'UNAUTHENTICATED'

# --- Tenant C: join, then only their own data --------------------------------------
Check 'C reads A membership -> 403' (Call GET ('/memberships/' + $M) $null $C) 403 'FORBIDDEN'
Check 'C links a profile to A account -> 403' (Call POST '/tenant-profiles' @{ userId = $userA; fullName = 'Fake'; phone = (NewPhone) } $C) 403 'FORBIDDEN'
$tc = Call POST '/tenant-profiles' @{ userId = $userC; fullName = 'Sita Tenant'; phone = $regC.Body.data.user.phone } $C
Check 'C creates own linked profile' $tc 201
$TC = $tc.Body.data.id
Check 'C asks to join for someone else -> 403' (Call POST '/join-requests' @{ tenantProfileId = $T; propertyId = $P } $C) 403 'FORBIDDEN'
$jr = Call POST '/join-requests' @{ tenantProfileId = $TC; propertyId = $P; message = 'Room 101 please' } $C
Check 'C asks to join A property' $jr 201
$JR = $jr.Body.data.id
Check 'B accepts C request on A property -> 403' (Call POST ('/join-requests/' + $JR + '/accept') @{ startedAtBs = $day } $B) 403 'FORBIDDEN'
Check 'A reads C request' (Call GET ('/join-requests/' + $JR) $null $A) 200
Check 'A reads C profile through the request' (Call GET ('/tenant-profiles/' + $TC) $null $A) 200
$acc = Call POST ('/join-requests/' + $JR + '/accept') @{ startedAtBs = $day } $A
Check 'A accepts C request (membership created)' $acc 201
$MC = $acc.Body.data.id
Check 'C reads own membership' (Call GET ('/memberships/' + $MC) $null $C) 200
Check 'C lists own memberships' (Call GET ('/memberships?tenantProfileId=' + $TC) $null $C) 200
Check 'C lists another tenant memberships -> 403' (Call GET ('/memberships?tenantProfileId=' + $T) $null $C) 403 'FORBIDDEN'
Check 'C lists own bills' (Call GET ('/memberships/' + $MC + '/bills') $null $C) 200
Check 'C reads other tenant bills -> 403' (Call GET ('/memberships/' + $M + '/bills') $null $C) 403 'FORBIDDEN'
Check 'C records own deposit -> 403' (Call POST ('/memberships/' + $MC + '/deposit') @{ amount = 1; currency = 'NPR'; receivedAtBs = $day } $C) 403 'FORBIDDEN'
Check 'C terminates own membership -> 403' (Call POST ('/memberships/' + $MC + '/terminate') @{ endedAtBs = $day } $C) 403 'FORBIDDEN'
Check 'C approves own KYC -> 403' (Call POST ('/tenant-profiles/' + $TC + '/kyc/approve') $null $C) 403 'FORBIDDEN'
Check 'C reads A floor -> 403' (Call GET ('/floors/' + $F) $null $C) 403 'FORBIDDEN'
Check 'B reads C membership -> 403' (Call GET ('/memberships/' + $MC) $null $B) 403 'FORBIDDEN'

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
