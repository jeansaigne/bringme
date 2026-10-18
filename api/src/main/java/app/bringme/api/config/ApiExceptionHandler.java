package app.bringme.api.config;

import app.bringme.api.course.IllegalTransitionException;
import app.bringme.api.course.StaleCourseException;
import app.bringme.api.geo.AddressNotFoundException;
import app.bringme.api.payment.PaymentProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handle(ApiException e) {
        return problem(e.status(), e.code(), e.getMessage());
    }

    @ExceptionHandler(IllegalTransitionException.class)
    ProblemDetail handle(IllegalTransitionException e) {
        return problem(HttpStatus.CONFLICT, "illegal_transition", e.getMessage());
    }

    @ExceptionHandler(StaleCourseException.class)
    ProblemDetail handle(StaleCourseException e) {
        return problem(HttpStatus.CONFLICT, "concurrent_update", "La course vient d'être modifiée, réessayez.");
    }

    @ExceptionHandler(AddressNotFoundException.class)
    ProblemDetail handle(AddressNotFoundException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "address_not_found", e.getMessage());
    }

    @ExceptionHandler(PaymentProviderException.class)
    ProblemDetail handle(PaymentProviderException e) {
        LOG.warn("Erreur du prestataire de paiement : {}", e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "payment_provider_error", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setProperty("code", code);
        return pd;
    }
}
