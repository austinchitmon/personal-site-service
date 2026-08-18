package com.chitmon.backend.shipmentTracker;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CarrierController {

    private final CarrierRepository carrierRepository;

    public CarrierController(CarrierRepository carrierRepository) {
        this.carrierRepository = carrierRepository;
    }

    @GetMapping("/carriers")
    public List<CarrierResponse> getCarriers() {
        return carrierRepository.findAll().stream()
                .map(CarrierResponse::from)
                .toList();
    }
}
