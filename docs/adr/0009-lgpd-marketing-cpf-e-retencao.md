# 0009. LGPD: marketing por consentimento, CPF não persistido e retenção do guest

- Status: **proposta** (dono: Quarteto Dev; o Alicerce revisa o impacto)
- Data: 2026-11-13
- Decisores: Quarteto Dev. **Não é parecer jurídico**: bases legais e prazos marcados como proposta precisam de validação antes de uso real.

## Contexto
O sistema trata nome, e-mail, WhatsApp, CEP (opcional) e, só no pagamento do guest, CPF. O prompt aprovado pede finalidade separada para o marketing por CEP, minimização, CPF nunca em log e retenção do guest definida. Inventário completo no [dossiê, seção 8](../contexto.md#8-lgpd-inventário-de-dados-pessoais).

## Decisões

### 1. Marketing por CEP só com consentimento
- Finalidade `MARKETING_CEP` separada da operação. Caixa **opcional e desmarcada** no cadastro; o texto tem versão (`versao_texto`).
- Cada aceite ou revogação é uma **linha nova** em `consentimento` (prova com data e versão). Revogação a qualquer momento em `PUT /me/consentimentos/marketing`.
- O painel de alcance (pós-beta) mostra **só agregados por bairro e cidade**, conta só quem consentiu e **esconde grupos com menos de 5 pessoas** (k ≥ 5).
- Opções consideradas: (a) usar o CEP do cadastro para marketing sem perguntar (descartada: finalidade diferente sem base legal); (b) consentimento separado (**escolhida**).

### 2. CPF não persistido
- O prompt aceita "guardado criptografado **ou só repassado ao gateway, se ele permitir**". O Asaas guarda o CPF no cliente da cobrança; a API precisa só do id desse cliente.
- **Decisão:** a API recebe o CPF em `POST /pagamentos/cobrancas`, valida os dígitos, repassa ao gateway e **não grava** (nem cifrado). Guarda `id_cliente_provedor`.
- ⚠️ **Divergência com a arquitetura do Alicerce**, que previa "CPF criptografado e mascarado" no módulo `pagamento`. Motivo: o que não é guardado não vaza, e nenhuma regra do beta lê o CPF de volta (a devolução manual é feita no painel do gateway). Ponto explícito para a revisão do Alicerce.
- **Crítica registrada (Gemini, 13/11/2026):** sem o CPF guardado, a barbearia não emite nota fiscal nem prova titularidade numa disputa; o Gemini sugere CPF cifrado com base em obrigação legal (art. 7º, II). Resposta: o beta não emite nota fiscal e o gateway guarda o CPF da cobrança; se houver obrigação fiscal real, um ADR novo adota CPF cifrado (AES-GCM, chave por variável de ambiente). Decisão final com o Alicerce e o Kevin.
- O value object `Cpf` mascara no `toString()` (`***.***.***-09`), e o teste de log (R30) falha se um CPF aparecer.

### 3. Retenção e descarte
| Dado | Retenção proposta | Como |
|---|---|---|
| Contato do guest (`reserva_guest`) | **HIPÓTESE H13:** 90 dias após o atendimento | job com ShedLock no módulo `privacidade` anonimiza nome, e-mail e WhatsApp (`anonimizado_em`) |
| Registro financeiro do sinal (`cobranca`) | **HIPÓTESE H13:** 5 anos, **pendente de fonte legal** | sem dado pessoal na tabela (só ids e valores) |
| Conta excluída (`DELETE /me`) | imediata | anonimiza `usuario`, revoga refresh tokens, cancela reservas futuras; histórico de reservas passadas fica sem dado pessoal |
| Auditoria do admin | 1 ano (proposta) | job de limpeza |
| Eventos de webhook | 1 ano (proposta) | sem payload, só ids |

### 4. Nada pessoal em log
- Logs estruturados com `traceId`; nunca corpo de requisição. `Cpf`, `Telefone`, `Cep` e `Email` mascaram no `toString()`.
- Teste automatizado captura o log de um roteiro com dados fictícios e falha se encontrar CPF, telefone ou CEP (R30), em toda fatia.

## Consequências
- ✅ Menos dado guardado, menos risco e menos trabalho no "exportar/excluir".
- ⚠️ Prazos de retenção e bases legais precisam de validação antes de uso real (H13).

## Gatilhos para revisitar
- Gateway que não guarde o CPF → avaliar criptografia (AES-GCM com chave em variável de ambiente) por um novo ADR.
- Ligar o painel de alcance ou o lembrete por WhatsApp (nova finalidade).
