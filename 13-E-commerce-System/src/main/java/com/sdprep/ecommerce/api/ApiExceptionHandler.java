package com.sdprep.ecommerce.api;

import com.sdprep.ecommerce.core.InvalidRequestException;
import com.sdprep.ecommerce.core.NotFoundException;
import com.sdprep.ecommerce.core.OutOfStockException;
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

    @ExceptionHandler(OutOfStockException.class)
    ProblemDetail conflict(OutOfStockException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
