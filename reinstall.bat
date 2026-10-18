@echo off
rem Installa sul telefono l'ultimo APK release compilato di Oxydian (dati e preferenze restano).
rem Prima compila una release firmata: Build > Build Bundle(s) / APK(s) > Build APK(s) (variante release)
rem oppure gradlew assembleRelease. L'APK della sola "Run" di Studio e' firmato con un'altra chiave:
rem non puo' aggiornare l'app gia' installata.
set APK=
for /f "delims=" %%f in ('dir /b /o-d "app\build\outputs\apk\release\*.apk" 2^>nul') do (
    set APK=app\build\outputs\apk\release\%%f
    goto :trovato
)
echo Nessun APK release trovato: prima compila l'app (vedi sopra).
pause
exit /b 1
:trovato
echo Installo %APK% ...
adb install -r "%APK%"
echo.
echo Fatto. Se compare "Success" l'app e' aggiornata.
echo Per le mod serve poi riavviare SystemUI dall'app, oppure riavviare il telefono.
pause
