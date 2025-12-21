package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "prediction_validations")
@IdClass(PredictionValidationId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionValidation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Id
    @Column(name = "validation_timestamp", nullable = false)
    private LocalDateTime validationTimestamp;

    @Column(nullable = false)
    private String ticker;

    @Column(name = "prediction_timestamp", nullable = false)
    private LocalDateTime predictionTimestamp;

    @Column(nullable = false)
    private String timeframe; // '1h', '1d', '1w'

    @Column(name = "signal_type", nullable = false)
    private String signalType;

    @Column(name = "predicted_price", nullable = false)
    private Double predictedPrice;

    @Column(name = "prediction_price", nullable = false)
    private Double predictionPrice; // price when prediction was made

    @Column(nullable = false)
    private Double confidence;

    @Column(name = "actual_price", nullable = false)
    private Double actualPrice;

    @Column(name = "predicted_change_percent", nullable = false)
    private Double predictedChangePercent;

    @Column(name = "actual_change_percent", nullable = false)
    private Double actualChangePercent;

    @Column(name = "price_error", nullable = false)
    private Double priceError;

    @Column(name = "price_error_percent", nullable = false)
    private Double priceErrorPercent;

    @Column(name = "direction_correct", nullable = false)
    private Boolean directionCorrect;

    @Column(name = "within_5_percent", nullable = false)
    private Boolean within5Percent;

    @Column(name = "within_10_percent", nullable = false)
    private Boolean within10Percent;
}
