package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.domain.Attendance;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator;
import io.github.diegofranciscog.textrack.service.calc.PieceRateCalculator.Shift;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

/** Conversión de la asistencia registrada a jornada efectiva para los cálculos. */
final class WorkTime {

    private static final Duration MAX_OPEN_SHIFT = Duration.ofHours(16);

    private WorkTime() {
    }

    /**
     * Fin efectivo de la jornada: la salida registrada; si sigue abierta, el momento actual (sin pasar de 16 h);
     * si quedó abierta en un día anterior se asume jornada completa (8 h + descanso).
     */
    static OffsetDateTime effectiveEnd(Attendance attendance, OffsetDateTime now) {
        if (attendance.checkOut() != null) {
            return attendance.checkOut();
        }
        OffsetDateTime cap = attendance.checkIn().plus(MAX_OPEN_SHIFT);
        if (now.isAfter(cap)) {
            return attendance.checkIn()
                    .plusHours(PieceRateCalculator.ORDINARY_HOURS_PER_DAY)
                    .plusMinutes(attendance.breakMinutes());
        }
        return now;
    }

    static Shift shift(Attendance attendance, OffsetDateTime now) {
        return new Shift(attendance.checkIn().toInstant(), effectiveEnd(attendance, now).toInstant(),
                attendance.breakMinutes());
    }

    static BigDecimal attendedMinutes(Attendance attendance, OffsetDateTime now) {
        long gross = Math.max(0, Duration.between(attendance.checkIn(), effectiveEnd(attendance, now)).toMinutes());
        long net = gross > attendance.breakMinutes() ? gross - attendance.breakMinutes() : gross;
        return BigDecimal.valueOf(net);
    }
}
