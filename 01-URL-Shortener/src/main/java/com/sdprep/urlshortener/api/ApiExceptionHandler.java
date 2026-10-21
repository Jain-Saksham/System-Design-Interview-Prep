package com.sdprep.urlshortener.api;

import com.sdprep.urlshortener.core.AliasTakenException;
import com.sdprep.urlshortener.core.InvalidRequestException;
import com.sdprep.urlshortener.core.LinkExpiredException;
import com.sdprep.urlshortener.core.LinkNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain exceptions to HTTP status codes (RFC 7807 problem responses). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail badRequest(InvalidRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AliasTakenException.class)
    ProblemDetail conflict(AliasTakenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(LinkNotFoundException.class)
    ProblemDetail notFound(LinkNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** 410 Gone tells clients the link existed but will never work again. */
    @ExceptionHandler(LinkExpiredException.class)
    ProblemDetail gone(LinkExpiredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, e.getMessage());
    }
}
