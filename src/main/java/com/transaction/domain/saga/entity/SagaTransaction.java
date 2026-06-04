package com.transaction.domain.saga.entity;

import com.transaction.domain.saga.enums.SagaStatus;
import com.transaction.domain.saga.enums.SagaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saga_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "saga_id")
    private Long sagaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50)
    private SagaType transactionType;

    @Column(name = "transaction_key", nullable = false, unique = true, length = 255)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "saga_status", nullable = false, length = 30)
    private SagaStatus sagaStatus;

    @Column(name = "current_step", length = 100)
    private String currentStep;

    @Column(name = "total_steps")
    private Integer totalSteps;

    @Lob
    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "rollback_yn", nullable = false)
    private boolean rollbackYn;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static SagaTransaction create(SagaType type, String transactionKey) {
        SagaTransaction saga = new SagaTransaction();
        saga.transactionType = type;
        saga.transactionKey = transactionKey;
        saga.sagaStatus = SagaStatus.STARTED;
        saga.rollbackYn = false;
        saga.startedAt = LocalDateTime.now();
        return saga;
    }

    public void updateStatus(SagaStatus status) {
        this.sagaStatus = status;
        boolean terminal = status == SagaStatus.SUCCESS
                || status == SagaStatus.FAILED
                || status == SagaStatus.COMPENSATED
                || status == SagaStatus.UNKNOWN
                || status == SagaStatus.COMPENSATION_FAILED;
        if (terminal) {
            this.completedAt = LocalDateTime.now();
        }
        if (status == SagaStatus.COMPENSATING || status == SagaStatus.COMPENSATED) {
            this.rollbackYn = true;
        }
    }

    public void updateCurrentStep(String stepName) {
        this.currentStep = stepName;
        this.sagaStatus = SagaStatus.PROCESSING;
    }

    public void fail(SagaStatus status, String reason) {
        this.sagaStatus = status;
        this.failureReason = reason;
        this.completedAt = LocalDateTime.now();
        if (status == SagaStatus.COMPENSATING || status == SagaStatus.COMPENSATED) {
            this.rollbackYn = true;
        }
    }
}
