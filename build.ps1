param(
    [string]$OneClientRoot = "$env:APPDATA/Polyfrost/OneClient",
    [string]$MinecraftJar,
    [string]$FabricApiJar,
    [string]$ModMenuJar,
    [switch]$WithoutKuudra,
    [ValidateSet('Nucleus','General','Dungeons','Chat','Kuudra')]
    [string[]]$Branches = @('Nucleus','General','Dungeons','Chat','Kuudra'),
    [string]$Destination
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$buildDir = Join-Path $projectRoot 'build'
$depsDir = Join-Path $buildDir 'deps'
$buildDir = [IO.Path]::GetFullPath($buildDir)
# Stage a fresh class tree so removed screens and resources cannot enter a later release.
$classesDir = Join-Path $buildDir ('classes-' + [guid]::NewGuid().ToString('N'))
$testsDir = Join-Path $buildDir 'tests'
$allBranches = @('Nucleus','General','Dungeons','Chat','Kuudra')
$selected = @($allBranches | Where-Object { $Branches -contains $_ -and ($_ -ne 'Kuudra' -or -not $WithoutKuudra) })
if (!$selected.Count) { throw 'Select at least one branch.' }
$custom = $selected.Count -lt $allBranches.Count
$manifestSource = Join-Path $projectRoot 'src/main/resources/fabric.mod.json'
$manifest = Get-Content -LiteralPath $manifestSource -Raw -Encoding UTF8 | ConvertFrom-Json
$variant = ($selected -join '-').ToLowerInvariant()
if ($Destination) {
    $outputJar = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Destination)
} elseif ($WithoutKuudra -and $selected.Count -eq 4) {
    $outputJar = Join-Path $buildDir 'KCA-without-Kuudra-mc26.1.2.jar'
} elseif ($custom) {
    $outputJar = Join-Path $buildDir "KCA-$($manifest.version)-$variant-mc26.1.2.jar"
} else {
    $outputJar = Join-Path $buildDir 'KCA-mc26.1.2.jar'
}
if ([IO.Path]::GetExtension($outputJar) -ne '.jar') { throw 'Choose a .jar output file.' }
$outputDir = [IO.Path]::GetDirectoryName($outputJar)
if (!(Test-Path -LiteralPath $outputDir -PathType Container) -and $outputDir -ne $buildDir) { throw 'Output folder does not exist.' }
New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
# Dependencies and compiler arguments are shared by builds in this project.
$buildLock = [IO.File]::Open((Join-Path $buildDir 'kca-build.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
$pendingJar = Join-Path $outputDir ('.kca-' + [guid]::NewGuid().ToString('N') + '.jar')
try {
foreach ($path in @($buildDir, $depsDir, $classesDir, $testsDir)) {
    New-Item -ItemType Directory -Force -Path $path | Out-Null
}
if (-not $MinecraftJar) {
    $MinecraftJar = Get-ChildItem -LiteralPath (Join-Path $OneClientRoot 'metadata/versions') -Recurse -Filter '26.1.2-*.jar' |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $FabricApiJar) {
    $FabricApiJar = Get-ChildItem -LiteralPath (Join-Path $OneClientRoot '.minecraft/mods/26.1.2 Fabric') -Filter 'fabric-api-*+26.1.2.jar' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $MinecraftJar -or -not $FabricApiJar) { throw 'Minecraft 26.1.2 and Fabric API JARs are required.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$api = [System.IO.Compression.ZipFile]::OpenRead($FabricApiJar)
try {
    foreach ($entry in $api.Entries) {
        if ($entry.FullName -like 'META-INF/jars/*.jar') {
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $depsDir $entry.Name), $true)
        }
    }
} finally { $api.Dispose() }
$loaderJar = Get-ChildItem -LiteralPath (Join-Path $OneClientRoot 'metadata/libraries/net/fabricmc/fabric-loader') -Recurse -Filter 'fabric-loader-*.jar' |
    Sort-Object { [version]$_.Directory.Name } -Descending | Select-Object -First 1
$loader = [IO.Compression.ZipFile]::OpenRead($loaderJar.FullName)
try {
    $extra = $loader.Entries | Where-Object FullName -Like 'META-INF/jars/mixinextras-fabric-*.jar' | Select-Object -First 1
    [IO.Compression.ZipFileExtensions]::ExtractToFile($extra, (Join-Path $depsDir 'mixinextras.jar'), $true)
} finally { $loader.Dispose() }
$libraries = @(Get-ChildItem -LiteralPath (Join-Path $OneClientRoot 'metadata/libraries') -Recurse -Filter '*.jar' |
    Select-Object -ExpandProperty FullName)
$apiLibraries = @(Get-ChildItem -LiteralPath $depsDir -Filter '*.jar' | Select-Object -ExpandProperty FullName)
if (-not $ModMenuJar) {
    $ModMenuJar = Get-ChildItem -LiteralPath (Join-Path $OneClientRoot '.minecraft/mods/26.1.2 Fabric') -Filter 'modmenu-*.jar' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $ModMenuJar) { throw 'Mod Menu for Minecraft 26.1.2 is required to compile the optional menu integration.' }
$classpath = (@($MinecraftJar, $ModMenuJar, (Join-Path $projectRoot 'src/main/resources/META-INF/jars/jlayer-1.0.1.4.jar')) + $libraries + $apiLibraries) -join [IO.Path]::PathSeparator
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/main/java') -Recurse -Filter '*.java' |
    Select-Object -ExpandProperty FullName)
if ($custom) {
    $template = Join-Path $projectRoot 'src/main/java/dev/purplepad/KcaEdition.java'
    $editionSource = Join-Path $classesDir 'KcaEdition.java'
    $sources = @($sources | Where-Object { $_ -ne $template })
    $edition = [IO.File]::ReadAllText($template)
    foreach ($branch in $allBranches) {
        if ($selected -notcontains $branch) {
            $flag = $branch.ToUpperInvariant() + '=true;'
            if (!$edition.Contains($flag)) { throw "Missing branch flag for $branch" }
            $edition = $edition.Replace($flag, ($branch.ToUpperInvariant() + '=false;'))
        }
    }
    [IO.File]::WriteAllText($editionSource, $edition)
    $sources += $editionSource
}
$arguments = @('--release', '25', '-encoding', 'UTF-8', '-proc:none', '-classpath', $classpath, '-d', $classesDir) + $sources
$argPath = Join-Path $buildDir 'javac.args'
# javac argument files use forward slashes to avoid interpreting Windows backslashes.
$argumentLines = $arguments | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }
[IO.File]::WriteAllLines($argPath, [string[]]$argumentLines, [Text.UTF8Encoding]::new($false))
& javac "@$argPath"
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/test/java') -Recurse -Filter '*.java' |
    Select-Object -ExpandProperty FullName)
