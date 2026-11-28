package br.com.kyoto.core.conversa;

import java.time.LocalDateTime;

/**
 * Agenda uma ação para ser executada a partir de um horário.
 *
 * <p>Não cria threads: quem usa o timer chama {@link #update()} periodicamente
 * e a ação roda quando o horário já passou. Pensado para prazos da conversa,
 * como expirar uma sessão inativa ou transferir para atendimento humano.</p>
 */
public class Timer {
    private final LocalDateTime origin;
    private final LocalDateTime callBackTime;
    private final Runnable callBack;
    private boolean executed;

    public Timer(LocalDateTime origin, LocalDateTime callBackTime, Runnable callBack) {
        this.origin = origin;
        this.callBackTime = callBackTime;
        this.callBack = callBack;
    }

    public Timer(Timer orig) {
        this(orig.origin, orig.callBackTime, orig.callBack);
    }

    public Timer(Timer orig, LocalDateTime callBackTime) {
        this(orig.origin, callBackTime, orig.callBack);
    }

    public Timer(LocalDateTime callBackTime, Runnable callBack) {
        this(LocalDateTime.now(), callBackTime, callBack);
    }

    /** Roda a ação uma vez só, na primeira chamada depois do horário. */
    public boolean update() {
        if (executed || !callBackTime.isBefore(LocalDateTime.now())) {
            return false;
        }
        executed = true;
        callBack.run();
        return true;
    }
}
