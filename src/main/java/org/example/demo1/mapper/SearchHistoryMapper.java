package org.example.demo1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.demo1.entity.SearchHistory;
import org.example.demo1.vo.KeywordCountVO;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SearchHistoryMapper extends BaseMapper<SearchHistory> {

    @Select("""
            SELECT keyword, COUNT(*) AS cnt
            FROM search_history
            WHERE created_at >= #{since}
              AND keyword IS NOT NULL
              AND TRIM(keyword) <> ''
            GROUP BY keyword
            ORDER BY cnt DESC
            LIMIT #{limit}
            """)
    List<KeywordCountVO> aggregateKeywordCounts(@Param("since") LocalDateTime since,
                                                  @Param("limit") int limit);
}
