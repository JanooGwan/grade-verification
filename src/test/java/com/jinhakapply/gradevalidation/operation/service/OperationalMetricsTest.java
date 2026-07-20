package com.jinhakapply.gradevalidation.operation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OperationalMetricsTest {

    @Test
    void returnsZeroValuesBeforeAnyRequestIsRecorded() {
        OperationalMetrics metrics = new OperationalMetrics();

        OperationalMetrics.Snapshot snapshot = metrics.snapshot();

        assertThat(snapshot.totalRequests()).isZero();
        assertThat(snapshot.errorRequests()).isZero();
        assertThat(snapshot.averageDurationMillis()).isZero();
        assertThat(snapshot.maxDurationMillis()).isZero();
        assertThat(snapshot.endpoints()).isEmpty();
    }

    @Test
    void aggregatesRequestsAndNormalizesNumericPathSegments() {
        OperationalMetrics metrics = new OperationalMetrics();

        metrics.record("GET", "/api/students/10/applications/3", 200, 20);
        metrics.record("GET", "/api/students/99/applications/7", 404, 40);
        metrics.record("POST", "/api/evaluations/verify", 500, 90);

        OperationalMetrics.Snapshot snapshot = metrics.snapshot();

        assertThat(snapshot.totalRequests()).isEqualTo(3);
        assertThat(snapshot.errorRequests()).isEqualTo(2);
        assertThat(snapshot.averageDurationMillis()).isEqualTo(50);
        assertThat(snapshot.maxDurationMillis()).isEqualTo(90);
        assertThat(snapshot.endpoints()).extracting(OperationalMetrics.EndpointSnapshot::endpoint)
            .containsExactly("GET /api/students/{id}/applications/{id}", "POST /api/evaluations/verify");
        assertThat(snapshot.endpoints().getFirst().requests()).isEqualTo(2);
        assertThat(snapshot.endpoints().getFirst().errors()).isEqualTo(1);
        assertThat(snapshot.endpoints().getFirst().averageDurationMillis()).isEqualTo(30);
    }
}
