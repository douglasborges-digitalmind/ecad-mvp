package br.com.ecad.captacao.controlcenter;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.controlcenter.models.CriarDestinatarioRequest;
import br.com.ecad.captacao.shared.domain.entities.Destinatario;
import br.com.ecad.captacao.shared.infrastructure.repositories.DestinatarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/destinatarios")
class DestinatariosController {
    private final DestinatarioRepository repository;

    DestinatariosController(DestinatarioRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    ResponseEntity<Destinatario> criar(@RequestBody CriarDestinatarioRequest request) throws Exception {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome e obrigatorio");
        }

        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Email e obrigatorio");
        }

        var destinatario = new Destinatario();
        destinatario.nome = request.nome();
        destinatario.email = request.email();
        destinatario.whatsapp = request.whatsapp();

        var criado = repository.criar(destinatario);
        return ResponseEntity.created(URI.create("/api/destinatarios/" + criado.id)).body(criado);
    }

    @GetMapping
    List<Destinatario> listar() throws Exception {
        return repository.listar();
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> remover(@PathVariable("id") UUID id) throws Exception {
        repository.remover(id);
        return ResponseEntity.noContent().build();
    }
}
