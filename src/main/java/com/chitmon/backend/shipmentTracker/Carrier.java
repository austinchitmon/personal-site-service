package com.chitmon.backend.shipmentTracker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "carriers", schema = "shipment_tracker")
public class Carrier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected Carrier() {
    }

    public Integer getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
