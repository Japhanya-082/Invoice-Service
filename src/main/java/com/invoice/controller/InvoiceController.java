package com.invoice.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.invoice.common.RestAPIResponse;
import com.invoice.entity.Invoice;
import com.invoice.serviceImpl.InvoiceServiceImpl;
import com.invoice.tenant.SecurityUtils;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/invoice")
@Slf4j
public class InvoiceController {

	@Autowired
	private InvoiceServiceImpl invoiceServiceImpl;

	@PostMapping("/upload")
	public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
		Map<String, Object> response = new HashMap<>();

		if (file == null || file.isEmpty()) {
			response.put("success", false);
			response.put("error", "No file selected");
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}

		try {
			List<Invoice> invoices = invoiceServiceImpl.uploadAndSaveInvoices(file);
			response.put("success", true);
			response.put("message", "File uploaded successfully");
			response.put("data", invoices);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Error processing uploaded file: {}", e.getMessage(), e);
			response.put("success", false);
			response.put("error", "Error processing file: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}
	}

	
	@GetMapping("/getData")
	public ResponseEntity<Map<String, Object>> getAllInvoices() {
		Map<String, Object> response = new HashMap<>();
		try {
			// Require an authenticated tenant; Invoice entity has no adminId
			// column to filter on, but unauthenticated callers are rejected here.
			SecurityUtils.getCurrentAdminId();
			List<Invoice> invoices = invoiceServiceImpl.getAll();
			response.put("success", true);
			response.put("data", invoices);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Failed to fetch invoices: {}", e.getMessage(), e);
			response.put("success", false);
			response.put("error", "Failed to fetch invoices: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	
	@DeleteMapping("/{invoiceId}")
	public ResponseEntity<RestAPIResponse> deleteInvoice(@PathVariable Long invoiceId) {
		try {
			invoiceServiceImpl.deleteByInvoiceNumber(invoiceId);
			return ResponseEntity.ok(new RestAPIResponse("success", "Deleted Successfully"));
		} catch (Exception e) {
			log.error("Failed to delete invoice id={}: {}", invoiceId, e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("error", "Invoice could not be deleted"));
		}
	}
}

