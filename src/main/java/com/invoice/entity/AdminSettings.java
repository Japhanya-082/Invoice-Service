package com.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "updated_profile", schema = "invoice")
public class AdminSettings {

	
    @Id
    private Long id;

    @Column(name = "primary_email")
    private String primaryEmail;

    @Column(name = "invoice_prefix")
    private String invoicePrefix;

    @Column(name = "overdue_alerts")
    private Boolean overdueAlerts;

    @Column(name = "email_reminders")
    private Boolean emailReminders;

    @Column(name = "reminder_days_before")
    private Integer reminderDaysBefore;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "scheduler_day")
    private String schedulerDay;

    @Column(name = "scheduler_time")
    private String schedulerTime;

    @Column(name = "cc_admin_email")
    private String ccAdminEmail;

    @Column(name = "cc_hr_email")
    private String ccHrEmail;

    @Column(name = "cc_accounts_email")
    private String ccAccountsEmail;

    public Long getId() { return id; }
    public String getPrimaryEmail() { return primaryEmail; }
    public String getInvoicePrefix() { return invoicePrefix; }
    public Boolean getOverdueAlerts() { return overdueAlerts; }
    public Boolean getEmailReminders() { return emailReminders; }
    public Integer getReminderDaysBefore() { return reminderDaysBefore; }
    public String getFullName() { return fullName; }
    public String getCompanyName() { return companyName; }
    public Long getAdminId() { return adminId; }
    public String getSchedulerDay() { return schedulerDay; }
    public String getSchedulerTime() { return schedulerTime; }
    public String getCcAdminEmail() { return ccAdminEmail; }
    public String getCcHrEmail() { return ccHrEmail; }
    public String getCcAccountsEmail() { return ccAccountsEmail; }
}
