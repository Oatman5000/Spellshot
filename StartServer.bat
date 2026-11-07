@echo off
REM ---------------------------------------------------------------------------
REM  Local test server for the moshpit plugin.
REM
REM  Runs the installed Hytale server -- 0.7.5, Update 7 -- against a COPY of the
REM  world in .local-test\. Nothing here touches the real world or the AMP
REM  instance. Delete .local-test to reset.
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
REM  This uses the game install's own jar, so the pair always matches. Mixing them
REM  fails with "Skipping pack at HytaleAssets: missing or invalid manifest.json",
REM  and then EVERY vanilla asset is missing: DamageCause, Weather, ItemQuality,
REM  every animation. The boot dies on "Missing default DamageCause assets".
REM
REM  The build now targets 0.7.5 too (gradle/libs.versions.toml), so for the first
REM  time the compiled plugin, the server jar and Assets.zip are all the same
REM  version. Previously the build was 0.5.7 against a 0.5.9 install, which meant
REM  local testing never exercised the API the plugin was actually compiled for.
REM
REM  If the launcher updates the install past 0.7.5, bump hytaleServer in
REM  libs.versions.toml to match, or you are back to testing a mismatched pair.
set "SRVJAR=%APPDATA%\Hytale\install\release\package\game\latest\Server\HytaleServer.jar"
set "ASSETS=%APPDATA%\Hytale\install\release\package\game\latest\Assets.zip"

echo.
echo ============================================================
echo  1/4  Finding a Java 25 runtime
echo ============================================================
REM  THE VERSION MATTERS, NOT JUST THE PRESENCE OF java.exe.
REM
REM  This used to be `where java` and nothing more, which only proves SOME java
REM  is on PATH. When PATH started resolving to a Java 8, the build still
REM  succeeded -- Gradle compiles with its own toolchain, jvmToolchain(25) -- and
REM  the server then died at launch with
REM
REM    UnsupportedClassVersionError: com/hypixel/hytale/Main has been compiled by
REM    a more recent version of the Java Runtime (class file version 69.0), this
REM    version of the Java Runtime only recognizes class file versions up to 52.0
REM
REM  69.0 is Java 25, 52.0 is Java 8. Nothing in that message says "your PATH is
REM  wrong", and the successful build immediately above makes it read like a
REM  problem with the jar.
REM
REM  JAVA_HOME first, PATH only as a fallback, then check the major version.
REM
REM  Kept in plain batch on purpose. The first attempt at this shelled out to
REM  PowerShell to search every JDK root on the machine, which was both overkill
REM  -- JAVA_HOME already points at a JDK 25 here -- and broken, because the pipes
REM  inside the quoted -Command string were escaped as ^| and PowerShell saw the
REM  carets as literal tokens. Two layers of escaping, no benefit.
REM  A trailing backslash on JAVA_HOME is legal and common, and would otherwise
REM  produce "...hotspot\\bin\java.exe". Strip it before building the path.
set "JAVA_EXE="
set "JH=%JAVA_HOME%"
if defined JH if "%JH:~-1%"=="\" set "JH=%JH:~0,-1%"
if defined JH if exist "%JH%\bin\java.exe" set "JAVA_EXE=%JH%\bin\java.exe"
if not defined JAVA_EXE for %%J in (java.exe) do if not "%%~$PATH:J"=="" set "JAVA_EXE=%%~$PATH:J"
if not defined JAVA_EXE goto :nojava

REM  Parse the major version out of `java -version`. Java 8 and older report
REM  1.8.0_503, 9 and newer report 25.0.4 -- so a leading "1" is always too old.
REM
REM  Run java to a FILE first, then parse the file. Do not try to run it inside
REM  for /f. Both of the obvious forms fail when the path contains a space, which
REM  "C:\Program Files\..." always does:
REM
REM    for /f ... in ('"%JAVA_EXE%" -version ...')   cmd strips the inner quotes
REM    for /f "usebackq" ... in (`"%JAVA_EXE%" ...`) same, quoted-command-at-start
REM
REM  Both give 'C:\Program' is not recognized, JAVA_VER stays empty, and the
REM  version check then reports "Java  is too old" about a perfectly good JDK.
REM  Invoking it as a plain command line sidesteps the whole quoting problem.
set "JVERFILE=%TEMP%\spellshot_java_version.txt"
"%JAVA_EXE%" -version > "%JVERFILE%" 2>&1
set "JAVA_VER="
for /f "usebackq tokens=3" %%v in (`findstr /i "version" "%JVERFILE%"`) do if not defined JAVA_VER set "JAVA_VER=%%~v"
del "%JVERFILE%" >nul 2>&1
for /f "delims=." %%a in ("%JAVA_VER%") do set "JAVA_MAJOR=%%a"
if "%JAVA_MAJOR%"=="1" goto :oldjava
if not defined JAVA_MAJOR goto :oldjava
if %JAVA_MAJOR% LSS 25 goto :oldjava

