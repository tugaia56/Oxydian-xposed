#!/system/bin/sh
# Annulla quanto fatto da apply.sh: le app tornano com'erano (limiti e ricevitori di avvio) e le scansioni sempre attive
# tornano come prima.
MODDIR=${0%/*}
if [ -f "$MODDIR/applied.list" ]; then
  # prima i ricevitori di avvio spenti da apply.sh (elenco in applied.recv)
  if [ -f "$MODDIR/applied.recv" ]; then
    while read -r c; do
      [ -z "$c" ] && continue
      pm enable --user 0 "$c" >/dev/null 2>&1 </dev/null
    done < "$MODDIR/applied.recv"
    rm -f "$MODDIR/applied.recv"
  fi
  # poi i limiti
  sed 's/[^[:print:]]//g' < "$MODDIR/applied.list" | while read -r p; do
    [ -z "$p" ] && continue
    cmd appops set "$p" RUN_ANY_IN_BACKGROUND allow 2>/dev/null </dev/null
    am set-standby-bucket "$p" active 2>/dev/null </dev/null
  done
  : > "$MODDIR/applied.list"
fi
if [ -f "$MODDIR/applied.scans" ]; then
  read -r w b < "$MODDIR/applied.scans"
  [ "$w" = "null" ] || [ -z "$w" ] && w=1
  [ "$b" = "null" ] || [ -z "$b" ] && b=1
  settings put global wifi_scan_always_enabled "$w"
  settings put global ble_scan_always_enabled "$b"
  rm -f "$MODDIR/applied.scans"
fi
