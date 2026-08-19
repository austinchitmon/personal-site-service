package com.chitmon.backend.shipmentTracker;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierRepository extends JpaRepository<Carrier, Integer> {
}
