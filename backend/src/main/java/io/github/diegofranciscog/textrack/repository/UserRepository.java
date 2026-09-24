package io.github.diegofranciscog.textrack.repository;

import io.github.diegofranciscog.textrack.domain.AppUser;
import io.github.diegofranciscog.textrack.domain.Role;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByEmail(String email) {
        return jdbc.sql("SELECT * FROM app_users WHERE email = :email")
                .param("email", email.toLowerCase())
                .query(AppUser.class)
                .optional();
    }

    public Optional<AppUser> findById(long id) {
        return jdbc.sql("SELECT * FROM app_users WHERE id = :id").param("id", id).query(AppUser.class).optional();
    }

    public long insert(String email, String fullName, String passwordHash, Role role) {
        return jdbc.sql("""
                        INSERT INTO app_users (email, full_name, password_hash, role)
                        VALUES (:email, :fullName, :hash, :role) RETURNING id""")
                .param("email", email.toLowerCase())
                .param("fullName", fullName)
                .param("hash", passwordHash)
                .param("role", role.name())
                .query(Long.class)
                .single();
    }

    public boolean exists(String email) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM app_users WHERE email = :email)")
                .param("email", email.toLowerCase())
                .query(Boolean.class)
                .single();
    }
}
