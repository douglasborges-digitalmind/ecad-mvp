package br.com.ecad.captacao.controlcenter.models;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record MetricasCustosResponse(
    CustoTotalPorPeriodo custoTotal,
    List<CustoPorComponente> custosPorComponente,
    List<CustoPorTipoDocumento> custosPorTipoDocumento,
    List<CustoPorFonte> custosPorFonte,
    ConsumoTokens tokens,
    DuracaoChamadas duracaoChamadas,
    List<DuracaoChamadasPorComponente> duracaoChamadasPorComponente,
    BigDecimal taxaFallbackPercentual,
    BigDecimal taxaSucessoPercentual,
    List<TaxaDescartePorTipo> taxasDescartePorTipo,
    List<ResumoOperacionalPorComponente> operacionalPorComponente,
    FiltrosAplicados filtros
) {
    public record CustoTotalPorPeriodo(BigDecimal totalUsd, BigDecimal diarioUsd, BigDecimal semanalUsd, BigDecimal mensalUsd) {
    }

    public record CustoPorComponente(String componente, BigDecimal custoUsd, int totalChamadas) {
    }

    public record CustoPorTipoDocumento(String tipoDocumento, BigDecimal custoUsd, int totalChamadas) {
    }

    public record CustoPorFonte(String idFonteCaptacao, BigDecimal custoUsd, int totalChamadas) {
    }

    public record ConsumoTokens(long totalInput, long totalOutput, long total) {
    }

    public record DuracaoChamadas(long totalMs, BigDecimal mediaMs, long minMs, long maxMs) {
    }

    public record DuracaoChamadasPorComponente(String componente, long totalMs, BigDecimal mediaMs, long minMs, long maxMs, int totalChamadas) {
    }

    public record ResumoOperacionalPorComponente(
        String componente,
        String operacao,
        int totalExecucoes,
        int sucessos,
        int falhas,
        BigDecimal taxaSucessoPercentual,
        long totalMs,
        BigDecimal mediaMs,
        long minMs,
        long maxMs,
        int totalItensProcessados) {
    }

    public record TaxaDescartePorTipo(String tipoDocumento, BigDecimal taxaDescartePercentual, int totalProcessados, int totalDescartados) {
    }

    public record FiltrosAplicados(OffsetDateTime periodoInicio, OffsetDateTime periodoFim, String componente, String tipoDocumento, String idFonteCaptacao) {
    }
}
