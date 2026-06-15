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
public class DashboardSummaryResponse {

    private DashboardKpisDTO kpis;
    private List<UpcomingDueDateDTO> upcomingDueDates;
    private List<RecentActivityDTO> recentActivities;
}
