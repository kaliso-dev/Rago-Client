<#
Essai du chargeur Rago avec le launcher officiel de Minecraft, en attendant la connexion
Microsoft dans le launcher Rago : le launcher officiel te connecte, le chargeur Rago lance le
jeu sur Java 21, sans Forge.

À lancer depuis la racine du dépôt, launcher Minecraft fermé :
  powershell -ExecutionPolicy Bypass -File loader\windows\essai-chargeur.ps1

  1. compile le chargeur et renomme Minecraft 1.8.9 avec lui (dans .minecraft\rago-loader) ;
  2. ajoute la version « 1.8.9-rago » au launcher officiel, avec ton jar Rago de .minecraft\mods.
Ton profil Forge, tes mods et tes options ne changent pas. Pour tout enlever : supprime
.minecraft\rago-loader, .minecraft\versions\1.8.9-rago et .minecraft\libraries\gg\rago.
#>
param(
    [string]$Minecraft = (Join-Path $env:APPDATA ".minecraft"),
    [string]$Mods = "",
    [string]$Java = ""
)
$ErrorActionPreference = "Stop"

if (-not $Mods) { $Mods = Join-Path $Minecraft "mods" }
if (-not $Java) {
    $jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jdk) { throw "Java 21 introuvable dans C:\Program Files\Eclipse Adoptium (dossier jdk-21...)." }
    $Java = $jdk.FullName
}
$env:JAVA_HOME = $Java
$client = Join-Path $Minecraft "versions\1.8.9\1.8.9.jar"
$versionFile = Join-Path $Minecraft "versions\1.8.9\1.8.9.json"
if (-not (Test-Path $client) -or -not (Test-Path $versionFile)) {
    throw "Minecraft 1.8.9 n'est pas dans $Minecraft : lance-le une fois avec le launcher officiel."
}
$forge = Get-ChildItem (Join-Path $Minecraft "libraries\net\minecraftforge\forge") -Recurse -Filter "forge-1.8.9-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $forge) { throw "Forge 1.8.9 introuvable dans $Minecraft\libraries : le chargeur lit les noms du jeu dans son jar." }
$rago = Get-ChildItem $Mods -Filter "rago-*+1.8.9.jar" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
if (-not $rago) { throw "Aucun jar Rago dans $Mods." }
$work = Join-Path $Minecraft "rago-loader"
if ($work.Contains(" ")) { throw "Le chemin $work contient un espace, que le launcher officiel couperait en deux." }

Write-Host "1/3 Bibliothèques de Minecraft 1.8.9"
$libs = Join-Path $work "libs"
New-Item -ItemType Directory -Force $libs | Out-Null
$version = Get-Content $versionFile -Raw | ConvertFrom-Json
foreach ($l in $version.libraries) {
    if ($l.natives -or -not $l.downloads -or -not $l.downloads.artifact) { continue }
    $path = Join-Path (Join-Path $Minecraft "libraries") $l.downloads.artifact.path
    if (Test-Path $path) { Copy-Item $path $libs -Force }
}

Write-Host "2/3 Chargeur Rago et jeu renommé"
& .\gradlew.bat --no-daemon -q -p loader jar
if ($LASTEXITCODE -ne 0) { throw "La compilation du chargeur a échoué." }
$built = (Get-ChildItem "loader\build\libs" -Filter "rago-loader-*.jar" | Select-Object -First 1).FullName
$renamed = Join-Path $work "1.8.9-renamed.jar"
& (Join-Path $Java "bin\java.exe") -cp $built gg.rago.loader.Main remap $forge.FullName $client $renamed
if ($LASTEXITCODE -ne 0) { throw "Le chargeur n'a pas pu renommer le jeu." }
& .\gradlew.bat --no-daemon -q -p loader jar "-PgameJar=$renamed" "-PgameLibs=$libs"
if ($LASTEXITCODE -ne 0) { throw "La compilation de la partie compatible Forge a échoué." }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($built)
$hooks = $zip.Entries | Where-Object { $_.FullName -eq "gg/rago/loader/compat/Hooks.class" }
$zip.Dispose()
if (-not $hooks) { throw "Le chargeur a été compilé sans sa partie compatible Forge." }

Write-Host "3/3 Version 1.8.9-rago pour le launcher officiel"
$lib = Join-Path $Minecraft "libraries\gg\rago\rago-loader\0.1.0"
New-Item -ItemType Directory -Force $lib | Out-Null
Copy-Item $built (Join-Path $lib "rago-loader-0.1.0.jar") -Force
$ragoCopy = Join-Path $work "rago.jar"
Copy-Item $rago.FullName $ragoCopy -Force
$json = [ordered]@{
    id = "1.8.9-rago"
    inheritsFrom = "1.8.9"
    jar = "1.8.9"
    type = "release"
    time = "2026-11-03T00:00:00+00:00"
    releaseTime = "2026-11-03T00:00:00+00:00"
    mainClass = "gg.rago.loader.Main"
    minecraftArguments = "launch --game $renamed --mod $ragoCopy -- " + $version.minecraftArguments
    libraries = @(@{ name = "gg.rago:rago-loader:0.1.0" })
} | ConvertTo-Json -Depth 5
$dir = Join-Path $Minecraft "versions\1.8.9-rago"
New-Item -ItemType Directory -Force $dir | Out-Null
[IO.File]::WriteAllText((Join-Path $dir "1.8.9-rago.json"), $json, (New-Object Text.UTF8Encoding $false))

Write-Host ""
Write-Host "C'est prêt, avec $($rago.Name). Dans le launcher Minecraft : Installations > Nouvelle installation"
Write-Host "  Version : release 1.8.9-rago"
Write-Host "  Plus d'options > Exécutable Java : $(Join-Path $Java 'bin\javaw.exe')"
