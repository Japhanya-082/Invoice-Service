package com.example.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "manual_invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String vendorId;
    private String companyName;
    private String companyEmail;
    private String companyPhone;
    private String companyAddress;
    private String companyUrl;
    private String customer;
    private String clientEmail;
    private String customerEmail;
    private String customerPhone;
    private String clientPhone;
    private String billingAddress;
    private String shippingAddress;
    private String salesRep;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private String paymentTerms;
    private String poNumber;
    private String status;
    private String template;
    private String termsAndConditions;
    private String notes;

    private Double totalHours = 0.0;
    private Double subtotal = 0.0;
    private Double tax = 0.0;
    private Double total = 0.0;
    private Double amountDue = 0.0;
    private Double credit = 0.0;

    private String currency;
    private String issuedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "manualInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<InvoiceItem> items;

    /**
     * Updates the current ManualInvoice with values from another invoice.
     * Does not update the id.
     */
    public void updateFrom(ManualInvoice invoice) {
        this.vendorId = invoice.getVendorId();
        this.companyName = invoice.getCompanyName();
        this.companyEmail = invoice.getCompanyEmail();
        this.companyPhone = invoice.getCompanyPhone();
        this.companyAddress = invoice.getCompanyAddress();
        this.companyUrl = invoice.getCompanyUrl();
        this.customer = invoice.getCustomer();
        this.clientEmail = invoice.getClientEmail();
        this.customerEmail = invoice.getCustomerEmail();
        this.customerPhone = invoice.getCustomerPhone();
        this.clientPhone = invoice.getClientPhone();
        this.billingAddress = invoice.getBillingAddress();
        this.shippingAddress = invoice.getShippingAddress();
        this.salesRep = invoice.getSalesRep();
        this.invoiceNumber = invoice.getInvoiceNumber();
        this.invoiceDate = invoice.getInvoiceDate();
        this.dueDate = invoice.getDueDate();
        this.paymentTerms = invoice.getPaymentTerms();
        this.poNumber = invoice.getPoNumber();
        this.status = invoice.getStatus();
        this.template = invoice.getTemplate();
        this.termsAndConditions = invoice.getTermsAndConditions();
        this.notes = invoice.getNotes();
        this.totalHours = invoice.getTotalHours();
        this.subtotal = invoice.getSubtotal();
        this.tax = invoice.getTax();
        this.total = invoice.getTotal();
        this.amountDue = invoice.getAmountDue();
        this.credit = invoice.getCredit();
        this.currency = invoice.getCurrency();
        this.issuedBy = invoice.getIssuedBy();
        this.createdAt = invoice.getCreatedAt();
        this.updatedAt = invoice.getUpdatedAt();
    }
}
