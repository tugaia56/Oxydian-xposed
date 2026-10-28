#!/system/bin/sh
# Riassunto (chiave=valore) dell'ultima registrazione del boot, per l'app.
LOG=$(ls -t "/sdcard/Log Oxydian"/bootcap_*.log 2>/dev/null | head -1)
[ -n "$LOG" ] || LOG=/data/local/tmp/bootcap.log
[ -f "$LOG" ] || { echo "samples=0"; exit 0; }
awk '
/^=== [0-9]/ { n++; for (i = 1; i <= NF; i++) { if ($i == "load") { l = $(i+1); if (l + 0 > maxl + 0) { maxl = l; maxl_at = $4 } } } mode = ""; next }
/^mem:/ { for (i = 1; i <= NF; i++) { if ($i ~ /MemAvailable/) m = $(i+1); if ($i ~ /swapused_kB=/) { sw = $i; sub(/swapused_kB=/, "", sw) } }
          if (minm == "" || m + 0 < minm + 0) { minm = m; minm_up = up }
          if (sw + 0 > maxsw + 0) maxsw = sw; next }
/^psi cpu:/ { split($4, a, "="); if (a[2] + 0 > maxc + 0) maxc = a[2]; next }
/^psi mem:/ { split($4, a, "="); if (a[2] + 0 > maxp + 0) maxp = a[2]; next }
/^-- CPU:/ { mode = "cpu"; next }
/^-- memoria/ { mode = "mem"; next }
/^fine:/ { mode = "end"; next }
mode == "cpu" && $1 ~ /^[0-9]+$/ && $2 ~ /^[0-9.]+$/ && $4 !~ /^(top|-b)/ { name = $4; cpu[name] += $2; cs[name]++ }
mode == "mem" && $1 ~ /^[0-9]+$/ && $2 ~ /^[0-9]+$/ { name = $3; if ($2 + 0 > rss[name] + 0) rss[name] = $2 }
END {
  print "samples=" n
  print "max_load=" maxl; print "max_load_at=" maxl_at
  printf "min_mem_mb=%d\n", minm / 1024
  printf "max_swap_mb=%d\n", maxsw / 1024
  print "max_psi_cpu=" maxc + 0; print "max_psi_mem=" maxp + 0
  for (k in cpu) printf "cpu=%s:%.1f\n", k, cpu[k] / n | "sort -t: -k2 -nr | head -6"
  close("sort -t: -k2 -nr | head -6")
  for (k in rss) printf "mem=%s:%d\n", k, rss[k] / 1024 | "sort -t: -k2 -nr | head -5"
}' "$LOG"