echo   Using Java %JAVA_VER%
echo   %JAVA_EXE%

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
REM  RELINK THE SERVER JAR EVERY RUN. Do not move this back into the create block.
REM
REM  This is a hardlink, not a copy -- the server only reads the jar. But when the
REM  launcher updates Hytale it writes a NEW file, so an old hardlink keeps
REM  pointing at the previous version's bytes forever. That is how a .local-test
REM  created under 0.5.9 kept booting 0.5.9 long after the install was 0.6.0,
REM  while --assets pointed at the live 0.6.0 Assets.zip.
REM
REM  The result compiles clean and dies at boot with NoSuchMethodError on whatever
REM  API the newer version added -- the plugin is built against one version and run
REM  against another, which is the single hazard this script exists to avoid.
if exist "%TEST%\HytaleServer.jar" del /Q "%TEST%\HytaleServer.jar" >nul 2>&1
mklink /H "%TEST%\HytaleServer.jar" "%SRVJAR%" >nul 2>&1
if not exist "%TEST%\HytaleServer.jar" copy "%SRVJAR%" "%TEST%\HytaleServer.jar" >nul
echo   Linked the current server jar.

REM  RESYNC THE ASSET PACKS, removing any that no longer exist in source.
REM
REM  mods\ used to be copied only when .local-test was created. A pack that was
REM  renamed -- Everlands.Game -> Spellshot.Game -- then left BOTH folders in the
REM  scratch server, so the old pack kept loading alongside the new one and every
REM  asset in it resolved twice. Mirroring the directory deletes the stale copy.
echo   Syncing asset packs...
robocopy "moshpit\server\mods" "%TEST%\mods" /MIR /XF "*.jar" /NFL /NDL /NJH /NJS /NP >nul
copy /Y "moshpit\server\mods\spark-1.10.165-beta2-hytale.jar" "%TEST%\mods\" >nul 2>&1

copy /Y "moshpit\build\libs\moshpit-plugin.jar" "%TEST%\mods\moshpit-plugin.jar" >nul
echo   Installed the freshly built plugin.

echo.
echo ============================================================
echo  4/4  Starting the server
echo ============================================================
echo.
echo   Watch for:
echo     "Booting up HytaleServer - Version: 0.7.5"     jar matches the build
echo     "Loaded pack: Hytale:Hytale from Assets.zip"   vanilla assets OK
echo     "Spellshot: registered NN component classes"   plugin loaded
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
powershell -NoProfile -Command "& '%JAVA_EXE%' -jar HytaleServer.jar --assets '%ASSETS%' 2>&1 | Tee-Object -FilePath 'server.log'"
goto :end

:oldjava
echo.
echo   ERROR: Java %JAVA_VER% is too old. Hytale 0.7.5 needs Java 25.
echo     %JAVA_EXE%
echo.
echo   Run this to see every JDK Gradle can find, then point JAVA_HOME at a 25:
echo     .\gradlew -q javaToolchains
echo     setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"
echo   Open a NEW terminal afterwards -- setx does not affect the current one.
goto :end

:nojava
echo.
echo   ERROR: no Java 25 or newer was found.
echo.
echo   Hytale 0.7.5 is compiled for Java 25 and will not start on anything older.
echo   A Java 8 on PATH is the common case: the Gradle build still succeeds,
echo   because it uses its own toolchain, and only the launch fails.
echo.
echo   Searched JAVA_HOME, PATH, the usual install roots and Gradle's own JDKs.
echo   Fix by installing a JDK 25 or pointing JAVA_HOME at one:
echo     setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-25"
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
