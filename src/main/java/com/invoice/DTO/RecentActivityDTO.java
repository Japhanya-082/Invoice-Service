package com.invoice.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentActivityDTO {

    /** INVOICE_CREATED | INVOICE_SENT | INVOICE_UPDATED | INVOICE_OVERDUE |
     *  PAYMENT_RECEIVED | VENDOR_PAYMENT | PARTIAL_PAYMENT */
    private String type;
    private String referenceNumber;
    private String customerVendor;
    private String description;
    private BigDecimal amount;
    private LocalDateTime timestamp;
    /** Material icon name */
    private String icon;
    /** For navigation */
    private Long invoiceId;
    private String vendorType;
}
