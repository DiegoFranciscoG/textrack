package io.github.diegofranciscog.textrack.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.diegofranciscog.textrack.config.AppProperties;
import io.github.diegofranciscog.textrack.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Limita intentos de login por (IP + correo) y por IP (OWASP API4 / API2). En memoria: suficiente para una
 * instancia; con varias réplicas se movería a Redis con el mismo contrato.
 */
@Component
public class LoginRateLimiter {

    private static final int IP_MULTIPLIER = 4;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();
    private final int attemptsPerMinute;

    public LoginRateLimiter(AppProperties properties) {
        this.attemptsPerMinute = properties.security().loginAttemptsPerMinute();
    }

    public void check(String clientIp, String email) {
        consume("ip:" + clientIp, attemptsPerMinute * IP_MULTIPLIER);
        consume("user:" + clientIp + ":" + email.toLowerCase(Locale.ROOT), attemptsPerMinute);
    }

    private void consume(String key, int capacity) {
        Bucket bucket = buckets.get(key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, Duration.ofMinutes(1)).build())
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
            throw new TooManyRequestsException(seconds);
        }
    }
}
