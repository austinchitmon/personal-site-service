package com.chitmon.backend.shipmentTracker;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping("/shipment-tracker")
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentResponse createShipment(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ShipmentCreateRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return shipmentService.createShipment(userId, request.trackingNumber(), request.carrierId());
    }

    @GetMapping("/shipment-tracker")
    public List<ShipmentResponse> getShipments(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return shipmentService.getShipmentsForUser(userId);
    }

    @DeleteMapping("/shipment-tracker/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShipment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        shipmentService.deleteShipment(userId, id);
    }
}
