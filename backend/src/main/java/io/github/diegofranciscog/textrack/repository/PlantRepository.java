package io.github.diegofranciscog.textrack.repository;

import io.github.diegofranciscog.textrack.domain.Attendance;
import io.github.diegofranciscog.textrack.domain.Machine;
import io.github.diegofranciscog.textrack.domain.MachineStop;
import io.github.diegofranciscog.textrack.domain.Operator;
import io.github.diegofranciscog.textrack.domain.ProductionLine;
import io.github.diegofranciscog.textrack.domain.StopReason;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Líneas, operarios, máquinas, asistencia y paros de máquina. */
@Repository
public class PlantRepository {

    private static final String OPERATOR_SELECT = """
            SELECT o.id, o.code, o.full_name, o.line_id, l.code AS line_code, o.active
              FROM operators o LEFT JOIN production_lines l ON l.id = o.line_id
            """;
    private static final String ATTENDANCE_SELECT = """
            SELECT a.id, a.operator_id, o.code AS operator_code, a.work_date, a.check_in, a.check_out, a.break_minutes
              FROM attendances a JOIN operators o ON o.id = a.operator_id
            """;
    private static final String STOP_SELECT = """
            SELECT s.id, s.machine_id, m.code AS machine_code, m.line_id, s.reason, s.planned, s.started_at,
                   s.ended_at, s.notes
              FROM machine_stops s JOIN machines m ON m.id = s.machine_id
            """;

    private final JdbcClient jdbc;

    public PlantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- líneas y operarios

    public List<ProductionLine> findLines() {
        return jdbc.sql("SELECT id, code, name FROM production_lines ORDER BY code").query(ProductionLine.class).list();
    }

    public Optional<ProductionLine> findLine(long id) {
        return jdbc.sql("SELECT id, code, name FROM production_lines WHERE id = :id").param("id", id)
                .query(ProductionLine.class).optional();
    }

    public long insertLine(String code, String name) {
        return jdbc.sql("INSERT INTO production_lines (code, name) VALUES (:code, :name) RETURNING id")
                .param("code", code).param("name", name).query(Long.class).single();
    }

    public List<Operator> findOperators(boolean onlyActive) {
        return jdbc.sql(OPERATOR_SELECT + (onlyActive ? " WHERE o.active" : "") + " ORDER BY o.code")
                .query(Operator.class).list();
    }

    public Optional<Operator> findOperatorByCode(String code) {
        return jdbc.sql(OPERATOR_SELECT + " WHERE o.code = :code").param("code", code).query(Operator.class).optional();
    }

    public Optional<Operator> findOperator(long id) {
        return jdbc.sql(OPERATOR_SELECT + " WHERE o.id = :id").param("id", id).query(Operator.class).optional();
    }

