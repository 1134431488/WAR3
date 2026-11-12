@echo off
REM Debug launcher: keeps the console open so any Java error is visible.
cd /d "%~dp0"
setlocal
set "JAVABIN=%~dp0runtime\bin\java.exe"
if not exist "%JAVABIN%" set "JAVABIN=java"
set "JVMOPTS=-Xmx1500m -Dfile.encoding=UTF-8 -Dsun.java2d.dpiaware=false"

if exist "%~dp0rms-blender.jar" (
  if exist "%~dp0rms-tooltips.jar" (
    "%JAVABIN%" %JVMOPTS% -cp "%~dp0RMS_zh_CN.jar;%~dp0rms-tooltips.jar;%~dp0rms-blender.jar" BlenderLauncher %*
  ) else (
    "%JAVABIN%" %JVMOPTS% -cp "%~dp0RMS_zh_CN.jar;%~dp0rms-blender.jar" BlenderLauncher %*
  )
) else (
  if exist "%~dp0rms-tooltips.jar" (
    "%JAVABIN%" %JVMOPTS% -cp "%~dp0RMS_zh_CN.jar;%~dp0rms-tooltips.jar" RmsTooltipInstaller %*
  ) else (
    "%JAVABIN%" %JVMOPTS% -jar "%~dp0RMS_zh_CN.jar" %*
  )
)
echo.
echo === Exit code: %errorlevel% ===
pause
endlocal
