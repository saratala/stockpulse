package com.stockpulse.agent.service;

import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.model.StockPriceId;
import com.stockpulse.agent.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RealTimeDataServiceTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    // Tuesday 2026-10-20; the daily bar key is 09:30 New York local time.
    private static final LocalDateTime BAR_DATE = LocalDateTime.of(2026, 10, 20, 9, 30);
    private static final StockPriceId BAR_ID = new StockPriceId("AAPL", BAR_DATE);

    private final StockPriceRepository stockPriceRepository = mock(StockPriceRepository.class);
    private final RealTimeDataService service = new RealTimeDataService(
        mock(YahooFinanceService.class), mock(StockRepository.class), stockPriceRepository,
        mock(ScreeningResultRepository.class), mock(SignalPredictionRepository.class),
        mock(HeikinAshiSignalRepository.class));

    private static YahooFinanceService.StockQuote quote(double price, long volume) {
        return YahooFinanceService.StockQuote.builder()
            .ticker("AAPL").price(BigDecimal.valueOf(price)).volume(volume).build();
    }

    private static StockPrice bar(LocalDateTime date, double o, double h, double l, double c, long v) {
        return StockPrice.builder().ticker("AAPL").date(date)
            .open(BigDecimal.valueOf(o)).high(BigDecimal.valueOf(h)).low(BigDecimal.valueOf(l))
            .close(BigDecimal.valueOf(c)).adjustedClose(BigDecimal.valueOf(c)).volume(v).build();
    }

    private StockPrice savedBar() {
        ArgumentCaptor<StockPrice> captor = ArgumentCaptor.forClass(StockPrice.class);
        verify(stockPriceRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void updatesExistingDailyBarInsteadOfInsertingNewRow() {
        when(stockPriceRepository.findById(BAR_ID))
            .thenReturn(Optional.of(bar(BAR_DATE, 340, 345, 339, 342, 1_000_000)));

        service.updateStockPrice("AAPL", quote(347.5, 2_000_000), ZonedDateTime.of(2026, 10, 20, 11, 7, 3, 0, NY));

        StockPrice saved = savedBar();
        assertThat(saved.getDate()).isEqualTo(BAR_DATE);
        assertThat(saved.getOpen()).isEqualByComparingTo("340");
        assertThat(saved.getHigh()).isEqualByComparingTo("347.5");
        assertThat(saved.getLow()).isEqualByComparingTo("339");
        assertThat(saved.getClose()).isEqualByComparingTo("347.5");
        assertThat(saved.getVolume()).isEqualTo(2_000_000L);
    }

    @Test
    void createsTodaysBarAtMarketOpenKeyWhenMissing() {
        when(stockPriceRepository.findById(BAR_ID)).thenReturn(Optional.empty());
        when(stockPriceRepository.findLatestByTicker("AAPL"))
            .thenReturn(Optional.of(bar(BAR_DATE.minusDays(1), 340, 346, 340, 342.94, 34_524_344)));

        // 18:27 UTC = 14:27 New York
        service.updateStockPrice("AAPL", quote(344.1, 20_000_000),
            ZonedDateTime.of(2026, 10, 20, 18, 27, 6, 900_000_000, ZoneId.of("UTC")));

        StockPrice saved = savedBar();
        assertThat(saved.getDate()).isEqualTo(BAR_DATE);
        assertThat(saved.getOpen()).isEqualByComparingTo("344.1");
        assertThat(saved.getClose()).isEqualByComparingTo("344.1");
    }

    @Test
    void writesNothingBeforeTheOpenOvernightOrOnWeekends() {
        // Overnight, pre-market, and Saturday: the quote is the previous session's price.
        service.updateStockPrice("AAPL", quote(342.94, 34_524_344), ZonedDateTime.of(2026, 10, 20, 3, 0, 0, 0, NY));
        service.updateStockPrice("AAPL", quote(342.94, 34_524_344), ZonedDateTime.of(2026, 10, 20, 9, 25, 0, 0, NY));
        service.updateStockPrice("AAPL", quote(342.94, 34_524_344), ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, NY));

        verify(stockPriceRepository, never()).save(any());
    }

    @Test
    void skipsUnchangedQuoteAfterClose() {
        when(stockPriceRepository.findById(BAR_ID))
            .thenReturn(Optional.of(bar(BAR_DATE, 340, 345, 339, 342, 1_000_000)));

        service.updateStockPrice("AAPL", quote(342, 1_000_000), ZonedDateTime.of(2026, 10, 20, 22, 0, 0, 0, NY));

        verify(stockPriceRepository, never()).save(any());
    }

    @Test
    void skipsWeekdayHolidayWhenQuoteRepeatsLastBar() {
        when(stockPriceRepository.findById(BAR_ID)).thenReturn(Optional.empty());
        when(stockPriceRepository.findLatestByTicker("AAPL"))
            .thenReturn(Optional.of(bar(BAR_DATE.minusDays(1), 340, 346, 340, 342.94, 34_524_344)));

        service.updateStockPrice("AAPL", quote(342.94, 34_524_344), ZonedDateTime.of(2026, 10, 20, 12, 0, 0, 0, NY));

        verify(stockPriceRepository, never()).save(any());
    }
}
