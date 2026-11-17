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

    // 修改密码接口
    @PutMapping("/admin/password")
    public Result<?> changePassword(@RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> params) {
        Admin currentAdmin = tokenStore.getAdmin(token);
        if (currentAdmin == null) {
            return Result.unauthorized("登录已过期，请重新登录");
        }
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        if (oldPassword == null || newPassword == null || newPassword.trim().length() < 4) {
            return Result.error("新密码不能为空且长度不少于 4 位");
        }
        int rows = adminMapper.updatePassword(currentAdmin.getId(), oldPassword.trim(), newPassword.trim());
        if (rows == 0) {
            return Result.error("原密码输入错误（请严格注意大小写）");
        }
        // 修改成功后立即销毁当前登录令牌，强制使用新密码重新登录
        tokenStore.removeToken(token);
        return Result.ok("密码修改成功，请重新登录");
    }

    // --- 1A. HTML 网页爬虫接口 (://toscrape.com) ---
    @PostMapping("/crawler/books")
    public Result<CrawlLog> crawlHtmlBooks(@RequestParam(defaultValue = "1") int startPage,
            @RequestParam(defaultValue = "5") int endPage) {
        if (startPage < 1 || endPage > 50 || startPage > endPage) {
            return Result.error("网页爬虫页码范围无效，请输入 1~50 之间的有效页码");
        }
        if (endPage - startPage + 1 > 10) {
            return Result.error("单次最多连续采集 10 页（约 200 本图书），请分批次采集");
        }
        return Result.ok(crawlerService.crawlHtmlBooks(startPage, endPage));
    }

    // --- 1D. 开源真实书目 CSV 批量导入接口 ---
    @PostMapping("/crawler/import-csv")
    public Result<CrawlLog> importCsvBooks(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("请选择要上传的 CSV 文件");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
            return Result.error("文件格式错误，仅支持 .csv 格式的书目数据集");
        }
        try {
            CrawlLog log = crawlerService.importBooksFromCsv(file.getInputStream(), originalFilename);
            return Result.ok(log);
        } catch (Exception e) {
            return Result.error("文件读取失败：" + e.getMessage());
        }
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
