package com.example.controller;

import com.example.common.RestAPIResponse;
import com.example.entity.ManualInvoice;
import com.example.serviceImpl.ManualInvoiceServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/manual-invoice")  // Base path for invoice APIs
public class ManualInvoiceController {

    private static final Logger logger = LoggerFactory.getLogger(ManualInvoiceController.class);

    @Autowired
    private ManualInvoiceServiceImpl manualInvoiceServiceImpl;

    @PostMapping("/save")
    public ResponseEntity<RestAPIResponse> createInvoice(@RequestBody ManualInvoice manualInvoice) {

        if (manualInvoice == null || manualInvoice.getInvoiceNumber() == null) {
            return ResponseEntity.badRequest()
                    .body(new RestAPIResponse("Error", "Request body is missing or invoiceNumber is required"));
        }

        try {
            ManualInvoice savedInvoice = manualInvoiceServiceImpl.createInvoice(manualInvoice);
            return ResponseEntity.ok(
                    new RestAPIResponse("Success", "Manual Invoice saved successfully", savedInvoice)
            );
        } catch (Exception e) {
            logger.error("Error saving invoice: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RestAPIResponse("Error", "Invoice not saved: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestAPIResponse> getInvoiceById(@PathVariable Long id) {
        try {
            ManualInvoice invoice = manualInvoiceServiceImpl.getInvoiceById(id);
            return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice found", invoice));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new RestAPIResponse("Error", e.getMessage()));
        }
    }

    @GetMapping("/getall")
    public ResponseEntity<RestAPIResponse> getAllInvoices() {
        return ResponseEntity.ok(
                new RestAPIResponse("Success", "Invoices fetched successfully", manualInvoiceServiceImpl.getAllInvoices())
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<RestAPIResponse> deleteInvoice(@PathVariable Long id) {
        try {
            manualInvoiceServiceImpl.deleteInvoice(id);
            return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new RestAPIResponse("Error", e.getMessage()));
        }
    }
}
