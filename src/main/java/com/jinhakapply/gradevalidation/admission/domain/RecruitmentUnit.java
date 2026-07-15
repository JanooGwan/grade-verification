package com.jinhakapply.gradevalidation.admission.domain;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

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
@Table(name = "recruitment_unit", uniqueConstraints = {
    @UniqueConstraint(name = "uk_recruitment_unit_name", columnNames = {"admission_track_id", "name"}),
    @UniqueConstraint(name = "uk_recruitment_unit_code", columnNames = {"admission_track_id", "code"})
})
@NoArgsConstructor(access = PROTECTED)
public class RecruitmentUnit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "admission_track_id", nullable = false)
    private AdmissionTrack admissionTrack;

    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static RecruitmentUnit create(AdmissionTrack track, String code, String name) {
        RecruitmentUnit unit = new RecruitmentUnit();
        unit.admissionTrack = track;
        unit.code = clean(code);
        unit.name = name.trim();
        unit.active = true;
        unit.createdAt = LocalDateTime.now();
        unit.updatedAt = unit.createdAt;
        return unit;
    }

    public void update(String code, String name, boolean active) {
        this.code = clean(code);
        this.name = name.trim();
        this.active = active;
        this.updatedAt = LocalDateTime.now();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
