#!/system/bin/sh
# Oxydian Battery: dopo l'avvio parte il registro dei consumi se acceso e si applicano i limiti scelti
MODDIR=${0%/*}
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 5; done
sleep 90
. "$MODDIR/battery.conf"
[ "$REPORT" = "1" ] && { nohup setsid sh "$MODDIR/logger.sh" >/dev/null 2>&1 & }
if [ "$LIMITS" = "1" ] || [ "$SCANS_OFF" = "1" ]; then sh "$MODDIR/apply.sh"; fi
