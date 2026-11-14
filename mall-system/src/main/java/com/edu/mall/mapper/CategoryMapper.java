package com.edu.mall.mapper;

import com.edu.mall.entity.Category;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CategoryMapper {
    @Select("SELECT id, category_name, create_time FROM t_category WHERE category_name = #{name}")
    Category findByName(String name);

    @Insert("INSERT INTO t_category(category_name) VALUES(#{categoryName})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);

    @Update("UPDATE t_category SET category_name = #{categoryName} WHERE id = #{id}")
    int update(Category category);

    @Delete("DELETE FROM t_category WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT COUNT(1) FROM t_product WHERE category_id = #{categoryId}")
    int countProductsByCategoryId(Long categoryId);

    @Select("SELECT c.id, c.category_name, c.create_time, COUNT(p.id) AS product_count " +
            "FROM t_category c LEFT JOIN t_product p ON c.id = p.category_id " +
            "GROUP BY c.id ORDER BY c.id DESC")
    List<Category> findAllWithCount();
}
