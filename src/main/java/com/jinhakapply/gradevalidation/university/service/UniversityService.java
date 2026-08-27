package com.jinhakapply.gradevalidation.university.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_UNIVERSITY_CODE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND;

import java.util.List;
import java.util.Locale;

import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.dto.CreateUniversityRequest;
import com.jinhakapply.gradevalidation.university.dto.UniversityResponse;
import com.jinhakapply.gradevalidation.university.dto.UpdateUniversityRequest;
import com.jinhakapply.gradevalidation.university.repository.UniversityDataCleanupRepository;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UniversityService {

    private final UniversityRepository universityRepository;
    private final UniversityDataCleanupRepository dataCleanupRepository;

    @Transactional
    public UniversityResponse create(CreateUniversityRequest request) {
        String normalizedCode = request.code().toUpperCase(Locale.ROOT);
        if (universityRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw CustomException.of(DUPLICATE_UNIVERSITY_CODE, normalizedCode);
        }

        University university = University.create(normalizedCode, request.name().trim());
        try {
            universityRepository.save(university);
            universityRepository.flush();
            return UniversityResponse.from(university);
        } catch (DataIntegrityViolationException exception) {
            throw CustomException.of(DUPLICATE_UNIVERSITY_CODE, normalizedCode);
        }
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
        University university = universityRepository.findByIdForUpdate(universityId)
            .orElseThrow(() -> CustomException.of(UNIVERSITY_NOT_FOUND, String.valueOf(universityId)));
        dataCleanupRepository.deleteAllByUniversityId(universityId);
        universityRepository.delete(university);
        universityRepository.flush();
    }

    private University getUniversity(Long universityId) {
        return universityRepository.findById(universityId)
            .orElseThrow(() -> CustomException.of(UNIVERSITY_NOT_FOUND, String.valueOf(universityId)));
    }
}
