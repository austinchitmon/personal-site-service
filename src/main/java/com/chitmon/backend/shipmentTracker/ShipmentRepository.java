package com.chitmon.backend.shipmentTracker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    List<Shipment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Shipment> findByIdAndUserId(UUID id, UUID userId);
}
