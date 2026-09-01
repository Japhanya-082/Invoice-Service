package com.invoice.repository;

import com.invoice.entity.ManageUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ManageUserRepository extends JpaRepository<ManageUser, Long> {

    @Query("SELECT u FROM ManageUser u WHERE u.adminId = :adminId " +
           "AND UPPER(u.roleName) IN ('ADMIN', 'HR', 'ACCOUNTANT') " +
           "AND u.active = true")
    List<ManageUser> findCcRecipientsByAdminId(@Param("adminId") Long adminId);
    
    
    @Query("SELECT u FROM ManageUser u WHERE u.adminId = :adminId " +
           "AND UPPER(u.roleName) = 'ACCOUNTANT' AND u.active = true")
    List<ManageUser> findAccountantsByAdminId(@Param("adminId") Long adminId);

    
    @Query("SELECT u FROM ManageUser u WHERE u.adminId = :adminId " +
           "AND UPPER(u.roleName) IN ('ADMIN', 'HR') AND u.active = true")
    List<ManageUser> findAdminAndHrByAdminId(@Param("adminId") Long adminId);
}