package com.chitmon.backend.shipmentTracker;

public record CarrierResponse(Integer id, String code, String displayName) {

    static CarrierResponse from(Carrier carrier) {
        return new CarrierResponse(carrier.getId(), carrier.getCode(), carrier.getDisplayName());
    }
}
