package com.example.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.entity.Invoice;
import com.example.serviceImpl.InvoiceServiceImpl;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/invoice")
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
            response.put("data", invoices); // return saved invoices
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace(); // log the actual exception
            response.put("success", false);
            response.put("error", "Error processing file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/getData")
    public ResponseEntity<Map<String, Object>> getAllInvoices() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Invoice> invoices = invoiceServiceImpl.getAll();
            response.put("success", true);
            response.put("data", invoices);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "Failed to fetch invoices: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
