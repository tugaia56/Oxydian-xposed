#!/system/bin/sh
# Ricevitori di avvio: "boot_receivers attivi" oppure "boot_receivers tutti" (con --query-flags 512 anche quelli spenti).
boot_receivers() {
  for act in android.intent.action.BOOT_COMPLETED android.intent.action.LOCKED_BOOT_COMPLETED android.intent.action.QUICKBOOT_POWERON; do
    if [ "$1" = "tutti" ]; then cmd package query-receivers --brief --query-flags 512 -a "$act" 2>/dev/null </dev/null
    else cmd package query-receivers --brief -a "$act" 2>/dev/null </dev/null; fi
  done | grep -E '^[[:space:]]+[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+[[:space:]]*$' | sed 's/[^[:graph:]]//g' | sort -u
}
