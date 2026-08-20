package br.com.ecad.captacao.sgastatussync;

import java.time.LocalDate;

record SgaEventQuery(String tituloEvento, LocalDate dataRealizacao, String municipio, String uf, Integer codMunicipio) {
}
