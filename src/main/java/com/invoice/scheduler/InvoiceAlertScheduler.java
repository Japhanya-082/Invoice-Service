package com.invoice.scheduler;

import com.invoice.entity.AdminSettings;
import com.invoice.entity.ManualInvoice;
import com.invoice.repository.AdminSettingsRepository;
import com.invoice.repository.ManageUserRepository;
import com.invoice.repository.ManualInvoiceRepository;
import com.invoice.serviceImpl.EmailServiceImpl;
import com.invoice.DTO.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceAlertScheduler {

    private final ManualInvoiceRepository invoiceRepository;
    private final AdminSettingsRepository adminSettingsRepository;
    private final ManageUserRepository manageUserRepository;
    private final EmailServiceImpl emailService;

    /**
     * Runs every day at 08:00 AM.
     *
     * Step 1 — Mark overdue: any Pending/Partially Paid invoice whose due date
     *           has passed is flipped to OVERDUE in the DB.
     *
     * Step 2 — Overdue alerts: for every admin with overdueAlerts=true, send one
     *           email per newly-overdue invoice to the customer's email address.
     *
     * Step 3 — Reminder alerts: for every admin with emailReminders=true, send a
     *           reminder email for invoices due exactly N days from today
     *           (where N = reminderDaysBefore setting).
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void runDailyAlerts() {
        LocalDate today = LocalDate.now();
        log.info("[InvoiceAlertScheduler] Running daily alerts for {}", today);

        // ── Step 1: mark overdue ──────────────────────────────────────────
        int marked = invoiceRepository.markOverdueInvoices(today);
        log.info("[InvoiceAlertScheduler] Marked {} invoice(s) as OVERDUE", marked);

        // ── Step 2: overdue alert emails ──────────────────────────────────
        List<AdminSettings> overdueAdmins = adminSettingsRepository.findAllWithOverdueAlertsEnabled();
        log.info("[InvoiceAlertScheduler] {} admin(s) have overdue alerts enabled", overdueAdmins.size());

        for (AdminSettings admin : overdueAdmins) {
            try {
                Long authAdminId = admin.getAdminId();
                if (authAdminId == null) {
                    log.warn("[InvoiceAlertScheduler] No adminId mapped for {} — skipped", admin.getPrimaryEmail());
                    continue;
                }
                List<ManualInvoice> overdueInvoices =
                        invoiceRepository.findOverdueByAdmin(authAdminId, today);

                if (overdueInvoices.isEmpty()) continue;

                UserDTO sender = buildSender(admin);
                List<String> cc = buildCcList(admin);
                int sent = 0;
                for (ManualInvoice invoice : overdueInvoices) {
                    if (invoice.getCustomerEmail() == null || invoice.getCustomerEmail().isBlank()) {
                        log.warn("[InvoiceAlertScheduler] Invoice {} has no customer email — skipped",
                                invoice.getInvoiceNumber());
                        continue;
                    }
                    try {
                        emailService.sendOverdueInvoiceEmail(sender, invoice, cc);
                        sent++;
                    } catch (Exception e) {
                        log.error("[InvoiceAlertScheduler] Failed to send overdue email for invoice {}: {}",
                                invoice.getInvoiceNumber(), e.getMessage());
                    }
                }
                log.info("[InvoiceAlertScheduler] Overdue alert: {} email(s) sent for admin {}",
                        sent, admin.getPrimaryEmail());

            } catch (Exception e) {
                log.error("[InvoiceAlertScheduler] Error processing overdue alerts for admin {}: {}",
                        admin.getPrimaryEmail(), e.getMessage());
            }
        }

        // ── Step 3: upcoming-due reminder emails ──────────────────────────
        List<AdminSettings> reminderAdmins = adminSettingsRepository.findAllWithEmailRemindersEnabled();
        log.info("[InvoiceAlertScheduler] {} admin(s) have email reminders enabled", reminderAdmins.size());

        for (AdminSettings admin : reminderAdmins) {
            try {
                int daysAhead = admin.getReminderDaysBefore();
                LocalDate reminderDate = today.plusDays(daysAhead);

                Long authAdminIdR = admin.getAdminId();
                if (authAdminIdR == null) continue;
                List<ManualInvoice> dueInvoices =
                        invoiceRepository.findDueOnByAdmin(authAdminIdR, reminderDate);

                if (dueInvoices.isEmpty()) continue;

                UserDTO sender = buildSender(admin);
                List<String> cc = buildCcList(admin);
                int sent = 0;
                for (ManualInvoice invoice : dueInvoices) {
                    if (invoice.getCustomerEmail() == null || invoice.getCustomerEmail().isBlank()) continue;
                    try {
                        emailService.sendPaymentReminderEmail(sender, invoice, daysAhead, cc);
                        sent++;
                    } catch (Exception e) {
                        log.error("[InvoiceAlertScheduler] Failed to send reminder email for invoice {}: {}",
                                invoice.getInvoiceNumber(), e.getMessage());
                    }
                }
                log.info("[InvoiceAlertScheduler] Reminder: {} email(s) sent for admin {} (due in {} days)",
                        sent, admin.getPrimaryEmail(), daysAhead);

            } catch (Exception e) {
                log.error("[InvoiceAlertScheduler] Error processing reminders for admin {}: {}",
                        admin.getPrimaryEmail(), e.getMessage());
            }
        }

        log.info("[InvoiceAlertScheduler] Daily alerts complete");
    }

    private List<String> buildCcList(AdminSettings admin) {
        return manageUserRepository.findAdminAndHrByAdminId(admin.getAdminId())
                .stream()
                .map(u -> u.getPrimaryEmail())
                .filter(e -> e != null && !e.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    private UserDTO buildSender(AdminSettings admin) {
        String companyAddress = manageUserRepository.findAdminAndHrByAdminId(admin.getAdminId())
                .stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRoleName()))
                .findFirst()
                .map(u -> u.getFormattedAddress())
                .orElse("");

        return manageUserRepository.findAccountantsByAdminId(admin.getAdminId())
                .stream().findFirst()
                .map(accountant -> new UserDTO(
                        accountant.getPrimaryEmail(),
                        admin.getFullName() != null ? admin.getFullName() : accountant.getPrimaryEmail(),
                        null,
                        admin.getCompanyName() != null ? admin.getCompanyName() : "",
                        null,
                        "Accountant",
                        companyAddress
                ))
                .orElseGet(() -> new UserDTO(
                        admin.getPrimaryEmail(),
                        admin.getFullName() != null ? admin.getFullName() : admin.getPrimaryEmail(),
                        null,
                        admin.getCompanyName() != null ? admin.getCompanyName() : "",
                        null,
                        "Admin",
                        companyAddress
                ));
    }
}
