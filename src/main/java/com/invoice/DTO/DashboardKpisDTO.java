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
public class DashboardKpisDTO {

    // AR Outstanding – receivable invoices not yet fully paid
    private BigDecimal arOutstanding;
    private Long arOutstandingCount;

    // AP Outstanding – payable invoices not yet fully paid
    private BigDecimal apOutstanding;
    private Long apOutstandingCount;

    // Overdue – any invoice past its due date with remaining balance
    private BigDecimal overdueAmount;
    private Long overdueCount;
    private BigDecimal arOverdueAmount;
    private BigDecimal apOverdueAmount;

    // Cash collected from customers this calendar month
    private BigDecimal collectedThisMonth;
    private Long collectedThisMonthCount;

    // Cash paid to vendors this calendar month
    private BigDecimal paidThisMonth;
    private Long paidThisMonthCount;

    private String currency;
}
