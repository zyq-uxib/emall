package com.edu.mall.mapper;

import com.edu.mall.entity.Product;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ProductMapper {
    List<Product> selectPage(@Param("offset") int offset,
                             @Param("size") int size,
                             @Param("keyword") String keyword,
                             @Param("categoryId") Long categoryId);

    long countTotal(@Param("keyword") String keyword, @Param("categoryId") Long categoryId);

    @Select("SELECT COUNT(1) FROM t_product WHERE source_url = #{sourceUrl}")
    int existsBySourceUrl(String sourceUrl);

    int insert(Product product);
    int batchInsertIgnore(@Param("list") List<Product> list);


    int update(Product product);

    @Update("UPDATE t_product SET status = IF(status=1, 0, 1) WHERE id = #{id}")
    int toggleStatus(Long id);

    @Delete("DELETE FROM t_product WHERE id = #{id}")
    int deleteById(Long id);
}
