#!/system/bin/sh
# Applica i limiti scelti in battery.conf. Ricorda cosa ha fatto in applied.list / applied.scans per poterlo annullare.
MODDIR=${0%/*}
. "$MODDIR/battery.conf"
: > "$MODDIR/applied.list"
if [ "$LIMITS" = "1" ]; then
  grep -v '^[[:space:]]*#' "$MODDIR/restrict.list" | sed 's/[^[:print:]]//g' | while read -r p; do
    [ -z "$p" ] && continue
    cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore 2>/dev/null </dev/null
    am set-standby-bucket "$p" restricted 2>/dev/null </dev/null
    echo "$p" >> "$MODDIR/applied.list"
  done
fi
# le app limitate non devono partire da sole all'avvio: si spengono i loro ricevitori di avvio.
# Prima si SCRIVE l'elenco in applied.recv (unito a quello gia' presente), poi si spegne: undo.sh riaccende esattamente quelli.
if [ "$LIMITS" = "1" ] && [ -s "$MODDIR/applied.list" ]; then
  . "$MODDIR/recv_lib.sh"
  boot_receivers attivi > "$MODDIR/recv.all"
  : > "$MODDIR/recv.new"
  sed 's/[^[:print:]]//g' < "$MODDIR/applied.list" | while read -r p; do
    [ -z "$p" ] && continue
    grep "^$p/" "$MODDIR/recv.all" >> "$MODDIR/recv.new"
  done
  { cat "$MODDIR/applied.recv" 2>/dev/null; cat "$MODDIR/recv.new"; } | sort -u > "$MODDIR/recv.merged"
  mv -f "$MODDIR/recv.merged" "$MODDIR/applied.recv"
  while read -r c; do
    [ -z "$c" ] && continue
    pm disable --user 0 "$c" >/dev/null 2>&1 </dev/null
  done < "$MODDIR/applied.recv"
  rm -f "$MODDIR/recv.all" "$MODDIR/recv.new"
fi
if [ "$SCANS_OFF" = "1" ]; then
  # ricorda come erano (solo la prima volta) per rimetterli uguali con undo.sh
  [ -f "$MODDIR/applied.scans" ] || echo "$(settings get global wifi_scan_always_enabled) $(settings get global ble_scan_always_enabled)" > "$MODDIR/applied.scans"
  settings put global wifi_scan_always_enabled 0
  settings put global ble_scan_always_enabled 0
fi
