package com.invoice.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.invoice.scheduler.InvoiceAlertScheduler;
import com.invoice.serviceImpl.InvoiceEmailServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/manual-invoice")
@RequiredArgsConstructor
@Slf4j
public class InvoiceEmailController {

	private final InvoiceEmailServiceImpl invoiceEmailService;
	
	private final InvoiceAlertScheduler invoiceAlertScheduler;

	@PostMapping("/send-overdue-email/{invoiceNumber}")
	public ResponseEntity<Map<String, Object>> sendOverdueEmail(@PathVariable String invoiceNumber,
			@RequestHeader("Authorization") String authHeader) {
		try {
			invoiceEmailService.sendOverdueInvoiceEmail(authHeader, invoiceNumber);
			return ResponseEntity.ok(Map.of("status", "success", "message", "Follow-up email sent successfully"));
		} catch (Exception e) {
			log.error("Error sending overdue email", e);
			return ResponseEntity.status(500)
					.body(Map.of("status", "error", "message", "Error sending overdue email: " + e.getMessage()));
		}
	}

	
	/**
	 * Manual trigger for testing — runs the full daily scheduler immediately.
	 * Restricted: this fans out email to every matching customer and vendor, so an
	 * unauthenticated caller could mail-bomb recipients or exhaust the SMTP quota.
	 */
	@PostMapping("/run-daily-alerts")
	@PreAuthorize("hasAnyRole('INTERNAL','ADMIN')")
	public ResponseEntity<Map<String, Object>> runDailyAlerts() {
		try {
			invoiceAlertScheduler.runDailyAlerts();
			return ResponseEntity.ok(Map.of("status", "success", "message", "Daily alerts ran successfully"));
		} catch (Exception e) {
			log.error("Error running daily alerts", e);
			return ResponseEntity.status(500)
					.body(Map.of("status", "error", "message", e.getMessage()));
		}
	}
}