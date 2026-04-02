package org.example.demo1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.demo1.entity.ChatTask;

@Mapper
public interface ChatTaskMapper extends BaseMapper<ChatTask> {
}
