package br.com.kyoto.core.conversa;

import java.util.Optional;

/** Será implementado com o {@code OdrconClient} da #22. */
public interface DiretorioDeMoradores {

    Optional<MoradorDaConversa> buscarPorTelefone(String telefone);
}
