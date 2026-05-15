#Requires -Version 5.1
<#
.SYNOPSIS
    End-to-end smoke test for JobPilot backend (no Docker required).
.DESCRIPTION
    Starts a mock AI service + Spring Boot backend (smoke profile with H2 + real Redis),
    then runs 8 API smoke tests and reports Pass/Fail for each.
#>

param(
    [string]$JavaHome = "D:\Java-jdk-21",
    [int]$BackendPort = 8080,
    [int]$MockAiPort = 8000,
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = "Continue"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $ProjectRoot "backend"
$MockAiScript = Join-Path $PSScriptRoot "mock_ai_server.py"

$BASE = "http://localhost:$BackendPort/api/v1"
$passCount = 0
$failCount = 0
$testResults = @()
$script:TOKEN = $null
$script:USER_ID = $null
$script:SESSION_ID = $null
$script:RESUME_ID = $null

# ── Helpers ──────────────────────────────────────────────────────────────────

function Write-Step($num, $title) {
    Write-Host ""
    Write-Host ("=" * 70) -ForegroundColor Cyan
    Write-Host "  STEP $num : $title" -ForegroundColor Cyan
    Write-Host ("=" * 70) -ForegroundColor Cyan
}

function Assert($label, $condition, $detail = "") {
    if ($condition) {
        Write-Host "  [PASS] $label" -ForegroundColor Green
        $script:passCount++
        $script:testResults += @{ Step = $label; Result = "PASS"; Detail = "" }
    } else {
        Write-Host "  [FAIL] $label" -ForegroundColor Red
        if ($detail) { Write-Host "         -> $detail" -ForegroundColor Yellow }
        $script:failCount++
        $script:testResults += @{ Step = $label; Result = "FAIL"; Detail = $detail }
    }
}

function Invoke-Api {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [object]$Body = $null,
        [switch]$Auth,
        [hashtable]$Headers = @{},
        [switch]$Multipart,
        [string]$ContentType = "application/json"
    )
    $uri = "$BASE$Path"
    $h = @{}
    if ($Auth -and $script:TOKEN) {
        $h["Authorization"] = "Bearer $($script:TOKEN)"
    }
    foreach ($k in $Headers.Keys) { $h[$k] = $Headers[$k] }

    try {
        $params = @{
            Method  = $Method
            Uri     = $uri
            Headers = $h
        }
        if ($Body -ne $null) {
            if ($Multipart) {
                $params.Body = $Body
            } else {
                $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
                $params.ContentType = $ContentType
            }
        }
        $response = Invoke-RestMethod @params -ErrorAction Stop
        return $response
    } catch {
        $statusCode = 0
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        Write-Host "  [HTTP ERROR] $Method $uri -> $statusCode : $($_.Exception.Message)" -ForegroundColor DarkYellow
        return $null
    }
}

function Wait-ForService($url, $name, $maxWait = 90) {
    Write-Host "  Waiting for $name at $url ..." -NoNewline
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $maxWait) {
        try {
            $null = Invoke-RestMethod -Uri $url -Method GET -TimeoutSec 3 -ErrorAction Stop
            Write-Host " READY ($([int]$sw.Elapsed.TotalSeconds)s)" -ForegroundColor Green
            return $true
        } catch {
            Write-Host "." -NoNewline
            Start-Sleep -Seconds 2
        }
    }
    Write-Host " TIMEOUT" -ForegroundColor Red
    return $false
}

# ── Cleanup on exit ──────────────────────────────────────────────────────────

$script:processes = @()

function Stop-All {
    foreach ($p in $script:processes) {
        if ($p -and !$p.HasExited) {
            try { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } catch {}
            Write-Host "  Stopped process PID=$($p.Id)"
        }
    }
}

# Register cleanup
Register-EngineEvent -SourceIdentifier PowerShell.Exiting -Action { Stop-All } | Out-Null

# ── MAIN ─────────────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "  JobPilot E2E Smoke Test Suite" -ForegroundColor White
Write-Host "  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray
Write-Host ""

# ── Pre-flight checks ────────────────────────────────────────────────────────

