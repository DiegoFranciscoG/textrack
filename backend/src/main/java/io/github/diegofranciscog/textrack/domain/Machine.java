package io.github.diegofranciscog.textrack.domain;

public record Machine(long id, String code, MachineType machineType, long lineId, String lineCode, boolean active) {
}
