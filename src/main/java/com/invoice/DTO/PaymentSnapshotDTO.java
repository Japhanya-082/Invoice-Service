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
public class PaymentSnapshotDTO {
    private Long paymentId;
    private Long invoiceId;
    private BigDecimal amount;
    private LocalDateTime createdAt;
    // Enriched from invoice — avoids a second Feign call in Invoice-Dashboard
    private String invoiceNumber;
    private String customer;
    private String vendorType;
}
