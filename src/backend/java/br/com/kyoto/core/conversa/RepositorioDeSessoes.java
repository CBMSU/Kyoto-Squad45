package br.com.kyoto.core.conversa;

import java.util.Optional;

/** Em produção deve gravar no banco, para a conversa sobreviver a um reinício. */
public interface RepositorioDeSessoes {

    Optional<SessaoConversa> buscar(String telefone);

    void salvar(SessaoConversa sessao);
}
