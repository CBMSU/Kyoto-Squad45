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

    /**
     * Executa a ação se o horário já passou.
     *
     * @return {@code true} se o horário já passou e a ação foi executada
     */
    public boolean update() {
        boolean hasPassed = callBackTime.isBefore(LocalDateTime.now());
        if (hasPassed) callBack.run();
        return hasPassed;
    }
}
