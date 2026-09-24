package io.github.diegofranciscog.textrack.domain;

import java.time.OffsetDateTime;

public record AppUser(long id, String email, String fullName, String passwordHash, Role role, boolean active,
                      OffsetDateTime createdAt) {
}
