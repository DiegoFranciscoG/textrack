package io.github.diegofranciscog.textrack.dto;

import io.github.diegofranciscog.textrack.domain.MachineType;
import io.github.diegofranciscog.textrack.domain.StopReason;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class PlantDtos {

    private PlantDtos() {
    }

    public record LineResponse(long id, String code, String name) {
    }

    public record OperatorResponse(long id, String code, String fullName, String lineCode, boolean active) {
    }

    public record CreateOperatorRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9-]{1,19}") String code,
            @NotBlank @Size(max = 120) String fullName,
            Long lineId) {
    }

    public record MachineResponse(long id, String code, MachineType machineType, String lineCode, boolean active) {
    }

    public record CheckInRequest(
            @NotNull Long operatorId,
            OffsetDateTime checkIn,
            @Min(0) @Max(120) Integer breakMinutes) {
    }

    public record CheckOutRequest(OffsetDateTime checkOut) {
    }

    public record AttendanceResponse(long id, long operatorId, String operatorCode, LocalDate workDate,
                                     OffsetDateTime checkIn, OffsetDateTime checkOut, int breakMinutes) {
    }

    public record CreateStopRequest(
            @NotNull Long machineId,
            @NotNull StopReason reason,
            boolean planned,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            @Size(max = 300) String notes) {
    }

    public record CloseStopRequest(OffsetDateTime endedAt) {
    }

    public record StopResponse(long id, long machineId, String machineCode, StopReason reason, boolean planned,
                               OffsetDateTime startedAt, OffsetDateTime endedAt, String notes) {
    }
}
