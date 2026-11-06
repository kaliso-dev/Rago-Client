# Checks rago-music.ps1 on a real Windows (CI): it loads Windows' media controls, prints JSON,
# sends its first line without waiting for a command, takes a command and stops when its input ends.
$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot '..\..\main\resources\assets\rago\music\rago-music.ps1'
$folder = Join-Path ([IO.Path]::GetTempPath()) 'rago-music-test'
New-Item -ItemType Directory -Force $folder | Out-Null
$arguments = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + $script + '" "' + $folder + '"'

function Start-Helper([string]$extra) {
    $info = New-Object System.Diagnostics.ProcessStartInfo 'powershell.exe'
    $info.Arguments = $arguments + $extra
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = New-Object System.Text.UTF8Encoding $false
    [System.Diagnostics.Process]::Start($info)
}

function Read-Line($process, [int]$ms) {
    $line = $process.StandardOutput.ReadLineAsync()
    if (-not $line.Wait($ms) -or $null -eq $line.Result) {
        if (-not $process.HasExited) { $process.Kill() }
        throw ('no line from the script in ' + $ms + ' ms; its errors: ' + $process.StandardError.ReadToEnd())
    }
    $json = $line.Result | ConvertFrom-Json
    if (-not ($json.none -or $json.title -or $json.error)) { throw ('unexpected line: ' + $line.Result) }
    if ($json.error) { throw ('the script could not read the media controls: ' + $line.Result) }
    Write-Host ('line: ' + $line.Result)
}

# once
$once = Start-Helper ' -Once'
Read-Line $once 30000
if (-not $once.WaitForExit(10000)) { $once.Kill(); throw '-Once did not exit' }

# the loop: a line with no command sent, a command, then it stops when its input closes
$loop = Start-Helper ''
Read-Line $loop 30000
$loop.StandardInput.WriteLine('toggle')
$loop.StandardInput.Flush()
Start-Sleep -Seconds 2
if ($loop.HasExited) { throw ('the script stopped after a command: ' + $loop.StandardError.ReadToEnd()) }
$loop.StandardInput.Close()
if (-not $loop.WaitForExit(10000)) { $loop.Kill(); throw 'the script did not stop when its input closed' }
Write-Host 'rago-music.ps1 OK'
