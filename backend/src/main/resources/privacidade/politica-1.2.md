# Política de privacidade · versão 1.2

> **Rascunho técnico, não é parecer jurídico.** Este texto descreve o que o sistema faz com os dados hoje. Prazos e bases legais são hipóteses de trabalho (H13) e precisam de validação de quem entende de LGPD antes de qualquer uso real. Esta versão é de um projeto de portfólio (beta). O texto da autorização de região e desta política ainda passa pela revisão do Cautelar.

## 1. Quem cuida dos seus dados

A Barbearia Altas Horas (a "barbearia") é quem decide para que os dados de agendamento são usados. Para falar sobre os seus dados, use este canal: **{{contato}}**.

## 2. O que guardamos e para quê

| Dado | Quando entra | Para quê | Base legal (LGPD art. 7º) |
|---|---|---|---|
| Nome, e-mail, WhatsApp e senha (guardada só como hash, nunca em texto) | cadastro | criar a conta, entrar, avisar sobre o agendamento | execução do contrato |
| Bairro, cidade, UF e código do município do IBGE, **tirados do CEP só se você marcar a caixinha de região** (o CEP em si **não é guardado**) | cadastro, perfil ou reserva de visitante (opcional) | entender de onde vêm os clientes | consentimento |
| Serviço, horário, preço e situação do agendamento | cada reserva | atender, cobrar e mostrar sua agenda | execução do contrato |
| Nome, e-mail e WhatsApp de quem agenda **sem conta** (visitante) | reserva de visitante | confirmar e atender a reserva | execução do contrato |
| Valor, situação e identificadores da cobrança | pagamento | conferir que o pagamento chegou e devolver quando for o caso | execução do contrato e obrigação legal |
| Endereço de IP do administrador | ações no painel da barbearia | segurança: provar quem fez o quê | legítimo interesse |
| Consentimento de marketing e de região (aceito ou não, versão do texto e data) | cadastro e "Meus dados" | provar o que você aceitou | consentimento |
| Nome e observação (opcionais) de um agendamento marcado por fora do sistema, digitados pela barbearia | quando você combina o horário pelo WhatsApp e a barbearia o trava na agenda | reservar o horário combinado e reconhecer quem vem | execução do contrato |
| WhatsApp comercial do barbeiro | cadastro do barbeiro pelo administrador | você chamar a barbearia pelo botão de WhatsApp do site | legítimo interesse (contato de trabalho divulgado pelo próprio barbeiro) |

**WhatsApp do barbeiro e agendamento por fora do sistema.** O número que o site mostra para chamar o barbeiro é o WhatsApp comercial do barbeiro: um contato de trabalho que ele mesmo divulga e que o administrador cadastra. Ele não é dado de cliente. Quando você chama esse número, a conversa acontece dentro do WhatsApp, fora do nosso sistema, e vale a política do próprio WhatsApp. Se vocês combinarem um horário, a barbearia pode travá-lo na agenda como um agendamento marcado por fora do sistema, com no máximo o seu nome e uma observação curta; nesse caso, não guardamos o WhatsApp de quem combinou o horário, nem e-mail ou CPF, e nenhuma cobrança é criada (o pagamento é combinado direto com a barbearia e não gera registro financeiro no sistema).

**O que NÃO guardamos:** o CEP (ele é usado uma única vez para descobrir o bairro e a cidade e depois é descartado), o CPF (vai direto ao gateway de pagamento para emitir a cobrança e não fica no nosso banco), dados de cartão (o cartão é pago na página do gateway) nem a sua senha em texto.

## 3. Marketing é uma escolha separada

Receber ofertas com base na sua região só acontece se você marcar a caixa de marketing (consentimento). Você pode aceitar ou revogar a qualquer momento em "Meus dados". Revogar o marketing não apaga o seu bairro e cidade; só impede o uso para mandar promoção. Para apagar a sua região, retire a autorização da seção 4.

## 4. Alcance por região: só com a sua autorização

A barbearia mostra de onde vêm os **clientes** (pessoas diferentes, não a quantidade de atendimentos) por estado, região, município e bairro. Isso só usa o seu bairro e a sua cidade se você **marcar a caixinha de autorização** (consentimento, LGPD art. 7º, I). A caixinha nasce desmarcada, não é obrigatória e não muda o seu atendimento. Regras para proteger você:

