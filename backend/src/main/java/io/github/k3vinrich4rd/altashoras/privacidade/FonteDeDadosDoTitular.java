package io.github.k3vinrich4rd.altashoras.privacidade;

import java.util.Map;
import java.util.UUID;

/**
 * Porta da exportação (LGPD art. 18, E10, E14): cada módulo que guarda dado do titular a implementa e entrega só o
 * que é DELE, no formato do contrato. A privacidade junta as respostas sem ler tabela alheia. Os pares devolvidos
 * entram na raiz da exportação (a fonte {@code conta} devolve {@code {"conta": {...}}}). Nunca devolver hash de senha,
 * token, IP, CPF nem dado de outra pessoa.
 */
public interface FonteDeDadosDoTitular {

    /** Nome da fonte, para ordenar e para o log (nunca o conteúdo). */
    String chave();

    Map<String, Object> exportar(UUID usuarioId);
}
