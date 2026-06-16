package com.invoice.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRawDataDTO {
    private KpiRawDTO kpiData;
    private List<InvoiceSnapshotDTO> upcomingInvoices;
    private List<InvoiceSnapshotDTO> recentInvoices;
    private List<PaymentSnapshotDTO> recentPayments;
}
