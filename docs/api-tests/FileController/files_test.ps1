# =============================================================================
# FileController (uploads) - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\FileController\files_test.ps1
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

# PowerShell variable names ignore case: $qrRes and $QR must not share a name.
# Multipart upload. $bytes is the raw file; $purpose / $propertyId as query.
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

function Png([int]$size) {
    $b = New-Object byte[] $size
    $sig = 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    for ($i = 0; $i -lt 8; $i++) { $b[$i] = [byte]$sig[$i] }
    for ($i = 8; $i -lt $size; $i++) { $b[$i] = [byte]($i % 251) }
    return ,$b
}
$png = Png 2048
$pdf = [System.Text.Encoding]::ASCII.GetBytes("%PDF-1.4`n1 0 obj << >> endobj`ntrailer << >>`n%%EOF")
$text = [System.Text.Encoding]::ASCII.GetBytes('just some text, not an image')

# --- Accounts and a property ---------------------------------------------------
$regA = Register (NewPhone) ('fa' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('fb' + $run + '@test.np') 'LANDLORD'
$regC = Register (NewPhone) ('fc' + $run + '@test.np') 'TENANT'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken; $C = $regC.Body.data.accessToken
$P = (Call POST '/properties' @{ name = 'Files ' + $run; electricityBillingMode = 'FIXED_PER_TENANT' } $A).Body.data.id
Assert 'setup: property created' ([bool]$P)

# --- Owner A uploads the payment QR --------------------------------------------
$qrRes = Upload $png '..\..\qr.png' 'PAYMENT_QR' $P $A
Check 'A uploads payment QR' $qrRes 201
$QR = $qrRes.Body.data.id
Assert 'response has a content url' ($qrRes.Body.data.url -eq ('/api/v1/files/' + $QR + '/content')) $qrRes.Raw
Assert 'client folder names are dropped' ($qrRes.Body.data.originalName -eq 'qr.png') $qrRes.Raw
Assert 'type comes from the bytes' ($qrRes.Body.data.contentType -eq 'image/png') $qrRes.Raw
$dl = Download $QR $A
Assert 'A downloads the QR, same bytes' (($dl.Status -eq 200) -and ([Convert]::ToBase64String($dl.Bytes) -eq [Convert]::ToBase64String($png))) ('' + $dl.Status)
Check 'A reads QR metadata' (Call GET ('/files/' + $QR) $null $A) 200
Assert 'B cannot download A QR -> 403' ((Download $QR $B).Status -eq 403)
Assert 'C (not a tenant yet) cannot download A QR -> 403' ((Download $QR $C).Status -eq 403)
Assert 'no token -> 401' ((Download $QR $null).Status -eq 401)

# --- Tenant C joins, then may see the QR (only the QR) -------------------------
$TC = (Call POST '/tenant-profiles' @{ userId = $regC.Body.data.user.id; fullName = 'Files Tenant'; phone = $regC.Body.data.user.phone } $C).Body.data.id
$JR = (Call POST '/join-requests' @{ tenantProfileId = $TC; propertyId = $P } $C).Body.data.id
Check 'A accepts C' (Call POST ('/join-requests/' + $JR + '/accept') @{ startedAtBs = '2082-06-01' } $A) 201
Assert 'active tenant C downloads the payment QR' ((Download $QR $C).Status -eq 200)

# --- Tenant C uploads a payment proof for the property -------------------------
$proofRes = Upload $pdf 'receipt.pdf' 'PAYMENT_PROOF' $P $C
Check 'C uploads payment proof (PDF)' $proofRes 201
$PROOF = $proofRes.Body.data.id
Assert 'owner A can read the proof' ((Download $PROOF $A).Status -eq 200)
Assert 'owner B cannot read the proof -> 403' ((Download $PROOF $B).Status -eq 403)
Check 'C cannot upload a payment QR -> 403' (Upload $png 'qr.png' 'PAYMENT_QR' $P $C) 403 'FORBIDDEN'
Check 'B cannot attach a file to A property -> 403' (Upload $png 'x.png' 'METER_PHOTO' $P $B) 403 'FORBIDDEN'

# --- Validation ----------------------------------------------------------------
Check 'text file -> 400' (Upload $text 'notes.png' 'OTHER' $null $A) 400 'FILE_TYPE_NOT_ALLOWED'
Check 'PDF where a photo is required -> 400' (Upload $pdf 'meter.pdf' 'METER_PHOTO' $null $A) 400 'FILE_TYPE_NOT_ALLOWED'
Check 'unknown purpose -> 400' (Upload $png 'x.png' 'SELFIES' $null $A) 400 'INVALID_PARAMETER'
Check 'missing file part -> 400' (Upload $png 'x.png' 'OTHER' $null $A 'document') 400 'MISSING_PARAMETER'
Check 'over 5 MB -> 413' (Upload (Png 5500000) 'big.png' 'OTHER' $null $A) 413 'FILE_TOO_LARGE'
Check 'upload without a token -> 401' (Upload $png 'x.png' 'OTHER' $null $null) 401 'UNAUTHENTICATED'
$selfieRes = Upload $png 'selfie.png' 'KYC_SELFIE' $null $C
Check 'C uploads a private selfie (no property)' $selfieRes 201
Assert 'A cannot read C private selfie -> 403' ((Download $selfieRes.Body.data.id $A).Status -eq 403)

# --- Delete --------------------------------------------------------------------
Check 'B deletes A QR -> 403' (Call DELETE ('/files/' + $QR) $null $B) 403 'FORBIDDEN'
Check 'A deletes own QR' (Call DELETE ('/files/' + $QR) $null $A) 200
Assert 'deleted file -> 404' ((Download $QR $A).Status -eq 404)

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
