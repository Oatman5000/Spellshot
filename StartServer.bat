@echo off
REM ---------------------------------------------------------------------------
REM  Local test server for the moshpit plugin.
REM
REM  Runs Hytale 0.5.7 -- the version AMP runs -- from the Gradle cache, against
REM  a COPY of the world in .local-test\. Nothing here touches the real world or
REM  the AMP instance. Delete .local-test to reset.
REM
REM  Written without parenthesised if/else blocks on purpose: those need escaped
REM  parens and break on "||" chaining, which is what produced
REM  "... was unexpected at this time."  Flow is goto-based instead.
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"

set "TEST=%CD%\.local-test"

REM  SERVER JAR AND ASSETS.ZIP MUST BE THE SAME VERSION.
REM
REM  This uses the game install's own jar, so the pair always matches -- currently
REM  0.5.9. Pointing the 0.5.7 jar from the Gradle cache at a 0.5.9 Assets.zip
REM  fails with "Skipping pack at HytaleAssets: missing or invalid manifest.json",
REM  and then EVERY vanilla asset is missing: DamageCause, Weather, ItemQuality,
REM  every animation. The boot dies on "Missing default DamageCause assets".
REM
REM  AMP runs 0.5.7. To test that exact version you would need a 0.5.7 Assets.zip,
REM  which is not on this machine -- download it from the AMP instance if it ever
REM  matters. For checking that the plugin loads and commands work, 0.5.9 is fine.
set "SRVJAR=%APPDATA%\Hytale\install\release\package\game\latest\Server\HytaleServer.jar"
set "ASSETS=%APPDATA%\Hytale\install\release\package\game\latest\Assets.zip"

echo.
echo ============================================================
echo  1/4  Checking Java
echo ============================================================
where java >nul 2>&1
if errorlevel 1 goto :nojava
java -version 2>&1

echo.
echo ============================================================
echo  2/4  Building moshpit-plugin.jar
echo ============================================================
call "%CD%\gradlew.bat" :moshpit:shadowJar
if errorlevel 1 goto :buildfail

echo.
echo ============================================================
echo  3/4  Preparing .local-test
echo ============================================================
if not exist "%SRVJAR%" goto :nojar
if not exist "%ASSETS%" goto :noassets
if exist "%TEST%" goto :reuse

echo   Creating scratch server...
mkdir "%TEST%"
mkdir "%TEST%\universe"
mkdir "%TEST%\universe\worlds"
copy "moshpit\server\config.json" "%TEST%\" >nul
echo   Copying mods...
xcopy "moshpit\server\mods" "%TEST%\mods\" /E /I /Q >nul
echo   Copying world, about 55 MB...
xcopy "moshpit\server\universe\worlds\Moshpit" "%TEST%\universe\worlds\Moshpit\" /E /I /Q >nul
if exist "moshpit\server\universe\warps.json" copy "moshpit\server\universe\warps.json" "%TEST%\universe\" >nul

REM Hardlink rather than copy -- the server only reads the jar.
REM Assets.zip is NOT copied here at all; it is passed with --assets below.
echo   Linking server jar...
mklink /H "%TEST%\HytaleServer.jar" "%SRVJAR%" >nul 2>&1
if not exist "%TEST%\HytaleServer.jar" copy "%SRVJAR%" "%TEST%\HytaleServer.jar" >nul
goto :install

:reuse
echo   Reusing existing .local-test - delete that folder to reset it.
REM  Always re-copy the markers. The scratch server saves its world on shutdown,
REM  so a previous run can leave .local-test holding markers that no longer match
REM  the source -- which is indistinguishable from the plugin failing to load
REM  them, and is exactly the confusion this whole exercise started with.
set "MARKERS=universe\worlds\Moshpit\resources\MiniGameArenaResource.json"
if not exist "moshpit\server\%MARKERS%" goto :install
copy /Y "moshpit\server\%MARKERS%" "%TEST%\%MARKERS%" >nul
echo   Refreshed arena markers from the source world.

:install
copy /Y "moshpit\build\libs\moshpit-plugin.jar" "%TEST%\mods\moshpit-plugin.jar" >nul
echo   Installed the freshly built plugin.

echo.
echo ============================================================
echo  4/4  Starting the server
echo ============================================================
echo.
echo   Watch for:
echo     "Loaded pack: Hytale:Hytale from Assets.zip"   vanilla assets OK
echo     "Everlands: registered NN component classes"   plugin loaded
echo     "Setup phase completed"                        it booted
echo.
echo   If you see "ARENAS ARE DISABLED" then nothing will teleport - that is
echo   the arena marker store failing to register.
echo.
echo   Once you join, each arena reports itself exactly once:
echo     "Arena 'nuketown' loaded 51 markers from world 'Moshpit'"    good
echo     "Arena 'nuketown' (Nuketown) has no usable markers: ..."     the cause
echo.
echo   Everything is also written to .local-test\server.log
echo.
echo   Then Direct Connect to 127.0.0.1 and try:
echo     /arena entries
echo     /arena loc set testarena spawn:1
echo.
echo   Ctrl+C to stop.
echo.
REM  --assets is REQUIRED.
REM
REM  Its default is "../HytaleAssets", a DIRECTORY that does not exist here, so
REM  without this flag the server logs "Skipping pack at HytaleAssets: missing or
REM  invalid manifest.json" and then no vanilla asset exists at all -- no
REM  DamageCause, no Weather, no ItemQuality, no animations -- and the boot dies
REM  on "Missing default DamageCause assets". AMP's own start script passes it.
REM Output goes to the console AND to .local-test\server.log, via PowerShell's
REM Tee-Object -- cmd has no tee. The log file is what to share when something
REM fails; it beats scrolling a console buffer.
cd /d "%TEST%"
powershell -NoProfile -Command "java -jar HytaleServer.jar --assets '%ASSETS%' 2>&1 | Tee-Object -FilePath 'server.log'"
goto :end

:nojava
echo.
echo   ERROR: java is not on your PATH.
echo   Hytale 0.5.7 needs Java 25.
goto :end

:buildfail
echo.
echo   BUILD FAILED. Nothing below will help until that is fixed.
goto :end

:nojar
echo.
echo   ERROR: Hytale server jar not found at:
echo     %SRVJAR%
echo   Launch the Hytale client once so it installs the game files.
goto :end

:noassets
echo.
echo   ERROR: Assets.zip not found at:
echo     %ASSETS%
echo   Launch the Hytale client once so it installs the game files.
goto :end

:end
echo.
pause
