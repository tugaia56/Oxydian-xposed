#!/system/bin/sh
# Rapporto in formato chiave=valore (l'app lo formatta nella lingua dell'utente), dall'ultima ricarica.
LOG=/data/local/tmp/battery_log.csv
TOP=/data/local/tmp/battery_top.log
[ -f "$LOG" ] || { echo "rows=0"; exit 0; }
awk -F, 'NR>1 && NF>=6 { n++; t[n]=$1; e[n]=$2; lv[n]=$3; ch[n]=$4; st[n]=$5; sc[n]=$6 }
END {
  print "rows=" n
  if (n < 2) exit
  s = 1
  for (i = n; i >= 1; i--) if (st[i] ~ /Charging|Full/) { s = i + 1; break }
  if (s >= n) { print "charging=1"; exit }
  print "since=" t[s]; print "hours=" int((e[n]-e[s])/360)/10
  print "level_start=" lv[s]; print "level_end=" lv[n]
  for (j = s + 1; j <= n; j++) {
    dt = e[j]-e[j-1]; d = ch[j-1]-ch[j]
    if (dt <= 0 || dt > 21600 || d < 0) continue
    if (sc[j-1] ~ /Awake/) { on_u += d; on_s += dt } else { off_u += d; off_s += dt }
  }
  if (off_s > 0) printf "off_ma=%d\noff_hours=%.1f\n", (off_u/1000)/(off_s/3600), off_s/3600
  if (on_s > 0) printf "on_ma=%d\non_hours=%.1f\n", (on_u/1000)/(on_s/3600), on_s/3600
}' "$LOG"
[ -f "$TOP" ] && awk '/^==/ { if ($0 !~ /Awake/) { off = 1; snaps++ } else off = 0; next }
  off && NF >= 3 { cpu[$3] += $2 }
  END { for (k in cpu) if (snaps > 0) printf "top=%s:%.1f\n", k, cpu[k]/snaps }' "$TOP" | sort -t: -k2 -nr | head -5
