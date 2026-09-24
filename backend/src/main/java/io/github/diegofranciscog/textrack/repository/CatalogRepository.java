package io.github.diegofranciscog.textrack.repository;

import io.github.diegofranciscog.textrack.domain.Severity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> sizes() {
        return jdbc.sql("SELECT code FROM sizes ORDER BY sort_order").query(String.class).list();
    }

    public List<DefectType> defectTypes() {
        return jdbc.sql("SELECT code, name, category, default_severity FROM defect_types ORDER BY category, code")
                .query(DefectType.class).list();
    }

    public Optional<DefectType> defectType(String code) {
        return jdbc.sql("SELECT code, name, category, default_severity FROM defect_types WHERE code = :code")
                .param("code", code).query(DefectType.class).optional();
    }

    public Optional<PayrollParameter> payrollParameter(int year) {
        return jdbc.sql("SELECT year, sbu, legal_reference FROM payroll_parameters WHERE year = :year")
                .param("year", year).query(PayrollParameter.class).optional();
    }

    public record DefectType(String code, String name, String category, Severity defaultSeverity) {
    }

    public record PayrollParameter(int year, BigDecimal sbu, String legalReference) {
    }
}
