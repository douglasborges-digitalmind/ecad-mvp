package br.com.ecad.captacao.controlcenter.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse;
import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse.ConsumoTokens;
import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse.CustoPorComponente;
import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse.CustoPorFonte;
import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse.CustoPorTipoDocumento;
import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse.CustoTotalPorPeriodo;
import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse.DuracaoChamadas;
import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse.DuracaoChamadasPorComponente;
import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse.FiltrosAplicados;
import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse.ResumoOperacionalPorComponente;
import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse.TaxaDescartePorTipo;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.JsonBackedEnum;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaIARepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaOperacionalRepository;
import org.springframework.stereotype.Service;

@Service
public class MetricasService {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final MetricaIARepository metricaRepository;
    private final MetricaOperacionalRepository metricaOperacionalRepository;

    public MetricasService(MetricaIARepository metricaRepository, MetricaOperacionalRepository metricaOperacionalRepository) {
        this.metricaRepository = metricaRepository;
        this.metricaOperacionalRepository = metricaOperacionalRepository;
    }

    public MetricasCustosResponse obterMetricasCustos(
        OffsetDateTime periodoInicio,
        OffsetDateTime periodoFim,
        ComponenteIA componente,
        TipoEvidencia tipoDocumento,
        UUID idFonteCaptacao) throws IOException {
        var metricas = metricaRepository.listar(periodoInicio, periodoFim, componente, tipoDocumento, idFonteCaptacao);
        var metricasOperacionais = metricaOperacionalRepository.listar(periodoInicio, periodoFim, componente, idFonteCaptacao);

        // Single-pass: acumula todos os agregados em uma única iteração pela lista
        var accumulator = new MetricasAccumulator(OffsetDateTime.now());
        for (var metrica : metricas) {
            accumulator.accumulate(metrica);
        }

        // Single-pass para métricas operacionais
        var operAccumulator = new OperacionalAccumulator();
        for (var metrica : metricasOperacionais) {
            operAccumulator.accumulate(metrica);
        }

        return new MetricasCustosResponse(
            accumulator.buildCustoTotal(),
            accumulator.buildCustosPorComponente(),
            accumulator.buildCustosPorTipoDocumento(),
            accumulator.buildCustosPorFonte(),
            accumulator.buildConsumoTokens(),
            accumulator.buildDuracaoChamadas(),
            accumulator.buildDuracaoChamadasPorComponente(),
            accumulator.buildTaxaFallback(),
            accumulator.buildTaxaSucesso(),
            accumulator.buildTaxasDescartePorTipo(),
            operAccumulator.buildResumoOperacional(),
            new FiltrosAplicados(
                periodoInicio,
                periodoFim,
                componente == null ? null : pascalCase(componente),
                tipoDocumento == null ? null : pascalCase(tipoDocumento),
                idFonteCaptacao == null ? null : idFonteCaptacao.toString()));
    }

    // -------------------------------------------------------------------------
    // Accumulator: single-pass por Metricas IA
    // -------------------------------------------------------------------------
    private static final class MetricasAccumulator {
        private final LocalDate hoje;
        private final LocalDate inicioSemana;
        private final int anoAtual;
        private final int mesAtual;

        // Totais gerais
        private BigDecimal custoTotal = BigDecimal.ZERO;
        private BigDecimal custoDiario = BigDecimal.ZERO;
        private BigDecimal custoSemanal = BigDecimal.ZERO;
        private BigDecimal custoMensal = BigDecimal.ZERO;
        private long tokensInput;
        private long tokensOutput;
        private long duracaoTotalMs;
        private long duracaoMinMs = Long.MAX_VALUE;
        private long duracaoMaxMs = Long.MIN_VALUE;
        private int totalItens;
        private int totalSucessos;

        // Agrupamentos incrementais (sem listas — apenas acumuladores)
        private final Map<ComponenteIA, GroupCost> byComponente = new HashMap<>();
        private final Map<TipoEvidencia, GroupCost> byTipoDocumento = new HashMap<>();
        private final Map<UUID, GroupCost> byFonte = new HashMap<>();
        private final Map<ComponenteIA, GroupDuration> duracaoByComponente = new HashMap<>();
        private final Map<UUID, Integer> ocorrenciasPorExecucao = new HashMap<>(); // idExecucao -> count
        private final Map<TipoEvidencia, DescarteGroup> descarteByTipo = new HashMap<>();

