package com.chitmon.backend.shipmentTracker.carrier;

import java.time.LocalDate;

public record TrackingResult(
        boolean statusKnown,
        String statusText,
        String lastLocation,
        LocalDate estimatedDelivery,
        boolean delivered) {

    public static TrackingResult of(String statusText, String lastLocation, LocalDate estimatedDelivery,
            boolean delivered) {
        return new TrackingResult(true, statusText, lastLocation, estimatedDelivery, delivered);
    }

    public static TrackingResult unavailable() {
        return new TrackingResult(false, "Unable to retrieve status", null, null, false);
    }
}
