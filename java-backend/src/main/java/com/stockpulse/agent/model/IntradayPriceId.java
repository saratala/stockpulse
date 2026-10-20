package com.stockpulse.agent.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntradayPriceId implements Serializable {
    private String ticker;
    private LocalDateTime timestamp;
}
