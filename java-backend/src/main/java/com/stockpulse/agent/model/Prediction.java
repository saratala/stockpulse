package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "predictions", indexes = {
    @Index(name = "idx_predictions_ticker_dates", columnList = "ticker, prediction_date, target_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prediction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 10)
    private String ticker;
    
    @Column(name = "prediction_date", nullable = false)
    private LocalDateTime predictionDate;
    
    @Column(name = "target_date", nullable = false)
    private LocalDateTime targetDate;
    
    @Column(name = "predicted_movement_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal predictedMovementPercent;
    
    @Column(name = "predicted_direction", nullable = false)
    private Integer predictedDirection; // -1, 0, 1
    
    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 3)
    private BigDecimal confidenceScore;
    
    @Column(name = "model_version", nullable = false, columnDefinition = "TEXT")
    private String modelVersion;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}