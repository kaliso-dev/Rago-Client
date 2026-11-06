# Checks rago-music.ps1 on a real Windows (CI): it loads Windows' media controls, prints JSON,
# sends its first line without waiting for a command, takes a command, sends a track's title and
# then its picture when the picture comes later (as browsers do), and stops when its input ends.
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

# a track of our own in Windows' media controls, its picture given 2 s after its title as browsers
# do: the script must send the title, then the picture, as a file with the same bytes
Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing
[void][Windows.Media.Playback.MediaPlayer, Windows.Media.Playback, ContentType = WindowsRuntime]
[void][Windows.Media.MediaPlaybackStatus, Windows.Media, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.RandomAccessStreamReference, Windows.Storage.Streams, ContentType = WindowsRuntime]
$player = [Windows.Media.Playback.MediaPlayer]::new()
$player.CommandManager.IsEnabled = $false
$controls = $player.SystemMediaTransportControls
$controls.IsEnabled = $true
$controls.IsPlayEnabled = $true
$controls.IsPauseEnabled = $true
$controls.PlaybackStatus = [Windows.Media.MediaPlaybackStatus]::Playing
$display = $controls.DisplayUpdater
$display.Type = [Windows.Media.MediaPlaybackType]::Music
$display.MusicProperties.Title = 'Rago test track'
$display.MusicProperties.Artist = 'Rago CI'
$display.Update()

$script:pending = $null
function Wait-For($process, [scriptblock]$test, [int]$seconds, [string]$what) {
    $until = [DateTime]::UtcNow.AddSeconds($seconds)
    while ([DateTime]::UtcNow -lt $until) {
        if ($null -eq $script:pending) { $script:pending = $process.StandardOutput.ReadLineAsync() }
        if ($script:pending.Wait(500)) {
            $text = $script:pending.Result
            $script:pending = $null
            if ($null -eq $text) { break }
            Write-Host ('line: ' + $text)
            $json = $text | ConvertFrom-Json
            if (& $test $json) { return $json }
        }
    }
    throw ('no line with ' + $what + ' in ' + $seconds + ' s')
}

$first = Wait-For $loop { param($j) $j.title -eq 'Rago test track' } 20 'the test track'
if ($first.artist -ne 'Rago CI') { throw ('wrong artist: ' + $first.artist) }
if ($first.cover) { throw 'a cover before the track has one' }

Start-Sleep -Seconds 2
$image = New-Object System.Drawing.Bitmap 300, 200
$g = [System.Drawing.Graphics]::FromImage($image)
$g.Clear([System.Drawing.Color]::FromArgb(220, 30, 60))
$g.Dispose()
$png = New-Object System.IO.MemoryStream
$image.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
$bytes = $png.ToArray()
$memory = New-Object System.IO.MemoryStream (, $bytes)
$display.Thumbnail = [Windows.Storage.Streams.RandomAccessStreamReference]::CreateFromStream(
    [System.IO.WindowsRuntimeStreamExtensions]::AsRandomAccessStream($memory))
$display.Update()

$withCover = Wait-For $loop { param($j) $j.title -eq 'Rago test track' -and $j.cover } 20 'the picture'
if ($withCover.coverId -le $first.coverId) { throw 'the cover id did not change' }
$saved = [IO.File]::ReadAllBytes($withCover.cover)
if ([Convert]::ToBase64String($saved) -ne [Convert]::ToBase64String($bytes)) {
    throw ('the saved picture differs: ' + $saved.Length + ' bytes, ' + $bytes.Length + ' given')
}
$player.Dispose()

$loop.StandardInput.Close()
if (-not $loop.WaitForExit(10000)) { $loop.Kill(); throw 'the script did not stop when its input closed' }
Write-Host 'rago-music.ps1 OK'
