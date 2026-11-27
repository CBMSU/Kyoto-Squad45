package br.com.kyoto.core.chamado;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RepositorioDeTermosEmMemoria implements RepositorioDeTermos {

    private final Map<String, TermoDeAbertura> porId = new ConcurrentHashMap<>();
    private final Map<String, String> idPorChamado = new ConcurrentHashMap<>();

    @Override
    public void salvar(TermoDeAbertura termo) {
        Objects.requireNonNull(termo, "termo");
        porId.put(termo.id(), termo);
        idPorChamado.put(termo.chamadoId(), termo.id());
    }

    @Override
    public Optional<TermoDeAbertura> buscar(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(porId.get(id));
    }

    @Override
    public Optional<TermoDeAbertura> buscarPorChamado(String chamadoId) {
        Objects.requireNonNull(chamadoId, "chamadoId");
        String id = idPorChamado.get(chamadoId);
        return id == null ? Optional.empty() : buscar(id);
    }
}