& javac --release 25 -encoding UTF-8 -proc:none -classpath $classesDir -d $testsDir @testSources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& java -classpath "$classesDir$([IO.Path]::PathSeparator)$testsDir" dev.purplepad.TimerStateTest
if ($LASTEXITCODE -ne 0) { throw 'Timer tests failed.' }
& java -classpath "$classesDir$([IO.Path]::PathSeparator)$testsDir" dev.purplepad.HudGeometryTest
if ($LASTEXITCODE -ne 0) { throw 'HUD geometry tests failed.' }
& java -classpath "$classesDir$([IO.Path]::PathSeparator)$testsDir" dev.purplepad.RunStatsTest
if ($LASTEXITCODE -ne 0) { throw 'Dungeon report tests failed.' }
& java -classpath "$classesDir$([IO.Path]::PathSeparator)$testsDir" dev.purplepad.PartyRosterTest
if ($LASTEXITCODE -ne 0) { throw 'Party roster tests failed.' }
Copy-Item -Path (Join-Path $projectRoot 'src/main/resources/*') -Destination $classesDir -Recurse -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'LICENSE'), (Join-Path $projectRoot 'NOTICE.md') -Destination $classesDir -Force
if ($custom) {
    Remove-Item -LiteralPath $editionSource
    $mixinsFile = Join-Path $classesDir 'purple-pad-timer.mixins.json'
    $mixins = Get-Content -LiteralPath $mixinsFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $branchMixins = @{
        General = @('BetaNameTag','RaritySlots','RarityHotbar','SkyblockerRarity','SkyblockerStorageRarity','SkysoftStorageRarity')
        Dungeons = @('ServerPingMixin','StormChatMixin','BladeSplitRecap','NoammClearReport')
        Chat = @('TypedShortcut','ShortcutKeys','SkyblockerShortcuts','SkysoftInventoryShortcuts','PartyPong','SboPartyReply','NoammPartyReply','OdinPartyReply')
    }
    $keepMixins = @($selected | ForEach-Object { $branchMixins[$_] } | Where-Object { $_ })
    if (@($selected | Where-Object { $_ -ne 'Kuudra' }).Count) { $keepMixins += 'SharedChat' }
    foreach ($name in $mixins.client) {
        if ($keepMixins -notcontains $name) {
            Remove-Item -LiteralPath (Join-Path $classesDir "dev/purplepad/mixin/$name.class")
        }
    }
    $mixins.client = @($mixins.client | Where-Object { $keepMixins -contains $_ })
    [IO.File]::WriteAllText($mixinsFile, ($mixins | ConvertTo-Json -Depth 10))
    $omit = @()
    if ($selected -notcontains 'Kuudra') { $omit += @('assets/kca/textures/kuudra.png*','dev/purplepad/KuudraScreen*.class') }
    if ($selected -notcontains 'Chat') { $omit += @('assets/kca/textures/party_chat.png','dev/purplepad/PartyScreen*.class') }
    if ($selected -notcontains 'General') { $omit += 'assets/kca/textures/gui/sprites/item_background*' }
    if ($selected -notcontains 'Nucleus') {
        $manifest.mixins = @($manifest.mixins | Where-Object { $_ -ne 'nucleus.mixins.json' })
        $manifest.jars = @()
        $omit += @('assets/nucleus/*','assets/minecraft/atlases/blocks.json','assets/kca/items/*','assets/kca/models/*',
            'assets/kca/textures/item/*','assets/kca/textures/block/*','assets/kca/textures/casino_mat.png',
            'assets/kca/textures/pb_trophy.png*','META-INF/jars/jlayer*.jar','nucleus.mixins.json','com/nucleus/client/mixin/*.class')
    }
    foreach ($file in (Get-ChildItem -LiteralPath $classesDir -Recurse -File)) {
        $relative = $file.FullName.Substring($classesDir.Length + 1).Replace('\','/')
        foreach ($pattern in $omit) {
            if ($relative -like $pattern) { Remove-Item -LiteralPath $file.FullName; break }
        }
    }
    $manifest.name += ' (' + ($selected -join ', ') + ')'
    [IO.File]::WriteAllText((Join-Path $classesDir 'fabric.mod.json'), ($manifest | ConvertTo-Json -Depth 12))
}
& jar --create --file $pendingJar -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
if ([IO.File]::Exists($outputJar)) { [IO.File]::Replace($pendingJar, $outputJar, [System.Management.Automation.Language.NullString]::Value) }
else { [IO.File]::Move($pendingJar, $outputJar) }
Write-Output "Built: $outputJar"
Get-FileHash -LiteralPath $outputJar -Algorithm SHA256 | Format-List
} finally {
    $buildLock.Dispose()
    if (Test-Path -LiteralPath $pendingJar) { Remove-Item -LiteralPath $pendingJar }
    if (Test-Path -LiteralPath $classesDir) {
        $stage = (Resolve-Path -LiteralPath $classesDir).Path
        if ([IO.Path]::GetDirectoryName($stage) -eq $buildDir -and [IO.Path]::GetFileName($stage) -match '^classes-[a-f0-9]{32}$') {
            Remove-Item -LiteralPath $stage -Recurse -Force
        }
    }
}
