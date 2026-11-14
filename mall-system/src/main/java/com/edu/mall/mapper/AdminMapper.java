package com.edu.mall.mapper;

import com.edu.mall.entity.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminMapper {
    @Select("SELECT id, username, nickname FROM t_admin WHERE username = #{username} AND password = #{password}")
    Admin login(@Param("username") String username, @Param("password") String password);
}
