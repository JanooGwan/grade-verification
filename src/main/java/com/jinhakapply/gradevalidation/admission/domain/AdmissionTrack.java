package com.jinhakapply.gradevalidation.admission.domain;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.university.domain.University;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "admission_track", uniqueConstraints = @UniqueConstraint(
    name = "uk_admission_track", columnNames = {"university_id", "admission_year", "name"}
))
@NoArgsConstructor(access = PROTECTED)
public class AdmissionTrack {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @Column(name = "admission_year", nullable = false)
    private int admissionYear;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static AdmissionTrack create(University university, int admissionYear, String name) {
        AdmissionTrack track = new AdmissionTrack();
        track.university = university;
        track.admissionYear = admissionYear;
        track.name = name.trim();
        track.active = true;
        track.createdAt = LocalDateTime.now();
        track.updatedAt = track.createdAt;
        return track;
    }

    public void update(String name, boolean active) {
        this.name = name.trim();
        this.active = active;
        this.updatedAt = LocalDateTime.now();
    }
}
