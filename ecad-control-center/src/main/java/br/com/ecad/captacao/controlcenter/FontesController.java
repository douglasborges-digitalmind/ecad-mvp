package br.com.ecad.captacao.controlcenter;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.controlcenter.models.AtualizarFonteRequest;
import br.com.ecad.captacao.controlcenter.models.CriarFonteRequest;
import br.com.ecad.captacao.controlcenter.models.ExecutarScrapingLotePncpAsyncResult;
import br.com.ecad.captacao.controlcenter.models.ExecutarScrapingLotePncpAsyncStatus;
import br.com.ecad.captacao.controlcenter.models.ExecutarScrapingLotePncpRequest;
import br.com.ecad.captacao.controlcenter.models.ExecutarScrapingLotePncpResult;
import br.com.ecad.captacao.controlcenter.models.ExecutarScrapingManualRequest;
import br.com.ecad.captacao.controlcenter.models.MigrarFontesContratosResult;
import br.com.ecad.captacao.controlcenter.models.SetupPncpUrlsRequest;
import br.com.ecad.captacao.controlcenter.models.SetupPncpUrlsResult;
import br.com.ecad.captacao.controlcenter.services.FonteCaptacaoService;
import br.com.ecad.captacao.controlcenter.services.PncpAsyncBatchJobService;
import br.com.ecad.captacao.controlcenter.services.PncpUrlSetupService;
import br.com.ecad.captacao.shared.domain.entities.FonteCaptacao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fontes")
class FontesController {
    private final FonteCaptacaoService service;
    private final PncpUrlSetupService pncpUrlSetupService;
    private final PncpAsyncBatchJobService pncpAsyncBatchJobService;

    FontesController(FonteCaptacaoService service, PncpUrlSetupService pncpUrlSetupService, PncpAsyncBatchJobService pncpAsyncBatchJobService) {
        this.service = service;
        this.pncpUrlSetupService = pncpUrlSetupService;
        this.pncpAsyncBatchJobService = pncpAsyncBatchJobService;
    }

    @PostMapping
    ResponseEntity<FonteCaptacao> criar(@RequestBody CriarFonteRequest request) throws Exception {
        var fonte = service.criar(request);
        return ResponseEntity.created(URI.create("/api/fontes/" + fonte.id)).body(fonte);
    }

    @GetMapping
    List<FonteCaptacao> listar(
        @RequestParam(name = "unidade_ecad", required = false) String unidadeEcad,
        @RequestParam(name = "ativo", required = false) Boolean ativo) throws Exception {
        return service.listar(unidadeEcad, ativo);
    }

    @GetMapping("/{id}")
    ResponseEntity<FonteCaptacao> obter(@PathVariable("id") UUID id) throws Exception {
        var fonte = service.obterPorId(id);
        return fonte == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(fonte);
    }

    @PutMapping("/{id}")
    FonteCaptacao atualizar(@PathVariable("id") UUID id, @RequestBody AtualizarFonteRequest request) throws Exception {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> remover(@PathVariable("id") UUID id) throws Exception {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/executar")
    ResponseEntity<Void> executar(@PathVariable("id") UUID id, @RequestBody(required = false) ExecutarScrapingManualRequest request) throws Exception {
        service.executarScrapingManual(id, request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/executarContratoSemIA")
    ResponseEntity<Void> executarContratoSemIa(@PathVariable("id") UUID id) throws Exception {
        service.executarScrapingContratoSemIa(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/executar-lote-pncp")
    ExecutarScrapingLotePncpResult executarLotePncp(@RequestBody(required = false) ExecutarScrapingLotePncpRequest request) throws Exception {
        return service.executarScrapingLotePncp(request);
    }

    @PostMapping("/executar-lote-pncp/async")
    ResponseEntity<ExecutarScrapingLotePncpAsyncResult> executarLotePncpAsync(@RequestBody(required = false) ExecutarScrapingLotePncpRequest request) {
        var result = pncpAsyncBatchJobService.iniciar(request);
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping("/executar-lote-pncp/jobs/{jobId}")
    ResponseEntity<ExecutarScrapingLotePncpAsyncStatus> obterStatusLotePncp(@PathVariable("jobId") UUID jobId) {
        var status = pncpAsyncBatchJobService.obter(jobId);
        return status == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(status);
    }

    @PostMapping("/setup-pncp-urls")
    SetupPncpUrlsResult setupPncpUrls(@RequestBody(required = false) SetupPncpUrlsRequest request) throws Exception {
        return pncpUrlSetupService.setup(request);
    }

    @PostMapping("/migrarFontesContratos")
    ResponseEntity<MigrarFontesContratosResult> migrarFontesContratos() throws Exception {
        return ResponseEntity.ok(service.migrarFontesContratos());
    }
}