Write-Host "[Pre-flight] Checking prerequisites..." -ForegroundColor Yellow

# Check Java
$env:JAVA_HOME = $JavaHome
$javaExe = Join-Path $JavaHome "bin\java.exe"
if (!(Test-Path $javaExe)) {
    Write-Host "  FATAL: Java not found at $javaExe" -ForegroundColor Red
    exit 1
}
Write-Host "  Java: $javaExe OK" -ForegroundColor Gray

# Check Redis
$redisOk = $false
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect("localhost", 6379)
    $tcp.Close()
    $redisOk = $true
    Write-Host "  Redis: localhost:6379 OK" -ForegroundColor Gray
} catch {
    Write-Host "  FATAL: Redis not available on port 6379" -ForegroundColor Red
    exit 1
}

# Check Gradle wrapper
$gradlewBat = Join-Path $BackendDir "gradlew.bat"
if (!(Test-Path $gradlewBat)) {
    Write-Host "  FATAL: gradlew.bat not found at $gradlewBat" -ForegroundColor Red
    exit 1
}
Write-Host "  Gradle wrapper: OK" -ForegroundColor Gray

# ── Start Mock AI Service ────────────────────────────────────────────────────

Write-Host ""
Write-Host "[Setup] Starting Mock AI Service on port $MockAiPort ..." -ForegroundColor Yellow
$mockAiProc = Start-Process -FilePath "python" -ArgumentList $MockAiScript -PassThru -WindowStyle Hidden -RedirectStandardOutput "$env:TEMP\mock_ai_stdout.log" -RedirectStandardError "$env:TEMP\mock_ai_stderr.log"
$script:processes += $mockAiProc
Write-Host "  Mock AI PID: $($mockAiProc.Id)" -ForegroundColor Gray
Start-Sleep -Seconds 2

if (!$(Wait-ForService "http://localhost:$MockAiPort/health" "Mock AI Service" 15)) {
    Write-Host "  Mock AI failed to start. Stderr:" -ForegroundColor Red
    Get-Content "$env:TEMP\mock_ai_stderr.log" -ErrorAction SilentlyContinue
    Stop-All
    exit 1
}

# ── Build & Start Spring Boot Backend ────────────────────────────────────────

Write-Host ""
Write-Host "[Setup] Building Spring Boot backend (first build may download dependencies)..." -ForegroundColor Yellow

$buildOutput = & cmd /c "cd /d `"$BackendDir`" && set JAVA_HOME=$JavaHome && gradlew.bat bootJar --no-daemon -q 2>&1"
$buildExit = $LASTEXITCODE

if ($buildExit -ne 0) {
    Write-Host "  BUILD FAILED (exit code $buildExit)" -ForegroundColor Red
    Write-Host $buildOutput -ForegroundColor DarkGray
    Stop-All
    exit 1
}
Write-Host "  Build successful." -ForegroundColor Green

Write-Host ""
Write-Host "[Setup] Starting Spring Boot backend (smoke profile)..." -ForegroundColor Yellow

$jarFile = Get-ChildItem -Path (Join-Path $BackendDir "build\libs") -Filter "*.jar" | Where-Object { $_.Name -notmatch "-plain" } | Select-Object -First 1
if (!$jarFile) {
    Write-Host "  FATAL: No JAR found in build/libs" -ForegroundColor Red
    Stop-All
    exit 1
}
Write-Host "  JAR: $($jarFile.Name)" -ForegroundColor Gray

$backendProc = Start-Process -FilePath $javaExe -ArgumentList "-jar", "`"$($jarFile.FullName)`"", "--spring.profiles.active=smoke" -PassThru -WindowStyle Hidden -RedirectStandardOutput "$env:TEMP\backend_stdout.log" -RedirectStandardError "$env:TEMP\backend_stderr.log"
$script:processes += $backendProc
Write-Host "  Backend PID: $($backendProc.Id)" -ForegroundColor Gray

