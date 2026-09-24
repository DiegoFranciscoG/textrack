package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.domain.Operation;
import io.github.diegofranciscog.textrack.domain.Style;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.CreateOperationRequest;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.CreateRateRequest;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.CreateStyleRequest;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.OperationResponse;
import io.github.diegofranciscog.textrack.dto.EngineeringDtos.StyleResponse;
import io.github.diegofranciscog.textrack.exception.BusinessRuleException;
import io.github.diegofranciscog.textrack.exception.ConflictException;
import io.github.diegofranciscog.textrack.exception.NotFoundException;
import io.github.diegofranciscog.textrack.mapper.EngineeringMapper;
import io.github.diegofranciscog.textrack.repository.EngineeringRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Estilos, rutas de operaciones con SAM y tarifas a destajo con vigencia. */
@Service
public class EngineeringService {

    private final EngineeringRepository repository;
    private final PlantTime time;

    public EngineeringService(EngineeringRepository repository, PlantTime time) {
        this.repository = repository;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public List<StyleResponse> styles() {
        LocalDate today = time.today();
        return repository.findStyles().stream()
                .map(style -> EngineeringMapper.toResponse(style, repository.findOperations(style.id(), today)))
                .toList();
    }

    @Transactional(readOnly = true)
    public StyleResponse style(long id) {
        Style style = repository.findStyle(id).orElseThrow(() -> new NotFoundException("Estilo no encontrado"));
        return EngineeringMapper.toResponse(style, repository.findOperations(id, time.today()));
    }

    @Transactional
    public StyleResponse createStyle(CreateStyleRequest request) {
        if (repository.styleCodeExists(request.code())) {
            throw new ConflictException("Ya existe un estilo con el código " + request.code());
        }
        long id = repository.insertStyle(request.code(), request.name().trim(), request.garmentType().trim());
        return style(id);
    }

    @Transactional
    public OperationResponse addOperation(long styleId, CreateOperationRequest request) {
        repository.findStyle(styleId).orElseThrow(() -> new NotFoundException("Estilo no encontrado"));
        if (repository.operationExists(styleId, request.sequence(), request.code())) {
            throw new ConflictException("La secuencia o el código de operación ya existe en el estilo");
        }
        long operationId = repository.insertOperation(styleId, request.sequence(), request.code(),
                request.name().trim(), request.machineType(), request.samMinutes());
        if (request.rateUsd() != null) {
            LocalDate validFrom = request.rateValidFrom() != null ? request.rateValidFrom() : time.today();
            repository.insertRate(operationId, request.rateUsd(), validFrom);
        }
        return EngineeringMapper.toResponse(operation(operationId));
    }

    /**
     * Nueva tarifa desde {@code validFrom}: cierra la vigente en esa fecha. No se permiten tarifas retroactivas
     * anteriores a otra ya programada, para no alterar pagos ya calculados.
     */
    @Transactional
    public OperationResponse changeRate(long operationId, CreateRateRequest request) {
        operation(operationId);
        if (repository.hasRateStartingOnOrAfter(operationId, request.validFrom())) {
            throw new BusinessRuleException("Ya existe una tarifa que inicia en esa fecha o después");
        }
        repository.closeOpenRate(operationId, request.validFrom());
        repository.insertRate(operationId, request.rateUsd(), request.validFrom());
        return EngineeringMapper.toResponse(operation(operationId));
    }

    private Operation operation(long operationId) {
        return repository.findOperation(operationId, time.today())
                .orElseThrow(() -> new NotFoundException("Operación no encontrada"));
    }
}
