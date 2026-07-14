package com.jinhakapply.gradevalidation.university.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.jinhakapply.gradevalidation.global.exception.DuplicateResourceException;
import com.jinhakapply.gradevalidation.global.exception.ResourceNotFoundException;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.dto.CreateUniversityRequest;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UniversityServiceTest {

	@Mock
	private UniversityRepository universityRepository;

	private UniversityService universityService;

	@BeforeEach
	void setUp() {
		universityService = new UniversityService(universityRepository);
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
				.isInstanceOf(DuplicateResourceException.class);
	}

	@Test
	void throwsWhenUniversityDoesNotExist() {
		when(universityRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> universityService.findById(99L))
				.isInstanceOf(ResourceNotFoundException.class);
	}
}
