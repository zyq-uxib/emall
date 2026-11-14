package com.edu.mall.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Product {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String title;
    private BigDecimal price;
    private Integer stock;
    private Integer rating;
    private String imageUrl;
    private String sourceUrl;
    private Integer status;
    private LocalDateTime createTime;
}
