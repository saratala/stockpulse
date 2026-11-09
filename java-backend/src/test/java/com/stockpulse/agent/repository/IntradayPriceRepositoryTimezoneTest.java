package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.IntradayPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IntradayPrice.timestamp is US/Eastern wall-clock time in Java, but the column is
 * TIMESTAMPTZ. Saving a bar must store the real instant, and reading it back must
 * return the same Eastern wall-clock time. The production JVM runs in UTC, which is
 * what made ET-as-UTC bugs invisible from the Java side.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IntradayPriceRepositoryTimezoneTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    IntradayPriceRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestEntityManager entityManager;

    @BeforeEach
    void createTable() {
        jdbc.execute("DROP TABLE IF EXISTS stock_prices_intraday");
        jdbc.execute("""
                CREATE TABLE stock_prices_intraday (
                    ticker TEXT NOT NULL,
                    timestamp TIMESTAMPTZ NOT NULL,
                    open DOUBLE PRECISION NOT NULL,
                    high DOUBLE PRECISION NOT NULL,
                    low DOUBLE PRECISION NOT NULL,
                    close DOUBLE PRECISION NOT NULL,
                    volume BIGINT NOT NULL,
                    PRIMARY KEY (ticker, timestamp))
                """);
    }

    private static IntradayPrice bar(LocalDateTime easternTime) {
        return IntradayPrice.builder()
                .ticker("QCOM")
                .timestamp(easternTime)
                .open(new BigDecimal("184.00"))
                .high(new BigDecimal("184.50"))
                .low(new BigDecimal("183.50"))
                .close(new BigDecimal("184.19"))
                .volume(1000L)
                .build();
    }

    private Instant storedInstant() {
        return jdbc.queryForObject("SELECT timestamp FROM stock_prices_intraday", OffsetDateTime.class)
                .toInstant();
    }

    @Test
    void savesEasternWallClockAsTheRealInstantDuringDaylightTime() {
        repository.saveAndFlush(bar(LocalDateTime.of(2026, 10, 27, 9, 30)));

        // 09:30 EDT (UTC-4) is 13:30 UTC
        assertThat(storedInstant()).isEqualTo(Instant.parse("2026-10-27T13:30:00Z"));
    }

    @Test
    void writesEveryValueToItsOwnColumn() {
        repository.saveAndFlush(bar(LocalDateTime.of(2026, 10, 27, 9, 30)));

        assertThat(jdbc.queryForList(
                "SELECT ticker, open, high, low, close, volume FROM stock_prices_intraday"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("ticker")).isEqualTo("QCOM");
                    assertThat(row.get("open")).isEqualTo(184.00);
                    assertThat(row.get("high")).isEqualTo(184.50);
                    assertThat(row.get("low")).isEqualTo(183.50);
                    assertThat(row.get("close")).isEqualTo(184.19);
                    assertThat(row.get("volume")).isEqualTo(1000L);
                });
    }

    @Test
    void savesEasternWallClockAsTheRealInstantDuringStandardTime() {
        repository.saveAll(List.of(bar(LocalDateTime.of(2026, 11, 6, 15, 59))));
        repository.flush();

        // 15:59 EST (UTC-5) is 20:59 UTC
        assertThat(storedInstant()).isEqualTo(Instant.parse("2026-11-06T20:59:00Z"));
    }

    @Test
    void readsBackTheSameEasternWallClockTime() {
        LocalDateTime open = LocalDateTime.of(2026, 11, 6, 9, 30);
        repository.saveAndFlush(bar(open));
        entityManager.clear();

        assertThat(repository.existsByTickerAndTimestamp("QCOM", open)).isTrue();
        assertThat(repository.findAll()).singleElement()
                .extracting(IntradayPrice::getTimestamp)
                .isEqualTo(open);
    }
}
