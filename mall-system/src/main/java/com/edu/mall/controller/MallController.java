package com.edu.mall.controller;

import com.edu.mall.common.Result;
import com.edu.mall.config.TokenStore;
import com.edu.mall.entity.Admin;
import com.edu.mall.entity.Category;
import com.edu.mall.entity.CrawlLog;
import com.edu.mall.entity.Product;
import com.edu.mall.mapper.AdminMapper;
import com.edu.mall.mapper.CategoryMapper;
import com.edu.mall.mapper.CrawlLogMapper;
import com.edu.mall.mapper.ProductMapper;
import com.edu.mall.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin
public class MallController {

    private final CrawlerService crawlerService;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final CrawlLogMapper crawlLogMapper;
    // 新增注入管理员查询接口与令牌管理器
    private final AdminMapper adminMapper;
    private final TokenStore tokenStore;

    // --- 0. 管理员登录与登出接口 ---
    @PostMapping("/login")
    public Result<?> login(@RequestBody Admin loginParam) {
        if (loginParam.getUsername() == null || loginParam.getPassword() == null) {
            return Result.error("账号和密码不能为空");
        }
        Admin admin = adminMapper.login(loginParam.getUsername().trim(), loginParam.getPassword().trim());
        if (admin == null) {
            return Result.error("账号或密码错误");
        }
        String token = tokenStore.createToken(admin);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("nickname", admin.getNickname());
        return Result.ok(data);
    }

    @PostMapping("/logout")
    public Result<?> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        tokenStore.removeToken(token);
        return Result.ok("已安全退出");
    }

    // --- 1. 爬虫调度与日志接口 ---
    @PostMapping("/crawler/start")
    public Result<CrawlLog> startCrawl(@RequestParam(defaultValue = "1") int pages) {
        if (pages < 1 || pages > 5) {
            return Result.error("单次采集页数限制在 1~5 页之间，确保合规与响应速度");
        }
        return Result.ok(crawlerService.executeCrawl(pages));
    }

    @GetMapping("/crawler/logs")
    public Result<?> getCrawlLogs() {
        return Result.ok(crawlLogMapper.findRecent());
    }

    // --- 2. 商品信息完整 CRUD 接口 ---
    @GetMapping("/products")
    public Result<?> listProducts(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) Long categoryId) {
        int offset = (page - 1) * size;
        Map<String, Object> data = new HashMap<>();
        data.put("list", productMapper.selectPage(offset, size, keyword, categoryId));
        data.put("total", productMapper.countTotal(keyword, categoryId));
        return Result.ok(data);
    }

    @PostMapping("/products")
    public Result<?> saveProduct(@RequestBody Product product) {
        if (product.getTitle() == null || product.getTitle().isBlank()) {
            return Result.error("商品标题不能为空");
        }
        if (product.getId() == null) {
            product.setSourceUrl("manual://" + System.currentTimeMillis());
            product.setStatus(1);
            productMapper.insert(product);
        } else {
            productMapper.update(product);
        }
        return Result.ok("商品保存成功");
    }

    @PutMapping("/products/{id}/status")
    public Result<?> toggleStatus(@PathVariable Long id) {
        productMapper.toggleStatus(id);
        return Result.ok("状态已更新");
    }

    @DeleteMapping("/products/{id}")
    public Result<?> deleteProduct(@PathVariable Long id) {
        productMapper.deleteById(id);
        return Result.ok("商品已删除");
    }

    // --- 3. 商品分类完整 CRUD 接口 ---
    @GetMapping("/categories")
    public Result<?> listCategories() {
        return Result.ok(categoryMapper.findAllWithCount());
    }

    @PostMapping("/categories")
    public Result<?> saveCategory(@RequestBody Category category) {
        if (category.getCategoryName() == null || category.getCategoryName().isBlank()) {
            return Result.error("分类名称不能为空");
        }
        String cleanName = category.getCategoryName().trim();
        Category exist = categoryMapper.findByName(cleanName);
        if (exist != null && !exist.getId().equals(category.getId())) {
            return Result.error("该分类名称已存在");
        }
        category.setCategoryName(cleanName);
        if (category.getId() == null) {
            categoryMapper.insert(category);
        } else {
            categoryMapper.update(category);
        }
        return Result.ok("分类保存成功");
    }

    @DeleteMapping("/categories/{id}")
    public Result<?> deleteCategory(@PathVariable Long id) {
        int count = categoryMapper.countProductsByCategoryId(id);
        if (count > 0) {
            return Result.error("删除失败：该分类下仍关联有 " + count + " 件商品，请先转移或删除相关商品");
        }
        categoryMapper.deleteById(id);
        return Result.ok("分类已删除");
    }
}
