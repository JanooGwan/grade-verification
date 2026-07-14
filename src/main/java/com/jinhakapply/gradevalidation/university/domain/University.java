package com.jinhakapply.gradevalidation.university.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "university",
		uniqueConstraints = @UniqueConstraint(name = "uk_university_code", columnNames = "code")
)
public class University {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected University() {
	}

	private University(String code, String name) {
		this.code = code;
		this.name = name;
		this.active = true;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static University create(String code, String name) {
		return new University(code, name);
	}

	public void update(String name, boolean active) {
		this.name = name;
		this.active = active;
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
