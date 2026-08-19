package com.chitmon.backend.shipmentTracker;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        String trackingNumber,
        CarrierResponse carrier,
        String statusText,
        String lastLocation,
        LocalDate estimatedDelivery,
        boolean delivered,
        Instant lastCheckedAt,
        Instant createdAt) {

    static ShipmentResponse from(Shipment shipment, Carrier carrier) {
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getTrackingNumber(),
                CarrierResponse.from(carrier),
                shipment.getStatusText(),
                shipment.getLastLocation(),
                shipment.getEstimatedDelivery(),
                shipment.isDelivered(),
                shipment.getLastCheckedAt(),
                shipment.getCreatedAt());
    }
}
