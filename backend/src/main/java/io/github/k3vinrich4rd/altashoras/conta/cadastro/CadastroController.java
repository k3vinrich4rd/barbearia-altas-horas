package io.github.k3vinrich4rd.altashoras.conta.cadastro;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.conta.usuario.PerfilResposta;

@RestController
class CadastroController {

    private final CadastrarCliente cadastrarCliente;

    CadastroController(CadastrarCliente cadastrarCliente) {
        this.cadastrarCliente = cadastrarCliente;
    }

    @PostMapping("/auth/cadastro")
    ResponseEntity<PerfilResposta> cadastrar(@Valid @RequestBody CadastroRequest pedido, HttpServletRequest request) {
        return ResponseEntity.created(URI.create("/me")).body(cadastrarCliente.cadastrar(pedido, request.getRemoteAddr()));
    }
}
