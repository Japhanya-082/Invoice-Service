package com.invoice.controller;

import com.invoice.DTO.DashboardSummaryResponse;
import com.invoice.common.RestAPIResponse;
import com.invoice.service.DashboardService;
import com.invoice.tenant.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Single endpoint returning all dashboard data.
     * AdminId is resolved from the authenticated JWT — never from a request parameter.
     * GET /dashboard/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<RestAPIResponse> getSummary() {
        Long adminId = SecurityUtils.getCurrentAdminId();
        log.debug("Dashboard summary requested for adminId={}", adminId);
        DashboardSummaryResponse summary = dashboardService.getSummary(adminId);
        return ResponseEntity.ok(new RestAPIResponse("success", "Dashboard summary", summary));
    }
}
