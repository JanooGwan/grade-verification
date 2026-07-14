package com.jinhakapply.gradevalidation.university.controller;

import java.net.URI;
import java.util.List;

import com.jinhakapply.gradevalidation.university.dto.CreateUniversityRequest;
import com.jinhakapply.gradevalidation.university.dto.UniversityResponse;
import com.jinhakapply.gradevalidation.university.dto.UpdateUniversityRequest;
import com.jinhakapply.gradevalidation.university.service.UniversityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UniversityController implements UniversityApi {

    private final UniversityService universityService;

    @Override
    public ResponseEntity<UniversityResponse> create(CreateUniversityRequest request) {
        UniversityResponse response = universityService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/universities/" + response.id())).body(response);
    }

    @Override
    public List<UniversityResponse> findAll() {
        return universityService.findAll();
    }

    @Override
    public UniversityResponse findById(Long universityId) {
        return universityService.findById(universityId);
    }

    @Override
    public UniversityResponse update(Long universityId, UpdateUniversityRequest request) {
        return universityService.update(universityId, request);
    }

    @Override
    public ResponseEntity<Void> delete(Long universityId) {
        universityService.delete(universityId);
        return ResponseEntity.noContent().build();
    }
}
