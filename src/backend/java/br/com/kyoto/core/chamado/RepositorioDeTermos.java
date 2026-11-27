package br.com.kyoto.core.chamado;

import java.util.Optional;

public interface RepositorioDeTermos {

    void salvar(TermoDeAbertura termo);

    Optional<TermoDeAbertura> buscar(String id);

    Optional<TermoDeAbertura> buscarPorChamado(String chamadoId);
}
