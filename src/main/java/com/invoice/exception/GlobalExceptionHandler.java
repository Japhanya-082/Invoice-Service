package com.invoice.exception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.invoice.common.RestAPIResponse;
import com.invoice.tenant.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
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

	
	// ===== Added standardized handlers =====

	@ExceptionHandler(SecurityUtils.SecurityIntegrityException.class)
	public ResponseEntity<Map<String, Object>> handleSecurityIntegrity(
			SecurityUtils.SecurityIntegrityException ex, HttpServletRequest request) {
		log.warn("Security integrity violation at {}: {}", request.getRequestURI(), ex.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(stdBody(HttpStatus.FORBIDDEN, ex.getMessage(), request));
	}
	

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, Object>> handleAccessDenied(
			AccessDeniedException ex, HttpServletRequest request) {
		log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(stdBody(HttpStatus.FORBIDDEN, "Access denied: insufficient privileges", request));
	}
	

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleMethodArgNotValid(
			MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<Map<String, String>> fieldErrors = new ArrayList<>();
		ex.getBindingResult().getAllErrors().forEach(error -> {
			Map<String, String> fe = new LinkedHashMap<>();
			fe.put("field", error instanceof FieldError ? ((FieldError) error).getField() : error.getObjectName());
			fe.put("message", error.getDefaultMessage());
			fieldErrors.add(fe);
		});
		log.warn("Validation failed at {}: {}", request.getRequestURI(), fieldErrors);
		Map<String, Object> resp = stdBody(HttpStatus.BAD_REQUEST, "Validation failed", request);
		resp.put("errors", fieldErrors);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
	}
	

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Map<String, Object>> handleConstraintViolation(
			ConstraintViolationException ex, HttpServletRequest request) {
		log.warn("Constraint violation at {}: {}", request.getRequestURI(), ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(stdBody(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(
			IllegalArgumentException ex, HttpServletRequest request) {
		log.warn("Illegal argument at {}: {}", request.getRequestURI(), ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(stdBody(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
	}
	

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<Map<String, Object>> handleOptimisticLocking(
			OptimisticLockingFailureException ex, HttpServletRequest request) {
		log.warn("Optimistic locking failure at {}: {}", request.getRequestURI(), ex.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(stdBody(HttpStatus.CONFLICT, "Resource was modified by another transaction", request));
	}

	
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> handleDataIntegrity(
			DataIntegrityViolationException ex, HttpServletRequest request) {
		log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(stdBody(HttpStatus.CONFLICT, "Data integrity violation", request));
	}

	
	private Map<String, Object> stdBody(HttpStatus status, String message, HttpServletRequest request) {
		Map<String, Object> b = new LinkedHashMap<>();
		b.put("status", status.value());
		b.put("message", message);
		b.put("timestamp", Instant.now().toString());
		b.put("path", request.getRequestURI());
		return b;
	}
	
}
