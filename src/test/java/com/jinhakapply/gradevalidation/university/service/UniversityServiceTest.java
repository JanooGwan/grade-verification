package com.jinhakapply.gradevalidation.university.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_UNIVERSITY_CODE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;

import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.dto.CreateUniversityRequest;
import com.jinhakapply.gradevalidation.university.repository.UniversityDataCleanupRepository;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UniversityServiceTest {

    @Mock
    private UniversityRepository universityRepository;

    @Mock
    private UniversityDataCleanupRepository dataCleanupRepository;

    private UniversityService universityService;

    @BeforeEach
    void setUp() {
        universityService = new UniversityService(universityRepository, dataCleanupRepository);
    }

    @Test
    void createsUniversityWithNormalizedCode() {
        when(universityRepository.existsByCodeIgnoreCase("MJC")).thenReturn(false);
        when(universityRepository.save(any(University.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = universityService.create(new CreateUniversityRequest("mjc", "명지전문대학교"));

        assertThat(response.code()).isEqualTo("MJC");
        assertThat(response.name()).isEqualTo("명지전문대학교");
        assertThat(response.active()).isTrue();
        verify(universityRepository).save(any(University.class));
    }

    @Test
    void rejectsDuplicateUniversityCode() {
        when(universityRepository.existsByCodeIgnoreCase("MJC")).thenReturn(true);

        assertThatThrownBy(() ->
            universityService.create(new CreateUniversityRequest("MJC", "명지전문대학교")))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(DUPLICATE_UNIVERSITY_CODE));
    }

    @Test
    void mapsUniqueConstraintFailureToDuplicateCode() {
        when(universityRepository.existsByCodeIgnoreCase("MJC")).thenReturn(false);
        when(universityRepository.save(any(University.class)))
            .thenThrow(new DataIntegrityViolationException("uk_university_code"));

        assertThatThrownBy(() ->
            universityService.create(new CreateUniversityRequest("MJC", "명지전문대학교")))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(DUPLICATE_UNIVERSITY_CODE));
    }

    @Test
    void throwsWhenUniversityDoesNotExist() {
        when(universityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> universityService.findById(99L))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(UNIVERSITY_NOT_FOUND));
    }

    @Test
    void findsUniversitiesSortedByName() {
        University first = university(1L, "AAA", "가 대학교");
        University second = university(2L, "BBB", "나 대학교");
        Sort sort = Sort.by(Sort.Direction.ASC, "name");
        when(universityRepository.findAll(sort)).thenReturn(List.of(first, second));

        var response = universityService.findAll();

        assertThat(response).extracting(item -> item.name())
            .containsExactly("가 대학교", "나 대학교");
    }

    @Test
    void updatesUniversityNameAndActiveState() {
        University university = university(1L, "TUK", "기존 이름");
        when(universityRepository.findById(1L)).thenReturn(Optional.of(university));

        var response = universityService.update(
            1L, new com.jinhakapply.gradevalidation.university.dto.UpdateUniversityRequest(" 변경 이름 ", false)
        );

        assertThat(response.name()).isEqualTo("변경 이름");
        assertThat(response.active()).isFalse();
    }

    @Test
    void deletesExistingUniversity() {
        University university = university(1L, "TUK", "한국공학대학교");
        when(universityRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(university));

        universityService.delete(1L);

        verify(dataCleanupRepository).deleteAllByUniversityId(1L);
        verify(universityRepository).delete(university);
        verify(universityRepository).flush();
    }

    @Test
    void doesNotCleanUpDataWhenUniversityDoesNotExist() {
        when(universityRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> universityService.delete(99L))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(UNIVERSITY_NOT_FOUND));

        org.mockito.Mockito.verifyNoInteractions(dataCleanupRepository);
    }

    private University university(Long id, String code, String name) {
        University university = University.create(code, name);
        ReflectionTestUtils.setField(university, "id", id);
        return university;
    }
}
