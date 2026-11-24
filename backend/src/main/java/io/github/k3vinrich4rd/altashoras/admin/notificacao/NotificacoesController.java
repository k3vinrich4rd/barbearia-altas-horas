package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * As rotas {@code /admin/notificacoes/**} (Fatia 13): ADMIN ou BARBEIRO (o SegurancaConfig barra o CLIENTE e a senha
 * inicial). O escopo é SEMPRE a conta do token ({@code sub}): nenhuma rota recebe {@code usuarioId} nem {@code barbeiroId},
 * e um parâmetro desses na URL é ignorado sem sinal (A46). Nunca em cache: a resposta depende de quem pergunta.
 */
@RestController
class NotificacoesController {

    private final AvisosDoPainel avisos;
    private final AparelhosDoBarbeiro aparelhos;

    NotificacoesController(AvisosDoPainel avisos, AparelhosDoBarbeiro aparelhos) {
        this.avisos = avisos;
        this.aparelhos = aparelhos;
    }

    @GetMapping("/admin/notificacoes/chave-publica")
    ResponseEntity<AparelhosDoBarbeiro.ChavePublica> chavePublica() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(aparelhos.chavePublica());
    }

    @PostMapping("/admin/notificacoes/inscricao")
    ResponseEntity<Void> inscrever(@AuthenticationPrincipal Jwt token, @Valid @RequestBody InscricaoRequest pedido) {
        aparelhos.inscrever(conta(token), pedido);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/admin/notificacoes/inscricao")
    ResponseEntity<Void> remover(@AuthenticationPrincipal Jwt token, @Valid @RequestBody RemocaoDeInscricaoRequest pedido) {
        aparelhos.remover(conta(token), pedido.endpoint());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/admin/notificacoes/teste")
    ResponseEntity<Void> teste(@AuthenticationPrincipal Jwt token) {
        aparelhos.enviarTeste(conta(token));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/admin/notificacoes")
    ResponseEntity<AvisosDoPainel.Lista> novidades(@AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "" + AvisosDoPainel.LIMITE_PADRAO) int limite) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(avisos.listar(conta(token), limite));
    }

    @PostMapping("/admin/notificacoes/marcar-lidas")
    ResponseEntity<Void> marcarLidas(@AuthenticationPrincipal Jwt token) {
        avisos.marcarLidas(conta(token));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static UUID conta(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
