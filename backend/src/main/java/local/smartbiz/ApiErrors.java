package local.smartbiz;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);
    public static class Failure extends RuntimeException {
        final int status;
        public Failure(int status, String message) { super(message); this.status = status; }
    }
    @ExceptionHandler(Failure.class)
    ResponseEntity<?> failure(Failure ex) { return response(ex.status, ex.getMessage()); }
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<?> duplicate() { return response(409, "El codigo, nombre o correo ya existe"); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, IllegalArgumentException.class, ArithmeticException.class})
    ResponseEntity<?> invalid() { return response(400, "Datos invalidos o fuera de los limites permitidos"); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> missing() { return response(404, "Recurso no encontrado"); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method() { return response(405, "Metodo no permitido"); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException ex) { return response(ex.getStatusCode().value(), "Solicitud rechazada"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ex) {
        LOG.error("Error procesando solicitud", ex);
        return response(500, "No fue posible completar la operacion");
    }
    static ResponseEntity<?> response(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
