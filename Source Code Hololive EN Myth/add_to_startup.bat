@echo off
set TARGET=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\HololiveEN Myth Shimeji-ee.lnk
set SOURCE=%~dp0HololiveEN Myth Shimeji-ee.exe
if not exist "%SOURCE%" (
    echo HololiveEN Myth Shimeji-ee.exe not found in %~dp0
    exit /b
)
echo Creating shortcut to HololiveEN Myth Shimeji-ee in Startup folder...
powershell -command "$s=(New-Object -COM WScript.Shell).CreateShortcut('%TARGET%');$s.TargetPath='%SOURCE%';$s.Save()"
