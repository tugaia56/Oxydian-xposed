#!/system/bin/sh
# Comandi per l'app: control.sh set CHIAVE VALORE | apply | undo | logger-start | logger-stop | clear | report | status
MODDIR=${0%/*}
CONF=$MODDIR/battery.conf
case "$1" in
  set)
    k=$2; v=$3
    if grep -q "^$k=" "$CONF"; then sed -i "s/^$k=.*/$k=$v/" "$CONF"; else echo "$k=$v" >> "$CONF"; fi ;;
  apply) sh "$MODDIR/undo.sh"; sh "$MODDIR/apply.sh" ;;
  undo) sh "$MODDIR/undo.sh" ;;
  logger-start) nohup setsid sh "$MODDIR/logger.sh" >/dev/null 2>&1 & ;;
  logger-stop)
    [ -f /data/local/tmp/oxydian_battery.pid ] && kill "$(cat /data/local/tmp/oxydian_battery.pid)" 2>/dev/null
    rm -f /data/local/tmp/oxydian_battery.pid ;;
  clear) rm -f /data/local/tmp/battery_log.csv /data/local/tmp/battery_log.csv.old /data/local/tmp/battery_top.log ;;
  report) sh "$MODDIR/report.sh" ;;
  status)
    p=$(cat /data/local/tmp/oxydian_battery.pid 2>/dev/null)
    if [ -n "$p" ] && kill -0 "$p" 2>/dev/null; then echo "logger=1"; else echo "logger=0"; fi
    grep -E '^(REPORT|LIMITS|SCANS_OFF)=' "$CONF"
    grep -v '^[[:space:]]*#' "$MODDIR/restrict.list" | tr -d '' | grep -v '^[[:space:]]*$' | sed 's/^/app=/' ;;
esac
