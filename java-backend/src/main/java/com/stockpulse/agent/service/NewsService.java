package com.stockpulse.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.agent.model.NewsArticle;
import com.stockpulse.agent.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {
    
    private final NewsArticleRepository newsArticleRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    
    @Value("${app.news.api.alpha-vantage.key:}")
    private String alphaVantageApiKey;
    
    @Value("${app.news.api.newsapi.key:}")
    private String newsApiKey;
    
    @Value("${app.news.api.finnhub.key:}")
    private String finnhubApiKey;
    
    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
    
    /**
     * Fetch news for a specific ticker from multiple sources
     */
    public List<NewsArticle> fetchNewsForTicker(String ticker) {
        log.info("Fetching news for ticker: {}", ticker);
        
        List<NewsArticle> allNews = new ArrayList<>();
        
        // Try multiple news sources
        try {
            // Alpha Vantage News
            if (!alphaVantageApiKey.isEmpty()) {
                List<NewsArticle> alphaVantageNews = fetchFromAlphaVantage(ticker);
                allNews.addAll(alphaVantageNews);
            }
            
            // Finnhub News
            if (!finnhubApiKey.isEmpty()) {
                List<NewsArticle> finnhubNews = fetchFromFinnhub(ticker);
                allNews.addAll(finnhubNews);
            }
            
            // NewsAPI as fallback
            if (!newsApiKey.isEmpty()) {
                List<NewsArticle> newsApiNews = fetchFromNewsAPI(ticker);
                allNews.addAll(newsApiNews);
            }
            
            // If no API keys configured, use free sources
            if (allNews.isEmpty()) {
                List<NewsArticle> freeNews = fetchFromFreeSources(ticker);
                allNews.addAll(freeNews);
            }
            
        } catch (Exception e) {
            log.error("Error fetching news for ticker: {}", ticker, e);
        }
        
        // Remove duplicates and sort by date
        allNews = removeDuplicates(allNews);
        allNews.sort((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()));
        
        // Save to database
        saveNewsArticles(allNews);
        
        log.info("Fetched {} news articles for ticker: {}", allNews.size(), ticker);
        return allNews;
    }
    
    private List<NewsArticle> fetchFromAlphaVantage(String ticker) {
        try {
            String url = String.format(
                "https://www.alphavantage.co/query?function=NEWS_SENTIMENT&tickers=%s&apikey=%s&limit=50",
                ticker, alphaVantageApiKey
            );
            
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .block();
            
            return parseAlphaVantageResponse(response, ticker);
            
        } catch (Exception e) {
            log.error("Error fetching from Alpha Vantage for ticker: {}", ticker, e);
            return new ArrayList<>();
        }
    }
    
    private List<NewsArticle> parseAlphaVantageResponse(String response, String ticker) {
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode feed = root.get("feed");
            
            if (feed != null && feed.isArray()) {
                for (JsonNode item : feed) {
                    NewsArticle article = NewsArticle.builder()
                            .ticker(ticker.toUpperCase())
                            .title(item.get("title").asText())
                            .content(item.get("summary").asText())
                            .url(item.get("url").asText())
                            .source("Alpha Vantage")
                            .publishedAt(parseDateTime(item.get("time_published").asText()))
                            .build();
                    
                    articles.add(article);
                }
            }
            
        } catch (Exception e) {
            log.error("Error parsing Alpha Vantage response for ticker: {}", ticker, e);
        }
        
        return articles;
    }
    
    private List<NewsArticle> fetchFromFinnhub(String ticker) {
        try {
            LocalDateTime from = LocalDateTime.now().minusDays(7);
            LocalDateTime to = LocalDateTime.now();
            
            String url = String.format(
                "https://finnhub.io/api/v1/company-news?symbol=%s&from=%s&to=%s&token=%s",
                ticker,
                from.format(DateTimeFormatter.ISO_LOCAL_DATE),
                to.format(DateTimeFormatter.ISO_LOCAL_DATE),
                finnhubApiKey
            );
            
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .block();
            
            return parseFinnhubResponse(response, ticker);
            
        } catch (Exception e) {
            log.error("Error fetching from Finnhub for ticker: {}", ticker, e);
            return new ArrayList<>();
        }
    }
    
    private List<NewsArticle> parseFinnhubResponse(String response, String ticker) {
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(response);
            
            if (root.isArray()) {
                for (JsonNode item : root) {
                    NewsArticle article = NewsArticle.builder()
                            .ticker(ticker.toUpperCase())
                            .title(item.get("headline").asText())
                            .content(item.get("summary").asText())
                            .url(item.get("url").asText())
                            .source("Finnhub")
                            .publishedAt(LocalDateTime.ofEpochSecond(
                                item.get("datetime").asLong(), 
                                0, 
                                ZoneOffset.UTC
                            ))
                            .build();
                    
                    articles.add(article);
                }
            }
            
        } catch (Exception e) {
            log.error("Error parsing Finnhub response for ticker: {}", ticker, e);
        }
        
        return articles;
    }
    
    private List<NewsArticle> fetchFromNewsAPI(String ticker) {
        try {
            String query = String.format("%s stock OR %s earnings OR %s financial", ticker, ticker, ticker);
            String url = String.format(
                "https://newsapi.org/v2/everything?q=%s&apiKey=%s&sortBy=publishedAt&pageSize=50",
                query, newsApiKey
            );
            
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .block();
            
            return parseNewsAPIResponse(response, ticker);
            
        } catch (Exception e) {
            log.error("Error fetching from NewsAPI for ticker: {}", ticker, e);
            return new ArrayList<>();
        }
    }
    
    private List<NewsArticle> parseNewsAPIResponse(String response, String ticker) {
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode articlesNode = root.get("articles");
            
            if (articlesNode != null && articlesNode.isArray()) {
                for (JsonNode item : articlesNode) {
                    NewsArticle article = NewsArticle.builder()
                            .ticker(ticker.toUpperCase())
                            .title(item.get("title").asText())
                            .content(item.get("description").asText())
                            .url(item.get("url").asText())
                            .source("NewsAPI")
                            .publishedAt(LocalDateTime.parse(
                                item.get("publishedAt").asText(),
                                DateTimeFormatter.ISO_DATE_TIME
                            ))
                            .build();
                    
                    articles.add(article);
                }
            }
            
        } catch (Exception e) {
            log.error("Error parsing NewsAPI response for ticker: {}", ticker, e);
        }
        
        return articles;
    }
    
    private List<NewsArticle> fetchFromFreeSources(String ticker) {
        List<NewsArticle> articles = new ArrayList<>();

        // Try Yahoo Finance RSS first
        try {
            List<NewsArticle> yahooNews = fetchFromYahooRSS(ticker);
            articles.addAll(yahooNews);
        } catch (Exception e) {
            log.warn("Yahoo RSS fetch failed for {}: {}", ticker, e.getMessage());
        }

        // Try Google News RSS as fallback
        if (articles.isEmpty()) {
            try {
                List<NewsArticle> googleNews = fetchFromGoogleNews(ticker);
                articles.addAll(googleNews);
            } catch (Exception e) {
                log.warn("Google News fetch failed for {}: {}", ticker, e.getMessage());
            }
        }

        // Try Finviz news scraping as last resort
        if (articles.isEmpty()) {
            try {
                List<NewsArticle> finvizNews = fetchFromFinviz(ticker);
                articles.addAll(finvizNews);
            } catch (Exception e) {
                log.warn("Finviz fetch failed for {}: {}", ticker, e.getMessage());
            }
        }

        log.info("Fetched {} articles from free sources for {}", articles.size(), ticker);
        return articles;
    }

    private List<NewsArticle> fetchFromYahooRSS(String ticker) {
        List<NewsArticle> articles = new ArrayList<>();

        try {
            String url = String.format("https://feeds.finance.yahoo.com/rss/2.0/headline?s=%s&region=US&lang=en-US", ticker);
            log.info("Fetching Yahoo RSS for: {}", ticker);

            String response = webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .block();

            if (response != null && response.contains("<item>")) {
                articles = parseRSSResponse(response, ticker, "Yahoo Finance");
            }
        } catch (Exception e) {
            log.error("Error fetching Yahoo RSS for {}: {}", ticker, e.getMessage());
        }

        return articles;
    }

    private List<NewsArticle> fetchFromGoogleNews(String ticker) {
        List<NewsArticle> articles = new ArrayList<>();

        try {
            // Google News RSS feed for stock ticker
            String query = ticker + "+stock";
            String url = String.format("https://news.google.com/rss/search?q=%s&hl=en-US&gl=US&ceid=US:en", query);
            log.info("Fetching Google News RSS for: {}", ticker);

            String response = webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .block();

            if (response != null && response.contains("<item>")) {
                articles = parseRSSResponse(response, ticker, "Google News");
            }
        } catch (Exception e) {
            log.error("Error fetching Google News for {}: {}", ticker, e.getMessage());
        }

        return articles;
    }

    private List<NewsArticle> fetchFromFinviz(String ticker) {
        List<NewsArticle> articles = new ArrayList<>();

        try {
            String url = String.format("https://finviz.com/quote.ashx?t=%s", ticker);
            log.info("Fetching Finviz news for: {}", ticker);

            String response = webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .block();

            if (response != null) {
                articles = parseFinvizHTML(response, ticker);
            }
        } catch (Exception e) {
            log.error("Error fetching Finviz news for {}: {}", ticker, e.getMessage());
        }

        return articles;
    }

    private List<NewsArticle> parseRSSResponse(String xml, String ticker, String sourceName) {
        List<NewsArticle> articles = new ArrayList<>();

        try {
            // Simple XML parsing using regex (works for RSS feeds)
            Pattern itemPattern = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
            Pattern titlePattern = Pattern.compile("<title><!\\[CDATA\\[(.*?)\\]\\]></title>|<title>(.*?)</title>", Pattern.DOTALL);
            Pattern linkPattern = Pattern.compile("<link><!\\[CDATA\\[(.*?)\\]\\]></link>|<link>(.*?)</link>", Pattern.DOTALL);
            Pattern descPattern = Pattern.compile("<description><!\\[CDATA\\[(.*?)\\]\\]></description>|<description>(.*?)</description>", Pattern.DOTALL);
            Pattern pubDatePattern = Pattern.compile("<pubDate>(.*?)</pubDate>", Pattern.DOTALL);

            Matcher itemMatcher = itemPattern.matcher(xml);
            int count = 0;

            while (itemMatcher.find() && count < 20) {
                String item = itemMatcher.group(1);

                // Extract title
                String title = extractRSSField(item, titlePattern);
                if (title == null || title.isEmpty()) continue;

                // Extract link
                String link = extractRSSField(item, linkPattern);
                if (link == null || link.isEmpty()) continue;

                // Extract description
                String description = extractRSSField(item, descPattern);
                if (description == null) description = title;

                // Clean HTML tags from description
                description = description.replaceAll("<[^>]+>", "").trim();
                if (description.length() > 500) {
                    description = description.substring(0, 500) + "...";
                }

                // Extract and parse publish date
                LocalDateTime pubDate = LocalDateTime.now();
                Matcher pubDateMatcher = pubDatePattern.matcher(item);
                if (pubDateMatcher.find()) {
                    pubDate = parseRSSDate(pubDateMatcher.group(1));
                }

                NewsArticle article = NewsArticle.builder()
                        .ticker(ticker.toUpperCase())
                        .title(cleanHtml(title))
                        .content(cleanHtml(description))
                        .url(link)
                        .source(sourceName)
                        .publishedAt(pubDate)
                        .build();

                articles.add(article);
                count++;
            }

            log.info("Parsed {} articles from {} RSS for {}", articles.size(), sourceName, ticker);

        } catch (Exception e) {
            log.error("Error parsing RSS for {}: {}", ticker, e.getMessage());
        }

        return articles;
    }

    private String extractRSSField(String item, Pattern pattern) {
        Matcher matcher = pattern.matcher(item);
        if (matcher.find()) {
            // Try CDATA group first, then regular group
            String result = matcher.group(1);
            if (result == null || result.isEmpty()) {
                result = matcher.group(2);
            }
            return result;
        }
        return null;
    }

    private LocalDateTime parseRSSDate(String dateStr) {
        try {
            // Common RSS date formats
            List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.RFC_1123_DATE_TIME,
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
                DateTimeFormatter.ISO_DATE_TIME
            );

            for (DateTimeFormatter formatter : formatters) {
                try {
                    ZonedDateTime zdt = ZonedDateTime.parse(dateStr.trim(), formatter);
                    return zdt.toLocalDateTime();
                } catch (Exception ignored) {}
            }

            // Try simple parsing
            return LocalDateTime.now();

        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private List<NewsArticle> parseFinvizHTML(String html, String ticker) {
        List<NewsArticle> articles = new ArrayList<>();

        try {
            // Find news table - look for news links in the page
            Pattern newsPattern = Pattern.compile("<a[^>]+href=\"(https?://[^\"]+)\"[^>]*class=\"tab-link-news\"[^>]*>(.*?)</a>", Pattern.DOTALL);
            Pattern newsRowPattern = Pattern.compile("<tr class=\"cursor-pointer[^\"]*\"[^>]*>(.*?)</tr>", Pattern.DOTALL);
            Pattern linkInRowPattern = Pattern.compile("<a[^>]+href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
            Pattern timePattern = Pattern.compile("<td[^>]*>(\\w{3}-\\d{1,2}-\\d{2}|\\d{1,2}:\\d{2}[AP]M|Today|Yesterday)</td>", Pattern.DOTALL);

            // Try to find news table rows
            Matcher rowMatcher = newsRowPattern.matcher(html);
            int count = 0;

            while (rowMatcher.find() && count < 15) {
                String row = rowMatcher.group(1);

                Matcher linkMatcher = linkInRowPattern.matcher(row);
                if (linkMatcher.find()) {
                    String url = linkMatcher.group(1);
                    String title = linkMatcher.group(2).replaceAll("<[^>]+>", "").trim();

                    if (title.length() > 10 && !url.contains("finviz.com")) {
                        NewsArticle article = NewsArticle.builder()
                                .ticker(ticker.toUpperCase())
                                .title(cleanHtml(title))
                                .content(cleanHtml(title))
                                .url(url)
                                .source("Finviz")
                                .publishedAt(LocalDateTime.now().minusHours(count))
                                .build();

                        articles.add(article);
                        count++;
                    }
                }
            }

            // Fallback: try alternate pattern for news links
            if (articles.isEmpty()) {
                Matcher newsMatcher = newsPattern.matcher(html);
                count = 0;

                while (newsMatcher.find() && count < 15) {
                    String url = newsMatcher.group(1);
                    String title = newsMatcher.group(2).replaceAll("<[^>]+>", "").trim();

                    if (title.length() > 10) {
                        NewsArticle article = NewsArticle.builder()
                                .ticker(ticker.toUpperCase())
                                .title(cleanHtml(title))
                                .content(cleanHtml(title))
                                .url(url)
                                .source("Finviz")
                                .publishedAt(LocalDateTime.now().minusHours(count))
                                .build();

                        articles.add(article);
                        count++;
                    }
                }
            }

            log.info("Parsed {} articles from Finviz for {}", articles.size(), ticker);

        } catch (Exception e) {
            log.error("Error parsing Finviz HTML for {}: {}", ticker, e.getMessage());
        }

        return articles;
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return text
                .replaceAll("<[^>]+>", "")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
    
    private LocalDateTime parseDateTime(String dateStr) {
        try {
            // Handle different date formats
            if (dateStr.length() == 8) {
                // YYYYMMDD format
                return LocalDateTime.parse(dateStr + "T00:00:00");
            } else if (dateStr.contains("T")) {
                // ISO format
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
            } else {
                // Default to current time
                return LocalDateTime.now();
            }
        } catch (Exception e) {
            log.warn("Could not parse date: {}, using current time", dateStr);
            return LocalDateTime.now();
        }
    }
    
    private List<NewsArticle> removeDuplicates(List<NewsArticle> articles) {
        Map<String, NewsArticle> uniqueArticles = new HashMap<>();
        
        for (NewsArticle article : articles) {
            String key = article.getTitle() + "|" + article.getUrl();
            uniqueArticles.put(key, article);
        }
        
        return new ArrayList<>(uniqueArticles.values());
    }
    
    private void saveNewsArticles(List<NewsArticle> articles) {
        try {
            for (NewsArticle article : articles) {
                // Check if article already exists
                boolean exists = newsArticleRepository.existsByTitleAndUrl(
                    article.getTitle(), 
                    article.getUrl()
                );
                
                if (!exists) {
                    newsArticleRepository.save(article);
                }
            }
        } catch (Exception e) {
            log.error("Error saving news articles", e);
        }
    }
    
    /**
     * Fetch news for multiple tickers
     */
    public void fetchNewsForTickers(List<String> tickers) {
        log.info("Fetching news for {} tickers", tickers.size());
        
        List<CompletableFuture<Void>> futures = tickers.stream()
                .map(ticker -> CompletableFuture.runAsync(() -> fetchNewsForTicker(ticker)))
                .toList();
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(5, TimeUnit.MINUTES)
                .join();
        
        log.info("Completed fetching news for all tickers");
    }
}