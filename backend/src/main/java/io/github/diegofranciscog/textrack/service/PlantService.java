package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.domain.Attendance;
import io.github.diegofranciscog.textrack.domain.Machine;
import io.github.diegofranciscog.textrack.dto.PlantDtos.AttendanceResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.CheckInRequest;
import io.github.diegofranciscog.textrack.dto.PlantDtos.CreateOperatorRequest;
import io.github.diegofranciscog.textrack.dto.PlantDtos.CreateStopRequest;
import io.github.diegofranciscog.textrack.dto.PlantDtos.LineResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.MachineResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.OperatorResponse;
import io.github.diegofranciscog.textrack.dto.PlantDtos.StopResponse;
import io.github.diegofranciscog.textrack.exception.BusinessRuleException;
import io.github.diegofranciscog.textrack.exception.ConflictException;
import io.github.diegofranciscog.textrack.exception.NotFoundException;
import io.github.diegofranciscog.textrack.mapper.PlantMapper;
import io.github.diegofranciscog.textrack.repository.PlantRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operarios, líneas, máquinas, asistencia (minutos reloj) y paros (PDOT/ADOT). */
@Service
public class PlantService {

    private final PlantRepository repository;
    private final PlantTime time;

    public PlantService(PlantRepository repository, PlantTime time) {
        this.repository = repository;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public List<LineResponse> lines() {
        return repository.findLines().stream().map(PlantMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OperatorResponse> operators(boolean onlyActive) {
        return repository.findOperators(onlyActive).stream().map(PlantMapper::toResponse).toList();
    }

    @Transactional
    public OperatorResponse createOperator(CreateOperatorRequest request) {
        if (repository.operatorCodeExists(request.code())) {
            throw new ConflictException("Ya existe un operario con el código " + request.code());
        }
        if (request.lineId() != null && repository.findLine(request.lineId()).isEmpty()) {
            throw new NotFoundException("Línea no encontrada");
        }
        long id = repository.insertOperator(request.code(), request.fullName().trim(), request.lineId());
        return PlantMapper.toResponse(repository.findOperator(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<MachineResponse> machines() {
        return repository.findMachines().stream().map(PlantMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> attendances(LocalDate date) {
        return repository.findAttendances(date).stream().map(PlantMapper::toResponse).toList();
    }

    @Transactional
    public AttendanceResponse checkIn(CheckInRequest request) {
        repository.findOperator(request.operatorId()).orElseThrow(() -> new NotFoundException("Operario no encontrado"));
        OffsetDateTime checkIn = request.checkIn() != null ? request.checkIn() : time.now();
        if (checkIn.isAfter(time.now().plusMinutes(5))) {
            throw new BusinessRuleException("La hora de entrada no puede estar en el futuro");
        }
        LocalDate workDate = time.dateOf(checkIn.toInstant());
        if (repository.findAttendance(request.operatorId(), workDate).isPresent()) {
            throw new ConflictException("El operario ya registró entrada en " + workDate);
        }
        int breakMinutes = request.breakMinutes() != null ? request.breakMinutes() : 30;
        long id = repository.insertAttendance(request.operatorId(), workDate, checkIn, null, breakMinutes);
        return PlantMapper.toResponse(repository.findAttendance(id).orElseThrow());
    }

    @Transactional
    public AttendanceResponse checkOut(long attendanceId, OffsetDateTime requested) {
        Attendance attendance = repository.findAttendance(attendanceId)
                .orElseThrow(() -> new NotFoundException("Asistencia no encontrada"));
        OffsetDateTime checkOut = requested != null ? requested : time.now();
        if (attendance.checkOut() != null) {
            throw new ConflictException("La salida ya fue registrada");
        }
        if (repository.checkOut(attendanceId, checkOut) == 0) {
            throw new BusinessRuleException("La salida debe ser posterior a la entrada");
        }
        return PlantMapper.toResponse(repository.findAttendance(attendanceId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<StopResponse> stops(LocalDate date) {
        return repository.findStopsOverlapping(time.startOf(date), time.endOf(date)).stream()
                .map(PlantMapper::toResponse).toList();
    }

    @Transactional
    public StopResponse reportStop(CreateStopRequest request, Long reportedBy) {
        Machine machine = repository.findMachine(request.machineId())
                .orElseThrow(() -> new NotFoundException("Máquina no encontrada"));
        OffsetDateTime start = request.startedAt() != null ? request.startedAt() : time.now();
        if (request.endedAt() != null && !request.endedAt().isAfter(start)) {
            throw new BusinessRuleException("El fin del paro debe ser posterior al inicio");
        }
        if (request.endedAt() == null && repository.hasOpenStop(machine.id())) {
            throw new ConflictException("La máquina " + machine.code() + " ya tiene un paro abierto");
        }
        long id = repository.insertStop(machine.id(), request.reason(), request.planned(), start, request.endedAt(),
                request.notes(), reportedBy);
        return PlantMapper.toResponse(repository.findStop(id).orElseThrow());
    }

    @Transactional
    public StopResponse closeStop(long stopId, OffsetDateTime requested) {
        var stop = repository.findStop(stopId).orElseThrow(() -> new NotFoundException("Paro no encontrado"));
        if (stop.endedAt() != null) {
            throw new ConflictException("El paro ya está cerrado");
        }
        OffsetDateTime end = requested != null ? requested : time.now();
        if (repository.closeStop(stopId, end) == 0) {
            throw new BusinessRuleException("El fin del paro debe ser posterior al inicio");
        }
        return PlantMapper.toResponse(repository.findStop(stopId).orElseThrow());
    }
}
