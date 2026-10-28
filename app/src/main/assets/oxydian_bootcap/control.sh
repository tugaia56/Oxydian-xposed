#!/system/bin/sh
# control.sh arm | disarm | always-on | always-off | status | report
MODDIR=${0%/*}
case "$1" in
  arm) rm -f "$MODDIR/disable" ;;
  disarm) touch "$MODDIR/disable" ;;
  always-on) touch "$MODDIR/always"; rm -f "$MODDIR/disable" ;;
  always-off) rm -f "$MODDIR/always" ;;
  report) sh "$MODDIR/report.sh" ;;
  status)
    [ -f "$MODDIR/disable" ] && echo "armed=0" || echo "armed=1"
    [ -f "$MODDIR/always" ] && echo "always=1" || echo "always=0"
    if ls "/sdcard/Log Oxydian"/bootcap_*.log >/dev/null 2>&1 || [ -f /data/local/tmp/bootcap.log ]; then echo "haslog=1"; else echo "haslog=0"; fi ;;
esac
