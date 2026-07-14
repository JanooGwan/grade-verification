package com.jinhakapply.gradevalidation.university.controller;

import java.net.URI;
import java.util.List;

import com.jinhakapply.gradevalidation.university.dto.CreateUniversityRequest;
import com.jinhakapply.gradevalidation.university.dto.UniversityResponse;
import com.jinhakapply.gradevalidation.university.dto.UpdateUniversityRequest;
import com.jinhakapply.gradevalidation.university.service.UniversityService;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Universities", description = "대학 기준정보 API")
@RestController
@RequestMapping("/api/v1/universities")
public class UniversityController {

	private final UniversityService universityService;

	public UniversityController(UniversityService universityService) {
		this.universityService = universityService;
	}

	@Operation(summary = "대학 등록")
	@PostMapping
	public ResponseEntity<UniversityResponse> create(@Valid @RequestBody CreateUniversityRequest request) {
		UniversityResponse response = universityService.create(request);
		return ResponseEntity.created(URI.create("/api/v1/universities/" + response.id())).body(response);
	}

	@Operation(summary = "대학 목록 조회")
	@GetMapping
	public List<UniversityResponse> findAll() {
		return universityService.findAll();
	}

	@Operation(summary = "대학 단건 조회")
	@GetMapping("/{universityId}")
	public UniversityResponse findById(@PathVariable Long universityId) {
		return universityService.findById(universityId);
	}

	@Operation(summary = "대학 수정")
	@PutMapping("/{universityId}")
	public UniversityResponse update(
			@PathVariable Long universityId,
			@Valid @RequestBody UpdateUniversityRequest request
	) {
		return universityService.update(universityId, request);
	}

	@Operation(summary = "대학 삭제")
	@DeleteMapping("/{universityId}")
	public ResponseEntity<Void> delete(@PathVariable Long universityId) {
		universityService.delete(universityId);
		return ResponseEntity.noContent().build();
	}
}
