
package com.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.invoice.entity.Invoice;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice,  Long> {
        boolean existsByInvoiceNumber(String invoiceNumber);
        public void deleteByInvoiceNumber(String invoiceNumber);
        public	boolean existsByConsultantId(Long consultantId);
}

