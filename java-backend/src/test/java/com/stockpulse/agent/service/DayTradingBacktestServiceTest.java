package com.stockpulse.agent.service;

import com.stockpulse.agent.model.IntradayPrice;
import com.stockpulse.agent.repository.IntradayPriceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DayTradingBacktestServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 16);
    private static final double SLIP = 0.0005;

    private final IntradayPriceRepository repository = mock(IntradayPriceRepository.class);
    private final DayTradingBacktestService service = new DayTradingBacktestService(repository);

    /** A flat session at 100 (opening range 99.95-100.05) with per-minute overrides. */
    private static Map<LocalTime, double[]> flatDay() {
        Map<LocalTime, double[]> bars = new TreeMap<>();
        for (LocalTime t = LocalTime.of(9, 30); t.isBefore(LocalTime.of(16, 0)); t = t.plusMinutes(1)) {
            bars.put(t, new double[]{100, 100.05, 99.95, 100, 1000});
        }
        return bars;
    }

    /** Keep the price at {@code level} from {@code from} onward, inside a narrow range. */
    private static void holdFrom(Map<LocalTime, double[]> bars, LocalTime from, double level) {
        bars.replaceAll((t, b) -> t.isBefore(from) ? b : new double[]{level, level + 0.02, level - 0.02, level, 1000});
    }

    private void givenBars(Map<LocalTime, double[]> bars) {
        List<IntradayPrice> rows = new ArrayList<>();
        bars.forEach((t, b) -> rows.add(IntradayPrice.builder()
            .ticker("TEST")
            .timestamp(LocalDateTime.of(DAY, t))
            .open(BigDecimal.valueOf(b[0])).high(BigDecimal.valueOf(b[1]))
            .low(BigDecimal.valueOf(b[2])).close(BigDecimal.valueOf(b[3]))
            .volume((long) b[4])
            .build()));
        when(repository.findByTickerAndTimestampBetween(eq("TEST"), any(), any())).thenReturn(rows);
    }

    private Map<String, Object> run(Map<String, Double> params) {
        return service.runDayTradingBacktest("TEST", BigDecimal.valueOf(10000), "breakout", params);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> trades(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("recentTrades");
    }

    private static double num(Map<String, Object> trade, String key) {
        return ((Number) trade.get(key)).doubleValue();
    }

    @Test
    void entersAtNextBarOpenAndExitsAtTarget() {
        Map<LocalTime, double[]> bars = flatDay();
        holdFrom(bars, LocalTime.of(10, 1), 100.6);
        bars.put(LocalTime.of(10, 0), new double[]{100, 100.55, 100, 100.5, 5000});   // breakout close
        bars.put(LocalTime.of(10, 1), new double[]{100.6, 100.62, 100.58, 100.6, 1000});
        bars.put(LocalTime.of(10, 5), new double[]{100.6, 102, 100.58, 101.9, 1000});  // runs through target
        givenBars(bars);

        List<Map<String, Object>> trades = trades(run(null));

        assertThat(trades).hasSize(1);
        Map<String, Object> trade = trades.get(0);
        double entry = 100.6 * (1 + SLIP);
        assertThat(trade.get("direction")).isEqualTo("LONG");
        assertThat(trade.get("entryTime")).isEqualTo("2026-10-16 10:01");   // not the signal bar
        assertThat(num(trade, "entryPrice")).isCloseTo(entry, org.assertj.core.data.Offset.offset(1e-3));
        assertThat(trade.get("exitReason")).isEqualTo("TAKE_PROFIT");
        assertThat(trade.get("exitTime")).isEqualTo("2026-10-16 10:05");
        assertThat(num(trade, "exitPrice")).isCloseTo(entry * 1.01 * (1 - SLIP), org.assertj.core.data.Offset.offset(1e-3));
        assertThat(num(trade, "pnl")).isPositive();
    }

    @Test
    void shortProfitsWhenPriceFalls() {
        Map<LocalTime, double[]> bars = flatDay();
        holdFrom(bars, LocalTime.of(10, 1), 99.4);
        bars.put(LocalTime.of(10, 0), new double[]{100, 100, 99.45, 99.5, 5000});      // breakdown close
        bars.put(LocalTime.of(10, 3), new double[]{99.4, 99.42, 98, 98.1, 1000});      // falls through target
        givenBars(bars);

        Map<String, Object> trade = trades(run(null)).get(0);

        assertThat(trade.get("direction")).isEqualTo("SHORT");
        assertThat(trade.get("exitReason")).isEqualTo("TAKE_PROFIT");
        assertThat(num(trade, "pnl")).isPositive();
    }

    @Test
    void assumesStopFillsFirstWhenBarTouchesBoth() {
        Map<LocalTime, double[]> bars = flatDay();
        holdFrom(bars, LocalTime.of(10, 1), 99.4);
        bars.put(LocalTime.of(10, 0), new double[]{100, 100, 99.45, 99.5, 5000});
        bars.put(LocalTime.of(10, 3), new double[]{99.4, 100, 98, 99.4, 1000});        // touches stop and target
        givenBars(bars);

        Map<String, Object> trade = trades(run(null)).get(0);

        assertThat(trade.get("exitReason")).isEqualTo("STOP_LOSS");
        assertThat(num(trade, "pnl")).isNegative();
    }

    @Test
    void closesOpenPositionBeforeTheBell() {
        Map<LocalTime, double[]> bars = flatDay();
        holdFrom(bars, LocalTime.of(10, 1), 100.6);
        bars.put(LocalTime.of(10, 0), new double[]{100, 100.55, 100, 100.5, 5000});
        givenBars(bars);

        Map<String, Object> trade = trades(run(Map.of("maxHoldMinutes", 1000.0))).get(0);

        assertThat(trade.get("exitReason")).isEqualTo("END_OF_DAY");
        assertThat(trade.get("exitTime")).isEqualTo("2026-10-16 15:55");
    }

    @Test
    void isDeterministic() {
        Map<LocalTime, double[]> bars = flatDay();
        holdFrom(bars, LocalTime.of(10, 1), 100.6);
        bars.put(LocalTime.of(10, 0), new double[]{100, 100.55, 100, 100.5, 5000});
        givenBars(bars);

        assertThat(run(null)).isEqualTo(run(null));
    }

    @Test
    void reportsMissingIntradayData() {
        when(repository.findByTickerAndTimestampBetween(eq("TEST"), any(), any())).thenReturn(List.of());

        assertThat(run(null)).containsKey("error");
    }
}
