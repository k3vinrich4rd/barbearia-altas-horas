# Diff proposto: ping da Barbearia no repositório do PERFIL (para o Kevin aplicar)

| | |
|---|---|
| 🎯 **Para que serve** | trocar o `manter-barbearia-no-ar.yml` do repositório **do perfil** pelo ping da Fatia 8B: só na janela da barbearia (a partir das 06:40), nada fora dela, laço de espera de no máximo 8 min |
| 🔗 **Origem** | [spec da Fatia 8B](../sdd/fatia-8b-deploy-robusto.md): E12, E13, A19 a A21 e D-9 (quem aplica é o Kevin, por commit ou PR dele: o workflow mora em **outro repositório**, `perfil-github`) |
| ✅ **Conta de horas** | `scripts/horas-render.sh --conferir` (neste repositório) lê o bloco `cron` abaixo e prova que fica em **~291 h/mês** (antes: ~335 h) |
| ⚠️ **Antes de aplicar** | o keep-alive do **banco** sai deste workflow e passa para o `manter-banco.yml` do repositório da barbearia (precisa do segredo `DB_URL_BACKUP`, [passo a passo](ativar-deploy.md)). Aplique o diff **depois** de o `manter-banco` rodar verde uma vez, senão o Aiven fica sem conexão fora da janela |

## Como aplicar

1. No repositório do perfil, edite `.github/workflows/manter-barbearia-no-ar.yml` com o diff abaixo (ou substitua o arquivo pelo "arquivo final").
2. **Actions → Manter Barbearia no ar → Run workflow** (`workflow_dispatch`) com a API dormindo: deve acordá-la e ficar verde em até 8 min (A21).
3. Rode `scripts/horas-render.sh --conferir` na barbearia para conferir que o cron colado bate com a conta.

## O diff

```diff
--- a/.github/workflows/manter-barbearia-no-ar.yml
+++ b/.github/workflows/manter-barbearia-no-ar.yml
@@
-# Mantém a Barbearia Altas Horas (API no Render Free + PostgreSQL Free na Aiven) no ar.
-# Por quê:
-#  - o Render Free dorme a API após 15 min sem uso; o primeiro acesso depois disso demora ~1 a 3 min;
-#  - a Aiven desliga o banco gratuito quando ele fica muito tempo sem conexão.
-# Como (sem estourar as 750 h/mês grátis do Render, que a API do RKF Bank também usa):
-#  - no horário da barbearia (terça a sábado, 7h às 20h de Brasília = 10h às 23h UTC) chama a API a cada 10 min,
-#    então ela fica acordada quando os clientes agendam (~300 h/mês);
-#  - fora disso, uma chamada a cada 3 horas só para o banco da Aiven não ser desligado.
+# Mantém a API da Barbearia Altas Horas (Render Free) acordada NA JANELA DA BARBEARIA (Fatia 8B, E12).
+# Por quê: o Render Free dorme a API após 15 min sem uso e a subida fria leva ~2 a 3 min.
+# Como (sem estourar as 750 h/mês grátis do Render, que a API do RKF Bank também usa):
+#  - terça a sábado, de 06:40 a 19:50 de Brasília (09:40 a 22:50 UTC), uma chamada a cada 10 min: a primeira (06:40)
+#    é uma volta antes da abertura, para a subida não pegar o primeiro cliente (~291 h/mês);
+#  - FORA da janela este workflow não acorda a API. O banco da Aiven é mantido ligado por outro workflow, no
+#    repositório da barbearia (manter-banco.yml: conexão direta, 2x por dia, 0 h de Render).
+# O intervalo de 10 min contra os 15 min de hibernação deixa ~5 min de folga para o atraso do cron do GitHub: não encurtar.
 # Se falhar, o job fica vermelho e o GitHub manda e-mail ao Kevin.
 name: Manter Barbearia no ar
 
 on:
   schedule:
-    - cron: "*/10 10-22 * * 2-6"   # horário da barbearia (UTC)
-    - cron: "41 */3 * * *"          # a cada 3 horas, para o banco não ser desligado
+    - cron: "40,50 9 * * 2-6"       # 06:40 e 06:50 BRT (UTC 09:40 e 09:50)
+    - cron: "*/10 10-21 * * 2-6"    # 07:00 a 18:50 BRT (UTC 10:00 a 21:50)
+    - cron: "0,10,20,30,40,50 22 * * 2-6"   # 19:00 a 19:50 BRT (UTC 22:00 a 22:50); a última chamada é às 19:50
   workflow_dispatch:
 
 permissions: {}
 
 jobs:
   ping:
     runs-on: ubuntu-latest
-    timeout-minutes: 15
+    timeout-minutes: 10
     steps:
       - name: Acordar a API e checar o banco
         env:
           API: https://altas-horas-api.onrender.com
         run: |
-          for i in 1 2 3 4 5 6 7 8; do
-            codigo=$(curl -s -m 90 -o resposta.json -w '%{http_code}' "$API/actuator/health" || true)
-            echo "tentativa $i: HTTP $codigo $(cat resposta.json 2>/dev/null)"
-            if [ "$codigo" = "200" ] && grep -q '"UP"' resposta.json; then
-              echo "✅ $API no ar"
-              exit 0
-            fi
-            sleep 30
-          done
+          # Laço de no máximo 8 min: cada tentativa espera no máximo 15 s e a pausa é de 20 s (A21).
+          fim=$((SECONDS + 480))
+          i=0
+          while [ "$SECONDS" -lt "$fim" ]; do
+            i=$((i + 1))
+            codigo=$(curl -s -m 15 -o resposta.json -w '%{http_code}' "$API/actuator/health" || true)
+            echo "tentativa $i (${SECONDS}s): HTTP $codigo $(cat resposta.json 2>/dev/null)"
+            if [ "$codigo" = "200" ] && grep -q '"UP"' resposta.json; then
+              echo "✅ $API no ar"
+              exit 0
+            fi
+            sleep 20
+          done
           echo "::error::$API não respondeu UP. Veja se o PostgreSQL altas-horas-db está 'Powered off' (console.aiven.io) e os logs no Render."
           exit 1
```

