package com.invoice.serviceImpl;

import com.invoice.DTO.*;
import com.invoice.entity.ManualInvoice;
import com.invoice.entity.Payment;
import com.invoice.repository.ManualInvoiceRepository;
import com.invoice.repository.PaymentRepository;
import com.invoice.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final ManualInvoiceRepository invoiceRepo;
    private final PaymentRepository paymentRepo;

    @Override
    public DashboardSummaryResponse getSummary(Long adminId) {
        return DashboardSummaryResponse.builder()
                .kpis(buildKpis(adminId))
                .upcomingDueDates(buildUpcomingDueDates(adminId))
                .recentActivities(buildRecentActivities(adminId))
                .build();
    }

    // ─── KPIs ─────────────────────────────────────────────────────────────────

    private DashboardKpisDTO buildKpis(Long adminId) {
        int year  = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        BigDecimal arOut   = safe(invoiceRepo.sumArOutstanding(adminId));
        Long       arCount = safe(invoiceRepo.countArOutstanding(adminId));

        BigDecimal apOut   = safe(invoiceRepo.sumApOutstanding(adminId));
        Long       apCount = safe(invoiceRepo.countApOutstanding(adminId));

        BigDecimal overdue      = safe(invoiceRepo.sumOverdueAmount(adminId));
        Long       overdueCount = safe(invoiceRepo.countOverdue(adminId));
        BigDecimal arOverdue    = safe(invoiceRepo.sumArOverdueAmount(adminId));
        BigDecimal apOverdue    = safe(invoiceRepo.sumApOverdueAmount(adminId));

        BigDecimal collected      = safe(invoiceRepo.sumCollectedThisMonth(adminId, year, month));
        Long       collectedCount = safe(invoiceRepo.countCollectedThisMonth(adminId, year, month));

        BigDecimal paid      = safe(invoiceRepo.sumPaidThisMonth(adminId, year, month));
        Long       paidCount = safe(invoiceRepo.countPaidThisMonth(adminId, year, month));

        return DashboardKpisDTO.builder()
                .arOutstanding(arOut)
                .arOutstandingCount(arCount)
                .apOutstanding(apOut)
                .apOutstandingCount(apCount)
                .overdueAmount(overdue)
                .overdueCount(overdueCount)
                .arOverdueAmount(arOverdue)
                .apOverdueAmount(apOverdue)
                .collectedThisMonth(collected)
                .collectedThisMonthCount(collectedCount)
                .paidThisMonth(paid)
                .paidThisMonthCount(paidCount)
                .currency("USD")
                .build();
    }

    // ─── Upcoming Due Dates ───────────────────────────────────────────────────

    private List<UpcomingDueDateDTO> buildUpcomingDueDates(Long adminId) {
        LocalDate today  = LocalDate.now();
        LocalDate end    = today.plusDays(14);

        List<ManualInvoice> invoices = invoiceRepo.findUpcomingAndOverdue(
                adminId, end, PageRequest.of(0, 20));

        return invoices.stream()
                .map(inv -> toUpcomingDTO(inv, today))
                .sorted(Comparator.comparingLong(UpcomingDueDateDTO::getDaysRemaining))
                .collect(Collectors.toList());
    }

    private UpcomingDueDateDTO toUpcomingDTO(ManualInvoice inv, LocalDate today) {
        long days = inv.getDueDate() != null
                ? ChronoUnit.DAYS.between(today, inv.getDueDate())
                : Long.MAX_VALUE;

        String urgency;
        if (days < 0)       urgency = "OVERDUE";
        else if (days <= 3) urgency = "HIGH";
        else if (days <= 7) urgency = "MEDIUM";
        else                urgency = "LOW";

        boolean isReceivable = "receivable".equalsIgnoreCase(inv.getVendorType());

        return UpcomingDueDateDTO.builder()
                .id(inv.getId())
                .invoiceNumber(inv.getInvoiceNumber())
                .documentType(isReceivable ? "Customer Invoice" : "Vendor Bill")
                .customerVendorName(inv.getCustomer())
                .issueDate(inv.getInvoiceDate())
                .dueDate(inv.getDueDate())
                .outstandingBalance(safe(inv.getAmountDue()))
                .status(formatStatus(inv.getStatus()))
                .daysRemaining(days)
                .urgency(urgency)
                .build();
    }

    // ─── Recent Activity ──────────────────────────────────────────────────────

    private List<RecentActivityDTO> buildRecentActivities(Long adminId) {
        List<RecentActivityDTO> activities = new ArrayList<>();

        // From invoices
        List<ManualInvoice> recentInvoices = invoiceRepo.findRecentlyUpdated(
                adminId, PageRequest.of(0, 20));
        for (ManualInvoice inv : recentInvoices) {
            activities.add(toActivityDTO(inv));
        }

        // From payments
        List<Payment> recentPayments = paymentRepo.findRecentPayments(
                adminId, PageRequest.of(0, 10));
        for (Payment pay : recentPayments) {
            activities.add(paymentToActivityDTO(pay, adminId));
        }

        return activities.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RecentActivityDTO::getTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(15)
                .collect(Collectors.toList());
    }

    private RecentActivityDTO toActivityDTO(ManualInvoice inv) {
        String status = inv.getStatus() != null ? inv.getStatus().toUpperCase() : "";
        boolean isReceivable = "receivable".equalsIgnoreCase(inv.getVendorType());
        boolean isNew = inv.getCreatedAt() != null && inv.getUpdatedAt() != null
                && ChronoUnit.SECONDS.between(inv.getCreatedAt(), inv.getUpdatedAt()) < 5;

        String type, desc, icon;
        if (isNew) {
            type = "INVOICE_CREATED";
            desc = (isReceivable ? "Invoice " : "Vendor bill ") + inv.getInvoiceNumber() + " created";
            icon = "note_add";
        } else if ("RECEIVED".equals(status) || "PAID".equals(status)) {
            type = isReceivable ? "PAYMENT_RECEIVED" : "VENDOR_PAYMENT";
            desc = isReceivable ? "Payment received for " + inv.getInvoiceNumber()
                                : "Vendor payment completed for " + inv.getInvoiceNumber();
            icon = isReceivable ? "payments" : "receipt_long";
        } else if ("PARTIALLY_RECEIVED".equals(status) || "PARTIALLY_PAID".equals(status)) {
            type = "PARTIAL_PAYMENT";
            desc = "Partial payment recorded for " + inv.getInvoiceNumber();
            icon = "pending_actions";
        } else if ("OVERDUE".equals(status)) {
            type = "INVOICE_OVERDUE";
            desc = (isReceivable ? "Invoice " : "Bill ") + inv.getInvoiceNumber() + " is overdue";
            icon = "warning";
        } else if ("PENDING".equals(status)) {
            type = "INVOICE_SENT";
            desc = (isReceivable ? "Invoice " : "Bill ") + inv.getInvoiceNumber() + " is pending";
            icon = "send";
        } else {
            type = "INVOICE_UPDATED";
            desc = (isReceivable ? "Invoice " : "Bill ") + inv.getInvoiceNumber() + " updated";
            icon = "edit_note";
        }

        return RecentActivityDTO.builder()
                .type(type)
                .referenceNumber(inv.getInvoiceNumber())
                .customerVendor(inv.getCustomer())
                .description(desc)
                .amount(inv.getAmountDue())
                .timestamp(inv.getUpdatedAt())
                .icon(icon)
                .invoiceId(inv.getId())
                .vendorType(inv.getVendorType())
                .build();
    }

    private RecentActivityDTO paymentToActivityDTO(Payment pay, Long adminId) {
        try {
            Optional<ManualInvoice> invOpt = invoiceRepo.findById(pay.getInvoiceId());
            if (invOpt.isEmpty()) return null;
            ManualInvoice inv = invOpt.get();
            if (!adminId.equals(inv.getAdminId())) return null;

            boolean isReceivable = "receivable".equalsIgnoreCase(inv.getVendorType());
            String type = isReceivable ? "PAYMENT_RECEIVED" : "VENDOR_PAYMENT";
            String desc = isReceivable
                    ? "Payment of $" + pay.getAmount() + " received for " + inv.getInvoiceNumber()
                    : "Vendor payment of $" + pay.getAmount() + " made for " + inv.getInvoiceNumber();

            return RecentActivityDTO.builder()
                    .type(type)
                    .referenceNumber(inv.getInvoiceNumber())
                    .customerVendor(inv.getCustomer())
                    .description(desc)
                    .amount(pay.getAmount())
                    .timestamp(pay.getCreatedAt())
                    .icon(isReceivable ? "payments" : "receipt_long")
                    .invoiceId(inv.getId())
                    .vendorType(inv.getVendorType())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to build activity for payment {}: {}", pay.getPaymentId(), e.getMessage());
            return null;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private Long safe(Long v) {
        return v != null ? v : 0L;
    }

    private String formatStatus(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase()
                .replace("_", " ")
                .substring(0, 1).toUpperCase()
                + s.trim().toLowerCase().replace("_", " ").substring(1);
    }
}
