package io.github.diegofranciscog.textrack.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {

    private final JdbcClient jdbc;

    public RefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long userId, String tokenHash, OffsetDateTime expiresAt) {
        jdbc.sql("INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (:user, :hash, :expires)")
                .param("user", userId)
                .param("hash", tokenHash)
                .param("expires", expiresAt)
                .update();
    }

    public Optional<StoredToken> findByHash(String tokenHash) {
        return jdbc.sql("SELECT id, user_id, expires_at, revoked_at FROM refresh_tokens WHERE token_hash = :hash")
                .param("hash", tokenHash)
                .query(StoredToken.class)
                .optional();
    }

    /** Revoca de forma atómica: devuelve false si otra petición ya lo había revocado (uso concurrente). */
    public boolean revoke(long id, OffsetDateTime when) {
        return jdbc.sql("UPDATE refresh_tokens SET revoked_at = :when WHERE id = :id AND revoked_at IS NULL")
                .param("when", when)
                .param("id", id)
                .update() == 1;
    }

    public void revokeAllForUser(long userId, OffsetDateTime when) {
        jdbc.sql("UPDATE refresh_tokens SET revoked_at = :when WHERE user_id = :user AND revoked_at IS NULL")
                .param("when", when)
                .param("user", userId)
                .update();
    }

    public int deleteExpired(OffsetDateTime before) {
        return jdbc.sql("DELETE FROM refresh_tokens WHERE expires_at < :before").param("before", before).update();
    }

    public record StoredToken(long id, long userId, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
    }
}
