package org.example.demo1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.demo1.entity.LegalCase;
import org.example.demo1.vo.CaseListVO;

@Mapper
public interface LegalCaseMapper extends BaseMapper<LegalCase> {

    /**
     * 全文搜索案例（分页）
     * isAdmin=true 时返回所有案例，否则只返回 ai_status=2 且 is_deleted=0 的案例
     */
    IPage<CaseListVO> searchCases(Page<CaseListVO> page,
                                  @Param("keyword") String keyword,
                                  @Param("caseType") Integer caseType,
                                  @Param("country") String country,
                                  @Param("userId") Long userId,
                                  @Param("isAdmin") boolean isAdmin);

    /**
     * 恢复逻辑删除（绕过 @TableLogic，直接将 is_deleted 置为 0）
     */
    @Update("UPDATE legal_case SET is_deleted = 0 WHERE id = #{caseId} AND is_deleted = 1")
    int restoreById(@Param("caseId") Long caseId);

    /**
     * 物理删除案例（绕过 @TableLogic，直接执行 DELETE）
     */
    @Delete("DELETE FROM legal_case WHERE id = #{caseId}")
    int physicalDeleteById(@Param("caseId") Long caseId);
}
