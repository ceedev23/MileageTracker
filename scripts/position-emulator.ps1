# Fits the Android Emulator inside the primary work area (right side, above the taskbar)
# and stores that origin in the AVD's emulator-user.ini.
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Windows.Forms

Add-Type @"
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public class EmuWin {
  public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
  public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr hWnd, int x, int y, int w, int h, bool repaint);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int cmd);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder s, int n);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetClassName(IntPtr hWnd, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
  public static List<string> Find() {
    var list = new List<string>();
    EnumWindows((h, l) => {
      var title = new StringBuilder(512);
      var cls = new StringBuilder(256);
      GetWindowText(h, title, title.Capacity);
      GetClassName(h, cls, cls.Capacity);
      string t = title.ToString();
      if (t.StartsWith("Android Emulator") || (t == "Emulator" && cls.ToString().Contains("Qt"))) {
        uint pid; GetWindowThreadProcessId(h, out pid);
        RECT r; GetWindowRect(h, out r);
        list.Add(h.ToInt64() + "|" + pid + "|" + t + "|" + r.Left + "|" + r.Top + "|" + r.Right + "|" + r.Bottom);
      }
      return true;
    }, IntPtr.Zero);
    return list;
  }
}
"@

$work = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$margin = 16
$rows = [EmuWin]::Find()
if (-not $rows -or $rows.Count -eq 0) {
    Write-Output "NO_EMULATOR"
    exit 0
}

$main = $null
$tool = $null
foreach ($row in $rows) {
    $p = $row.Split("|")
    $obj = [pscustomobject]@{
        H = [IntPtr]::new([int64]$p[0]); Pid = [uint32]$p[1]; Title = $p[2]
        L = [int]$p[3]; T = [int]$p[4]; R = [int]$p[5]; B = [int]$p[6]
    }
    if ($obj.Title.StartsWith("Android Emulator")) { $main = $obj }
    elseif ($null -eq $tool) { $tool = $obj }
}
if ($null -eq $main) { Write-Output "NO_EMULATOR"; exit 0 }

$curW = $main.R - $main.L
$curH = $main.B - $main.T
$overW = 0
$toolDx = 0
$toolDy = 0
$toolW = 0
$toolH = 0
if ($null -ne $tool -and $tool.Pid -eq $main.Pid) {
    $toolW = $tool.R - $tool.L
    $toolH = $tool.B - $tool.T
    $overW = [Math]::Max(0, $tool.R - $main.R)
    $toolDx = $tool.L - $main.R
    $toolDy = $tool.T - $main.T
}

$maxH = $work.Height - (2 * $margin)
$maxW = $work.Width - (2 * $margin) - $overW
$scale = [Math]::Min(1.0, [Math]::Min($maxH / $curH, $maxW / $curW))
$newW = [int][Math]::Round($curW * $scale)
$newH = [int][Math]::Round($curH * $scale)
$x = $work.Right - $margin - $overW - $newW
$y = $work.Top + $margin

[EmuWin]::ShowWindow($main.H, 9) | Out-Null
[EmuWin]::MoveWindow($main.H, $x, $y, $newW, $newH, $true) | Out-Null
if ($null -ne $tool -and $tool.Pid -eq $main.Pid) {
    $tx = $x + $newW + $toolDx
    $ty = $y + [int]$toolDy
    if ($ty -lt $work.Top) { $ty = $work.Top }
    if ($ty + $toolH -gt $work.Bottom) { $ty = [Math]::Max($work.Top, $work.Bottom - $toolH) }
    [EmuWin]::MoveWindow($tool.H, $tx, $ty, $toolW, $toolH, $true) | Out-Null
}
[EmuWin]::SetForegroundWindow($main.H) | Out-Null

$avdRoot = Join-Path $env:USERPROFILE ".android\avd"
Get-ChildItem $avdRoot -Filter "*.ini" -File | ForEach-Object {
    $pathLine = Select-String -Path $_.FullName -Pattern "^path=(.+)$" | Select-Object -First 1
    if (-not $pathLine) { return }
    $dir = $pathLine.Matches[0].Groups[1].Value.Trim()
    $cfg = Join-Path $dir "config.ini"
    if (-not (Test-Path $cfg)) { return }
    $idLine = Select-String -Path $cfg -Pattern "^AvdId\s*=\s*(.+)$" | Select-Object -First 1
    if (-not $idLine) { return }
    $id = $idLine.Matches[0].Groups[1].Value.Trim()
    if ($main.Title -notlike "*$id*") { return }
    $userIni = Join-Path $dir "emulator-user.ini"
    if (-not (Test-Path $userIni)) { return }
    $lines = Get-Content $userIni | ForEach-Object {
        if ($_ -match "^window\.x\s*=") { "window.x = $x" }
        elseif ($_ -match "^window\.y\s*=") { "window.y = $y" }
        else { $_ }
    }
    Set-Content -Path $userIni -Value $lines -Encoding ascii
}

Write-Output "PLACED x=$x y=$y w=$newW h=$newH"
