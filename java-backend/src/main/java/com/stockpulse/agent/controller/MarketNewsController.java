package com.stockpulse.agent.controller;

import com.stockpulse.agent.model.MarketNews;
import com.stockpulse.agent.model.MarketNews.ImpactLevel;
import com.stockpulse.agent.model.MarketNews.MarketNewsCategory;
import com.stockpulse.agent.repository.MarketNewsRepository;
import com.stockpulse.agent.service.MarketNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * REST Controller for market-wide news and economic events.
 * Provides endpoints for fetching macro news that affects the entire market.
 */
@RestController
@RequestMapping("/market-news")
@RequiredArgsConstructor
@Slf4j
public class MarketNewsController {

    private final MarketNewsService marketNewsService;
    private final MarketNewsRepository marketNewsRepository;

    /**
     * Get recent market news
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRecentNews(
            @RequestParam(defaultValue = "48") int hours,
            @RequestParam(defaultValue = "50") int limit) {

        log.info("Getting recent market news: hours={}, limit={}", hours, limit);

        List<MarketNews> news = marketNewsService.getRecentNews(hours, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("count", news.size());
        response.put("hours", hours);
        response.put("news", news);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Get high-impact market news
     */
    @GetMapping("/high-impact")
    public ResponseEntity<Map<String, Object>> getHighImpactNews(
            @RequestParam(defaultValue = "72") int hours) {

        log.info("Getting high-impact market news: hours={}", hours);

        List<MarketNews> news = marketNewsService.getHighImpactNews(hours);

        Map<String, Object> response = new HashMap<>();
        response.put("count", news.size());
        response.put("hours", hours);
        response.put("impactLevel", "HIGH");
        response.put("news", news);

        return ResponseEntity.ok(response);
    }

    /**
     * Get market news by category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<Map<String, Object>> getNewsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "20") int limit) {

        log.info("Getting market news by category: {}", category);

        try {
            MarketNewsCategory newsCategory = MarketNewsCategory.valueOf(category.toUpperCase());
            List<MarketNews> news = marketNewsService.getNewsByCategory(newsCategory, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("category", newsCategory.name());
            response.put("count", news.size());
            response.put("news", news);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid category: " + category);
            error.put("validCategories", Arrays.stream(MarketNewsCategory.values())
                    .map(Enum::name).toList());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get market news by impact level
     */
    @GetMapping("/impact/{level}")
    public ResponseEntity<Map<String, Object>> getNewsByImpactLevel(
            @PathVariable String level,
            @RequestParam(defaultValue = "72") int hours) {

        log.info("Getting market news by impact level: {}", level);

        try {
            ImpactLevel impactLevel = ImpactLevel.valueOf(level.toUpperCase());
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            List<MarketNews> news = marketNewsRepository.findByImpactLevelSince(impactLevel, since);

            Map<String, Object> response = new HashMap<>();
            response.put("impactLevel", impactLevel.name());
            response.put("hours", hours);
            response.put("count", news.size());
            response.put("news", news);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid impact level: " + level);
            error.put("validLevels", Arrays.stream(ImpactLevel.values())
                    .map(Enum::name).toList());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get market news affecting a specific sector
     */
    @GetMapping("/sector/{sector}")
    public ResponseEntity<Map<String, Object>> getNewsBySector(
            @PathVariable String sector) {

        log.info("Getting market news by sector: {}", sector);

        List<MarketNews> news = marketNewsService.getNewsBySector(sector.toUpperCase());

        Map<String, Object> response = new HashMap<>();
        response.put("sector", sector.toUpperCase());
        response.put("count", news.size());
        response.put("news", news);

        return ResponseEntity.ok(response);
    }

    /**
     * Get summary of market news
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getNewsSummary(
            @RequestParam(defaultValue = "24") int hours) {

        log.info("Getting market news summary: hours={}", hours);

        Map<String, Object> summary = marketNewsService.getNewsSummary(hours);
        summary.put("hours", hours);
        summary.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(summary);
    }

    /**
     * Get available categories
     */
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategories() {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, String>> categories = new ArrayList<>();
        for (MarketNewsCategory cat : MarketNewsCategory.values()) {
            Map<String, String> catInfo = new HashMap<>();
            catInfo.put("name", cat.name());
            catInfo.put("description", getCategoryDescription(cat));
            categories.add(catInfo);
        }

        response.put("categories", categories);
        response.put("impactLevels", Arrays.stream(ImpactLevel.values())
                .map(Enum::name).toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Trigger manual fetch of market news
     */
    @PostMapping("/fetch")
    public ResponseEntity<Map<String, Object>> triggerFetch() {
        log.info("Manually triggering market news fetch");

        Map<String, Object> result = marketNewsService.triggerFetch();
        result.put("status", "success");
        result.put("message", "Market news fetch triggered");

        return ResponseEntity.ok(result);
    }

    /**
     * Get latest news for each category
     */
    @GetMapping("/latest-by-category")
    public ResponseEntity<Map<String, Object>> getLatestByCategory() {
        List<MarketNews> latestNews = marketNewsRepository.findLatestByEachCategory();

        Map<String, MarketNews> byCategory = new HashMap<>();
        for (MarketNews news : latestNews) {
            byCategory.put(news.getCategory().name(), news);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("latestByCategory", byCategory);
        response.put("count", latestNews.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Get category description
     */
    private String getCategoryDescription(MarketNewsCategory category) {
        return switch (category) {
            case FED -> "Federal Reserve decisions, speeches, and monetary policy";
            case EMPLOYMENT -> "Jobs reports, unemployment claims, and labor market data";
            case CPI -> "Consumer Price Index and inflation data";
            case GDP -> "Gross Domestic Product and economic growth reports";
            case PPI -> "Producer Price Index data";
            case RETAIL_SALES -> "Retail sales and consumer spending data";
            case HOUSING -> "Housing market data and home sales reports";
            case MANUFACTURING -> "ISM, PMI, and manufacturing data";
            case CONSUMER_SENTIMENT -> "Consumer confidence and sentiment surveys";
            case EARNINGS_SEASON -> "Broad earnings season news and outlook";
            case GEOPOLITICAL -> "Trade wars, sanctions, and global events";
            case MARKET_OUTLOOK -> "General market analysis and outlook";
            case TREASURY -> "Treasury yields and bond market news";
            case CRYPTO -> "Cryptocurrency market news affecting stocks";
            case OTHER -> "Other market-related news";
        };
    }
}
