package com.example.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "invoice")
@Builder
public class Invoice {
	
	@Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
	private long invoiceId;
	private LocalDate date;
	private LocalDate dueDate;
	private String customer;
	
	@Column(precision = 15, scale = 2)
    private BigDecimal amount;       
	
	 private String status;     
     private String fileName; 
     

     @CreationTimestamp
     private LocalDateTime createdAt;

     @UpdateTimestamp
     private LocalDateTime updatedAt;

	 }

