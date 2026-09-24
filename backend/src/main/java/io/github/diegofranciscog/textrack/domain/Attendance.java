package io.github.diegofranciscog.textrack.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record Attendance(long id, long operatorId, String operatorCode, LocalDate workDate, OffsetDateTime checkIn,
                         OffsetDateTime checkOut, int breakMinutes) {
}
