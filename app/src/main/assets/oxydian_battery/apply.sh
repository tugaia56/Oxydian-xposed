#!/system/bin/sh
# Applica i limiti scelti in battery.conf. Ricorda cosa ha fatto in applied.list / applied.scans per poterlo annullare.
MODDIR=${0%/*}
. "$MODDIR/battery.conf"
: > "$MODDIR/applied.list"
if [ "$LIMITS" = "1" ]; then
  grep -v '^[[:space:]]*#' "$MODDIR/restrict.list" | tr -d '' | while read -r p; do
    [ -z "$p" ] && continue
    cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore 2>/dev/null
    am set-standby-bucket "$p" restricted 2>/dev/null
    echo "$p" >> "$MODDIR/applied.list"
  done
fi
if [ "$SCANS_OFF" = "1" ]; then
  # ricorda come erano (solo la prima volta) per rimetterli uguali con undo.sh
  [ -f "$MODDIR/applied.scans" ] || echo "$(settings get global wifi_scan_always_enabled) $(settings get global ble_scan_always_enabled)" > "$MODDIR/applied.scans"
  settings put global wifi_scan_always_enabled 0
  settings put global ble_scan_always_enabled 0
fi
