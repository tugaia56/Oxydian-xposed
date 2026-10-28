#!/system/bin/sh
# Registrazione una tantum del carico all'avvio. Ogni 10 s per 4 minuti: carico, pressione CPU/memoria/IO, memoria libera,
# swap usato, stato dello sblocco e i 8 processi piu' attivi in quel momento. Poi si disattiva (file "disable").
MODDIR=${0%/*}
OUT=/data/local/tmp/bootcap.log
DUR=${BOOTCAP_SECS:-240}
renice -n 19 $$ >/dev/null 2>&1
: > "$OUT"
echo "bootcap v3 $(date)" >> "$OUT"
n=0
while [ $((n * 10)) -lt "$DUR" ]; do
  up=$(cut -d' ' -f1 /proc/uptime)
  {
    echo "=== $(date +%H:%M:%S) up ${up}s load $(cut -d' ' -f1-3 /proc/loadavg) procs $(cut -d' ' -f4 /proc/loadavg)"
    echo "mem: $(grep -E 'MemAvailable' /proc/meminfo | tr -s ' ') swapused_kB=$(awk '/SwapTotal/{t=$2}/SwapFree/{f=$2}END{print t-f}' /proc/meminfo)"
    echo "psi cpu: $(sed -n 1p /proc/pressure/cpu)"
    echo "psi mem: $(sed -n 1p /proc/pressure/memory) | full: $(sed -n 2p /proc/pressure/memory)"
    echo "psi io : $(sed -n 1p /proc/pressure/io) | full: $(sed -n 2p /proc/pressure/io)"
    echo "keyguard: $(dumpsys window 2>/dev/null | grep -m1 isKeyguardShowing | sed 's/.*=//')"
    # percorsi completi: nel servizio Magisk/KernelSU il PATH ha busybox davanti, che non capisce queste opzioni
    echo "-- CPU:"
    /system/bin/top -b -n 2 -d 1 -m 8 -s 2 -o PID,%CPU,RES,ARGS 2>/dev/null | awk '/PID/{blk++} blk==2' | head -9 | cut -c1-110
    echo "-- memoria (RSS):"
    /system/bin/ps -A -o PID,RSS,ARGS --sort=-rss 2>/dev/null | head -7 | cut -c1-110
  } >> "$OUT"
  n=$((n + 1))
  sleep 10
done
{
  echo "=== fine: processi con piu' tempo CPU accumulato dall'avvio"
  /system/bin/ps -A -o PID,TIME,RSS,ARGS --sort=-time 2>/dev/null | head -15 | cut -c1-110
  echo "=== servizi/moduli attivi al boot"
  ls /data/adb/modules 2>/dev/null | tr '\n' ' '; echo
} >> "$OUT"
# una tantum: il modulo si disarma da solo (si riattiva dalla pagina Log di avvio di Oxydian)
if [ ! -f "$MODDIR/always" ]; then touch "$MODDIR/disable"; fi
