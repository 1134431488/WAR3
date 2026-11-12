@echo off
REM Retera Model Studio - Chinese Edition launcher with Blender-style viewport
REM Uses the bundled portable Java runtime; no system Java required.
REM Priority: Blender+tooltips > Blender only > tooltips only > plain -jar
cd /d "%~dp0"
setlocal
set "JAVABIN=%~dp0runtime\bin\javaw.exe"
if not exist "%JAVABIN%" set "JAVABIN=javaw"
set "JVMOPTS=-Xmx1500m -Dfile.encoding=UTF-8 -Dsun.java2d.dpiaware=false"

if exist "%~dp0rms-blender.jar" (
  if exist "%~dp0rms-tooltips.jar" (
    start "" "%JAVABIN%" %JVMOPTS% -cp "%~dp0RMS_zh_CN.jar;%~dp0rms-tooltips.jar;%~dp0rms-blender.jar" BlenderLauncher %*
  ) else (
    start "" "%JAVABIN%" %JVMOPTS% -cp "%~dp0RMS_zh_CN.jar;%~dp0rms-blender.jar" BlenderLauncher %*
  )
) else (
  if exist "%~dp0rms-tooltips.jar" (
    start "" "%JAVABIN%" %JVMOPTS% -cp "%~dp0RMS_zh_CN.jar;%~dp0rms-tooltips.jar" RmsTooltipInstaller %*
  ) else (
    start "" "%JAVABIN%" %JVMOPTS% -jar "%~dp0RMS_zh_CN.jar" %*
  )
)
endlocal
