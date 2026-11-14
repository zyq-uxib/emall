package com.edu.mall.mapper;

import com.edu.mall.entity.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminMapper {
    @Select("SELECT id, username, nickname FROM t_admin WHERE BINARY username = #{username} AND BINARY password = #{password}")
    Admin login(@Param("username") String username, @Param("password") String password);

    @Update("UPDATE t_admin SET password = #{newPassword} WHERE id = #{id} AND BINARY password = #{oldPassword}")
    int updatePassword(@Param("id") Long id, @Param("oldPassword") String oldPassword, @Param("newPassword") String newPassword);
}
