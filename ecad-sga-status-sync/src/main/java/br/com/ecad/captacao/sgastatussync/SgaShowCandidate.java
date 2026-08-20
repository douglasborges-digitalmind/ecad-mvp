package br.com.ecad.captacao.sgastatussync;

import java.time.LocalDate;

record SgaShowCandidate(String titulo, LocalDate dataPrevista, String municipio, String codigo, String status) {
}
