package com.careerradar.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One collector execution against one source, used for operational visibility.
 */
@Entity
@Table(name = "source_runs")
public class SourceRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SourceRunStatus status = SourceRunStatus.RUNNING;

    @Column(name = "items_found", nullable = false)
    private int itemsFound;

    @Column(name = "items_created", nullable = false)
    private int itemsCreated;

    @Column(name = "items_updated", nullable = false)
    private int itemsUpdated;

    @Column(name = "items_skipped", nullable = false)
    private int itemsSkipped;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected SourceRun() {
    }

    public SourceRun(Source source) {
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public SourceRunStatus getStatus() {
        return status;
    }

    public void setStatus(SourceRunStatus status) {
        this.status = status;
    }

    public int getItemsFound() {
        return itemsFound;
    }

    public void setItemsFound(int itemsFound) {
        this.itemsFound = itemsFound;
    }

    public int getItemsCreated() {
        return itemsCreated;
    }

    public void setItemsCreated(int itemsCreated) {
        this.itemsCreated = itemsCreated;
    }

    public int getItemsUpdated() {
        return itemsUpdated;
    }

    public void setItemsUpdated(int itemsUpdated) {
        this.itemsUpdated = itemsUpdated;
    }

    public int getItemsSkipped() {
        return itemsSkipped;
    }

    public void setItemsSkipped(int itemsSkipped) {
        this.itemsSkipped = itemsSkipped;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
