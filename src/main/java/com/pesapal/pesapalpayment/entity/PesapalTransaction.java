package com.pesapal.pesapalpayment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "pesapal_transactions",
    uniqueConstraints = @UniqueConstraint(columnNames = "merchantReference")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PesapalTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String merchantReference;

    @Column(unique = true)
    private String pesapalOrderId;

    private String customerId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}