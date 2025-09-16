package com.example.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "manual_invoices")
public class ManualInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate invoiceDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    private String template;
    private String paymentTerms;
    private String salesRep;
    private String poNumber;

    private Double subtotal;
    private Double tax;
    private Double total;
    private Double credit;
    private Double amountDue;

    @OneToMany(mappedBy = "manualInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<InvoiceItem> items = new ArrayList<>();

    // Helper methods
    public void addItem(InvoiceItem item) {
        this.items.add(item);
        item.setManualInvoice(this);
    }

    public void removeItem(InvoiceItem item) {
        this.items.remove(item);
        item.setManualInvoice(null);
    }
}
