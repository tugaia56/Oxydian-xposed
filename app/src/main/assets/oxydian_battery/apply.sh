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
# le app limitate non devono partire da sole all'avvio: si spengono i loro ricevitori di avvio (si ricordano per annullare)
: > "$MODDIR/applied.recv"
if [ "$LIMITS" = "1" ] && [ -s "$MODDIR/applied.list" ]; then
  for act in android.intent.action.BOOT_COMPLETED android.intent.action.LOCKED_BOOT_COMPLETED android.intent.action.QUICKBOOT_POWERON; do
    cmd package query-receivers --brief -a "$act" 2>/dev/null
  done | grep -E '^[[:space:]]+[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+[[:space:]]*$' | tr -d ' ' | sort -u > "$MODDIR/recv.all"
  tr -d '' < "$MODDIR/applied.list" | while read -r p; do
    [ -z "$p" ] && continue
    grep "^$p/" "$MODDIR/recv.all" | while read -r c; do
      pm disable --user 0 "$c" >/dev/null 2>&1 && echo "$c" >> "$MODDIR/applied.recv"
    done
  done
  rm -f "$MODDIR/recv.all"
fi
if [ "$SCANS_OFF" = "1" ]; then
  # ricorda come erano (solo la prima volta) per rimetterli uguali con undo.sh
  [ -f "$MODDIR/applied.scans" ] || echo "$(settings get global wifi_scan_always_enabled) $(settings get global ble_scan_always_enabled)" > "$MODDIR/applied.scans"
  settings put global wifi_scan_always_enabled 0
  settings put global ble_scan_always_enabled 0
fi
