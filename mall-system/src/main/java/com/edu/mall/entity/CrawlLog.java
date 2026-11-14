package com.edu.mall.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CrawlLog {
    private Long id;
    private String targetUrl;
    private Integer successCount;
    private String status;
    private Long costTimeMs;
    private LocalDateTime createTime;
}
