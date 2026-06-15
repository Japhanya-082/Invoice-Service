package com.invoice.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingDueDateDTO {

    private Long id;
    private String invoiceNumber;
    /** "Customer Invoice" or "Vendor Bill" */
    private String documentType;
    private String customerVendorName;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private BigDecimal outstandingBalance;
    private String status;
    /** Negative = overdue by N days */
    private long daysRemaining;
    /** OVERDUE | HIGH (1-3d) | MEDIUM (4-7d) | LOW (8-14d) */
    private String urgency;
}
