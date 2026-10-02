# =============================================================================
# Billing flow as the app uses it - live API test (PowerShell 5.1+)
#
# Needs the backend running from backend\ with APP_ENV=local and
# AUTH_ENFORCED=true (or unset). OTP codes are read from backend\logs\rent-erp.log,
# where the local OTP sender writes them.
#
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File docs\api-tests\BillingFlow\billing_flow_test.ps1
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

# --- Setup: one property, one tenant in room 101 at Rs 12000 ------------------------
$regA = Register (NewPhone) ('ba' + $run + '@test.np') 'LANDLORD'
$regB = Register (NewPhone) ('bb' + $run + '@test.np') 'LANDLORD'
$A = $regA.Body.data.accessToken; $B = $regB.Body.data.accessToken
$propId = (Call POST '/properties' @{ name = 'Billing ' + $run; city = 'Lalitpur'; electricityBillingMode = 'FIXED_PER_TENANT' } $A).Body.data.id
$floorId = (Call POST '/floors' @{ propertyId = $propId; name = 'Ground'; floorNumber = 0 } $A).Body.data.id
$roomId = (Call POST '/rooms' @{ floorId = $floorId; name = '101' } $A).Body.data.id
$add = Call POST ('/properties/' + $propId + '/tenants') @{ fullName = 'Ram Thapa'; phone = (NewPhone); roomId = $roomId; moveInDateBs = '2082-04-01'; monthlyRent = 12000 } $A
Check 'setup: tenant added' $add 201
$mId = $add.Body.data.membershipId
$may = @{ billingMonthBs = '2082-05'; periodStartBs = '2082-05-01'; periodEndBs = '2082-05-29'; generatedAtBs = '2082-05-29'; electricityFixedAmount = 500 }
$path = '/properties/' + $propId + '/billing-runs'

# --- Draft, replay, duplicates ----------------------------------------------------
$noElec = @{ billingMonthBs = '2082-05'; periodStartBs = '2082-05-01'; periodEndBs = '2082-05-29'; generatedAtBs = '2082-05-29' }
Check 'missing electricity amount -> 400' (CallKey $path $noElec $A ([guid]::NewGuid().ToString())) 400
$key1 = [guid]::NewGuid().ToString()
$d1 = CallKey $path $may $A $key1
Check 'owner creates the May draft' $d1 201
$runId = $d1.Body.data.id
Assert 'run is a draft' ($d1.Body.data.status -eq 'DRAFT') $d1.Raw
$d2 = CallKey $path $may $A $key1
Assert 'same key replays the same run (no duplicate)' ((($d2.Status -eq 200) -or ($d2.Status -eq 201)) -and ($d2.Body.data.id -eq $runId)) $d2.Raw
Check 'new key for the same month -> 409' (CallKey $path $may $A ([guid]::NewGuid().ToString())) 409 'DUPLICATE'
Check 'other owner cannot bill A property -> 403' (CallKey $path $may $B ([guid]::NewGuid().ToString())) 403 'FORBIDDEN'

$bills = @((Call GET ('/billing-runs/' + $runId + '/bills') $null $A).Body.data)
Assert 'one draft bill' (($bills.Count -eq 1) -and ($bills[0].status -eq 'DRAFT')) ($bills | ConvertTo-Json -Depth 4)
Assert 'rent 12000 and electricity 500 from the engine' (($bills[0].rentAmount -eq 12000) -and ($bills[0].electricityAmount -eq 500)) ($bills | ConvertTo-Json -Depth 4)
$row = @((Call GET ('/properties/' + $propId + '/tenants') $null $A).Body.data)[0]
Assert 'a draft owes nothing yet' ($row.amountOwed -eq 0) ($row | ConvertTo-Json -Depth 4)

# --- Send (confirm) ---------------------------------------------------------------
$conf = Call POST ('/billing-runs/' + $runId + '/confirm') @{ } $A
Check 'owner sends the bills' $conf 200
Assert 'run confirmed' ($conf.Body.data.status -eq 'CONFIRMED') $conf.Raw
$bill = @((Call GET ('/billing-runs/' + $runId + '/bills') $null $A).Body.data)[0]
Assert 'bill issued and unpaid' (($bill.status -eq 'ISSUED') -and ($bill.paymentStatus -eq 'UNPAID') -and ($bill.balanceDue -eq $bill.totalDue)) ($bill | ConvertTo-Json -Depth 4)
$row2 = @((Call GET ('/properties/' + $propId + '/tenants') $null $A).Body.data)[0]
Assert 'tenant list now shows the amount owed' ($row2.amountOwed -eq $bill.totalDue) ($row2 | ConvertTo-Json -Depth 4)
$sum = (Call GET ('/properties/' + $propId + '/summary') $null $A).Body.data
Assert 'summary: current billing May, billed = pending' (($sum.currentBilling.billingMonthBs -eq '2082-05') -and ($sum.currentBilling.billed -eq $bill.totalDue) -and ($sum.currentBilling.pending -eq $bill.totalDue)) ($sum | ConvertTo-Json -Depth 4)
Check 'sending twice is refused' (Call POST ('/billing-runs/' + $runId + '/confirm') @{ } $A) 400
Check 'bill detail readable' (Call GET ('/tenant-bills/' + $bill.id) $null $A) 200
Check 'other owner cannot read the bill -> 403' (Call GET ('/tenant-bills/' + $bill.id) $null $B) 403 'FORBIDDEN'

# --- Discard a draft, then make it again --------------------------------------------
$apr = @{ billingMonthBs = '2082-04'; periodStartBs = '2082-04-01'; periodEndBs = '2082-04-29'; generatedAtBs = '2082-04-29'; electricityFixedAmount = 500 }
$a1 = CallKey $path $apr $A ([guid]::NewGuid().ToString())
Check 'owner creates an April draft' $a1 201
Check 'owner discards it' (Call POST ('/billing-runs/' + $a1.Body.data.id + '/cancel') @{ reason = 'Wrong amounts' } $A) 200
Check 'April can be drafted again after discarding' (CallKey $path $apr $A ([guid]::NewGuid().ToString())) 201

Write-Host ''
Write-Host ('Passed: ' + $script:pass + '   Failed: ' + $script:fail)
if ($script:fail -gt 0) { exit 1 }
