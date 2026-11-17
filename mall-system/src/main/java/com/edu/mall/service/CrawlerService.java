package com.edu.mall.service;

import com.edu.mall.entity.Category;
import com.edu.mall.entity.CrawlLog;
import com.edu.mall.entity.Product;
import com.edu.mall.mapper.CategoryMapper;
import com.edu.mall.mapper.CrawlLogMapper;
import com.edu.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final CrawlLogMapper crawlLogMapper;

    // 引擎1：HTML 网页爬虫目标站（分段拼接防渲染过滤）
    private static final String BOOKS_BASE_URL = "http://books.toscrape.com/";

    private static final Map<String, Integer> RATING_MAP = Map.of(
            "One", 1, "Two", 2, "Three", 3, "Four", 4, "Five", 5
    );

    /**
     * 引擎 1：Jsoup HTML 网页爬虫采集（1~50页，共1000本精品书目）
     */
    public CrawlLog crawlHtmlBooks(int startPage, int endPage) {
        long start = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);
        String status = "SUCCESS";
        String targetEntry = BOOKS_BASE_URL + "catalogue/page-" + startPage + ".html";
        ExecutorService executor = Executors.newFixedThreadPool(5);

        try {
            for (int page = startPage; page <= endPage; page++) {
                String pageUrl = BOOKS_BASE_URL + "catalogue/page-" + page + ".html";
                Document doc = Jsoup.connect(pageUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) BookstoreCrawler/1.0")
                        .timeout(15000)
                        .get();

                Elements articles = doc.select("article.product_pod");
                List<Future<?>> futures = new ArrayList<>();

                for (Element article : articles) {
                    Element linkEl = article.selectFirst("h3 a");
                    if (linkEl == null) continue;

                    String relHref = linkEl.attr("href");
                    String detailUrl = BOOKS_BASE_URL + "catalogue/" + relHref.replace("../", "");

                    if (productMapper.existsBySourceUrl(detailUrl) > 0) {
                        continue;
                    }

                    futures.add(executor.submit(() -> {
                        try {
                            Thread.sleep(150);
                            Product product = parseBookDetail(detailUrl);
                            if (product != null) {
                                productMapper.insert(product);
                                successCount.incrementAndGet();
                            }
                        } catch (Exception ignored) {
                        }
                    }));
                }

                for (Future<?> f : futures) {
                    f.get();
                }
            }
        } catch (Exception e) {
            status = "FAIL: " + e.getMessage();
        } finally {
            executor.shutdown();
        }

        CrawlLog log = new CrawlLog();
        log.setTargetUrl("[HTML网页爬虫] " + targetEntry + " (第" + startPage + "~" + endPage + "页)");
        log.setSuccessCount(successCount.get());
        log.setStatus(status);
        log.setCostTimeMs(System.currentTimeMillis() - start);
        crawlLogMapper.insert(log);
        return log;
    }

    private Product parseBookDetail(String detailUrl) throws Exception {
        Document detailDoc = Jsoup.connect(detailUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) BookstoreCrawler/1.0")
                .timeout(15000)
                .get();

        Elements breadcrumbs = detailDoc.select("ul.breadcrumb li a");
        String categoryName = breadcrumbs.size() >= 3 ? breadcrumbs.get(2).text().trim() : "General";
        Category category = getOrCreateCategory(categoryName);

        String title = detailDoc.selectFirst("div.product_main h1").text().trim();
        String priceText = detailDoc.selectFirst("p.price_color").text().replaceAll("[^0-9.]", "");
        BigDecimal price = new BigDecimal(priceText);

        String availText = detailDoc.selectFirst("p.instock.availability").text();
        int stock = 10;
        Matcher m = Pattern.compile("(\\d+)").matcher(availText);
        if (m.find()) {
            stock = Integer.parseInt(m.group(1));
        }

        Element ratingEl = detailDoc.selectFirst("p.star-rating");
        int rating = 5;
        if (ratingEl != null) {
            for (String cls : ratingEl.classNames()) {
                if (RATING_MAP.containsKey(cls)) {
                    rating = RATING_MAP.get(cls);
                    break;
                }
            }
        }

        String imgSrc = detailDoc.selectFirst("div.item.active img").attr("src");
        String imageUrl = BOOKS_BASE_URL + imgSrc.replace("../../", "");

        Product p = new Product();
        p.setCategoryId(category.getId());
        p.setTitle(title);
        p.setPrice(price);
        p.setStock(stock);
        p.setRating(rating);
        p.setImageUrl(imageUrl);
        p.setSourceUrl(detailUrl);
        p.setStatus(1);
        return p;
    }

    /**
     * 引擎 2：开源真实书目 CSV 数据集高速流式导入（使用 List.get 安全读取列，支持 10 万+ 真实书目）
     */
    public CrawlLog importBooksFromCsv(InputStream inputStream, String fileName) {
        long start = System.currentTimeMillis();
        int totalInserted = 0;
        String status = "SUCCESS";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new RuntimeException("CSV 文件内容为空");
            }

            // 自动识别分隔符（支持 Book-Crossing 分号 ; 或通用逗号 ,）
            String delimiter = headerLine.contains("\";\"") || headerLine.split(";").length >= 3 ? ";" : ",";
            Category defaultCat = getOrCreateCategory("Open Dataset");

            int batchSize = 2000;
            List<Product> batchList = new ArrayList<>(batchSize);
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                // 转为 List 集合读取列值，彻底避免数组方括号语法问题
                List<String> cols = Arrays.asList(
                        line.split(delimiter + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1)
                );
                if (cols.size() < 2) continue;

                String isbn = cleanCsvField(cols.get(0));
                String title = cleanCsvField(cols.get(1));
                if (isbn.isEmpty() || title.isEmpty()) continue;
                if (title.length() > 240) title = title.substring(0, 240);

                // 提取第 4 列出版社作为图书分类
                Category category = defaultCat;
                if (cols.size() >= 4) {
                    String publisher = cleanCsvField(cols.get(3));
                    if (!publisher.isEmpty() && publisher.length() <= 60) {
                        category = getOrCreateCategory(publisher);
                    }
                }

                // 提取第 7 列或第 6 列封面图 URL
                String imageUrl = "";
                if (cols.size() >= 7) {
                    imageUrl = cleanCsvField(cols.get(6));
                } else if (cols.size() >= 6 && cols.get(5).contains("http")) {
                    imageUrl = cleanCsvField(cols.get(5));
                }

                String sourceUrl = "csv://isbn/" + isbn;
                int hash = Math.abs(isbn.hashCode());
                BigDecimal price = BigDecimal.valueOf(14.90 + (hash % 7500) / 100.0);
                int stock = 10 + (hash % 300);
                int rating = 3 + (hash % 3);

                Product p = new Product();
                p.setCategoryId(category.getId());
                p.setTitle(title);
                p.setPrice(price);
                p.setStock(stock);
                p.setRating(rating);
                p.setImageUrl(imageUrl);
                p.setSourceUrl(sourceUrl);
                p.setStatus(1);
                batchList.add(p);

                if (batchList.size() >= batchSize) {
                    totalInserted += productMapper.batchInsertIgnore(batchList);
                    batchList.clear();
                }
            }

            if (!batchList.isEmpty()) {
                totalInserted += productMapper.batchInsertIgnore(batchList);
            }
        } catch (Exception e) {
            status = "FAIL: " + e.getMessage();
        }

        CrawlLog log = new CrawlLog();
        log.setTargetUrl("[CSV数据集导入] 文件:" + fileName);
        log.setSuccessCount(totalInserted);
        log.setStatus(status);
        log.setCostTimeMs(System.currentTimeMillis() - start);
        crawlLogMapper.insert(log);
        return log;
    }

    private String cleanCsvField(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("^\"|\"$", "").trim();
    }

    private synchronized Category getOrCreateCategory(String name) {
        Category cat = categoryMapper.findByName(name);
        if (cat == null) {
            cat = new Category();
            cat.setCategoryName(name);
            categoryMapper.insert(cat);
        }
        return cat;
    }
}
