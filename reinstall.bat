@echo off
rem Installa sul telefono l'ultimo APK compilato di Oxydian (dati e preferenze restano).
rem Prima compila: Build > Generate Signed App Bundle or APK > APK (release) oppure ./gradlew assembleRelease.
set APK=
for /f "delims=" %%f in ('dir /b /o-d "app\build\outputs\apk\release\*.apk" 2^>nul') do (
    set APK=app\build\outputs\apk\release\%%f
    goto :trovato
)
for /f "delims=" %%f in ('dir /b /o-d "app\build\intermediates\apk\release\*.apk" 2^>nul') do (
    set APK=app\build\intermediates\apk\release\%%f
    goto :trovato
)
echo Nessun APK trovato: prima compila l'app.
pause
exit /b 1
:trovato
echo Installo %APK% ...
adb install -r -t "%APK%"
echo.
echo Fatto. Se compare "Success" l'app e' aggiornata.
echo Per le mod serve poi riavviare SystemUI dall'app, oppure riavviare il telefono.
pause