## Arquivo final (para colar inteiro)

```yaml
# Mantém a API da Barbearia Altas Horas (Render Free) acordada NA JANELA DA BARBEARIA (Fatia 8B, E12).
# Por quê: o Render Free dorme a API após 15 min sem uso e a subida fria leva ~2 a 3 min.
# Como (sem estourar as 750 h/mês grátis do Render, que a API do RKF Bank também usa):
#  - terça a sábado, de 06:40 a 19:50 de Brasília (09:40 a 22:50 UTC), uma chamada a cada 10 min: a primeira (06:40)
#    é uma volta antes da abertura, para a subida não pegar o primeiro cliente (~291 h/mês);
#  - FORA da janela este workflow não acorda a API. O banco da Aiven é mantido ligado por outro workflow, no
#    repositório da barbearia (manter-banco.yml: conexão direta, 2x por dia, 0 h de Render).
# O intervalo de 10 min contra os 15 min de hibernação deixa ~5 min de folga para o atraso do cron do GitHub: não encurtar.
# Se falhar, o job fica vermelho e o GitHub manda e-mail ao Kevin.
name: Manter Barbearia no ar

on:
  schedule:
    - cron: "40,50 9 * * 2-6"       # 06:40 e 06:50 BRT (UTC 09:40 e 09:50)
    - cron: "*/10 10-21 * * 2-6"    # 07:00 a 18:50 BRT (UTC 10:00 a 21:50)
    - cron: "0,10,20,30,40,50 22 * * 2-6"   # 19:00 a 19:50 BRT (UTC 22:00 a 22:50); a última chamada é às 19:50
  workflow_dispatch:

permissions: {}

jobs:
  ping:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - name: Acordar a API e checar o banco
        env:
          API: https://altas-horas-api.onrender.com
        run: |
          # Laço de no máximo 8 min: cada tentativa espera no máximo 15 s e a pausa é de 20 s (A21).
          fim=$((SECONDS + 480))
          i=0
          while [ "$SECONDS" -lt "$fim" ]; do
            i=$((i + 1))
            codigo=$(curl -s -m 15 -o resposta.json -w '%{http_code}' "$API/actuator/health" || true)
            echo "tentativa $i (${SECONDS}s): HTTP $codigo $(cat resposta.json 2>/dev/null)"
            if [ "$codigo" = "200" ] && grep -q '"UP"' resposta.json; then
              echo "✅ $API no ar"
              exit 0
            fi
            sleep 20
          done
          echo "::error::$API não respondeu UP. Veja se o PostgreSQL altas-horas-db está 'Powered off' (console.aiven.io) e os logs no Render."
          exit 1
```

## Observações

- **Domingo e segunda** a API dorme o dia todo (a barbearia fecha): o primeiro acesso espera a subida (~2 a 3 min, ou menos com o cache AOT). É o preço de caber nas 750 h (D-2).
- O ping continua chamando o `/actuator/health` **completo** (toca o banco): ele é o alarme de "banco fora" (o Render usa só o liveness). Falhou, o job fica vermelho e o GitHub manda e-mail.
- Se o repositório do perfil ficar 60 dias sem atividade, o GitHub desativa os workflows agendados; a automação diária do perfil gera commits e mantém ativo (conferir).
