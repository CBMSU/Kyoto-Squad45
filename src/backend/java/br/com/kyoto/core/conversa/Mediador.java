package br.com.kyoto.core.conversa;

/** A SophIA (#21). Ela mesma leva a mensagem moderada ao outro morador. */
public interface Mediador {

    void encaminhar(MensagemDeMediacao mensagem);
}
