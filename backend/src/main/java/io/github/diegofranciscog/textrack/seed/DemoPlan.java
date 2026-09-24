package io.github.diegofranciscog.textrack.seed;

import io.github.diegofranciscog.textrack.domain.MachineType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Planta de demostración con datos 100 % ficticios: dos módulos de costura, sus operarios (con una eficiencia
 * habitual para la simulación) y las rutas de dos prendas con SAM realistas.
 */
final class DemoPlan {

    /**
     * Tarifa por minuto estándar: al 100 % de eficiencia 480 min × 0,05 = 24,00 USD; bajo ~67 % de eficiencia el
     * pago ordinario cae bajo el piso SBU (16,07 USD) y aparece la alerta.
     */
    static final BigDecimal RATE_PER_STANDARD_MINUTE = new BigDecimal("0.0500");

    static final StylePlan POLO = new StylePlan("POLO-PIQUE-01", "Polo piqué manga corta", "POLO", List.of(
            new OperationPlan(10, "OP10", "Unir hombros", MachineType.OVERLOCK, "0.3500"),
            new OperationPlan(20, "OP20", "Pegar tapeta", MachineType.LOCKSTITCH, "1.2500"),
            new OperationPlan(30, "OP30", "Pegar cuello", MachineType.OVERLOCK, "0.9000"),
            new OperationPlan(40, "OP40", "Pegar mangas", MachineType.OVERLOCK, "0.8500"),
            new OperationPlan(50, "OP50", "Cerrar costados", MachineType.OVERLOCK, "0.9500"),
            new OperationPlan(60, "OP60", "Dobladillo de mangas", MachineType.COVERSTITCH, "0.6000"),
            new OperationPlan(70, "OP70", "Dobladillo de bajo", MachineType.COVERSTITCH, "0.5500"),
            new OperationPlan(80, "OP80", "Ojales", MachineType.BUTTONHOLE, "0.4000"),
            new OperationPlan(90, "OP90", "Pegar botones", MachineType.BUTTON, "0.4500"),
            new OperationPlan(100, "OP100", "Pulir e inspeccionar", MachineType.INSPECTION, "0.7000"),
            new OperationPlan(110, "OP110", "Planchar y empacar", MachineType.PACKING, "0.6000")));

    static final StylePlan TSHIRT = new StylePlan("CAM-BASICA-01", "Camiseta básica cuello redondo", "TSHIRT", List.of(
            new OperationPlan(10, "OP10", "Unir hombros", MachineType.OVERLOCK, "0.3000"),
            new OperationPlan(20, "OP20", "Pegar rib de cuello", MachineType.OVERLOCK, "0.5500"),
            new OperationPlan(30, "OP30", "Pespunte de cuello", MachineType.COVERSTITCH, "0.4500"),
            new OperationPlan(40, "OP40", "Pegar mangas", MachineType.OVERLOCK, "0.8000"),
            new OperationPlan(50, "OP50", "Cerrar costados", MachineType.OVERLOCK, "0.9000"),
            new OperationPlan(60, "OP60", "Dobladillo de mangas", MachineType.COVERSTITCH, "0.5000"),
            new OperationPlan(70, "OP70", "Dobladillo de bajo", MachineType.COVERSTITCH, "0.4500"),
            new OperationPlan(80, "OP80", "Pegar etiqueta", MachineType.LOCKSTITCH, "0.2500"),
            new OperationPlan(90, "OP90", "Pulir e inspeccionar", MachineType.INSPECTION, "0.5000"),
            new OperationPlan(100, "OP100", "Doblar y empacar", MachineType.PACKING, "0.4000")));

    /** Balanceo: operaciones pesadas compartidas y ligeras agrupadas; el cuello de botella queda en OP30. */
    static final LinePlan POLO_LINE = new LinePlan("L1", "Módulo 1 · Polos", POLO, "Azul marino", List.of(
            new WorkerPlan("OPR-101", "María Guamán", 1.05, Set.of("OP10", "OP60"), MachineType.OVERLOCK),
            new WorkerPlan("OPR-102", "Rosa Pilataxi", 0.92, Set.of("OP20"), MachineType.LOCKSTITCH),
            new WorkerPlan("OPR-103", "Luis Chicaiza", 0.78, Set.of("OP30"), MachineType.OVERLOCK),
            new WorkerPlan("OPR-104", "Carmen Toapanta", 1.10, Set.of("OP40"), MachineType.OVERLOCK),
            new WorkerPlan("OPR-105", "Jorge Quishpe", 0.88, Set.of("OP50"), MachineType.OVERLOCK),
            new WorkerPlan("OPR-106", "Ana Lucía Caiza", 0.70, Set.of("OP70"), MachineType.COVERSTITCH),
            new WorkerPlan("OPR-107", "Diana Morocho", 0.95, Set.of("OP20", "OP80"), MachineType.LOCKSTITCH),
            new WorkerPlan("OPR-108", "Patricia Tipán", 0.83, Set.of("OP100"), MachineType.INSPECTION),
            new WorkerPlan("OPR-109", "Wilson Yánez", 1.00, Set.of("OP90", "OP110"), MachineType.PACKING)));

    static final LinePlan TSHIRT_LINE = new LinePlan("L2", "Módulo 2 · Camisetas", TSHIRT, "Blanco", List.of(
            new WorkerPlan("OPR-201", "Gabriela Andrango", 0.97, Set.of("OP10", "OP20"), MachineType.OVERLOCK),
            new WorkerPlan("OPR-202", "Silvia Collaguazo", 0.85, Set.of("OP30", "OP80"), MachineType.COVERSTITCH),
            new WorkerPlan("OPR-203", "Marco Simbaña", 1.08, Set.of("OP40"), MachineType.OVERLOCK),
            new WorkerPlan("OPR-204", "Verónica Chiluisa", 0.74, Set.of("OP50"), MachineType.OVERLOCK),
            new WorkerPlan("OPR-205", "Fernanda Lema", 0.90, Set.of("OP60", "OP70"), MachineType.COVERSTITCH),
            new WorkerPlan("OPR-206", "Johanna Yupangui", 0.81, Set.of("OP90"), MachineType.INSPECTION),
            new WorkerPlan("OPR-207", "Pedro Cuzco", 1.02, Set.of("OP50", "OP100"), MachineType.PACKING)));

    static final List<LinePlan> LINES = List.of(POLO_LINE, TSHIRT_LINE);

    private DemoPlan() {
    }

    record OperationPlan(int sequence, String code, String name, MachineType machineType, String sam) {

        BigDecimal samMinutes() {
            return new BigDecimal(sam);
        }
    }

    record StylePlan(String code, String name, String garmentType, List<OperationPlan> operations) {
    }

    record WorkerPlan(String code, String name, double efficiency, Set<String> operations, MachineType machineType) {

        String machineCode() {
            return "M-" + code.substring(4);
        }
    }

    record LinePlan(String code, String name, StylePlan style, String color, List<WorkerPlan> workers) {
    }
}
