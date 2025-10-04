package com.example.controller;

import com.example.common.RestAPIResponse;
import com.example.entity.ManualInvoice;
import com.example.serviceImpl.InvoiceServiceImpl;
import com.example.serviceImpl.ManualInvoiceServiceImpl1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/manual-invoice")
public class ManualInvoiceController1 {

    private final InvoiceServiceImpl invoiceServiceImpl;

    @Autowired
    private ManualInvoiceServiceImpl1 serviceImpl1;

    public ManualInvoiceController1(InvoiceServiceImpl invoiceServiceImpl) {
        this.invoiceServiceImpl = invoiceServiceImpl;
    }

    @PostMapping("/save")
    public ResponseEntity<RestAPIResponse> saveInvoice(@RequestBody ManualInvoice invoice) {
        try {
            ManualInvoice savedInvoice = serviceImpl1.saveInvoice(invoice);
            return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Data Saved Successfully", savedInvoice));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RestAPIResponse("Error", "Failed to save invoice: " + e.getMessage(), null));
        }
    }

    @PostMapping("/upload-template")
    public ResponseEntity<RestAPIResponse> uploadTemplate(@RequestParam("file") MultipartFile file) {
        try {
            String savedFilename = serviceImpl1.storeFile(file);
            return ResponseEntity.ok(new RestAPIResponse("Success", "File Uploaded Successfully", savedFilename));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RestAPIResponse("Error", "Failed to upload the file: " + e.getMessage(), null));
        }
    }

    @GetMapping("/templates")
    public ResponseEntity<RestAPIResponse> getAllTemplates() {
        try {
            return ResponseEntity.ok(new RestAPIResponse("Success", "All templates retrieved successfully", serviceImpl1.getAllTemplates()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RestAPIResponse("Error", "Failed to retrieve templates", e.getMessage()));
        }
    }

    @GetMapping("/view/{filename}")
    public ResponseEntity<Resource> viewTemplate(@PathVariable String filename) {
        try {
            Resource resource = serviceImpl1.loadFileAsResource(filename);
            String contentType = "application/octet-stream";

            if (filename.endsWith(".pdf")) {
                contentType = "application/pdf";
            } else if (filename.endsWith(".csv")) {
                contentType = "text/csv";
            } else if (filename.endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }

            return ResponseEntity.ok()
                    .header("Content-Disposition", "inline; filename=\"" + resource.getFilename() + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestAPIResponse> getInvoiceById(@PathVariable Long id) {
        try {
            ManualInvoice invoice = serviceImpl1.getInvoiceById(id);
            if (invoice == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new RestAPIResponse("Error", "Invoice not found", null));
            }
            return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Retrieved", invoice));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RestAPIResponse("Error", "Failed to retrieve invoice: " + e.getMessage(), null));
        }
    }

    @GetMapping("/getall")
    public ResponseEntity<RestAPIResponse> getAllInvoices() {
        try {
            return ResponseEntity.ok(new RestAPIResponse("Success", "All Invoices Retrieved", serviceImpl1.getAllInvoices()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RestAPIResponse("Error", "Failed to retrieve invoices: " + e.getMessage(), null));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<RestAPIResponse> searchInvoices(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageRequest = PageRequest.of(page, size);
            return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Retrieved", serviceImpl1.searchInvoices(keyword, pageRequest)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RestAPIResponse("Error", "Failed to search Invoices: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<RestAPIResponse> updateInvoice(@PathVariable Long id, @RequestBody ManualInvoice invoice) {
        try {
            ManualInvoice updatedInvoice = serviceImpl1.updateInvoice(id, invoice);
            if (updatedInvoice == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new RestAPIResponse("Error", "Invoice not found", null));
            }
            return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Updated Successfully", updatedInvoice));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RestAPIResponse("Error", "Failed to update invoice: " + e.getMessage(), null));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<RestAPIResponse> deleteInvoice(@PathVariable Long id) {
        try {
            serviceImpl1.deleteInvoice(id);
            return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Deleted Successfully", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RestAPIResponse("Error", "Failed to delete invoice: " + e.getMessage(), null));
        }
    }
}
