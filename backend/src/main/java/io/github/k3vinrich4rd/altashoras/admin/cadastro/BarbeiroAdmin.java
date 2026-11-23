package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.BarbeiroCadastrado;

/**
 * O barbeiro como o painel vê (contrato {@code BarbeiroAdmin}): com a conta ligada, o estado e a jornada. O {@code acesso}
 * (só leitura, contrato 1.8.0) diz em que pé está o login dele: {@code SEM_CONTA}, {@code CONVITE_PENDENTE} (com
 * {@code conviteExpiraEm}), {@code ATIVO} ou {@code DESLIGADO}. O e-mail do barbeiro nunca volta aqui (E7).
 */
record BarbeiroAdmin(UUID id, String nomeExibicao, @Nullable UUID usuarioId, @Nullable String whatsapp, boolean ativo,
        long version, List<IntervaloJornada> jornada, String acesso, @Nullable String conviteExpiraEm) {

    static final String SEM_CONTA = "SEM_CONTA";
    static final String CONVITE_PENDENTE = "CONVITE_PENDENTE";
    static final String ATIVO = "ATIVO";
    static final String DESLIGADO = "DESLIGADO";

    static BarbeiroAdmin de(BarbeiroCadastrado b, @Nullable String conviteExpiraEm, boolean contaDeBarbeiroAtiva) {
        return new BarbeiroAdmin(b.id(), b.nomeExibicao(), b.usuarioId(), b.whatsapp(), b.ativo(), b.version(),
                b.jornada().stream().map(IntervaloJornada::de).toList(), acesso(b, conviteExpiraEm != null, contaDeBarbeiroAtiva),
                conviteExpiraEm);
    }

    /**
     * Convite que ainda vale primeiro; depois o barbeiro desligado; depois a conta de barbeiro ativa. Um barbeiro reativado
     * sozinho (a conta antiga sem o papel) continua {@code DESLIGADO}: só um convite novo devolve o acesso (H31).
     */
    private static String acesso(BarbeiroCadastrado b, boolean convitePendente, boolean contaDeBarbeiroAtiva) {
        if (convitePendente) {
            return CONVITE_PENDENTE;
        }
        if (!b.ativo()) {
            return DESLIGADO;
        }
        if (b.usuarioId() == null) {
            return SEM_CONTA;
        }
        return contaDeBarbeiroAtiva ? ATIVO : DESLIGADO;
    }
}
