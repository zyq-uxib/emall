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

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final CrawlLogMapper crawlLogMapper;

    private static final String BASE_URL = "http://books.toscrape.com/";
    private static final Map<String, Integer> RATING_MAP = Map.of(
            "One", 1, "Two", 2, "Three", 3, "Four", 4, "Five", 5
    );

    public CrawlLog executeCrawl(int maxPages) {
        long start = System.currentTimeMillis();
        int successCount = 0;
        String status = "SUCCESS";
        String targetEntry = BASE_URL + "catalogue/page-1.html";

        try {
            for (int page = 1; page <= maxPages; page++) {
                String pageUrl = BASE_URL + "catalogue/page-" + page + ".html";
                Document doc = Jsoup.connect(pageUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) EduCrawler/1.0")
                        .timeout(15000)
                        .get();

                Elements articles = doc.select("article.product_pod");
                for (Element article : articles) {
                    Element linkEl = article.selectFirst("h3 a");
                    if (linkEl == null) continue;

                    String relHref = linkEl.attr("href");
                    String detailUrl = BASE_URL + "catalogue/" + relHref.replace("../", "");

                    if (productMapper.existsBySourceUrl(detailUrl) > 0) {
                        continue;
                    }

                    Thread.sleep(250);

                    Product product = parseDetail(detailUrl);
                    if (product != null) {
                        productMapper.insert(product);
                        successCount++;
                    }
                }
            }
        } catch (Exception e) {
            status = "FAIL: " + e.getMessage();
        }

        CrawlLog log = new CrawlLog();
        log.setTargetUrl(targetEntry + " (共" + maxPages + "页)");
        log.setSuccessCount(successCount);
        log.setStatus(status);
        log.setCostTimeMs(System.currentTimeMillis() - start);
        crawlLogMapper.insert(log);
        return log;
    }

    private Product parseDetail(String detailUrl) throws Exception {
        Document detailDoc = Jsoup.connect(detailUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) EduCrawler/1.0")
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
        String imageUrl = BASE_URL + imgSrc.replace("../../", "");

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
