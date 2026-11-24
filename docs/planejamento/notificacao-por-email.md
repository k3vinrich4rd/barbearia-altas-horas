# Notificação por e-mail: o que falta para ligar (Fatia 13, E12)

**Hoje o canal de e-mail está pronto e desligado.** A porta `CanalDeNotificacao` existe, o `EmailCanal` implementa a porta como stub e é provado com um cliente falso (`EmailCanalTest`); o `Despachante` só o chama se `email` estiver em `app.notificacao.canais` (padrão: só `webpush`). Ligado sem configuração, ele devolve `NAO_CONFIGURADO` e registra uma linha sem dado pessoal.

## O que falta (nada disso foi feito; cada item é decisão ou conta do Kevin)

| # | O que | Quem | Detalhe |
|---|---|---|---|
| 1 | Conta num provedor de e-mail gratuito | **Kevin** | Proposta: **Brevo** (plano grátis). A conta é criada por ele; nenhuma conta foi criada pelo sistema |
| 2 | Remetente verificado | Kevin | domínio ou e-mail remetente com **SPF e DKIM** configurados no provedor, senão cai em spam |
| 3 | Chave de API como segredo | Kevin | variável nova no Render com `sync: false` (nunca no Git nem em log) |
| 4 | Adaptador do provedor | desenvolvimento | uma classe que implementa `ClienteDeEmail` (um POST na API do provedor com o `HttpClient` do JDK, timeout de 5 s, fora de transação) e vira bean; o `EmailCanal` já a usa |
| 5 | `app.notificacao.email.remetente` | Kevin | o remetente do item 2 (a propriedade já é lida pelo `EmailCanal`; no Render, uma variável nova mapeada para ela) |
| 6 | Ligar o canal | Kevin | `APP_NOTIFICACAO_CANAIS=webpush,email` e um deploy |
| 7 | Política de privacidade | Cautelar e advogado | citar o provedor de e-mail como operador (a política vigente é a 1.2 da Fatia 11; a mudança seria a **1.3**) |

## Para onde vai o e-mail

Para o **e-mail de login do barbeiro** (o que ele mesmo digitou ao aceitar o convite, D154), lido por `Contas.emailDaConta` só quando o canal de e-mail está ligado e só de conta ativa. O texto é o mesmo do push (serviço, dia e hora): **sem nome do cliente**, sem WhatsApp, sem valor.

## Como provar sem provedor real

Os testes usam um `ClienteDeEmail` falso que só grava o que recebeu. Nenhum teste fala com a rede.