        MetricasAccumulator(OffsetDateTime referencia) {
            var agora = referencia == null ? OffsetDateTime.now() : referencia;
            this.hoje = agora.toLocalDate();
            this.inicioSemana = hoje.minusDays(agora.getDayOfWeek().getValue() % DayOfWeek.SUNDAY.getValue());
            this.anoAtual = agora.getYear();
            this.mesAtual = agora.getMonthValue();
        }

        void accumulate(MetricaExecucaoIA m) {
            // -- Custo
            var custo = m.custoUsd == null ? BigDecimal.ZERO : m.custoUsd;
            custoTotal = custoTotal.add(custo);
            if (m.timestamp != null) {
                var data = m.timestamp.toLocalDate();
                if (data.equals(hoje)) custoDiario = custoDiario.add(custo);
                if (!data.isBefore(inicioSemana)) custoSemanal = custoSemanal.add(custo);
                if (m.timestamp.getYear() == anoAtual && m.timestamp.getMonthValue() == mesAtual)
                    custoMensal = custoMensal.add(custo);
            }

            // -- Tokens
            tokensInput += m.tokensInput;
            tokensOutput += m.tokensOutput;

            // -- Duração
            duracaoTotalMs += m.duracaoChamadaMs;
            duracaoMinMs = Math.min(duracaoMinMs, m.duracaoChamadaMs);
            duracaoMaxMs = Math.max(duracaoMaxMs, m.duracaoChamadaMs);
            totalItens++;
            if (m.sucesso) totalSucessos++;

            // -- Agrupamentos por componente
            if (m.componente != null) {
                byComponente.computeIfAbsent(m.componente, k -> new GroupCost()).add(custo);
                duracaoByComponente.computeIfAbsent(m.componente, k -> new GroupDuration()).add(m.duracaoChamadaMs);
            }

            // -- Agrupamento por tipo de documento
            if (m.tipoDocumento != null) {
                byTipoDocumento.computeIfAbsent(m.tipoDocumento, k -> new GroupCost()).add(custo);
            }

            // -- Agrupamento por fonte
            if (m.idFonteCaptacao != null) {
                byFonte.computeIfAbsent(m.idFonteCaptacao, k -> new GroupCost()).add(custo);
            }

            // -- Fallback (agrupa por idExecucao)
            if (m.idExecucao != null) {
                ocorrenciasPorExecucao.merge(m.idExecucao, Integer.valueOf(1), Integer::sum);
            }

            // -- Descarte (apenas itens com resultadoDescarte != null)
            if (m.resultadoDescarte != null && m.tipoDocumento != null) {
                descarteByTipo.computeIfAbsent(m.tipoDocumento, k -> new DescarteGroup()).add(m.resultadoDescarte);
            }
        }

        // --- Builders ---

        CustoTotalPorPeriodo buildCustoTotal() {
            return new CustoTotalPorPeriodo(custoTotal, custoDiario, custoSemanal, custoMensal);
        }

        List<CustoPorComponente> buildCustosPorComponente() {
            return byComponente.entrySet().stream()
                .map(e -> new CustoPorComponente(pascalCase(e.getKey()), e.getValue().custo, e.getValue().count))
                .sorted(Comparator.comparing(CustoPorComponente::custoUsd).reversed())
                .toList();
        }

        List<CustoPorTipoDocumento> buildCustosPorTipoDocumento() {
            return byTipoDocumento.entrySet().stream()
                .map(e -> new CustoPorTipoDocumento(pascalCase(e.getKey()), e.getValue().custo, e.getValue().count))
                .sorted(Comparator.comparing(CustoPorTipoDocumento::custoUsd).reversed())
                .toList();
        }

        List<CustoPorFonte> buildCustosPorFonte() {
            return byFonte.entrySet().stream()
                .map(e -> new CustoPorFonte(e.getKey().toString(), e.getValue().custo, e.getValue().count))
                .sorted(Comparator.comparing(CustoPorFonte::custoUsd).reversed())
                .toList();
        }

        ConsumoTokens buildConsumoTokens() {
            return new ConsumoTokens(tokensInput, tokensOutput, tokensInput + tokensOutput);
        }

        DuracaoChamadas buildDuracaoChamadas() {
            if (totalItens == 0) {
                return new DuracaoChamadas(0, BigDecimal.ZERO, 0, 0);
            }
            return new DuracaoChamadas(
                duracaoTotalMs,
                divide(duracaoTotalMs, totalItens),
                duracaoMinMs,
                duracaoMaxMs);
        }

