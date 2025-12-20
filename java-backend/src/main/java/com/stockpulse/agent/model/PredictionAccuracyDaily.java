package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "prediction_accuracy_daily")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionAccuracyDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    private String ticker; // NULL for overall metrics

    @Column(nullable = false)
    private String timeframe;

    // Count metrics
    @Column(name = "total_predictions")
    @Builder.Default
    private Integer totalPredictions = 0;

    @Column(name = "correct_direction")
    @Builder.Default
    private Integer correctDirection = 0;

    @Column(name = "within_5_percent")
    @Builder.Default
    private Integer within5Percent = 0;

    @Column(name = "within_10_percent")
    @Builder.Default
    private Integer within10Percent = 0;

    // Accuracy percentages
    @Column(name = "direction_accuracy")
    @Builder.Default
    private Double directionAccuracy = 0.0;

    @Column(name = "accuracy_5_percent")
    @Builder.Default
    private Double accuracy5Percent = 0.0;

    @Column(name = "accuracy_10_percent")
    @Builder.Default
    private Double accuracy10Percent = 0.0;

    // Error metrics
    @Column(name = "avg_error_percent")
    @Builder.Default
    private Double avgErrorPercent = 0.0;

    @Column(name = "max_error_percent")
    @Builder.Default
    private Double maxErrorPercent = 0.0;

    @Column(name = "min_error_percent")
    @Builder.Default
    private Double minErrorPercent = 0.0;

    // Confidence calibration
    @Column(name = "avg_confidence")
    @Builder.Default
    private Double avgConfidence = 0.0;

    @Column(name = "confidence_calibration")
    private Double confidenceCalibration;

    // By signal type
    @Column(name = "bullish_count")
    @Builder.Default
    private Integer bullishCount = 0;

    @Column(name = "bullish_correct")
    @Builder.Default
    private Integer bullishCorrect = 0;

    @Column(name = "bearish_count")
    @Builder.Default
    private Integer bearishCount = 0;

    @Column(name = "bearish_correct")
    @Builder.Default
    private Integer bearishCorrect = 0;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
