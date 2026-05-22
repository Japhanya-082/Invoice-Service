package com.invoice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.invoice.common.RestAPIResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(feign.FeignException.Unauthorized.class)
    public ResponseEntity<RestAPIResponse> handleUnauthorized() {
        log.warn("Feign Unauthorized exception caught");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new RestAPIResponse("fail", "Token expired. Please login again.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestAPIResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RestAPIResponse("fail", ex.getMessage(), null));
    }
}
