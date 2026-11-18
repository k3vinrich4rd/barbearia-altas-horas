package io.github.k3vinrich4rd.altashoras.privacidade;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.SENHA;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.emailNovo;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.json;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/**
 * Começo comum dos testes da privacidade (Fatia 6B). Aproveita o contexto e a massa do {@link PainelBase}: o relógio
 * começa em terça 01/12/2026, 09:30, as tabelas de reserva, cobrança e auditoria começam vazias, e os tokens saem do
 * login de verdade. Contas, e-mails, WhatsApp e IPs são fictícios ({@code @exemplo.test}, 203.0.113.x). O cadastro
 * dos testes não manda CEP: nenhum teste desta fatia fala com provedor de endereço.
 */
public abstract class PrivacidadeBase extends PainelBase {

    protected static final String IP = "203.0.113.7";

    /** Cliente cadastrado e logado de verdade (o token e o refresh vêm do login). */
    protected record Cliente(UUID id, String email, String acesso, String refresh) {
    }

    /** Cadastra pela API, no instante atual do relógio do teste, e devolve o e-mail. */
    protected String cadastrar(boolean aceitaMarketing) throws Exception {
        String email = emailNovo();
        mvc.perform(json(MockMvcRequestBuilders.post("/auth/cadastro"), """
                { "nome": "Cliente Teste", "email": "%s", "whatsapp": "(11) 90000-0001", "senha": "%s",
                  "aceitaMarketing": %s }
                """.formatted(email, SENHA, aceitaMarketing))).andExpect(status().isCreated());
        return email;
    }

    /** Login de verdade, no instante atual do relógio (o token vale 15 minutos a partir daí). */
    protected Cliente entrar(String email) throws Exception {
        ContaDeTeste.Sessao sessao = ContaDeTeste.entrar(mvc, email, SENHA);
        UUID id = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
        return new Cliente(id, email, sessao.acesso(), sessao.refresh());
    }

    protected Cliente novoCliente(boolean aceitaMarketing) throws Exception {
        return entrar(cadastrar(aceitaMarketing));
    }

    protected ResultActions put(String rota, String token, String corpo) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(rota).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    protected ResultActions delete(String rota, String token) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete(rota).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected static String consentimento(boolean aceito, String versao) {
        return """
                { "aceito": %s, "versaoTexto": "%s" }
                """.formatted(aceito, versao);
    }

    /** As linhas de consentimento da conta, da mais antiga para a mais nova. */
    protected List<Map<String, Object>> consentimentosNoBanco(UUID usuarioId) {
        return jdbc.queryForList("""
                SELECT finalidade, aceito, versao_texto, registrado_em FROM consentimento
                WHERE usuario_id = ? ORDER BY registrado_em, id
                """, usuarioId);
    }

    protected long linhasDeConsentimento(UUID usuarioId) {
        return jdbc.queryForObject("SELECT count(*) FROM consentimento WHERE usuario_id = ?", Long.class, usuarioId);
    }

    /** Linha que o teste põe no histórico com a data que quiser (a API sempre grava com o relógio). */
    protected void consentimentoEm(UUID usuarioId, boolean aceito, OffsetDateTime quando) {
        jdbc.update("""
                INSERT INTO consentimento (usuario_id, finalidade, versao_texto, aceito, registrado_em)
                VALUES (?, 'MARKETING_CEP', '1.0', ?, ?)
                """, usuarioId, aceito, quando);
    }

    protected static Instant instante(Object valorDoBanco) {
        // queryForMap devolve java.sql.Timestamp para timestamptz; o JDBC também pode entregar OffsetDateTime.
        if (valorDoBanco instanceof java.sql.Timestamp carimbo) {
            return carimbo.toInstant();
        }
        return ((OffsetDateTime) valorDoBanco).toInstant();
    }

    protected long contar(String sql, Object... parametros) {
        return jdbc.queryForObject(sql, Long.class, parametros);
    }
}
