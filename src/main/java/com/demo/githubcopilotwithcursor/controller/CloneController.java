package com.demo.githubcopilotwithcursor.controller;

import com.demo.githubcopilotwithcursor.dto.CloneRequest;
import com.demo.githubcopilotwithcursor.dto.CloneResponse;
import com.demo.githubcopilotwithcursor.service.CloneService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CloneController {

    private final CloneService cloneService;

    public CloneController(CloneService cloneService) {
        this.cloneService = cloneService;
    }

    @PostMapping("/clone")
    public ResponseEntity<CloneResponse> clone(@Valid @RequestBody CloneRequest request) {
        return ResponseEntity.ok(cloneService.cloneRepository(request));
    }
}
