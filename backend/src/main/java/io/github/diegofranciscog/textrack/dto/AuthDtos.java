package io.github.diegofranciscog.textrack.dto;

import io.github.diegofranciscog.textrack.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=***]";
        }
    }

    public record RefreshRequest(@NotBlank @Size(max = 100) String refreshToken) {

        @Override
        public String toString() {
            return "RefreshRequest[***]";
        }
    }

    public record UserSummary(long id, String email, String fullName, Role role) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
                                UserSummary user) {

        @Override
        public String toString() {
            return "TokenResponse[user=" + user + "]";
        }
    }
}
