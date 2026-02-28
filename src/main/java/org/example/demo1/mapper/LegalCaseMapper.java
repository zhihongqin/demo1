package org.example.demo1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.demo1.entity.LegalCase;
import org.example.demo1.vo.CaseListVO;

@Mapper
public interface LegalCaseMapper extends BaseMapper<LegalCase> {

    /**
     * 全文搜索案例（分页）
     */
    IPage<CaseListVO> searchCases(Page<CaseListVO> page,
                                  @Param("keyword") String keyword,
                                  @Param("caseType") Integer caseType,
                                  @Param("country") String country,
                                  @Param("userId") Long userId);
}
