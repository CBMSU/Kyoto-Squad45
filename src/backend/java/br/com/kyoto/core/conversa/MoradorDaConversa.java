package br.com.kyoto.core.conversa;

import java.util.Objects;

/** {@code telefone} já normalizado: é a chave da sessão. */
public record MoradorDaConversa(String id, String nome, String telefone) {
    public MoradorDaConversa {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nome, "nome");
        Objects.requireNonNull(telefone, "telefone");
    }
}
