package com.ivs.masterdata.partner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByCode(String code);

    boolean existsByCode(String code);

    List<Customer> findAllByOrderByCreatedAtDesc();

    @Query(value = "select nextval('ivs.customer_no_seq')", nativeQuery = true)
    long nextCustomerSequence();
}