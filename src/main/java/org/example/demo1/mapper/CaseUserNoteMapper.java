package org.example.demo1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.demo1.entity.CaseUserNote;
import org.example.demo1.vo.CaseNoteListItemVO;

@Mapper
public interface CaseUserNoteMapper extends BaseMapper<CaseUserNote> {

    @Select("""
            SELECT n.case_id AS caseId,
                   SUBSTRING(n.content, 1, 160) AS contentPreview,
                   n.updated_at AS updatedAt,
                   c.title_zh AS titleZh,
                   c.title_en AS titleEn
            FROM case_user_note n
            INNER JOIN legal_case c ON c.id = n.case_id AND (c.is_deleted = 0 OR c.is_deleted IS NULL)
            WHERE n.user_id = #{userId}
            ORDER BY n.updated_at DESC
            """)
    IPage<CaseNoteListItemVO> selectMyNotesPage(Page<?> page, @Param("userId") Long userId);
}
