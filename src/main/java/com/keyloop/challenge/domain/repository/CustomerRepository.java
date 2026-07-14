package com.keyloop.challenge.domain.repository;

import com.keyloop.challenge.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
