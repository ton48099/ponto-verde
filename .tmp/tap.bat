@echo off
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
%ADB% shell input tap 635 1480
%ADB% shell input tap 240 1275
