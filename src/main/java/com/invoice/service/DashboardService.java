package com.invoice.service;

import com.invoice.DTO.DashboardSummaryResponse;

public interface DashboardService {
    DashboardSummaryResponse getSummary(Long adminId);
}
