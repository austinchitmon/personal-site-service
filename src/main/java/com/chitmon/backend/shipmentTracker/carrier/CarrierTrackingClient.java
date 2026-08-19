package com.chitmon.backend.shipmentTracker.carrier;

public interface CarrierTrackingClient {

    String supportedCarrierCode();

    TrackingResult fetchStatus(String trackingNumber);
}
