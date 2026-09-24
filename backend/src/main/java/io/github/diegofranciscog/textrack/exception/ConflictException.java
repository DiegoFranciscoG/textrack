package io.github.diegofranciscog.textrack.exception;

/** El recurso ya existe o su estado impide la operación (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
