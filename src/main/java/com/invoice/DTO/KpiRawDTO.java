package com.invoice.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiRawDTO {
    private BigDecimal arOutstanding;
    private Long arOutstandingCount;
    private BigDecimal apOutstanding;
    private Long apOutstandingCount;
    private BigDecimal overdueAmount;
    private Long overdueCount;
    private BigDecimal arOverdueAmount;
    private BigDecimal apOverdueAmount;
    private BigDecimal collectedThisMonth;
    private Long collectedThisMonthCount;
    private BigDecimal paidThisMonth;
    private Long paidThisMonthCount;
}
