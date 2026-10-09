package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Dispute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    List<Dispute> findByStatus(String status);

    Page<Dispute> findAllByOrderByDateFiledDesc(Pageable pageable);
}