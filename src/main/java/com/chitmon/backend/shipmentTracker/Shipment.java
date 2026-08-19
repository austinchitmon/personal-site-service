package com.chitmon.backend.shipmentTracker;

import com.chitmon.backend.shipmentTracker.carrier.TrackingResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "shipments", schema = "shipment_tracker")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "carrier_id", nullable = false)
    private Integer carrierId;

    @Column(name = "tracking_number", nullable = false)
    private String trackingNumber;

    @Column(name = "status_text")
    private String statusText;

    @Column(name = "last_location")
    private String lastLocation;

    @Column(name = "estimated_delivery")
    private LocalDate estimatedDelivery;

    @Column(nullable = false)
    private boolean delivered;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Shipment() {
    }

    Shipment(UUID userId, Integer carrierId, String trackingNumber) {
        this.userId = userId;
        this.carrierId = carrierId;
        this.trackingNumber = trackingNumber;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Integer getCarrierId() {
        return carrierId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastLocation() {
        return lastLocation;
    }

    public LocalDate getEstimatedDelivery() {
        return estimatedDelivery;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void applyTrackingResult(TrackingResult result, Instant now) {
        if (result.statusKnown()) {
            this.statusText = result.statusText();
            this.lastLocation = result.lastLocation();
            this.estimatedDelivery = result.estimatedDelivery();
            if (result.delivered() && !this.delivered) {
                this.delivered = true;
                this.deliveredAt = now;
            }
        }
        this.lastCheckedAt = now;
        this.updatedAt = now;
    }
}
