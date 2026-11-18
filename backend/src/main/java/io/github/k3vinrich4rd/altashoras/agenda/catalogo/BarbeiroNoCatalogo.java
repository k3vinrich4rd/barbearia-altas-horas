package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * O barbeiro como o visitante vê em {@code GET /barbeiros} (Fatia 6D): nome e o WhatsApp comercial, só dígitos, para o
 * front montar {@code https://wa.me/<whatsapp>}. É contato de trabalho divulgado pelo próprio barbeiro (D122), nunca dado
 * de cliente. Nulo quando o barbeiro não cadastrou número: o front esconde o botão.
 */
public record BarbeiroNoCatalogo(UUID id, String nomeExibicao, @Nullable String whatsapp) {
}