if (!$(Wait-ForService "http://localhost:$BackendPort/api/v1/health" "Spring Boot Backend" $TimeoutSeconds)) {
    Write-Host "  Backend failed to start. Last 30 lines of log:" -ForegroundColor Red
    Get-Content "$env:TEMP\backend_stderr.log" -Tail 30 -ErrorAction SilentlyContinue
    Stop-All
    exit 1
}

# ── Run Smoke Tests ──────────────────────────────────────────────────────────

Write-Host ""
Write-Host ("#" * 70) -ForegroundColor Magenta
Write-Host "  RUNNING SMOKE TESTS" -ForegroundColor Magenta
Write-Host ("#" * 70) -ForegroundColor Magenta

# ── STEP 1: Register ─────────────────────────────────────────────────────────
Write-Step 1 "POST /auth/register - Register new user"

$regBody = @{
    username       = "smoketest_$(Get-Random -Maximum 99999)"
    email          = "smoke_$(Get-Random -Maximum 99999)@test.com"
    password       = "Smoke@12345"
    major          = "计算机科学与技术"
    graduationYear = 2025
}
$regResp = Invoke-Api -Method POST -Path "/auth/register" -Body $regBody

Assert "Register returns HTTP 200 with code=0" ($regResp -ne $null -and $regResp.code -eq 0) "Response: $($regResp | ConvertTo-Json -Compress -Depth 3)"
if ($regResp -and $regResp.code -eq 0) {
    $script:TOKEN = $regResp.data.token
    $script:USER_ID = $regResp.data.user.id
    Assert "Register returns valid JWT token" ($null -ne $script:TOKEN -and $script:TOKEN.Length -gt 20) "Token length: $($script:TOKEN.Length)"
    Assert "Register returns user info with id" ($null -ne $script:USER_ID) "UserId: $script:USER_ID"
}

# ── STEP 2: Login ────────────────────────────────────────────────────────────
Write-Step 2 "POST /auth/login - Login and get JWT Token"

$loginBody = @{
    email    = $regBody.email
    password = $regBody.password
}
$loginResp = Invoke-Api -Method POST -Path "/auth/login" -Body $loginBody

Assert "Login returns HTTP 200 with code=0" ($loginResp -ne $null -and $loginResp.code -eq 0) "Response: $($loginResp | ConvertTo-Json -Compress -Depth 3)"
if ($loginResp -and $loginResp.code -eq 0) {
    Assert "Login returns new JWT token" ($null -ne $loginResp.data.token -and $loginResp.data.token.Length -gt 20)
    $script:TOKEN = $loginResp.data.token
}

# ── STEP 3: Create Chat Session ─────────────────────────────────────────────
Write-Step 3 "POST /chat/sessions - Create chat session (Redis + MySQL dual-write)"

$sessResp = Invoke-Api -Method POST -Path "/chat/sessions" -Auth

Assert "Create session returns HTTP 200 with code=0" ($sessResp -ne $null -and $sessResp.code -eq 0) "Response: $($sessResp | ConvertTo-Json -Compress -Depth 3)"
if ($sessResp -and $sessResp.code -eq 0) {
    $script:SESSION_ID = $sessResp.data.sessionId
    Assert "Session has UUID sessionId" ($script:SESSION_ID -match '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') "SessionId: $script:SESSION_ID"
    Assert "Session has id and channel" ($null -ne $sessResp.data.id -and $sessResp.data.id -gt 0) "Id: $($sessResp.data.id)"
}

# ── STEP 4: GET /jobs/categories ─────────────────────────────────────────────
Write-Step 4 "GET /jobs/categories - Verify 21 professional categories"

$catResp = Invoke-Api -Method GET -Path "/jobs/categories" -Auth

Assert "Categories returns HTTP 200 with code=0" ($catResp -ne $null -and $catResp.code -eq 0) "Response: $($catResp | ConvertTo-Json -Compress -Depth 3)"
if ($catResp -and $catResp.code -eq 0) {
    $catCount = $catResp.data.Count
    Assert "Returns exactly 21 categories (got $catCount)" ($catCount -eq 21) "First 3: $($catResp.data[0..2] -join ', ')"
}

