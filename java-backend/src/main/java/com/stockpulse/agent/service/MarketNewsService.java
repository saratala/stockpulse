package com.stockpulse.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.agent.model.MarketNews;
import com.stockpulse.agent.model.MarketNews.ImpactLevel;
import com.stockpulse.agent.model.MarketNews.MarketNewsCategory;
import com.stockpulse.agent.repository.MarketNewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for fetching and managing market-wide news and economic events.
 * Fetches from Finnhub general news and categorizes based on content.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketNewsService {

    private final MarketNewsRepository marketNewsRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.news.api.finnhub.key:}")
    private String finnhubApiKey;

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();

    // Keywords for categorizing news
    private static final Map<MarketNewsCategory, List<Pattern>> CATEGORY_PATTERNS = new HashMap<>();

    static {
        CATEGORY_PATTERNS.put(MarketNewsCategory.FED, Arrays.asList(
            Pattern.compile("(?i)(federal reserve|fed |fomc|powell|interest rate|rate hike|rate cut|monetary policy|quantitative|tightening|dovish|hawkish)"),
            Pattern.compile("(?i)(fed chair|central bank|fed meeting|fed decision|fed minutes)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.EMPLOYMENT, Arrays.asList(
            Pattern.compile("(?i)(jobs report|unemployment|nonfarm payroll|NFP|jobless claims|labor market|employment data|hiring|layoffs)"),
            Pattern.compile("(?i)(job growth|wage growth|labor department|employment rate)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.CPI, Arrays.asList(
            Pattern.compile("(?i)(CPI|consumer price|inflation data|inflation rate|inflation report|core inflation)"),
            Pattern.compile("(?i)(price index|inflationary|disinflation|deflation)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.GDP, Arrays.asList(
            Pattern.compile("(?i)(GDP|gross domestic product|economic growth|recession|economic contraction)"),
            Pattern.compile("(?i)(quarterly growth|economic output|economic expansion)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.PPI, Arrays.asList(
            Pattern.compile("(?i)(PPI|producer price|wholesale price|producer inflation)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.RETAIL_SALES, Arrays.asList(
            Pattern.compile("(?i)(retail sales|consumer spending|holiday sales|shopping|retail data)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.HOUSING, Arrays.asList(
            Pattern.compile("(?i)(housing starts|home sales|housing market|mortgage rate|real estate|home prices)"),
            Pattern.compile("(?i)(existing home|new home|housing data|pending home)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.MANUFACTURING, Arrays.asList(
            Pattern.compile("(?i)(ISM|PMI|manufacturing|industrial production|factory orders|durable goods)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.CONSUMER_SENTIMENT, Arrays.asList(
            Pattern.compile("(?i)(consumer confidence|consumer sentiment|michigan sentiment|consumer outlook)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.TREASURY, Arrays.asList(
            Pattern.compile("(?i)(treasury yield|bond yield|10-year|2-year|yield curve|inverted|treasury auction)"),
            Pattern.compile("(?i)(bond market|debt ceiling|treasury department)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.GEOPOLITICAL, Arrays.asList(
            Pattern.compile("(?i)(trade war|tariff|sanction|geopolitical|china trade|war|conflict|nato)"),
            Pattern.compile("(?i)(russia|ukraine|middle east|oil embargo|supply chain)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.EARNINGS_SEASON, Arrays.asList(
            Pattern.compile("(?i)(earnings season|quarterly earnings|reporting season|earnings outlook)")
        ));

        CATEGORY_PATTERNS.put(MarketNewsCategory.CRYPTO, Arrays.asList(
            Pattern.compile("(?i)(bitcoin|crypto|cryptocurrency|ethereum|blockchain|digital currency|SEC crypto)")
        ));
    }

    // Keywords for determining impact level
    private static final List<Pattern> HIGH_IMPACT_PATTERNS = Arrays.asList(
        Pattern.compile("(?i)(federal reserve|fomc|rate decision|rate hike|rate cut|jobs report|NFP|CPI|inflation)"),
        Pattern.compile("(?i)(recession|GDP|economic crisis|market crash|emergency|breaking)")
    );

    private static final List<Pattern> MEDIUM_IMPACT_PATTERNS = Arrays.asList(
        Pattern.compile("(?i)(PMI|ISM|housing|retail sales|consumer confidence|treasury yield)"),
        Pattern.compile("(?i)(earnings season|trade|tariff|oil price)")
    );

    /**
     * Fetch market news from Finnhub general news endpoint
     */
    public List<MarketNews> fetchMarketNews() {
        if (finnhubApiKey == null || finnhubApiKey.isEmpty()) {
            log.warn("Finnhub API key not configured, skipping market news fetch");
            return Collections.emptyList();
        }

        log.info("Fetching market-wide news from Finnhub");
        List<MarketNews> allNews = new ArrayList<>();

        try {
            // Fetch general market news
            allNews.addAll(fetchFromFinnhubGeneral("general"));

            // Also fetch forex news (often contains Fed/economic news)
            allNews.addAll(fetchFromFinnhubGeneral("forex"));

            // Fetch merger news
            allNews.addAll(fetchFromFinnhubGeneral("merger"));

            log.info("Fetched {} market news articles", allNews.size());

        } catch (Exception e) {
            log.error("Error fetching market news", e);
        }

        return allNews;
    }

    private List<MarketNews> fetchFromFinnhubGeneral(String category) {
        List<MarketNews> articles = new ArrayList<>();

        try {
            String url = String.format(
                "https://finnhub.io/api/v1/news?category=%s&token=%s",
                category, finnhubApiKey
            );

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .block();

            if (response != null) {
                articles = parseFinnhubGeneralNews(response);
            }

        } catch (Exception e) {
            log.error("Error fetching Finnhub {} news: {}", category, e.getMessage());
        }

        return articles;
    }

    private List<MarketNews> parseFinnhubGeneralNews(String response) {
        List<MarketNews> articles = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(response);

            if (root.isArray()) {
                for (JsonNode item : root) {
                    String headline = item.has("headline") ? item.get("headline").asText() : "";
                    String summary = item.has("summary") ? item.get("summary").asText() : "";
                    String url = item.has("url") ? item.get("url").asText() : "";
                    String source = item.has("source") ? item.get("source").asText() : "Finnhub";
                    long datetime = item.has("datetime") ? item.get("datetime").asLong() : 0;

                    if (headline.isEmpty() || url.isEmpty()) continue;

                    // Skip if already exists
                    if (marketNewsRepository.existsByUrl(url)) continue;

                    // Determine category and impact
                    String fullText = headline + " " + summary;
                    MarketNewsCategory newsCategory = categorizeNews(fullText);
                    ImpactLevel impactLevel = determineImpactLevel(fullText, newsCategory);

                    // Only save if it's a relevant market/economic news
                    if (newsCategory != MarketNewsCategory.OTHER || isRelevantMarketNews(fullText)) {
                        MarketNews article = MarketNews.builder()
                                .title(headline)
                                .content(summary)
                                .url(url)
                                .source(source)
                                .publishedAt(LocalDateTime.ofEpochSecond(datetime, 0, ZoneOffset.UTC))
                                .category(newsCategory)
                                .impactLevel(impactLevel)
                                .affectedSectors(determineAffectedSectors(fullText))
                                .build();

                        articles.add(article);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error parsing Finnhub general news: {}", e.getMessage());
        }

        return articles;
    }

    /**
     * Categorize news based on content
     */
    private MarketNewsCategory categorizeNews(String text) {
        for (Map.Entry<MarketNewsCategory, List<Pattern>> entry : CATEGORY_PATTERNS.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(text).find()) {
                    return entry.getKey();
                }
            }
        }
        return MarketNewsCategory.MARKET_OUTLOOK;
    }

    /**
     * Determine impact level based on content and category
     */
    private ImpactLevel determineImpactLevel(String text, MarketNewsCategory category) {
        // High impact categories
        if (category == MarketNewsCategory.FED ||
            category == MarketNewsCategory.EMPLOYMENT ||
            category == MarketNewsCategory.CPI ||
            category == MarketNewsCategory.GDP) {
            return ImpactLevel.HIGH;
        }

        // Check for high impact keywords
        for (Pattern pattern : HIGH_IMPACT_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return ImpactLevel.HIGH;
            }
        }

        // Check for medium impact keywords
        for (Pattern pattern : MEDIUM_IMPACT_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return ImpactLevel.MEDIUM;
            }
        }

        return ImpactLevel.LOW;
    }

    /**
     * Check if news is relevant market news (for OTHER category)
     */
    private boolean isRelevantMarketNews(String text) {
        Pattern marketPattern = Pattern.compile(
            "(?i)(stock market|wall street|S&P 500|dow jones|nasdaq|market rally|market sell|bull market|bear market|stock futures)"
        );
        return marketPattern.matcher(text).find();
    }

    /**
     * Determine affected sectors based on content
     */
    private String determineAffectedSectors(String text) {
        List<String> sectors = new ArrayList<>();

        if (Pattern.compile("(?i)(tech|technology|software|semiconductor|chip|AI|artificial intelligence)").matcher(text).find()) {
            sectors.add("TECH");
        }
        if (Pattern.compile("(?i)(bank|financial|finance|lending|credit)").matcher(text).find()) {
            sectors.add("FINANCIALS");
        }
        if (Pattern.compile("(?i)(oil|gas|energy|petroleum|crude)").matcher(text).find()) {
            sectors.add("ENERGY");
        }
        if (Pattern.compile("(?i)(healthcare|pharma|drug|biotech|medical)").matcher(text).find()) {
            sectors.add("HEALTHCARE");
        }
        if (Pattern.compile("(?i)(retail|consumer|shopping)").matcher(text).find()) {
            sectors.add("CONSUMER");
        }
        if (Pattern.compile("(?i)(real estate|REIT|housing|property)").matcher(text).find()) {
            sectors.add("REAL_ESTATE");
        }
        if (Pattern.compile("(?i)(utility|utilities|electric|power grid)").matcher(text).find()) {
            sectors.add("UTILITIES");
        }
        if (Pattern.compile("(?i)(industrial|manufacturing|factory)").matcher(text).find()) {
            sectors.add("INDUSTRIALS");
        }

        return sectors.isEmpty() ? null : String.join(",", sectors);
    }

    /**
     * Save market news to database
     */
    public int saveMarketNews(List<MarketNews> articles) {
        int saved = 0;
        for (MarketNews article : articles) {
            try {
                if (!marketNewsRepository.existsByUrl(article.getUrl())) {
                    marketNewsRepository.save(article);
                    saved++;
                }
            } catch (Exception e) {
                log.warn("Error saving market news article: {}", e.getMessage());
            }
        }
        log.info("Saved {} new market news articles", saved);
        return saved;
    }

    /**
     * Get recent market news
     */
    public List<MarketNews> getRecentNews(int hours, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return marketNewsRepository.findRecentNews(since, PageRequest.of(0, limit)).getContent();
    }

    /**
     * Get high-impact news from recent hours
     */
    public List<MarketNews> getHighImpactNews(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return marketNewsRepository.findByImpactLevelSince(ImpactLevel.HIGH, since);
    }

    /**
     * Get news by category
     */
    public List<MarketNews> getNewsByCategory(MarketNewsCategory category, int limit) {
        return marketNewsRepository.findByCategoryOrderByPublishedAtDesc(category, PageRequest.of(0, limit)).getContent();
    }

    /**
     * Get news affecting a specific sector
     */
    public List<MarketNews> getNewsBySector(String sector) {
        return marketNewsRepository.findByAffectedSector(sector);
    }

    /**
     * Get summary of news categories
     */
    public Map<String, Object> getNewsSummary(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);

        Map<String, Object> summary = new HashMap<>();

        // Count by category
        List<Object[]> categoryCounts = marketNewsRepository.countByCategorySince(since);
        Map<String, Long> categoryMap = new HashMap<>();
        for (Object[] row : categoryCounts) {
            categoryMap.put(((MarketNewsCategory) row[0]).name(), (Long) row[1]);
        }
        summary.put("categoryCounts", categoryMap);

        // Get high impact news
        List<MarketNews> highImpact = getHighImpactNews(hours);
        summary.put("highImpactCount", highImpact.size());
        summary.put("highImpactNews", highImpact);

        // Total count
        List<MarketNews> allRecent = marketNewsRepository.findRecentNews(since);
        summary.put("totalCount", allRecent.size());

        return summary;
    }

    /**
     * Scheduled task to fetch market news every 30 minutes
     */
    @Scheduled(fixedRateString = "${app.market-news.fetch-interval:1800000}") // Default: 30 minutes
    public void scheduledFetchMarketNews() {
        log.info("Running scheduled market news fetch");
        try {
            List<MarketNews> news = fetchMarketNews();
            int saved = saveMarketNews(news);
            log.info("Scheduled fetch complete: {} new articles saved", saved);
        } catch (Exception e) {
            log.error("Error in scheduled market news fetch", e);
        }
    }

    /**
     * Manual trigger to fetch market news
     */
    public Map<String, Object> triggerFetch() {
        List<MarketNews> news = fetchMarketNews();
        int saved = saveMarketNews(news);

        Map<String, Object> result = new HashMap<>();
        result.put("fetched", news.size());
        result.put("saved", saved);
        result.put("timestamp", LocalDateTime.now().toString());
        return result;
    }
}
