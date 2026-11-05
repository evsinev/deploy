package io.pne.deploy.client.redmine.remote.queue;

/** Общая политика повторов для durable-очередей: не больше {@link #MAX_ATTEMPTS} попыток с экспоненциальным backoff. */
public final class Backoff {

    /** Максимум попыток отправки одной операции за прогон; после — dead-letter. */
    public static final int MAX_ATTEMPTS = 10;

    private static final long BASE_MS = 1000;
    private static final long MAX_MS  = 60_000;

    private Backoff() {
    }

    /** Пауза перед попыткой {@code aAttempt} (1-based): экспоненциальная, с потолком {@value #MAX_MS} мс. */
    public static long delayMs(int aAttempt) {
        int shift = Math.min(Math.max(aAttempt - 1, 0), 20);
        return Math.min(BASE_MS << shift, MAX_MS);
    }
}
