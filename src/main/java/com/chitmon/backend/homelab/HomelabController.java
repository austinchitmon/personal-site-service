package com.chitmon.backend.homelab;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomelabController {
    private final HomelabStatusService status;

    public HomelabController(HomelabStatusService status) {
        this.status = status;
    }

    @GetMapping("/admin/homelab")
    public ResponseEntity<HomelabStatusService.Status> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(status.status());
    }
}
