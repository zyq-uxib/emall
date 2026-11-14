package com.edu.mall.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Category {
    private Long id;
    private String categoryName;
    private Integer productCount;
    private LocalDateTime createTime;
}
