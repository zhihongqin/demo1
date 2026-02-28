package org.example.demo1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.demo1.entity.UserFavorite;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
}
