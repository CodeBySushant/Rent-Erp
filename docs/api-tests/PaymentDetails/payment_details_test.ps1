# =============================================================================
# Owner payment details - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\PaymentDetails\payment_details_test.ps1
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

function Download([string]$id, [string]$token) {
    $req = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::Get), ($BaseUrl + '/files/' + $id + '/content')
    if ($token) { $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token) }
    $res = $null
    $why = ''
    try { $res = $client.SendAsync($req).Result } catch { $e = $_.Exception; while ($e.InnerException) { $e = $e.InnerException }; $why = $e.Message }
    if ($res -eq $null) { throw ('No response from ' + $BaseUrl + ' (' + $why + '). Start the backend in its own window (cd backend; mvn spring-boot:run), wait for "Started RentErpApplication", then run this script in a second window.') }
    return [pscustomobject]@{ Status = [int]$res.StatusCode; Bytes = $res.Content.ReadAsByteArrayAsync().Result;
        Type = [string]$res.Content.Headers.ContentType }
}

# --- Setup ------------------------------------------------------------------------------
$regA = Register (NewPhone) ('ya' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('yb' + $run + '@test.np') 'LANDLORD'
$regC = Register (NewPhone) ('yc' + $run + '@test.np') 'TENANT'
$regD = Register (NewPhone) ('yd' + $run + '@test.np') 'TENANT'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken; $C = $regC.Body.data.accessToken; $D = $regD.Body.data.accessToken
$prop = (Call POST '/properties' @{ name = 'PayTo ' + $run; city = 'Hetauda' } $A).Body.data
$propB = (Call POST '/properties' @{ name = 'Other ' + $run; city = 'Hetauda' } $B).Body.data
$jrq = (Call POST ('/join/' + $prop.joinCode) @{ } $C).Body.data
Check 'setup: C becomes a tenant' (Call POST ('/join-requests/' + $jrq.id + '/accept') @{ startedAtBs = '2082-04-01' } $A) 201
$path = '/properties/' + $prop.id + '/payment-details'

# --- Owner sets the details --------------------------------------------------------------
$none = Call GET $path $null $A
Assert 'nothing set yet' (($none.Status -eq 200) -and ($null -eq $none.Body.data)) $none.Raw
Check 'empty details -> 400' (Call PUT $path @{ bankName = 'NIC Asia' } $A) 400 'DETAILS_REQUIRED'
$qr = (Upload $png 'qr.png' 'PAYMENT_QR' $prop.id $A).Body.data.id
$otherQr = (Upload $png 'qr.png' 'PAYMENT_QR' $propB.id $B).Body.data.id
$photo = (Upload $png 'm.png' 'METER_PHOTO' $prop.id $A).Body.data.id
Check 'QR of another property -> 400' (Call PUT $path @{ qrFileId = $otherQr; walletId = '9800000000' } $A) 400 'QR_INVALID'
Check 'a file that is not a payment QR -> 400' (Call PUT $path @{ qrFileId = $photo } $A) 400 'QR_INVALID'
$set = Call PUT $path @{ qrFileId = $qr; walletName = 'eSewa'; walletId = '9812345678'; bankName = 'NIC Asia Bank'; accountName = 'Shyam Shrestha'; accountNumber = '0123456789012'; branch = 'Baneshwor'; notes = 'Write your room number in the remarks' } $A
Check 'owner saves QR, wallet and bank details' $set 200
Assert 'QR link returned' ($set.Body.data.qrUrl -eq ('/api/v1/files/' + $qr + '/content')) $set.Raw

# --- Who can see them ----------------------------------------------------------------------
$seen = Call GET $path $null $C
Assert 'active tenant sees wallet and bank details' (($seen.Status -eq 200) -and ($seen.Body.data.walletId -eq '9812345678') -and ($seen.Body.data.accountNumber -eq '0123456789012')) $seen.Raw
Assert 'active tenant can open the QR' ((Download $qr $C).Status -eq 200)
Check 'user who is not a tenant here -> 403' (Call GET $path $null $D) 403 'FORBIDDEN'
Check 'other owner cannot read -> 403' (Call GET $path $null $B) 403 'FORBIDDEN'
Check 'other owner cannot change -> 403' (Call PUT $path @{ walletId = '9800000000' } $B) 403 'FORBIDDEN'
Check 'tenant cannot change -> 403' (Call PUT $path @{ walletId = '9800000000' } $C) 403 'FORBIDDEN'

# --- Update replaces the set --------------------------------------------------------------------
$upd = Call PUT $path @{ walletName = 'Khalti'; walletId = '9811111111' } $A
Assert 'update replaces the details (no QR, new wallet)' (($upd.Status -eq 200) -and ($null -eq $upd.Body.data.qrUrl) -and ($upd.Body.data.walletName -eq 'Khalti') -and ($null -eq $upd.Body.data.accountNumber)) $upd.Raw

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
