package org.example.demo1.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.Result;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.service.CosService;
import org.example.demo1.util.UserContext;
import org.example.demo1.vo.FileUploadVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传（COS），供小程序智能问答附件使用
 */
@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final CosService cosService;

    /**
     * 上传单个文件到 COS
     * POST /api/files/upload  multipart/form-data 字段名 file
     */
    @PostMapping("/upload")
    public Result<FileUploadVO> upload(@RequestParam("file") MultipartFile file) {
        Long userId = requireLogin();
        String url = cosService.upload(file);
        FileUploadVO vo = new FileUploadVO();
        vo.setUrl(url);
        vo.setFileName(file.getOriginalFilename());
        log.info("用户上传问答附件: userId={}, fileName={}", userId, vo.getFileName());
        return Result.success(vo);
    }

    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
