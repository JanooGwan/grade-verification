package com.jinhakapply.gradevalidation.university.controller;

import java.util.List;

import com.jinhakapply.gradevalidation.university.dto.CreateUniversityRequest;
import com.jinhakapply.gradevalidation.university.dto.UniversityResponse;
import com.jinhakapply.gradevalidation.university.dto.UpdateUniversityRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Universities", description = "대학교 정보 API")
@RequestMapping("/api/v1/universities")
public interface UniversityApi {

    @Operation(summary = "대학교 등록")
    @PostMapping
    ResponseEntity<UniversityResponse> create(@Valid @RequestBody CreateUniversityRequest request);

    @Operation(summary = "대학교 목록 조회")
    @GetMapping
    List<UniversityResponse> findAll();

    @Operation(summary = "대학교 단건 조회")
    @GetMapping("/{universityId}")
    UniversityResponse findById(@PathVariable Long universityId);

    @Operation(summary = "대학교 수정")
    @PutMapping("/{universityId}")
    UniversityResponse update(
        @PathVariable Long universityId,
        @Valid @RequestBody UpdateUniversityRequest request
    );

    @Operation(summary = "대학교 삭제")
    @DeleteMapping("/{universityId}")
    ResponseEntity<Void> delete(@PathVariable Long universityId);
}
