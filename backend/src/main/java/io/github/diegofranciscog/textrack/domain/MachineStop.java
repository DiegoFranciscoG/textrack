package io.github.diegofranciscog.textrack.domain;

import java.time.OffsetDateTime;

public record MachineStop(long id, long machineId, String machineCode, long lineId, StopReason reason,
                          boolean planned, OffsetDateTime startedAt, OffsetDateTime endedAt, String notes) {
}