- o CEP não é guardado: ficam só bairro, cidade, UF e o código do município;
- você pode **retirar a autorização** quando quiser. Quem tem conta retira em "Meus dados"; quem reservou como visitante usa o link "Retirar minha autorização de região" da confirmação da reserva. A região é apagada na hora, também das reservas anteriores da conta, e você sai do resumo;
- um lugar com **menos de 5 clientes** não aparece com o nome ou o número dele: ele sobe de nível e é somado a outros (bairro, município, região do IBGE, estado). O número de 5 é uma prática de anonimização da barbearia, não uma exigência da lei;
- a **vitrine pública** do site mostra só os nomes e **faixas de porcentagem** ("cerca de 15%", "menos de 5%"), nunca o total nem a quantidade de clientes;
- o resumo do painel mostra números só para o dono da barbearia e, para cada barbeiro convidado, só dos clientes dele, e nunca traz nome, e-mail, WhatsApp ou endereço de ninguém;
- esse resumo **não** é permissão para mandar promoção: para isso continua valendo só o consentimento da seção 3.

## 5. Com quem os dados passam

- **ViaCEP e BrasilAPI** (consulta de endereço): recebem o CEP que você digita, **só se você marcar a caixinha de região**, sem nome nem outro dado seu. Servem um ao outro de reserva.
- **Gateway de pagamento** (Pix e cartão): recebe o necessário para cobrar (nome, CPF informado no pagamento e valor). Não guardamos o CPF.
- **Hospedagem e banco de dados** (serviços em nuvem usados para rodar o sistema): guardam os dados descritos acima em nosso nome.

Não vendemos nem cedemos seus dados para anunciantes.

## 6. Por quanto tempo guardamos

| O quê | Prazo |
|---|---|
| Contato de quem agendou **como visitante** (nome, e-mail, WhatsApp) | anonimizado **{{retencaoGuestDias}} dias** depois do fim do atendimento; o link de acompanhamento deixa de funcionar |
| Bairro e cidade de quem reservou **como visitante** (se autorizou a região) | apagados junto com o contato, **{{retencaoGuestDias}} dias** depois do fim do atendimento, ou antes, se você retirar a autorização |
| Nome e observação de um agendamento marcado por fora do sistema | apagados **{{retencaoGuestDias}} dias** depois do fim do atendimento (o horário e o serviço continuam, sem identificar ninguém) |
| Endereço de IP no registro de ações do painel | apagado depois de **{{retencaoIpDias}} dias** (o registro de "quem fez o quê" continua, sem o IP) |
| Notificações técnicas do gateway já processadas (sem dados pessoais) | apagadas depois de **{{retencaoWebhookDias}} dias** |
| Registro financeiro da cobrança (valor, situação, datas, sem nome nem contato) | até 5 anos, por obrigação legal (prazo a validar, H13) |
| Conta de cliente (inclusive o bairro e a cidade, se autorizados) | até você pedir a exclusão ou retirar a autorização de região |
| Backup semanal do banco (criptografado) | guarda os dados por até **30 dias**, inclusive os de contas já excluídas; depois disso o arquivo é apagado |
| Cópias de segurança do provedor do banco | podem guardar dados já anonimizados por mais algum tempo, até expirarem pelo prazo do próprio provedor |

## 7. Seus direitos e como exercê-los

Você pode, a qualquer momento:

- **ver e mudar o consentimento de marketing e o de região**: em "Meus dados";
- **baixar uma cópia dos seus dados**: em "Meus dados", opção de exportar (até 5 vezes por hora);
- **excluir a sua conta**: em "Meus dados", opção de excluir. A conta é anonimizada (nome, e-mail, WhatsApp, senha e endereço saem), as sessões abertas deixam de valer e os agendamentos futuros que ainda não foram pagos são cancelados. O e-mail fica livre para um novo cadastro. O histórico de agendamentos passados continua, sem os seus dados pessoais;
- **corrigir ou pedir informações**: pelo canal da seção 1.

**Exclusão com agendamento pago:** se você tem um agendamento futuro já pago, ou um valor esperando devolução, a exclusão da conta é recusada até o atendimento acontecer, a barbearia cancelar ou a devolução ser registrada. Isso existe porque, sem a conta e o WhatsApp, a barbearia não teria como devolver o valor. Combine o cancelamento com a barbearia pelo canal da seção 1 e peça a exclusão de novo.

**Visitantes (sem conta):** a região você retira pelo link da confirmação da reserva. Para apagar o contato antes do prazo da seção 6, peça pelo canal da seção 1; a barbearia anonimiza por fora do sistema.

## 8. Mudanças neste texto

Quando o texto mudar, a versão sobe e a anterior continua guardada: a prova do seu consentimento precisa do texto exato que você viu. A versão vigente aparece em "Meus dados" e no cadastro.
