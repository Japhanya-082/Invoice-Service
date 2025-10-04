package com.example.repository;

import com.example.entity.ManualInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualInvoiceRepository extends JpaRepository<ManualInvoice, Long> {

    // Check if an invoice with the given number exists
    boolean existsByInvoiceNumber(String invoiceNumber);

    // Search invoices by keyword in multiple fields
    @Query("SELECT m FROM ManualInvoice m " +
           "WHERE LOWER(m.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.customer) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.clientEmail) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.clientPhone) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.billingAddress) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.invoiceNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.currency) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<ManualInvoice> searchInvoices(@Param("keyword") String keyword, Pageable pageable);
}
