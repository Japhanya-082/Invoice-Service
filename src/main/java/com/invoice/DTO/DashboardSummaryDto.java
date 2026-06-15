package com.invoice.DTO;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {
    private DashboardKpisDTO kpis;
    private List<UpcomingDueDateDTO> upcomingDueDates;
    private List<RecentActivityDTO> recentActivities;
}
