package com.example.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "invoice_items")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemName;
    private String description;
    private Integer quantity;
    private Double price;
    private Double tax;
    private Double total;

    @ManyToOne
    @JoinColumn(name = "invoice_id" , nullable = false)
    @JsonBackReference
    private ManualInvoice manualInvoice;
}
