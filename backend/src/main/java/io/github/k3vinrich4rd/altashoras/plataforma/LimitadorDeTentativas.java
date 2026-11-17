package io.github.k3vinrich4rd.altashoras.plataforma;

import java.time.Duration;

/**
 * Porta do limite de tentativas (R26, ADR 0011). Hoje o adaptador guarda tudo em memória (uma instância só);
 * com mais de uma instância, troca-se por Redis ou tabela sem tocar em quem usa.
 */
public interface LimitadorDeTentativas {

    /** Segundos que faltam para a chave ser liberada; 0 quando ainda há tentativa disponível. */
    long segundosBloqueado(String chave, int limite, Duration janela);

    /** Conta uma tentativa agora. */
    void registrar(String chave, Duration janela);

    /** Esquece as tentativas da chave (ex.: login certo). */
    void zerar(String chave);
}
