package com.stockpulse.agent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredictionValidationId implements Serializable {

    @Column(name = "id")
    private Long id;

    @Column(name = "validation_timestamp", nullable = false)
    private LocalDateTime validationTimestamp;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PredictionValidationId that = (PredictionValidationId) o;
        return Objects.equals(id, that.id) && Objects.equals(validationTimestamp, that.validationTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, validationTimestamp);
    }
}
