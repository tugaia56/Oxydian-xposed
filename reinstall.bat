@echo off
echo Aggiornamento (dati/preferenze mantenuti)...
adb install -r "app\build\outputs\apk\debug\Oxydian-debug.apk"
echo.
echo Fatto! Riavvia SystemUI dall'app per applicare i nuovi hook.
pause
