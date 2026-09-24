package io.github.diegofranciscog.textrack.domain;

public record Operator(long id, String code, String fullName, Long lineId, String lineCode, boolean active) {
}
