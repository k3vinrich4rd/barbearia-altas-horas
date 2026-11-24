#!/usr/bin/env sh
# Conta as horas de API acordada no Render por mês (Fatia 8B, A19 e A20), a partir do cron PROPOSTO para o ping do
# repositório do perfil (docs/planejamento/ping-perfil.diff.md, bloco "Arquivo final").
#
# Conta (spec, seção 8): a API fica acordada da primeira chamada do dia até a última + 15 min de cauda (o Render só a
# põe para dormir depois de ~15 min sem tráfego). Multiplica pelos dias úteis da barbearia (ter a sáb) do mês.
#
# Uso: scripts/horas-render.sh              (imprime a conta passo a passo)
#      scripts/horas-render.sh --conferir   (também falha se passar de 300 h, se algum cron cair fora de ter a sáb, ou
#                                            se a janela não começar às 06:40 BRT e terminar às 19:50 BRT)
# Variáveis: DIAS_UTEIS (padrão 21,7), CAUDA_MIN (padrão 15), LIMITE_H (padrão 300), ARQUIVO (padrão o diff do ping).
set -eu
raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"
ARQUIVO="${ARQUIVO:-docs/planejamento/ping-perfil.diff.md}"
DIAS_UTEIS="${DIAS_UTEIS:-21.7}"
CAUDA_MIN="${CAUDA_MIN:-15}"
LIMITE_H="${LIMITE_H:-300}"
HORAS_NO_MES=744   # 31 dias
conferir=0
[ "${1:-}" = "--conferir" ] && conferir=1

[ -f "$ARQUIVO" ] || { echo "não achei $ARQUIVO" >&2; exit 2; }

# Só o bloco yaml do "Arquivo final" (depois do título), só as linhas "- cron:".
crons="$(awk '/^## Arquivo final/{f=1} f && /^[[:space:]]*- cron:/{print}' "$ARQUIVO" | sed 's/#.*//')"
[ -n "$crons" ] || { echo "nenhum cron no bloco 'Arquivo final' de $ARQUIVO" >&2; exit 2; }

# awk: expande cada cron em minutos-do-dia (UTC) e confere o dia da semana.
resultado="$(printf '%s\n' "$crons" | awk '
function expande(campo, min, max,    n, partes, i, p, a, b, passo, v, saida, base) {
  saida = ""
  n = split(campo, partes, ",")
  for (i = 1; i <= n; i++) {
    p = partes[i]; passo = 1
    if (p ~ /\//) { split(p, a, "/"); p = a[1]; passo = a[2] + 0 }
    if (p == "*") { b = min; base = max }
    else if (p ~ /-/) { split(p, a, "-"); b = a[1] + 0; base = a[2] + 0 }
    else { b = p + 0; base = (passo > 1 ? max : b) }
    for (v = b; v <= base; v += passo) saida = saida " " v
  }
  return saida
}
{
  gsub(/"/, ""); sub(/^[[:space:]]*- cron:[[:space:]]*/, "")
  n = split($0, c, /[[:space:]]+/)
  minutos = expande(c[1], 0, 59); horas = expande(c[2], 0, 23)
  dia_semana = c[5]
  if (dia_semana != "2-6") fora_de_dia++
  nm = split(minutos, M, " "); nh = split(horas, H, " ")
  for (i = 1; i <= nh; i++) for (j = 1; j <= nm; j++) { t = H[i] * 60 + M[j]; if (!(t in vistos)) { vistos[t] = 1; total++ } }
}
END {
  primeiro = 99999; ultimo = -1
  for (t in vistos) { t += 0; if (t < primeiro) primeiro = t; if (t > ultimo) ultimo = t }
  # maior intervalo entre chamadas consecutivas
  maior = 0; ant = -1
  for (t = primeiro; t <= ultimo; t++) if (t in vistos) { if (ant >= 0 && t - ant > maior) maior = t - ant; ant = t }
  printf "%d %d %d %d %d\n", primeiro, ultimo, maior, total, fora_de_dia + 0
}')"
set -- $resultado
primeiro="$1"; ultimo="$2"; maior="$3"; chamadas="$4"; fora_de_dia="$5"

hhmm() { printf '%02d:%02d' $(( ($1 / 60) % 24 )) $(( $1 % 60 )); }
brt() { hhmm $(( $1 - 180 + 1440 )); }   # UTC-3

echo "Conta de horas do Render (Barbearia sozinha; um mês de 31 dias = $HORAS_NO_MES h)"
echo "  1. Cron lido de $ARQUIVO:"
printf '%s\n' "$crons" | sed 's/^/       /'
echo "  2. Primeira chamada do dia: $(hhmm "$primeiro") UTC = $(brt "$primeiro") BRT"
echo "     Última chamada do dia:   $(hhmm "$ultimo") UTC = $(brt "$ultimo") BRT"
echo "     Maior intervalo entre chamadas: $maior min; chamadas por dia: $chamadas"
awk -v p="$primeiro" -v u="$ultimo" -v cauda="$CAUDA_MIN" -v dias="$DIAS_UTEIS" -v limite="$LIMITE_H" -v mes="$HORAS_NO_MES" -v hoje=335 'BEGIN {
  janela = (u - p) / 60; cauda_h = cauda / 60; por_dia = janela + cauda_h; total = por_dia * dias
  printf "  3. Janela acordada por dia: (%.2f h de janela + %.2f h de cauda) = %.2f h\n", janela, cauda_h, por_dia
  printf "  4. Por mês: %.2f h x %s dias úteis (ter a sáb) = %.0f h de API acordada\n", por_dia, dias, total
  printf "  5. Sobra no workspace do Render (750 h) para o RKF Bank: %.0f h (antes desta fatia: ~%d h de ping e sobra de ~%d h)\n", 750 - total, hoje, 750 - hoje
  printf "  6. Limite desta fatia: %d h/mês -> %s\n", limite, (total <= limite ? "dentro" : "ESTOUROU")
  exit (total <= limite ? 0 : 1)
}' && dentro=1 || dentro=0

if [ "$conferir" -eq 1 ]; then
  erros=0
  [ "$dentro" -eq 1 ] || { echo "❌ passou de ${LIMITE_H} h/mês"; erros=$((erros + 1)); }
  [ "$fora_de_dia" -eq 0 ] || { echo "❌ há cron fora de terça a sábado (2-6): a API seria acordada em dia sem barbearia"; erros=$((erros + 1)); }
  [ "$primeiro" -eq $((9 * 60 + 40)) ] || { echo "❌ a janela deve começar às 09:40 UTC (06:40 BRT)"; erros=$((erros + 1)); }
  [ "$ultimo" -eq $((22 * 60 + 50)) ] || { echo "❌ a janela deve terminar às 22:50 UTC (19:50 BRT)"; erros=$((erros + 1)); }
  [ "$maior" -le 10 ] || { echo "❌ intervalo maior que 10 min: a API hibernaria entre as chamadas"; erros=$((erros + 1)); }
  grep -q 'curl -s -m 15' "$ARQUIVO" && grep -q 'fim=\$((SECONDS + 480))' "$ARQUIVO" \
    || { echo "❌ o laço de espera deve usar curl -m 15 e durar no máximo 8 min (480 s)"; erros=$((erros + 1)); }
  [ "$erros" -eq 0 ] && echo "✅ A19/A20: configuração do ping confere (≤ ${LIMITE_H} h/mês, só ter a sáb, 06:40 a 19:50 BRT, a cada 10 min, laço de 8 min)" || exit 1
fi
