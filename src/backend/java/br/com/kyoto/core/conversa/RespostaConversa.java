package br.com.kyoto.core.conversa;

import java.util.Objects;

/** {@code texto} vazio quando a resposta vem depois, pela SophIA. */
public record RespostaConversa(Destino destino, String texto) {
    public RespostaConversa {
        Objects.requireNonNull(destino, "destino");
        texto = texto == null ? "" : texto;
    }

    public static RespostaConversa semTexto(Destino destino) {
        return new RespostaConversa(destino, "");
    }

    public boolean temTexto() {
        return !texto.isBlank();
    }
}
