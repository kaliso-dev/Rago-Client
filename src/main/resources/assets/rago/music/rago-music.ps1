# Rago Client: the music playing on this PC, for the Music HUD.
#
# Reads Windows' media controls (what the volume panel shows: Spotify, Deezer, a browser playing
# YouTube...) and writes one JSON line when something changes: the title, the artist, whether it
# plays, the position and length in seconds, and the cover saved as a file in the folder given.
# Reads commands from the game, one a line: toggle, next, previous. Stops when the game closes
# (its input ends). Runs at low priority, wakes 10 times a second, asks Windows once a second.
#
#   powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File rago-music.ps1 <folder>
param([string]$Folder = $env:TEMP, [switch]$Once)

$ErrorActionPreference = 'Stop'
try { (Get-Process -Id $PID).PriorityClass = 'BelowNormal' } catch { }
# titles in any language: UTF-8 to the game, without a byte order mark
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

function Await($operation, [Type]$type) {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($operation))
    [void]$task.Wait(5000)
    if (-not $task.IsCompleted) { return $null }
    $task.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$manager = Await ($managerType::RequestAsync()) $managerType
$propertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]

$out = [Console]::Out
function Send([string]$line) {
    $out.WriteLine($line)
    $out.Flush()
}

$coverTrack = $null
$coverId = 0
$coverFile = ''
$coverText = ''
$coverChecks = 0

function Read-Thumbnail($properties) {
    # the picture's bytes, or $null while there is none
    try {
        if ($null -eq $properties.Thumbnail) { return $null }
        $stream = Await ($properties.Thumbnail.OpenReadAsync()) $streamType
        if ($null -eq $stream) { return $null }
        try {
            if ($stream.Size -eq 0 -or $stream.Size -gt 4MB) { return $null }
            $size = [uint32]$stream.Size
            $reader = [Windows.Storage.Streams.DataReader]::new($stream.GetInputStreamAt(0))
            try {
                $loaded = Await ($reader.LoadAsync($size)) ([uint32])
                if ($null -eq $loaded -or [uint32]$loaded -lt $size) { return $null }
                $bytes = New-Object byte[] $size
                $reader.ReadBytes($bytes)
                # the comma keeps the array whole (PowerShell would return its bytes one by one)
                return ,$bytes
            } finally {
                $reader.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
    } catch {
        return $null
    }
}

function Update-Cover($properties, [string]$track) {
    if ($track -ne $script:coverTrack) {
        # a new track: no picture until its own is read, so the game drops the last one
        $script:coverTrack = $track
        $script:coverId++
        $script:coverFile = ''
        $script:coverText = ''
        # browsers (YouTube...) give the picture a moment after the title, at times the last
        # track's first: look again each second for a few seconds
        $script:coverChecks = 10
    }
    if ($script:coverChecks -le 0) { return }
    $script:coverChecks--
    $bytes = Read-Thumbnail $properties
    if ($null -eq $bytes -or $bytes.Length -eq 0) { return }
    try {
        # the same picture as the one sent: nothing to do
        $text = [Convert]::ToBase64String([byte[]]$bytes)
        if ($text -eq $script:coverText) { return }
        # one of two files in turn: the game may still be reading the other
        $id = $script:coverId + 1
        $file = Join-Path $Folder ('cover-' + ($id % 2) + '.img')
        [IO.File]::WriteAllBytes($file, [byte[]]$bytes)
        $script:coverId = $id
        $script:coverFile = $file
        $script:coverText = $text
    } catch { }
}

function Read-Media {
    $session = $manager.GetCurrentSession()
    if ($null -eq $session) { return @{ none = $true } }
    $properties = Await ($session.TryGetMediaPropertiesAsync()) $propertiesType
    if ($null -eq $properties) { return @{ none = $true } }
    $playing = $session.GetPlaybackInfo().PlaybackStatus -eq 'Playing'
    $timeline = $session.GetTimelineProperties()
    $position = $timeline.Position.TotalSeconds
    if ($playing -and $timeline.LastUpdatedTime.Year -gt 2000) {
        # the position Windows has is as of its last update: add the time played since
        $position += ([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalSeconds
    }
    $duration = ($timeline.EndTime - $timeline.StartTime).TotalSeconds
    if ($duration -gt 0) { $position = [Math]::Min([Math]::Max($position, 0), $duration) }
    $track = $properties.Title + "`n" + $properties.Artist
    Update-Cover $properties $track
    return [ordered]@{
        title = $properties.Title; artist = $properties.Artist; playing = $playing
        position = [Math]::Round($position, 1); duration = [Math]::Round($duration, 1)
        cover = $script:coverFile; coverId = $script:coverId; app = $session.SourceAppUserModelId
    }
}

if ($Once) {
    Send (Read-Media | ConvertTo-Json -Compress)
    exit 0
}

# not [Console]::In: its ReadLineAsync waits for a line, which would stop the loop until a command
$in = [System.IO.StreamReader]::new([Console]::OpenStandardInput(), (New-Object System.Text.UTF8Encoding $false))
$command = $in.ReadLineAsync()
$sent = ''
$sentAt = [DateTime]::UtcNow
$sentPosition = 0.0
$sentPlaying = $false
$wait = 0
while ($true) {
    if ($command.IsCompleted) {
        $line = $command.Result
        if ($null -eq $line) { break }
        try {
            $session = $manager.GetCurrentSession()
            if ($null -ne $session) {
                switch ($line.Trim()) {
                    'toggle' { [void](Await ($session.TryTogglePlayPauseAsync()) ([bool])) }
                    'next' { [void](Await ($session.TrySkipNextAsync()) ([bool])) }
                    'previous' { [void](Await ($session.TrySkipPreviousAsync()) ([bool])) }
                }
            }
        } catch { }
        $command = $in.ReadLineAsync()
        $wait = 2
    }
    if ($wait -le 0) {
        $wait = 10
        try {
            $media = Read-Media
            $position = if ($media.Contains('position')) { $media.position } else { 0 }
            $media.Remove('position')
            $key = $media | ConvertTo-Json -Compress
            # the game moves the position on by itself while playing: send it again only when
            # something else changed or it jumped (a seek); without a length (a live stream, an
            # app that gives no time) the game shows no time, so the position does not matter
            $expected = $sentPosition
            if ($sentPlaying) { $expected += ([DateTime]::UtcNow - $sentAt).TotalSeconds }
            $jumped = $media.duration -gt 0 -and [Math]::Abs($position - $expected) -gt 1.5
            if ($key -ne $sent -or $jumped) {
                $media.position = $position
                Send ($media | ConvertTo-Json -Compress)
                $sent = $key
                $sentAt = [DateTime]::UtcNow
                $sentPosition = $position
                $sentPlaying = [bool]$media.playing
            }
        } catch {
            # once, not every second while it lasts
            if ($sent -ne 'error') { Send '{"error":true}' }
            $sent = 'error'
        }
    }
    $wait--
    Start-Sleep -Milliseconds 100
}
