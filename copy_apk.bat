@echo off
set "DOWNLOADS=%USERPROFILE%\Downloads\inDrive-Assistant-Debug-APK.zip"
set "DEST=%~dp0inDrive-Assistant.apk"

if exist "%DOWNLOADS%" (
    tar -xf "%DOWNLOADS%" -C "%~dp0"
    if exist "%~dp0app-debug.apk" (
        move /y "%~dp0app-debug.apk" "%DEST%"
        echo ========================================================
        echo  SUCCESS: APK is ready at: %DEST%
        echo ========================================================
    )
) else (
    echo Please click 'inDrive-Assistant-Debug-APK' on the GitHub page to download it first.
)
pause
