package com.jinhakapply.gradevalidation.transcript.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
final class TransactionObservation {
    private TransactionObservation() { }

    static void register(String workflow, String id) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            private long started;
            @Override public void beforeCommit(boolean readOnly) {
                started = System.nanoTime();
                log.info("workflow_transaction_start workflow={} id={} readOnly={}", workflow, id, readOnly);
            }
            @Override public void afterCompletion(int status) {
                Long elapsed = started == 0 ? null : (System.nanoTime() - started) / 1_000_000;
                log.info("workflow_transaction_end workflow={} id={} outcome={} beforeCommitToCompletionMs={}",
                    workflow, id, status == STATUS_COMMITTED ? "COMMITTED" : status == STATUS_ROLLED_BACK ? "ROLLED_BACK" : "UNKNOWN", elapsed);
            }
        });
    }
}
