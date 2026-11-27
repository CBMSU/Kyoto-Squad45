package br.com.kyoto.core.chamado;

public class TermoNaoGeradoException extends RuntimeException {

    public TermoNaoGeradoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
