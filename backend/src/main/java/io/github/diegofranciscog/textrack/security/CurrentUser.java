package io.github.diegofranciscog.textrack.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Utilidad para obtener el id del usuario autenticado desde el JWT (claim {@code sub}). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
