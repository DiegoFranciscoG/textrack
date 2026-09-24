package io.github.diegofranciscog.textrack.exception;

/** Violación de una regla de negocio documentada (HTTP 422). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
