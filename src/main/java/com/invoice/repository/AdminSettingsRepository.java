package com.invoice.repository;

import com.invoice.entity.AdminSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AdminSettingsRepository extends JpaRepository<AdminSettings, Long> {

    @Query("SELECT a FROM AdminSettings a WHERE LOWER(a.primaryEmail) = LOWER(:email)")
    Optional<AdminSettings> findByEmailIgnoreCase(@Param("email") String email);

    @Query("SELECT a FROM AdminSettings a WHERE a.adminId = :adminId")
    Optional<AdminSettings> findByAuthAdminId(@Param("adminId") Long adminId);

    @Query("SELECT a FROM AdminSettings a WHERE a.overdueAlerts = true")
    List<AdminSettings> findAllWithOverdueAlertsEnabled();

    @Query("SELECT a FROM AdminSettings a WHERE a.emailReminders = true AND a.reminderDaysBefore IS NOT NULL")
    List<AdminSettings> findAllWithEmailRemindersEnabled();
    
}