        List<DuracaoChamadasPorComponente> buildDuracaoChamadasPorComponente() {
            return duracaoByComponente.entrySet().stream()
                .map(e -> {
                    var g = e.getValue();
                    return new DuracaoChamadasPorComponente(
                        pascalCase(e.getKey()), g.total, divide(g.total, g.count),
                        g.min, g.max, g.count);
                })
                .sorted(Comparator.comparing(DuracaoChamadasPorComponente::totalMs).reversed())
                .toList();
        }

        BigDecimal buildTaxaFallback() {
            if (ocorrenciasPorExecucao.isEmpty()) return BigDecimal.ZERO;
            var comFallback = ocorrenciasPorExecucao.values().stream().filter(c -> c > 1).count();
            return percent(comFallback, ocorrenciasPorExecucao.size());
        }

        BigDecimal buildTaxaSucesso() {
            if (totalItens == 0) return BigDecimal.ZERO;
            return percent(totalSucessos, totalItens);
        }

        List<TaxaDescartePorTipo> buildTaxasDescartePorTipo() {
            return descarteByTipo.entrySet().stream()
                .map(e -> {
                    var g = e.getValue();
                    return new TaxaDescartePorTipo(
                        pascalCase(e.getKey()), percent(g.descartados, g.total), g.total, g.descartados);
                })
                .sorted(Comparator.comparing(TaxaDescartePorTipo::taxaDescartePercentual).reversed())
                .toList();
        }
    }

    // -------------------------------------------------------------------------
    // Accumulator: single-pass por Metricas Operacionais
    // -------------------------------------------------------------------------
    private static final class OperacionalAccumulator {
        // Chave composta: componente + "\u0000" + operacao
        private final Map<String, OperGroup> groups = new HashMap<>();

        void accumulate(MetricaExecucaoOperacional m) {
            var key = m.componente + "\u0000" + m.operacao;
            groups.computeIfAbsent(key, k -> new OperGroup(m.componente, m.operacao)).add(m);
        }

        List<ResumoOperacionalPorComponente> buildResumoOperacional() {
            return groups.values().stream()
                .map(g -> new ResumoOperacionalPorComponente(
                    pascalCase(g.componente), g.operacao, g.total,
                    g.sucessos, g.total - g.sucessos,
                    percent(g.sucessos, g.total),
                    g.duracaoTotal, divide(g.duracaoTotal, g.total),
                    g.duracaoMin, g.duracaoMax, g.itensProcessados))
                .sorted(Comparator.comparing(ResumoOperacionalPorComponente::totalMs).reversed())
                .toList();
        }

        private static final class OperGroup {
            final ComponenteIA componente;
            final String operacao;
            int total;
            int sucessos;
            long duracaoTotal;
            long duracaoMin = Long.MAX_VALUE;
            long duracaoMax = Long.MIN_VALUE;
            int itensProcessados;

            OperGroup(ComponenteIA componente, String operacao) {
                this.componente = componente;
                this.operacao = operacao;
            }

            void add(MetricaExecucaoOperacional m) {
                total++;
                if (m.sucesso) sucessos++;
                duracaoTotal += m.duracaoTotalMs;
                duracaoMin = Math.min(duracaoMin, m.duracaoTotalMs);
                duracaoMax = Math.max(duracaoMax, m.duracaoTotalMs);
                itensProcessados += m.itensProcessados;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Inner records and helpers
    // -------------------------------------------------------------------------
    private static final class GroupCost {
        BigDecimal custo = BigDecimal.ZERO;
        int count;

        void add(BigDecimal custo) {
            this.custo = this.custo.add(custo);
            this.count++;
        }
    }

    private static final class GroupDuration {
        long total;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        int count;

        void add(long ms) {
            total += ms;
            min = Math.min(min, ms);
            max = Math.max(max, ms);
            count++;
        }
    }

    private static final class DescarteGroup {
        int total;
        int descartados;

        void add(boolean descarte) {
            total++;
            if (descarte) descartados++;
        }
    }

    private static BigDecimal divide(long value, int divisor) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(long numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).multiply(ONE_HUNDRED)
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static String pascalCase(JsonBackedEnum value) {
        var jsonValue = value.jsonValue();
        return Character.toUpperCase(jsonValue.charAt(0)) + jsonValue.substring(1);
    }
}