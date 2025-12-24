package com.pesapal.pesapalpayment.entity;

import jakarta.persistence.*;
import lombok.*;


@Entity @Table(name = "pesapal_transactions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PesapalTransaction {
    @Id @GeneratedValue
    private Long id;

    private String merchantReference;    
    private String pesapalOrderId;       
    private String customerId;
    private Long amount;
    private String currency;
    private String status;               
    private String description;
}