# ── STEP 5: GET /jobs/search?q=计算机相关工作 ────────────────────────────────
Write-Step 5 "GET /jobs/search?q=计算机 - Verify fuzzy search mock return"

$searchResp = Invoke-Api -Method GET -Path "/jobs/search?q=计算机" -Auth

Assert "Job search returns HTTP 200 with code=0" ($searchResp -ne $null -and $searchResp.code -eq 0) "Response: $($searchResp | ConvertTo-Json -Compress -Depth 5)"
if ($searchResp -and $searchResp.code -eq 0) {
    $resultCount = $searchResp.data.Count
    Assert "Search returns results (got $resultCount)" ($resultCount -gt 0) "Results count: $resultCount"
    if ($resultCount -gt 0) {
        $first = $searchResp.data[0]
        Assert "Each result has job_title, tags, confidence" ($null -ne $first.job_title -and $null -ne $first.tags -and $null -ne $first.confidence) "First: $($first.job_title)"
    }
}

# ── STEP 6: GET /companies/字节跳动 ──────────────────────────────────────────
Write-Step 6 "GET /companies/字节跳动 - Verify three-tier cache (AI mock first, Redis second)"

$companyResp1 = Invoke-Api -Method GET -Path "/companies/字节跳动" -Auth

Assert "Company intel returns HTTP 200 with code=0" ($companyResp1 -ne $null -and $companyResp1.code -eq 0) "Response: $($companyResp1 | ConvertTo-Json -Compress -Depth 5)"
if ($companyResp1 -and $companyResp1.code -eq 0) {
    Assert "Company name is 字节跳动" ($companyResp1.data.name -eq "字节跳动") "Got: $($companyResp1.data.name)"
    Assert "Has basic_info with industry" ($null -ne $companyResp1.data.basic_info.industry) "Industry: $($companyResp1.data.basic_info.industry)"
    Assert "Has salary_data" ($null -ne $companyResp1.data.salary_data) "Salary keys: $($companyResp1.data.salary_data.PSObject.Properties.Name -join ', ')"
    Assert "Has review_summary with dimensions" ($null -ne $companyResp1.data.review_summary.dimensions) "Dimensions count: $($companyResp1.data.review_summary.dimensions.Count)"
}

# Second request should hit Redis cache
$companyResp2 = Invoke-Api -Method GET -Path "/companies/字节跳动" -Auth
Assert "Second request (Redis cache hit) returns same data" ($companyResp2 -ne $null -and $companyResp2.code -eq 0 -and $companyResp2.data.name -eq "字节跳动")

# ── STEP 7: POST /resumes/upload ─────────────────────────────────────────────
Write-Step 7 "POST /resumes/upload - Upload PDF and verify file path format"

# Create a minimal PDF file
$pdfPath = Join-Path $env:TEMP "smoke_test_resume.pdf"
$pdfContent = @(
    '%PDF-1.4',
    '1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj',
    '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj',
    '3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R>>endobj',
    'xref',
    '0 4',
    '0000000000 65535 f ',
    '0000000009 00000 n ',
    '0000000058 00000 n ',
    '0000000115 00000 n ',
    'trailer<</Size 4/Root 1 0 R>>',
    'startxref',
    '190',
    '%%EOF'
)
[System.IO.File]::WriteAllLines($pdfPath, $pdfContent)

