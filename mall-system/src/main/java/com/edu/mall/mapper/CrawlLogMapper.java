package com.edu.mall.mapper;

import com.edu.mall.entity.CrawlLog;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CrawlLogMapper {
    @Insert("INSERT INTO t_crawl_log(target_url, success_count, status, cost_time_ms) " +
            "VALUES(#{targetUrl}, #{successCount}, #{status}, #{costTimeMs})")
    int insert(CrawlLog log);

    @Select("SELECT * FROM t_crawl_log ORDER BY id DESC LIMIT 10")
    List<CrawlLog> findRecent();
}
