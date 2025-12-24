package com.pesapal.pesapalpayment.repository;

import com.pesapal.pesapalpayment.entity.PesapalTransaction;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface PesapalTransactionRepository extends JpaRepository<PesapalTransaction, Long> {
    PesapalTransaction findByPesapalOrderId(String pesapalOrderId);
    Optional<PesapalTransaction> findByMerchantReference(String merchantReference);
}
