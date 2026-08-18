package com.chitmon.backend.shipmentTracker;

import com.chitmon.backend.shipmentTracker.carrier.CarrierTrackingClient;
import com.chitmon.backend.shipmentTracker.carrier.TrackingResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    private final ShipmentRepository shipmentRepository;
    private final CarrierRepository carrierRepository;
    private final Map<String, CarrierTrackingClient> clientsByCarrierCode;
    private final Duration trackingTtl;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            CarrierRepository carrierRepository,
            List<CarrierTrackingClient> trackingClients,
            @Value("${shipment-tracker.tracking-ttl-minutes}") long trackingTtlMinutes) {
        this.shipmentRepository = shipmentRepository;
        this.carrierRepository = carrierRepository;
        this.clientsByCarrierCode = trackingClients.stream()
                .collect(Collectors.toMap(CarrierTrackingClient::supportedCarrierCode, Function.identity()));
        this.trackingTtl = Duration.ofMinutes(trackingTtlMinutes);
    }

    public ShipmentResponse createShipment(UUID userId, String trackingNumber, Integer carrierId) {
        Carrier carrier = carrierRepository.findById(carrierId)
                .orElseThrow(() -> new CarrierNotFoundException(carrierId));

        Shipment shipment = new Shipment(userId, carrierId, trackingNumber);
        try {
            shipment = shipmentRepository.save(shipment);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateShipmentException(trackingNumber);
        }

        refresh(shipment, carrier);
        shipment = shipmentRepository.save(shipment);
        return ShipmentResponse.from(shipment, carrier);
    }

    public List<ShipmentResponse> getShipmentsForUser(UUID userId) {
        List<Shipment> shipments = shipmentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return shipments.stream()
                .map(this::toResponseRefreshingIfStale)
                .toList();
    }

    private ShipmentResponse toResponseRefreshingIfStale(Shipment shipment) {
        Integer carrierId = shipment.getCarrierId();
        Carrier carrier = carrierRepository.findById(carrierId)
                .orElseThrow(() -> new IllegalStateException("Shipment references missing carrier: " + carrierId));
        if (needsRefresh(shipment)) {
            refresh(shipment, carrier);
            shipment = shipmentRepository.save(shipment);
        }
        return ShipmentResponse.from(shipment, carrier);
    }

    public void deleteShipment(UUID userId, UUID id) {
        Shipment shipment = shipmentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ShipmentNotFoundException(id));
        shipmentRepository.delete(shipment);
    }

    private boolean needsRefresh(Shipment shipment) {
        if (shipment.isDelivered()) {
            return false;
        }
        Instant lastCheckedAt = shipment.getLastCheckedAt();
        return lastCheckedAt == null || Duration.between(lastCheckedAt, Instant.now()).compareTo(trackingTtl) > 0;
    }

    // Always advances lastCheckedAt, even on failure — otherwise a broken/blocked
    // scraper gets retried on every single list request instead of once per TTL window.
    private void refresh(Shipment shipment, Carrier carrier) {
        CarrierTrackingClient client = clientsByCarrierCode.get(carrier.getCode());
        TrackingResult result;
        if (client == null) {
            log.warn("No tracking client registered for carrier code {}", carrier.getCode());
            result = TrackingResult.unavailable();
        } else {
            result = client.fetchStatus(shipment.getTrackingNumber());
        }
        shipment.applyTrackingResult(result, Instant.now());
    }
}
