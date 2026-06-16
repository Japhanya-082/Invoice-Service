package com.invoice.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceSnapshotDTO {
    private Long id;
    private String invoiceNumber;
    private String customer;
    private String vendorType;
    private String status;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal amountDue;
    private BigDecimal total;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
