package com.chitmon.backend.shipmentTracker;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateShipmentException extends RuntimeException {

    public DuplicateShipmentException(String trackingNumber) {
        super("Already tracking this shipment: " + trackingNumber);
    }
}
