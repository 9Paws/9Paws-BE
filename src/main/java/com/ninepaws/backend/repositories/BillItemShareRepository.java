package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.BillItemShare;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillItemShareRepository extends JpaRepository<BillItemShare, UUID> {

  List<BillItemShare> findByBillItemId(UUID billItemId);

  List<BillItemShare> findByParticipantId(UUID participantId);
}
