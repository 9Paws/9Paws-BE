package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.BillParticipant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillParticipantRepository extends JpaRepository<BillParticipant, UUID> {

  List<BillParticipant> findByBillId(UUID billId);

  Optional<BillParticipant> findByIdAndBillId(UUID id, UUID billId);
}
