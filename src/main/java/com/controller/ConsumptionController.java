package com.controller;

import com.service.ConsumptionService;
import com.model.LoginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/consumption")
public class ConsumptionController {

    @Autowired
    private ConsumptionService consumptionService;

    @PostMapping("/fetch")
    public ResponseEntity<String> fetchAndSave(@RequestBody LoginRequest request,
                                               @RequestParam(defaultValue = "01/01/2025") String fromDate,
                                               @RequestParam(defaultValue = "11/07/2025") String toDate) {
        String result = consumptionService.fetchAndSaveConsumption(
                request.getEmail(),
                request.getPassword(),
                fromDate,
                toDate
        );
        return ResponseEntity.ok(result);
    }
}
