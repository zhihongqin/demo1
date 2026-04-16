package org.example.demo1.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.demo1.dto.CrawlJobQueryDTO;
import org.example.demo1.vo.CrawlJobRecordVO;

public interface CrawlJobRecordService {

    Long startJob(String crawlType, String paramsJson, Long startedBy);

    void finishSuccess(Long jobId, Integer savedCount);

    void finishFailure(Long jobId, String errorMessage);

    IPage<CrawlJobRecordVO> pageForAdmin(CrawlJobQueryDTO query);
}
