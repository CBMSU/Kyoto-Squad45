package br.com.kyoto.core.conversa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TimerTest {

    @Test
    void naoExecutaAntesDoHorario() {
        AtomicInteger execucoes = new AtomicInteger();
        Timer timer = new Timer(LocalDateTime.now().plusHours(1), execucoes::incrementAndGet);

        assertFalse(timer.update());
        assertEquals(0, execucoes.get());
    }

    @Test
    void executaDepoisDoHorario() {
        AtomicInteger execucoes = new AtomicInteger();
        Timer timer = new Timer(LocalDateTime.now().minusSeconds(1), execucoes::incrementAndGet);

        assertTrue(timer.update());
        assertEquals(1, execucoes.get());
    }

    @Test
    void executaUmaVezSo() {
        AtomicInteger execucoes = new AtomicInteger();
        Timer timer = new Timer(LocalDateTime.now().minusSeconds(1), execucoes::incrementAndGet);

        assertTrue(timer.update());
        assertFalse(timer.update());
        assertFalse(timer.update());
        assertEquals(1, execucoes.get());
    }

    @Test
    void copiaComNovoHorarioMantemAAcao() {
        AtomicInteger execucoes = new AtomicInteger();
        Timer original = new Timer(LocalDateTime.now().plusHours(1), execucoes::incrementAndGet);
        Timer adiantado = new Timer(original, LocalDateTime.now().minusSeconds(1));

        assertFalse(original.update());
        assertTrue(adiantado.update());
        assertEquals(1, execucoes.get());
    }
}
