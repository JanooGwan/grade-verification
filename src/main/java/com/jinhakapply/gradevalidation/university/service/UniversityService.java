package com.jinhakapply.gradevalidation.university.service;

import java.util.List;
import java.util.Locale;

import com.jinhakapply.gradevalidation.global.exception.DuplicateResourceException;
import com.jinhakapply.gradevalidation.global.exception.ResourceNotFoundException;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.dto.CreateUniversityRequest;
import com.jinhakapply.gradevalidation.university.dto.UniversityResponse;
import com.jinhakapply.gradevalidation.university.dto.UpdateUniversityRequest;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UniversityService {

	private final UniversityRepository universityRepository;

	public UniversityService(UniversityRepository universityRepository) {
		this.universityRepository = universityRepository;
	}

	@Transactional
	public UniversityResponse create(CreateUniversityRequest request) {
		String normalizedCode = request.code().toUpperCase(Locale.ROOT);
		if (universityRepository.existsByCodeIgnoreCase(normalizedCode)) {
			throw new DuplicateResourceException("이미 등록된 대학 코드입니다: " + normalizedCode);
		}

		University university = University.create(normalizedCode, request.name().trim());
		return UniversityResponse.from(universityRepository.save(university));
	}

	public List<UniversityResponse> findAll() {
		return universityRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
				.map(UniversityResponse::from)
				.toList();
	}

	public UniversityResponse findById(Long universityId) {
		return UniversityResponse.from(getUniversity(universityId));
	}

	@Transactional
	public UniversityResponse update(Long universityId, UpdateUniversityRequest request) {
		University university = getUniversity(universityId);
		university.update(request.name().trim(), request.active());
		return UniversityResponse.from(university);
	}

	@Transactional
	public void delete(Long universityId) {
		University university = getUniversity(universityId);
		universityRepository.delete(university);
	}

	private University getUniversity(Long universityId) {
		return universityRepository.findById(universityId)
				.orElseThrow(() -> new ResourceNotFoundException("대학을 찾을 수 없습니다: " + universityId));
	}
}