# Multipart upload using .NET HttpClient
try {
    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $script:TOKEN)

    $multipartContent = [System.Net.Http.MultipartFormDataContent]::new()
    $fileBytes = [System.IO.File]::ReadAllBytes($pdfPath)
    $fileContent = [System.Net.Http.ByteArrayContent]::new($fileBytes)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new("application/pdf")
    $multipartContent.Add($fileContent, "file", "smoke_test_resume.pdf")

    $uploadRespMsg = $client.PostAsync("$BASE/resumes/upload", $multipartContent).Result
    $uploadBody = $uploadRespMsg.Content.ReadAsStringAsync().Result
    $uploadResp = $uploadBody | ConvertFrom-Json

    Assert "Upload returns HTTP 200 with code=0" ($uploadRespMsg.IsSuccessStatusCode -and $uploadResp.code -eq 0) "Body: $uploadBody"

    if ($uploadResp.code -eq 0) {
        $script:RESUME_ID = $uploadResp.data.id
        $filePath = $uploadResp.data.rawFileUrl
        Assert "Resume has id ($script:RESUME_ID)" ($null -ne $script:RESUME_ID)
        Assert "File path matches format /uploads/{userId}/..." ($filePath -match "^/uploads/\d+/\d{14}_smoke_test_resume\.pdf$") "Path: $filePath"
        Assert "Resume has structuredData (AI parsed)" ($null -ne $uploadResp.data.structuredData) "Keys: $($uploadResp.data.structuredData.PSObject.Properties.Name -join ', ')"
        if ($uploadResp.data.structuredData) {
            Assert "structuredData has basic_info with name" ($null -ne $uploadResp.data.structuredData.basic_info.name) "Name: $($uploadResp.data.structuredData.basic_info.name)"
        }
    }

    $client.Dispose()
} catch {
    Assert "Resume upload request" $false "Exception: $($_.Exception.Message)"
}

# ── STEP 8: GET /resumes/{id} ────────────────────────────────────────────────
Write-Step 8 "GET /resumes/{id} - Verify AI parsed resume with radar chart data"

if ($script:RESUME_ID) {
    $resumeResp = Invoke-Api -Method GET -Path "/resumes/$($script:RESUME_ID)" -Auth

    Assert "Get resume returns HTTP 200 with code=0" ($resumeResp -ne $null -and $resumeResp.code -eq 0) "Response: $($resumeResp | ConvertTo-Json -Compress -Depth 5)"
    if ($resumeResp -and $resumeResp.code -eq 0) {
        $sd = $resumeResp.data.structuredData
        Assert "Resume has structuredData" ($null -ne $sd)
        Assert "structuredData has basic_info" ($null -ne $sd.basic_info)
        Assert "structuredData has education array" ($null -ne $sd.education -and $sd.education.Count -gt 0)
        Assert "structuredData has projects array" ($null -ne $sd.projects -and $sd.projects.Count -gt 0)
        Assert "structuredData has skills array" ($null -ne $sd.skills -and $sd.skills.Count -gt 0)
        Assert "structuredData has internships (radar chart data)" ($null -ne $sd.internships)
    }
} else {
    Assert "Get resume (skipped - no resume uploaded)" $false "Resume ID not available from Step 7"
}

# ── Summary ──────────────────────────────────────────────────────────────────

Write-Host ""
Write-Host ("#" * 70) -ForegroundColor Magenta
Write-Host "  SMOKE TEST SUMMARY" -ForegroundColor Magenta
Write-Host ("#" * 70) -ForegroundColor Magenta
Write-Host ""

$total = $passCount + $failCount
Write-Host "  Total: $total  |  " -NoNewline
Write-Host "PASS: $passCount" -ForegroundColor Green -NoNewline
Write-Host "  |  " -NoNewline
if ($failCount -gt 0) {
    Write-Host "FAIL: $failCount" -ForegroundColor Red
} else {
    Write-Host "FAIL: 0" -ForegroundColor Green
}
Write-Host ""

foreach ($r in $testResults) {
    $color = if ($r.Result -eq "PASS") { "Green" } else { "Red" }
    Write-Host "  [$($r.Result)] $($r.Step)" -ForegroundColor $color
    if ($r.Detail) { Write-Host "         -> $($r.Detail)" -ForegroundColor Yellow }
}

Write-Host ""
if ($failCount -eq 0) {
    Write-Host "  ALL SMOKE TESTS PASSED!" -ForegroundColor Green
} else {
    Write-Host "  $failCount TEST(S) FAILED!" -ForegroundColor Red
}

# ── Cleanup ──────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[Cleanup] Stopping services..." -ForegroundColor Yellow
Stop-All

# Clean up temp files
Remove-Item $pdfPath -ErrorAction SilentlyContinue

Write-Host "  Done." -ForegroundColor Gray
Write-Host ""

exit $failCount
