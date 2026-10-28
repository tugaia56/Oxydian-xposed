#!/system/bin/sh
# Registro dei consumi: ogni 10 minuti una riga (ora, epoch, livello, contatore di carica in uAh, stato, schermo) e i 5
# processi piu' attivi. File in /data/local/tmp: battery_log.csv e battery_top.log (restano le ultime righe).
PIDF=/data/local/tmp/oxydian_battery.pid
if [ -f "$PIDF" ] && kill -0 "$(cat "$PIDF")" 2>/dev/null; then exit 0; fi
echo $$ > "$PIDF"
LOG=/data/local/tmp/battery_log.csv
TOP=/data/local/tmp/battery_top.log
B=/sys/class/power_supply/battery
# vecchio formato senza epoch: si archivia
if [ -f "$LOG" ] && ! head -1 "$LOG" | grep -q epoch; then mv "$LOG" "$LOG.old"; fi
[ -f "$LOG" ] || echo "time,epoch,level,charge_uah,status,screen" > "$LOG"
while true; do
  scr=$(dumpsys power 2>/dev/null | grep -m1 mWakefulness= | sed 's/.*=//')
  echo "$(date +%Y-%m-%dT%H:%M:%S),$(date +%s),$(cat $B/capacity),$(cat $B/charge_counter),$(cat $B/status),$scr" >> "$LOG"
  { echo "== $(date +%H:%M:%S) $scr"; top -b -n 2 -d 3 -m 5 -s 2 -o PID,%CPU,ARGS 2>/dev/null | awk '/PID/{blk++} blk==2' | tail -n +2 | head -5; } >> "$TOP"
  [ "$(wc -l < "$LOG")" -gt 4500 ] && { tail -n 4000 "$LOG" > "$LOG.tmp"; { head -1 "$LOG"; cat "$LOG.tmp"; } > "$LOG.new"; mv "$LOG.new" "$LOG"; rm -f "$LOG.tmp"; }
  [ "$(wc -l < "$TOP")" -gt 4000 ] && { tail -n 3000 "$TOP" > "$TOP.tmp"; mv "$TOP.tmp" "$TOP"; }
  sleep 600
done
