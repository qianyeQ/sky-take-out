package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    public User getByOpenid(String openid);

    @Options(useGeneratedKeys = true,keyProperty = "id")
    @Insert("insert into user (openid, create_time) values (#{openid},#{createTime}) ")
    void insert(User user);

    @Select("select name from user where id = #{id}")
    String getNameById(Long id);

    @Select("select * from user where id = #{userId}")
    User getById(Long userId);
}
