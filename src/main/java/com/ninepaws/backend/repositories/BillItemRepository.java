package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.BillItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillItemRepository extends JpaRepository<BillItem, UUID> {

  List<BillItem> findByBillId(UUID billId);
}
