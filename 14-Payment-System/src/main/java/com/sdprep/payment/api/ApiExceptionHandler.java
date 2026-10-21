package com.sdprep.payment.api;

import com.sdprep.payment.core.IdempotencyConflictException;
import com.sdprep.payment.core.InvalidRequestException;
import com.sdprep.payment.core.InvalidStateException;
import com.sdprep.payment.core.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain exceptions to HTTP status codes (RFC 7807 problem responses). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({InvalidRequestException.class, MissingRequestHeaderException.class})
    ProblemDetail badRequest(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidStateException.class)
    ProblemDetail conflict(InvalidStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail unprocessable(IdempotencyConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }
}
