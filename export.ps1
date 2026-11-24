$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Windows.Forms.Application]::EnableVisualStyles()
$names = @('Nucleus','General','Dungeons','Chat','Kuudra')
$version = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'src/main/resources/fabric.mod.json') -Raw -Encoding UTF8 | ConvertFrom-Json).version
$form = New-Object Windows.Forms.Form
$form.Text = 'Export KCA'
$form.ClientSize = New-Object Drawing.Size(370,260)
$form.FormBorderStyle = 'FixedDialog'
$form.StartPosition = 'CenterScreen'
$form.MaximizeBox = $false
$label = New-Object Windows.Forms.Label
$label.Text = 'Select the branches to include'
$label.AutoSize = $true
$label.Location = New-Object Drawing.Point(16,14)
$list = New-Object Windows.Forms.CheckedListBox
$list.Location = New-Object Drawing.Point(16,42)
$list.Size = New-Object Drawing.Size(338,108)
$list.CheckOnClick = $true
foreach ($name in $names) {
    $caption = if ($name -eq 'Chat') { 'Chat commands' } else { $name }
    [void]$list.Items.Add($caption, $true)
}
$hint = New-Object Windows.Forms.Label
$hint.Text = 'Export one jar combining your selection, or a separate jar for each selected branch.'
$hint.Location = New-Object Drawing.Point(16,157)
$hint.Size = New-Object Drawing.Size(338,36)
function New-ExportButton($label, $x, $result) {
    $button = New-Object Windows.Forms.Button
    $button.Text = $label
    $button.Location = New-Object Drawing.Point($x,207)
    $button.Size = New-Object Drawing.Size(163,32)
    $button.DialogResult = $result
    return $button
}
$together = New-ExportButton 'Export together...' 16 'OK'
$separate = New-ExportButton 'Export separately...' 191 'Yes'
$form.Controls.AddRange(@($label,$list,$hint,$together,$separate))
$list.Add_ItemCheck({
    $count = $list.CheckedItems.Count
    if ($_.NewValue -eq 'Checked') { $count++ } else { $count-- }
    $together.Enabled = $separate.Enabled = $count -gt 0
})
function Get-JarName([string[]]$BranchNames) {
    $suffix = if ($BranchNames.Count -eq $names.Count) { 'full' } else { ($BranchNames -join '-').ToLowerInvariant() }
    "KCA-$version-$suffix-mc26.2.jar"
}
try {
    $choice = $form.ShowDialog()
    if ($choice -notin @('OK','Yes')) { return }
    $selected = @($list.CheckedIndices | ForEach-Object { $names[$_] })
    if (!$selected.Count) { return }
    $jobs = @()
    if ($choice -eq 'OK') {
        $save = New-Object Windows.Forms.SaveFileDialog
        try {
            $save.Title = 'Save KCA export'
            $save.Filter = 'Minecraft mod (*.jar)|*.jar'
            $save.FileName = Get-JarName $selected
            if ($save.ShowDialog() -ne 'OK') { return }
            $jobs += @{Branches=$selected; Destination=$save.FileName}
        } finally { $save.Dispose() }
    } else {
        $folder = New-Object Windows.Forms.FolderBrowserDialog
        try {
            $folder.Description = 'Choose a folder for the individual branch jars.'
            if ($folder.ShowDialog() -ne 'OK') { return }
            foreach ($name in $selected) {
                $jobs += @{Branches=@($name); Destination=(Join-Path $folder.SelectedPath (Get-JarName @($name)))}
            }
        } finally { $folder.Dispose() }
        $existing = @($jobs | Where-Object { Test-Path -LiteralPath $_.Destination } | ForEach-Object { [IO.Path]::GetFileName($_.Destination) })
        if ($existing.Count -and [Windows.Forms.MessageBox]::Show(("Replace the existing files?" + [Environment]::NewLine + ($existing -join [Environment]::NewLine)),'Export KCA','YesNo') -ne 'Yes') { return }
    }
    $saved = @()
    foreach ($job in $jobs) {
        & (Join-Path $PSScriptRoot 'build.ps1') @job
        $saved += $job.Destination
    }
    [void][Windows.Forms.MessageBox]::Show(($saved -join [Environment]::NewLine),'KCA export complete')
} catch {
    [void][Windows.Forms.MessageBox]::Show($_.Exception.Message,'KCA export failed')
    exit 1
} finally { $form.Dispose() }
