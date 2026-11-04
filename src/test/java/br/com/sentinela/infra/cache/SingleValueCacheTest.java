package br.com.sentinela.infra.cache;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SingleValueCacheTest {

    private static final Instant START = Instant.parse("2026-10-22T12:00:00Z");

    @Test
    void recarregaSoDepoisDeVencer() throws InterruptedException {
        MutableClock clock = new MutableClock(START);
        AtomicInteger loads = new AtomicInteger();
        SingleValueCache<Integer> cache = new SingleValueCache<>(Duration.ofMinutes(10), loads::incrementAndGet, clock);

        assertThat(cache.get()).isEqualTo(1);
        clock.now = START.plus(Duration.ofMinutes(9));
        assertThat(cache.get()).isEqualTo(1);
        clock.now = START.plus(Duration.ofMinutes(10));
        assertThat(cache.get()).isEqualTo(1);
        awaitValue(cache, 2);
    }

    @Test
    void falhaNaRecargaMantemOValorAntigoETentaDeNovoEmUmMinuto() throws InterruptedException {
        MutableClock clock = new MutableClock(START);
        AtomicInteger calls = new AtomicInteger();
        SingleValueCache<String> cache = new SingleValueCache<>(Duration.ofMinutes(10), () -> {
            int call = calls.incrementAndGet();
            if (call == 2) {
                throw new IllegalStateException("SNIRH fora do ar");
            }
            return "carga " + call;
        }, clock);

        assertThat(cache.get()).isEqualTo("carga 1");
        clock.now = START.plus(Duration.ofMinutes(11));
        assertThat(cache.get()).isEqualTo("carga 1");
        awaitCalls(calls, 2);
        clock.now = START.plus(Duration.ofMinutes(11).plusSeconds(30));
        assertThat(cache.get()).isEqualTo("carga 1");
        clock.now = START.plus(Duration.ofMinutes(12));
        assertThat(cache.get()).isEqualTo("carga 1");
        awaitValue(cache, "carga 3");
    }

    @Test
    void falhaNaPrimeiraCargaPropagaOErro() {
        SingleValueCache<String> cache = new SingleValueCache<>(Duration.ofMinutes(10), () -> {
            throw new IllegalStateException("SNIRH fora do ar");
        });

        assertThatThrownBy(cache::get).isInstanceOf(IllegalStateException.class);
    }

    private static <T> void awaitValue(SingleValueCache<T> cache, T expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (expected.equals(cache.get())) return;
            Thread.sleep(5);
        }
        assertThat(cache.get()).isEqualTo(expected);
    }

    private static void awaitCalls(AtomicInteger calls, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (calls.get() < expected && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(calls.get()).isEqualTo(expected);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
