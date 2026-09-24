package io.github.diegofranciscog.textrack.mapper;

import io.github.diegofranciscog.textrack.domain.Attendance;
import io.github.diegofranciscog.textrack.domain.Machine;
import io.github.diegofranciscog.textrack.domain.MachineStop;
import io.github.diegofranciscog.textrack.domain.Operator;
import io.github.diegofranciscog.textrack.domain.ProductionLine;
import io.github.diegofranciscog.textrack.dto.PlantDtos.AttendanceResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.LineResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.MachineResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.OperatorResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.StopResponse;

public final class PlantMapper {

    private PlantMapper() {
    }

    public static LineResponse toResponse(ProductionLine line) {
        return new LineResponse(line.id(), line.code(), line.name());
    }

    public static OperatorResponse toResponse(Operator operator) {
        return new OperatorResponse(operator.id(), operator.code(), operator.fullName(), operator.lineCode(),
                operator.active());
    }

    public static MachineResponse toResponse(Machine machine) {
        return new MachineResponse(machine.id(), machine.code(), machine.machineType(), machine.lineCode(),
                machine.active());
    }

    public static AttendanceResponse toResponse(Attendance attendance) {
        return new AttendanceResponse(attendance.id(), attendance.operatorId(), attendance.operatorCode(),
                attendance.workDate(), attendance.checkIn(), attendance.checkOut(), attendance.breakMinutes());
    }

    public static StopResponse toResponse(MachineStop stop) {
        return new StopResponse(stop.id(), stop.machineId(), stop.machineCode(), stop.reason(), stop.planned(),
                stop.startedAt(), stop.endedAt(), stop.notes());
    }
}