    public boolean operatorCodeExists(String code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM operators WHERE code = :code)").param("code", code)
                .query(Boolean.class).single();
    }

    public long insertOperator(String code, String fullName, Long lineId) {
        return jdbc.sql("INSERT INTO operators (code, full_name, line_id) VALUES (:code, :name, :line) RETURNING id")
                .param("code", code).param("name", fullName).param("line", lineId)
                .query(Long.class).single();
    }

    // ---------------------------------------------------------------- máquinas

    public List<Machine> findMachines() {
        return jdbc.sql("""
                        SELECT m.id, m.code, m.machine_type, m.line_id, l.code AS line_code, m.active
                          FROM machines m JOIN production_lines l ON l.id = m.line_id ORDER BY m.code""")
                .query(Machine.class).list();
    }

    public Optional<Machine> findMachine(long id) {
        return jdbc.sql("""
                        SELECT m.id, m.code, m.machine_type, m.line_id, l.code AS line_code, m.active
                          FROM machines m JOIN production_lines l ON l.id = m.line_id WHERE m.id = :id""")
                .param("id", id).query(Machine.class).optional();
    }

    public long insertMachine(String code, String machineType, long lineId) {
        return jdbc.sql("INSERT INTO machines (code, machine_type, line_id) VALUES (:code, :type, :line) RETURNING id")
                .param("code", code).param("type", machineType).param("line", lineId)
                .query(Long.class).single();
    }

    // ---------------------------------------------------------------- asistencia

    public List<Attendance> findAttendances(LocalDate workDate) {
        return jdbc.sql(ATTENDANCE_SELECT + " WHERE a.work_date = :date ORDER BY o.code")
                .param("date", workDate).query(Attendance.class).list();
    }

    public Optional<Attendance> findAttendance(long id) {
        return jdbc.sql(ATTENDANCE_SELECT + " WHERE a.id = :id").param("id", id).query(Attendance.class).optional();
    }

    public Optional<Attendance> findAttendance(long operatorId, LocalDate workDate) {
        return jdbc.sql(ATTENDANCE_SELECT + " WHERE a.operator_id = :op AND a.work_date = :date")
                .param("op", operatorId).param("date", workDate).query(Attendance.class).optional();
    }

    /**
     * Jornada que contiene el instante (para turnos que cruzan la medianoche la lectura pertenece a la fecha de
     * entrada). Una jornada abierta se considera vigente hasta 16 horas después de la entrada.
     */
    public Optional<Attendance> findAttendanceCovering(long operatorId, OffsetDateTime instant) {
        return jdbc.sql(ATTENDANCE_SELECT + """
                         WHERE a.operator_id = :op AND a.check_in <= :at
                           AND COALESCE(a.check_out, a.check_in + INTERVAL '16 hours') >= :at
                         ORDER BY a.check_in DESC LIMIT 1""")
                .param("op", operatorId).param("at", instant).query(Attendance.class).optional();
    }

    public long insertAttendance(long operatorId, LocalDate workDate, OffsetDateTime checkIn, OffsetDateTime checkOut,
                                 int breakMinutes) {
        return jdbc.sql("""
                        INSERT INTO attendances (operator_id, work_date, check_in, check_out, break_minutes)
                        VALUES (:op, :date, :in, :out, :break) RETURNING id""")
                .param("op", operatorId).param("date", workDate).param("in", checkIn).param("out", checkOut)
                .param("break", breakMinutes).query(Long.class).single();
    }

    public int checkOut(long attendanceId, OffsetDateTime checkOut) {
        return jdbc.sql("UPDATE attendances SET check_out = :out WHERE id = :id AND check_out IS NULL AND check_in < :out")
                .param("out", checkOut).param("id", attendanceId).update();
    }

    // ---------------------------------------------------------------- paros

    public List<MachineStop> findStopsOverlapping(OffsetDateTime from, OffsetDateTime to) {
        return jdbc.sql(STOP_SELECT + """
                         WHERE s.started_at < :to AND COALESCE(s.ended_at, :to) > :from
                         ORDER BY s.started_at DESC""")
                .param("from", from).param("to", to).query(MachineStop.class).list();
    }

    public Optional<MachineStop> findStop(long id) {
        return jdbc.sql(STOP_SELECT + " WHERE s.id = :id").param("id", id).query(MachineStop.class).optional();
    }

    public boolean hasOpenStop(long machineId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM machine_stops WHERE machine_id = :m AND ended_at IS NULL)")
                .param("m", machineId).query(Boolean.class).single();
    }

    public long insertStop(long machineId, StopReason reason, boolean planned, OffsetDateTime startedAt,
                           OffsetDateTime endedAt, String notes, Long reportedBy) {
        return jdbc.sql("""
                        INSERT INTO machine_stops (machine_id, reason, planned, started_at, ended_at, notes, reported_by)
                        VALUES (:m, :reason, :planned, :start, :end, :notes, :by) RETURNING id""")
                .param("m", machineId).param("reason", reason.name()).param("planned", planned)
                .param("start", startedAt).param("end", endedAt).param("notes", notes).param("by", reportedBy)
                .query(Long.class).single();
    }

    public int closeStop(long stopId, OffsetDateTime endedAt) {
        return jdbc.sql("UPDATE machine_stops SET ended_at = :end WHERE id = :id AND ended_at IS NULL AND started_at < :end")
                .param("end", endedAt).param("id", stopId).update();
    }
}
