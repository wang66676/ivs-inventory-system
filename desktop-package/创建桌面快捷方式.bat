@echo off
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop')+'\进销存系统.lnk'); $s.TargetPath='%~dp0启动进销存系统.bat'; $s.WorkingDirectory='%~dp0'; $s.Save()"
echo 已创建桌面快捷方式：进销存系统
pause
