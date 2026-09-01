package com.invoice.scheduler;

import com.invoice.entity.AdminSettings;
import com.invoice.entity.ManageUser;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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

	private static final String DEFAULT_DAY = "MON";
	private static final String DEFAULT_TIME = "20:30";
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

	// Runs every minute; each admin's configured day+time controls when their
	// alerts fire.
	@Scheduled(cron = "0 * * * * *")
	@Transactional
	public void runDailyAlerts() {
		LocalDate today = LocalDate.now();
		DayOfWeek todayDow = today.getDayOfWeek();
		LocalTime nowTime = LocalTime.now().withSecond(0).withNano(0);
		log.info("[InvoiceAlertScheduler] Running daily alerts for {}", today);

		// ── Step 1: overdue alert emails ──────────────────────────────────
		List<AdminSettings> overdueAdmins = adminSettingsRepository.findAllWithOverdueAlertsEnabled();

		for (AdminSettings admin : overdueAdmins) {
			try {
				if (!isScheduledNow(admin, todayDow, nowTime))
					continue;
				Long authAdminId = admin.getAdminId();
				if (authAdminId == null) {
					log.warn("[InvoiceAlertScheduler] No adminId mapped for {} — skipped", admin.getPrimaryEmail());
					continue;
				}
				List<ManualInvoice> overdueInvoices = invoiceRepository.findOverdueByAdmin(authAdminId, today);

				if (overdueInvoices.isEmpty())
					continue;

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
				log.info("[InvoiceAlertScheduler] Overdue alert: {} email(s) sent for admin {}", sent,
						admin.getPrimaryEmail());

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
				if (!isScheduledNow(admin, todayDow, nowTime))
					continue;
				int daysAhead = admin.getReminderDaysBefore();
				LocalDate reminderDate = today.plusDays(daysAhead);

				Long authAdminIdR = admin.getAdminId();
				if (authAdminIdR == null)
					continue;
				List<ManualInvoice> dueInvoices = invoiceRepository.findDueOnByAdmin(authAdminIdR, reminderDate);

				if (dueInvoices.isEmpty())
					continue;

				UserDTO sender = buildSender(admin);
				List<String> cc = buildCcList(admin);
				int sent = 0;
				for (ManualInvoice invoice : dueInvoices) {
					if (invoice.getCustomerEmail() == null || invoice.getCustomerEmail().isBlank())
						continue;
					try {
						emailService.sendPaymentReminderEmail(sender, invoice, daysAhead, cc);
						sent++;
					} catch (Exception e) {
						log.error("[InvoiceAlertScheduler] Failed to send reminder email for invoice {}: {}",
								invoice.getInvoiceNumber(), e.getMessage());
					}
				}
				log.info("[InvoiceAlertScheduler] Reminder: {} email(s) sent for admin {} (due in {} days)", sent,
						admin.getPrimaryEmail(), daysAhead);

			} catch (Exception e) {
				log.error("[InvoiceAlertScheduler] Error processing reminders for admin {}: {}",
						admin.getPrimaryEmail(), e.getMessage());
			}
		}

		log.info("[InvoiceAlertScheduler] Daily alerts complete");
	}

	private boolean isScheduledNow(AdminSettings admin, DayOfWeek todayDow, LocalTime nowTime) {
		String dayStr = (admin.getSchedulerDay() != null && !admin.getSchedulerDay().isBlank())
				? admin.getSchedulerDay().trim().toUpperCase()
				: DEFAULT_DAY;
		String timeStr = (admin.getSchedulerTime() != null && !admin.getSchedulerTime().isBlank())
				? admin.getSchedulerTime().trim()
				: DEFAULT_TIME;
		try {
			DayOfWeek configured = DayOfWeek.valueOf(dayStr);
			LocalTime configuredTime = LocalTime.parse(timeStr, TIME_FMT);
			return todayDow == configured && nowTime.equals(configuredTime);
		} catch (Exception e) {
			log.warn("[InvoiceAlertScheduler] Invalid scheduler config day='{}' time='{}' for {} — using defaults",
					dayStr, timeStr, admin.getPrimaryEmail());
			return todayDow == DayOfWeek.MONDAY && nowTime.equals(LocalTime.parse(DEFAULT_TIME, TIME_FMT));
		}
	}

	private List<String> buildCcList(AdminSettings admin) {
		return manageUserRepository.findAdminAndHrByAdminId(admin.getAdminId()).stream().map(u -> u.getPrimaryEmail())
				.filter(e -> e != null && !e.isBlank()).distinct().collect(java.util.stream.Collectors.toList());
	}

	private UserDTO buildSender(AdminSettings admin) {
		ManageUser adminUser = manageUserRepository.findAdminAndHrByAdminId(admin.getAdminId()).stream()
				.filter(u -> "ADMIN".equalsIgnoreCase(u.getRoleName())).findFirst().orElse(null);

		String companyAddress = adminUser != null ? adminUser.getFormattedAddress() : "";
		String fullName = adminUser != null && adminUser.getFullName() != null ? adminUser.getFullName()
				: admin.getPrimaryEmail();
		String companyName = adminUser != null && adminUser.getCompanyName() != null ? adminUser.getCompanyName() : "";

		return manageUserRepository.findAccountantsByAdminId(admin.getAdminId()).stream().findFirst()
				.map(accountant -> new UserDTO(accountant.getPrimaryEmail(),
						accountant.getFullName() != null ? accountant.getFullName() : accountant.getPrimaryEmail(),
						null, companyName, null, "Accountant", companyAddress))
				.orElseGet(() -> new UserDTO(admin.getPrimaryEmail(), fullName, null, companyName, null, "Admin",
						companyAddress));
	}
}
