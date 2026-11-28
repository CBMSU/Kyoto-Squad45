package br.com.kyoto.core.conversa;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RepositorioDeSessoesEmMemoria implements RepositorioDeSessoes {
    private final Map<String, SessaoConversa> sessoes = new ConcurrentHashMap<>();

    @Override
    public Optional<SessaoConversa> buscar(String telefone) {
        return Optional.ofNullable(sessoes.get(telefone));
    }

    @Override
    public void salvar(SessaoConversa sessao) {
        sessoes.put(sessao.morador().telefone(), sessao);
    }
}
