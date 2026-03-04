package com.pesapal.pesapalpayment.repository;

import com.pesapal.pesapalpayment.entity.PesapalTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PesapalTransactionRepository
        extends JpaRepository<PesapalTransaction, Long> {

    Optional<PesapalTransaction> findByPesapalOrderId(String pesapalOrderId);

    Optional<PesapalTransaction> findByMerchantReference(String merchantReference);
}