package com.chitmon.backend.shipmentTracker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShipmentCreateRequest(
        @NotBlank String trackingNumber,
        @NotNull Integer carrierId) {
}
