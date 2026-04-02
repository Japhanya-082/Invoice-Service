package com.example.exception;

import org.apache.hc.core5.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.common.RestAPIResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(feign.FeignException.Unauthorized.class)
    public ResponseEntity<RestAPIResponse> handleUnauthorized() {
        return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED)
                .body(new RestAPIResponse("fail", "Token expired. Please login again.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestAPIResponse> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(new RestAPIResponse("fail", ex.getMessage(), null));
    }
}
