#!/bin/bash
# Legge changelog.en.txt (inglese) e changelog.txt (italiano), scritti a mano prima di ogni tag, e
# costruisce il testo per la GitHub Release (Body, prima l'inglese poi l'italiano) e per Telegram
# (file telegram_1.msg, telegram_2.msg, ...: un messaggio per blocco, perche' Telegram accetta al
# massimo 4096 caratteri per messaggio). Sezioni riconosciute (tutte opzionali):
#   inglese:  "Added:", "Changed:", "Removed:"
#   italiano: "Aggiunto:", "Aggiornato:", "Rimosso:"
# Ogni riga sotto una sezione diventa un punto elenco. Righe vuote ignorate.
# Se manca un file, si usa solo l'altro.

# $1 = file; stampa il testo ripulito
clean_file() {
  local file="$1" out="" line trimmed
  [ -f "$file" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    trimmed=$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    [ -z "$trimmed" ] && continue
    case "$trimmed" in
      Added:|Changed:|Removed:|Aggiunto:|Aggiornato:|Rimosso:)
        out+=$'\n'"$trimmed"$'\n'
        ;;
      -)
        ;;
      *)
        out+="$trimmed"$'\n'
        ;;
    esac
  done < "$file"
  printf '%s' "$out"
}

CHANGELOG_EN=$(clean_file "changelog.en.txt")
CHANGELOG_IT=$(clean_file "changelog.txt")

# GitHub Release: inglese poi italiano
{
  echo "$VName"
  if [ -n "$CHANGELOG_EN" ]; then
    echo
    echo "### 🇬🇧 English"
    echo "$CHANGELOG_EN"
  fi
  if [ -n "$CHANGELOG_IT" ]; then
    echo
    echo "### 🇮🇹 Italiano"
    echo "$CHANGELOG_IT"
  fi
} > body.md
echo 'Body<<EOF' >> $GITHUB_ENV
cat body.md >> $GITHUB_ENV
echo 'EOF' >> $GITHUB_ENV

# Telegram: blocchi da al massimo ~3800 caratteri, spezzati a fine riga
rm -f telegram_*.msg
N=0
CUR=""
flush() {
  if [ -n "$CUR" ]; then
    N=$((N + 1))
    printf '%s' "$CUR" > "telegram_${N}.msg"
    CUR=""
  fi
}
add_text() {
  local text="$1" line
  while IFS= read -r line || [ -n "$line" ]; do
    if [ $(( ${#CUR} + ${#line} + 1 )) -gt 3800 ]; then flush; fi
    CUR+="$line"$'\n'
  done <<< "$text"
}
if [ -n "$CHANGELOG_EN" ]; then
  add_text "$VName released! 🇬🇧"
  add_text "$CHANGELOG_EN"
  flush
fi
if [ -n "$CHANGELOG_IT" ]; then
  add_text "$VName rilasciato! 🇮🇹"
  add_text "$CHANGELOG_IT"
  flush
fi
# primo messaggio come variabile (compatibilita')
echo 'TMessage<<EOF' >> $GITHUB_ENV
cat telegram_1.msg >> $GITHUB_ENV 2>/dev/null
echo 'EOF' >> $GITHUB_ENV
