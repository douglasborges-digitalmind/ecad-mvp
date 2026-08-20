package br.com.ecad.captacao.controlcenter.services;

import java.util.List;
import java.util.Optional;

import br.com.ecad.captacao.shared.referencedata.PncpMunicipiosReferenceCatalog;
import org.springframework.stereotype.Service;

@Service
public class PncpMunicipiosCatalog {
    public Optional<PncpMunicipioCatalogItem> find(String municipio, String uf) {
        return PncpMunicipiosReferenceCatalog.tryResolve(municipio, uf)
            .map(item -> new PncpMunicipioCatalogItem(item.municipio(), item.uf(), item.unidadeEcad(), item.idPncp(), item.url()));
    }

    public List<PncpMunicipioCatalogItem> listAll() {
        return PncpMunicipiosReferenceCatalog.getAll().stream()
            .map(item -> new PncpMunicipioCatalogItem(item.municipio(), item.uf(), item.unidadeEcad(), item.idPncp(), item.url()))
            .toList();
    }

    public record PncpMunicipioCatalogItem(String municipio, String uf, String unidadeEcad, String idPncp, String url) {
    }
}