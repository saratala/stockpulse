package com.stockpulse.agent.service;

import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.repository.StockPriceRepository;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwingBacktestServiceTest {

    private static final LocalDate START = LocalDate.of(2025, 1, 2);
    private static final double SLIP = 0.0005;
    private static final Offset<Double> EPS = Offset.offset(1e-3);

    private final StockPriceRepository repository = mock(StockPriceRepository.class);
    private final SwingBacktestService service = new SwingBacktestService(repository);

    // ---- synthetic data helpers ----

    /** n trading days (weekdays) starting at START. */
    private static List<LocalDate> tradingDates(int n) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = START; dates.size() < n; d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) dates.add(d);
        }
        return dates;
    }

    /** Flat series: open/close 100, high 101, low 99. Each bar is {open, high, low, close}. */
    private static List<double[]> flat(int n) {
        List<double[]> bars = new ArrayList<>();
        for (int i = 0; i < n; i++) bars.add(new double[]{100, 101, 99, 100});
        return bars;
    }

    /** Deterministic random walk with an upward drift, enough to make every strategy trade. */
    private static List<double[]> randomWalk(int n, long seed) {
        Random rnd = new Random(seed);
        List<double[]> bars = new ArrayList<>();
        double close = 100;
        for (int i = 0; i < n; i++) {
            double open = close * (1 + rnd.nextGaussian() * 0.005);
            close = open * (1 + 0.0008 + rnd.nextGaussian() * 0.015);
            double high = Math.max(open, close) * (1 + Math.abs(rnd.nextGaussian()) * 0.005);
            double low = Math.min(open, close) * (1 - Math.abs(rnd.nextGaussian()) * 0.005);
            bars.add(new double[]{open, high, low, close});
        }
        return bars;
    }

    /** Entity timestamp for a UTC wall-clock time, as Hibernate would load it in this JVM's zone. */
    private static LocalDateTime utc(LocalDate date, LocalTime time) {
        return LocalDateTime.ofInstant(date.atTime(time).toInstant(ZoneOffset.UTC), ZoneId.systemDefault());
    }

    private static StockPrice row(LocalDateTime ts, double[] b) {
        return StockPrice.builder()
            .ticker("TEST").date(ts)
            .open(BigDecimal.valueOf(b[0])).high(BigDecimal.valueOf(b[1]))
            .low(BigDecimal.valueOf(b[2])).close(BigDecimal.valueOf(b[3]))
            .volume(1_000_000L)
            .build();
    }

    private static List<StockPrice> dailyRows(List<double[]> bars) {
        List<LocalDate> dates = tradingDates(bars.size());
        List<StockPrice> rows = new ArrayList<>();
        for (int i = 0; i < bars.size(); i++) rows.add(row(utc(dates.get(i), LocalTime.of(9, 30)), bars.get(i)));
        return rows;
    }

    private void given(List<StockPrice> rows) {
        when(repository.findDailyBarsByTicker("TEST")).thenReturn(rows);
    }

    private Map<String, Object> run(String strategy, Map<String, Double> params) {
        return service.runSwingBacktest("TEST", BigDecimal.valueOf(10000), strategy, params);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> trades(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("trades");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> result, String key) {
        return (Map<String, Object>) result.get(key);
    }

    private static double num(Map<String, Object> m, String key) {
        return ((Number) m.get(key)).doubleValue();
    }

    private static String day(int index) {
        return tradingDates(index + 1).get(index).toString();
    }

    /** Flat history, a breakout close on day 30, holding near 106 from day 31. */
    private static List<double[]> breakoutSeries() {
        List<double[]> bars = flat(60);
        bars.set(30, new double[]{100, 105.5, 99.5, 105});            // close > prior 20-day high (101)
        for (int i = 31; i < 60; i++) bars.set(i, new double[]{106, 106.5, 105.5, 106});
        return bars;
    }

    // ---- tests ----

    @Test
    void entersAndExitsAtNextDayOpen() {
        List<double[]> bars = breakoutSeries();
        bars.set(35, new double[]{106, 106.5, 94.5, 95});             // close < prior 10-day low -> exit signal
        for (int i = 36; i < 60; i++) bars.set(i, new double[]{94, 94.5, 93.5, 94});
        given(dailyRows(bars));

        Map<String, Object> result = run("donchian", Map.of("warmupDays", 0.0, "stopAtrMultiple", 0.0));
        List<Map<String, Object>> trades = trades(result);

        assertThat(trades).hasSize(1);
        Map<String, Object> t = trades.get(0);
        double entry = 106 * (1 + SLIP);
        double exit = 94 * (1 - SLIP);
        assertThat(t.get("entryDate")).isEqualTo(day(31));             // not the signal day
        assertThat(num(t, "entryPrice")).isCloseTo(entry, EPS);
        assertThat(t.get("exitDate")).isEqualTo(day(36));
        assertThat(num(t, "exitPrice")).isCloseTo(exit, EPS);
        assertThat(t.get("exitReason")).isEqualTo("SIGNAL_EXIT");
        assertThat(num(t, "holdingDays")).isEqualTo(5);
        long shares = (long) Math.floor(10000 / entry);
        assertThat(num(t, "shares")).isEqualTo(shares);
        assertThat(num(t, "pnl")).isCloseTo((exit - entry) * shares, Offset.offset(0.01));
        assertThat(section(result, "testPeriod").get("start")).isEqualTo(day(20));
    }

    @Test
    void stopGapFillsAtTheOpen() {
        List<double[]> bars = breakoutSeries();
        bars.set(33, new double[]{90, 91, 89, 90});                   // gaps far below entry - 2*ATR
        for (int i = 34; i < 60; i++) bars.set(i, new double[]{90, 91, 89, 90});
        given(dailyRows(bars));

        Map<String, Object> t = trades(run("donchian", Map.of("warmupDays", 0.0))).get(0);

        assertThat(t.get("exitReason")).isEqualTo("STOP_LOSS");
        assertThat(t.get("exitDate")).isEqualTo(day(33));
        assertThat(num(t, "exitPrice")).isCloseTo(90 * (1 - SLIP), EPS);   // the open, not the stop level
    }

    @Test
    void stopCanTriggerOnTheEntryDayLow() {
        List<double[]> bars = breakoutSeries();
        bars.set(31, new double[]{106, 106.5, 100, 101});              // opens at 106, trades down through the stop
        given(dailyRows(bars));

        Map<String, Object> result = run("donchian", Map.of("warmupDays", 0.0));
        Map<String, Object> t = trades(result).get(0);

        // Wilder ATR(14) on day 30: flat ranges of 2, then a true range of 6 (105.5 - 99.5)
        double atr = (13 * 2 + 6.0) / 14;
        double entry = 106 * (1 + SLIP);
        double stop = entry - 2 * atr;
        assertThat(t.get("entryDate")).isEqualTo(day(31));
        assertThat(t.get("exitDate")).isEqualTo(day(31));
        assertThat(num(t, "holdingDays")).isEqualTo(0);
        assertThat(num(t, "exitPrice")).isCloseTo(stop * (1 - SLIP), EPS);
    }

    @Test
    void changingAFutureBarNeverChangesEarlierTrades() {
        List<double[]> base = randomWalk(500, 7);
        for (String strategy : List.of("trend", "donchian", "rsi2_pullback")) {
            given(dailyRows(base));
            List<Map<String, Object>> baseline = new ArrayList<>(trades(run(strategy, Map.of())));
            assertThat(baseline).as(strategy + " should trade").isNotEmpty();

            for (int k : new int[]{260, 330, 400, 470}) {
                List<double[]> changed = new ArrayList<>(base);
                double[] b = base.get(k);
                changed.set(k, new double[]{b[0] * 0.7, b[1] * 1.4, b[2] * 0.6, b[3] * 1.3});
                given(dailyRows(changed));
                List<Map<String, Object>> after = trades(run(strategy, Map.of()));

                String cut = day(k);
                List<Map<String, Object>> before = baseline.stream()
                    .filter(t -> ((String) t.get("exitDate")).compareTo(cut) < 0).toList();
                List<Map<String, Object>> afterBefore = after.stream()
                    .filter(t -> ((String) t.get("exitDate")).compareTo(cut) < 0).toList();
                assertThat(afterBefore).as(strategy + " trades closed before " + cut).isEqualTo(before);
            }
        }
    }

    @Test
    void ignoresIntradaySnapshotRows() {
        List<double[]> bars = randomWalk(300, 3);
        List<StockPrice> clean = dailyRows(bars);
        List<StockPrice> junk = new ArrayList<>(clean);
        List<LocalDate> dates = tradingDates(bars.size());
        for (int i = 0; i < dates.size(); i += 3) {
            for (LocalTime t = LocalTime.of(13, 30); t.isBefore(LocalTime.of(21, 0)); t = t.plusMinutes(5)) {
                double p = 1 + (i % 7) * 50;                           // wild flat snapshot prices
                junk.add(row(utc(dates.get(i), t), new double[]{p, p, p, p}));
            }
        }
        // A date with only snapshot rows must not become a bar
        LocalDate extra = dates.get(dates.size() - 1).plusDays(1);
        junk.add(row(utc(extra, LocalTime.of(16, 0)), new double[]{5, 5, 5, 5}));
        junk.sort(Comparator.comparing(StockPrice::getDate));

        for (String strategy : SwingBacktestService.STRATEGIES) {
            given(clean);
            Map<String, Object> expected = run(strategy, Map.of());
            given(junk);
            Map<String, Object> actual = run(strategy, Map.of());

            assertThat(actual).as(strategy).isEqualTo(expected);
            assertThat(section(actual, "testPeriod").get("tradingDays")).isEqualTo(100);
        }
    }

    @Test
    void buyHoldPaysCostsOnceAndStaysOpen() {
        List<double[]> bars = randomWalk(300, 11);
        given(dailyRows(bars));

        Map<String, Object> result = run("buy_hold", Map.of());

        assertThat(trades(result)).isEmpty();
        Map<String, Object> open = section(result, "openPosition");
        assertThat(open.get("entryDate")).isEqualTo(day(200));
        double entry = bars.get(200)[0] * (1 + SLIP);
        assertThat(num(open, "entryPrice")).isCloseTo(entry, EPS);
        long shares = (long) Math.floor(10000 / entry);
        double finalCapital = 10000 - shares * entry + shares * bars.get(299)[3];
        assertThat(num(section(result, "performance"), "finalCapital")).isCloseTo(finalCapital, Offset.offset(0.01));
        assertThat(section(result, "performance").get("exposurePct")).isEqualTo("100.0%");
    }

    @Test
    void isDeterministic() {
        given(dailyRows(randomWalk(400, 5)));

        for (String strategy : SwingBacktestService.STRATEGIES) {
            assertThat(run(strategy, null)).isEqualTo(run(strategy, null));
        }
    }

    @Test
    void flagsTooFewTradesAsInsufficientData() {
        given(dailyRows(breakoutSeries()));

        Map<String, Object> result = run("donchian", Map.of("warmupDays", 0.0));

        assertThat((String) result.get("recommendation")).startsWith("INSUFFICIENT DATA");
        assertThat(result).containsKey("warning");
    }

    @Test
    void reportsInsufficientHistory() {
        given(dailyRows(flat(210)));                                  // 200 warmup + 10 < 200 + 20
        assertThat(run("trend", null)).containsKey("error");

        given(List.of());
        assertThat(run("rsi2_pullback", null)).containsKey("error");

        given(dailyRows(flat(300)));
        assertThat(run("nonsense", null)).containsKey("error");
    }
}
