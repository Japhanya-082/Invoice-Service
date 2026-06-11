package com.invoice.DTO;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PaymentRequest {

    @NotNull(message = "invoiceId is required")
    private Long invoiceId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0001", message = "amount must be > 0")
    private BigDecimal amount;

    @NotNull(message = "paymentDate is required")
    @PastOrPresent(message = "paymentDate cannot be in the future")
    private LocalDate paymentDate;

    @Size(max = 120)
    private String paymentReference;

    @Size(max = 40)
    private String paymentMethod;

    @Size(max = 1000)
    private String remarks;
}
