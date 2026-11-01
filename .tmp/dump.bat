@echo off
set ADB=C:\Users\Admin\Downloads\zecaeuacho\.tmp\..\..\..\..\android\sdk\platform-tools\adb.exe
set ADB2=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
%ADB2% shell uiautomator dump /sdcard/ui.xml
%ADB2% shell cat /sdcard/ui.xml > C:\Users\Admin\Downloads\zecaeuacho\ponto-verde\.tmp\ui.xml
