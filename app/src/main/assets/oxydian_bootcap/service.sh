#!/system/bin/sh
# Registrazione una tantum del carico all'avvio. Ogni 10 s per 4 minuti: carico, pressione CPU/memoria/IO, memoria libera,
# swap usato, stato dello sblocco e i 8 processi piu' attivi in quel momento. Poi si disattiva (file "disable").
MODDIR=${0%/*}
OUT=/data/local/tmp/bootcap.log
DUR=${BOOTCAP_SECS:-240}
renice -n 5 $$ >/dev/null 2>&1
: > "$OUT"
echo "bootcap v6 $(date)" >> "$OUT"

# CPU per thread di un processo in 1 secondo: "percentuale-tick tid nome", i primi 6
threads() {
  p=$(pidof "$1" | cut -d' ' -f1)
  [ -n "$p" ] || return
  echo "-- thread $1:"
  snap() { awk '{ r = $0; tid = $1; c = r; sub(/^[0-9]+ \(/, "", c); sub(/\) .*/, "", c); sub(/.*\) /, "", r); n = split(r, f, " "); print tid, f[12] + f[13], c }' /proc/$p/task/*/stat 2>/dev/null; }
  snap > "$OUT.t0"; sleep 1; snap > "$OUT.t1"
  awk 'NR == FNR { a[$1] = $2; next } { d = $2 - a[$1]; if (d > 0) print d, $1, $3 }' "$OUT.t0" "$OUT.t1" | sort -nr | head -6
  rm -f "$OUT.t0" "$OUT.t1"
}
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
    if [ "${up%%.*}" -lt 130 ]; then threads system_server; threads com.android.systemui; fi
  } >> "$OUT"
  n=$((n + 1))
  if [ "${up%%.*}" -lt 130 ]; then sleep 3; else sleep 10; fi
done
{
  echo "=== fine: processi con piu' tempo CPU accumulato dall'avvio"
  /system/bin/ps -A -o PID,TIME,RSS,ARGS --sort=-time 2>/dev/null | head -15 | cut -c1-110
  echo "=== servizi/moduli attivi al boot"
  ls /data/adb/modules 2>/dev/null | tr '\n' ' '; echo
} >> "$OUT"
# copia nella cartella "Log Oxydian" della memoria interna (visibile da qualsiasi file manager), tiene gli ultimi 10
i=0
until touch /sdcard/.oxydian_w 2>/dev/null || [ $i -ge 60 ]; do sleep 5; i=$((i + 1)); done
if [ -f /sdcard/.oxydian_w ]; then
  rm -f /sdcard/.oxydian_w
  LDIR=$(head -n 1 /data/adb/oxydian_logdir 2>/dev/null | tr -d '\r')
  [ -n "$LDIR" ] || LDIR="/sdcard/Log Oxydian"
  LD="$LDIR"
  mkdir -p "$LD"
  cp "$OUT" "$LD/bootcap_$(date +%Y%m%d_%H%M).log"
  ls -t "$LD"/bootcap_*.log 2>/dev/null | tail -n +11 | while read -r f; do rm -f "$f"; done
fi
# una tantum: il modulo si disarma da solo (si riattiva dalla pagina Log di avvio di Oxydian)
if [ ! -f "$MODDIR/always" ]; then touch "$MODDIR/disable"; fi
