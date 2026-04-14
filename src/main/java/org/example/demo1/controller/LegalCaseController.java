package org.example.demo1.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.Result;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.CaseNoteSaveDTO;
import org.example.demo1.dto.CaseQueryDTO;
import org.example.demo1.dto.CaseSaveDTO;
import org.example.demo1.dto.CaseUpdateDTO;
import org.example.demo1.service.HotKeywordService;
import org.example.demo1.service.LegalCaseService;
import org.example.demo1.util.UserContext;
import org.example.demo1.vo.BrowseHistoryVO;
import org.example.demo1.vo.CaseDetailVO;
import org.example.demo1.vo.CaseListVO;
import org.example.demo1.vo.CaseNoteListItemVO;
import org.example.demo1.vo.CaseNoteVO;
import org.example.demo1.vo.CaseScoreRecordVO;
import org.example.demo1.vo.CaseSummaryRecordVO;
import org.example.demo1.vo.CaseTranslationRecordVO;

import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 案例管理接口
 *
 * <p>权限说明：
 * <ul>
 *   <li>公开接口（无需登录）：查询列表、查看详情</li>
 *   <li>登录接口：收藏/取消收藏、查看收藏列表</li>
 *   <li>管理员接口：新增、修正、删除、恢复、触发AI处理</li>
 * </ul>
 *
 * <p>普通用户查询时只能看到 ai_status=2（AI处理完成）且未删除的案例；
 * 管理员可查看所有状态的案例。
 */
@RestController
@RequestMapping("/cases")
@RequiredArgsConstructor
public class LegalCaseController {

    private final LegalCaseService legalCaseService;
    private final HotKeywordService hotKeywordService;

    // ─────────────────────────────────────────────────────────────────────────
    // 查询
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 分页查询案例列表。
     * <p>普通用户只能获取已完成AI处理且未删除的案例；管理员可获取全部案例。
     */
    @GetMapping
    public Result<IPage<CaseListVO>> queryCases(CaseQueryDTO dto) {
        Long userId = UserContext.getUserId();
        boolean isAdmin = UserContext.isAdmin();
        return Result.success(legalCaseService.queryCases(dto, userId, isAdmin));
    }

    /**
     * 热门搜索词（公开，用于小程序搜索页等）
     */
    @GetMapping("/hot-keywords")
    public Result<List<String>> hotKeywords(@RequestParam(defaultValue = "10") Integer limit) {
        int n = limit == null ? 10 : Math.min(Math.max(limit, 1), 30);
        return Result.success(hotKeywordService.listEnabledKeywords(n));
    }

    /**
     * 获取案例详情，同时返回收藏状态、摘要及评分信息。
     * 管理员可查看已逻辑删除的案例详情。
     */
    @GetMapping("/{id}")
    public Result<CaseDetailVO> getCaseDetail(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        boolean isAdmin = UserContext.isAdmin();
        return Result.success(legalCaseService.getCaseDetail(id, userId, isAdmin));
    }

    /**
     * 代理获取案例原始 PDF 文书（公开接口）。
     * <p>后端以服务器身份向日本裁判所请求 PDF，携带必要的 Referer/User-Agent 头绕过防盗链，
     * 再将字节流透传给微信小程序的 wx.downloadFile，从而实现小程序内直接打开 PDF 文书。
     */
    @GetMapping("/{id}/pdf-proxy")
    public ResponseEntity<byte[]> proxyPdf(@PathVariable Long id) {
        byte[] pdfBytes = legalCaseService.proxyPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.inline().filename("case_" + id + ".pdf").build());
        headers.setContentLength(pdfBytes.length);
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 管理员 - 案例维护
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 新增案例（管理员）。
     * <p>保存后自动在后台异步触发完整的AI处理流程（翻译 → 摘要 → 评分）。
     */
    @PostMapping
    public Result<Long> saveCase(@Valid @RequestBody CaseSaveDTO dto) {
        requireAdmin();
        Long caseId = legalCaseService.saveCase(dto);
        return Result.success("案例保存成功，AI处理已在后台启动", caseId);
    }

    /**
     * 修正案例内容（管理员）。
     * <p>用于对AI处理结果进行人工校正，仅覆盖请求体中非 null 的字段，
     * 不会重置 ai_status，也不会重新触发AI处理。
     */
    @PutMapping("/{id}")
    public Result<Void> updateCase(@PathVariable Long id,
                                   @RequestBody CaseUpdateDTO dto) {
        requireAdmin();
        legalCaseService.updateCase(id, dto);
        return Result.success("案例内容已更新");
    }

    /**
     * 手动将案例 AI 处理状态标记为已完成（管理员）。
     * <p>用于 AI 处理卡住或失败后的人工干预，将 ai_status 强制置为 2（已完成），
     * 使案例对普通用户可见。
     */
    @PutMapping("/{id}/ai-complete")
    public Result<Void> markAiCompleted(@PathVariable Long id) {
        requireAdmin();
        legalCaseService.markAiCompleted(id);
        return Result.success("AI状态已标记为已完成");
    }

    /**
     * 将案例推送至 FastGPT 知识库（管理员）。
     * <p>异步执行，要求 ai_status=2；重复推送会在知识库中产生多个集合，请按需使用。
     */
    @PostMapping("/{id}/sync-fastgpt")
    public Result<Void> syncFastgptKnowledge(@PathVariable Long id) {
        requireAdmin();
        legalCaseService.triggerFastgptKnowledgeSync(id);
        return Result.success("知识库同步任务已提交，正在后台处理");
    }

    /**
     * 逻辑删除案例（管理员）。
     * <p>将 is_deleted 置为 1，案例对普通用户不可见，可通过恢复接口撤销。
     */
    @DeleteMapping("/{id}")
    public Result<Void> softDeleteCase(@PathVariable Long id) {
        requireAdmin();
        legalCaseService.softDeleteCase(id);
        return Result.success("案例已删除");
    }

    /**
     * 恢复逻辑删除的案例（管理员）。
     * <p>将 is_deleted 重新置为 0，案例恢复对普通用户可见。
     */
    @PutMapping("/{id}/restore")
    public Result<Void> restoreCase(@PathVariable Long id) {
        requireAdmin();
        legalCaseService.restoreCase(id);
        return Result.success("案例已恢复");
    }

    /**
     * 物理删除案例（管理员）。
     * <p>从数据库中彻底移除记录，操作不可逆，请谨慎使用。
     */
    @DeleteMapping("/{id}/permanent")
    public Result<Void> hardDeleteCase(@PathVariable Long id) {
        requireAdmin();
        legalCaseService.hardDeleteCase(id);
        return Result.success("案例已永久删除");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 管理员 - 手动触发AI处理
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 手动触发翻译（管理员）。
     * <p>异步执行，立即返回。可通过 GET /{id}/translations 查询处理进度。
     */
    @PostMapping("/{id}/translate")
    public Result<Void> triggerTranslation(@PathVariable Long id) {
        requireAdmin();
        legalCaseService.triggerTranslation(id);
        return Result.success("翻译任务已提交，正在后台处理");
    }

    /**
     * 手动触发字段补全（管理员）。
     * <p>异步执行，立即返回。提取案件类型、关键词、法律条文、国家/地区、法院等字段。
     */
    @PostMapping("/{id}/enrich")
    public Result<Void> triggerEnrich(@PathVariable Long id) {
        requireAdmin();
        legalCaseService.triggerEnrich(id);
        return Result.success("字段补全任务已提交，正在后台处理");
    }

    /**
     * 手动触发摘要提取（管理员）。
     * <p>异步执行，立即返回。可通过 GET /{id}/summaries 查询处理进度。
     */
    @PostMapping("/{id}/summary")
    public Result<Void> triggerSummary(@PathVariable Long id) {
        requireAdmin();
        legalCaseService.triggerSummary(id);
        return Result.success("摘要提取任务已提交，正在后台处理");
    }

    /**
     * 手动触发重要性评分（管理员）。
     * <p>异步执行，立即返回。可通过 GET /{id}/scores 查询处理进度。
     */
    @PostMapping("/{id}/score")
    public Result<Void> triggerScore(@PathVariable Long id) {
        requireAdmin();
        legalCaseService.triggerScore(id);
        return Result.success("评分任务已提交，正在后台处理");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 管理员 - AI处理记录查询
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 获取案例的所有翻译记录（管理员）。
     * <p>按创建时间倒序返回，可用于查看历次翻译的状态、耗时及失败原因。
     */
    @GetMapping("/{id}/translations")
    public Result<List<CaseTranslationRecordVO>> getTranslationRecords(@PathVariable Long id) {
        requireAdmin();
        return Result.success(legalCaseService.getTranslationRecords(id));
    }

    /**
     * 获取案例的所有摘要记录（管理员）。
     * <p>按创建时间倒序返回，可用于查看历次摘要提取的结果及失败原因。
     */
    @GetMapping("/{id}/summaries")
    public Result<List<CaseSummaryRecordVO>> getSummaryRecords(@PathVariable Long id) {
        requireAdmin();
        return Result.success(legalCaseService.getSummaryRecords(id));
    }

    /**
     * 获取案例的所有评分记录（管理员）。
     * <p>按创建时间倒序返回，可用于查看历次评分的各维度得分及失败原因。
     */
    @GetMapping("/{id}/scores")
    public Result<List<CaseScoreRecordVO>> getScoreRecords(@PathVariable Long id) {
        requireAdmin();
        return Result.success(legalCaseService.getScoreRecords(id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 登录用户 - 收藏
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 收藏或取消收藏案例（登录用户）。
     * <p>已收藏时调用则取消收藏，未收藏时调用则添加收藏，返回操作后的收藏状态。
     */
    @PostMapping("/{id}/favorite")
    public Result<Boolean> toggleFavorite(@PathVariable Long id) {
        Long userId = requireLogin();
        boolean isFavorited = legalCaseService.toggleFavorite(id, userId);
        return Result.success(isFavorited ? "收藏成功" : "已取消收藏", isFavorited);
    }

    /**
     * 获取我的收藏列表（登录用户）。
     */
    @GetMapping("/favorites")
    public Result<IPage<CaseListVO>> getFavorites(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = requireLogin();
        return Result.success(legalCaseService.getFavorites(userId, pageNum, pageSize));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 登录用户 - 浏览记录
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 获取我的浏览记录（登录用户）。
     * <p>按浏览时间倒序分页返回，每次查看案例详情时自动记录/更新浏览时间。
     */
    @GetMapping("/browse-history")
    public Result<IPage<BrowseHistoryVO>> getBrowseHistory(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = requireLogin();
        return Result.success(legalCaseService.getBrowseHistory(userId, pageNum, pageSize));
    }

    /**
     * 删除浏览记录（登录用户，支持批量）。
     * <p>请求体传入要删除的浏览记录 ID 列表，只能删除当前用户自己的记录。
     */
    @DeleteMapping("/browse-history")
    public Result<Void> deleteBrowseHistory(@RequestBody List<Long> ids) {
        Long userId = requireLogin();
        legalCaseService.deleteBrowseHistory(userId, ids);
        return Result.success("浏览记录已删除");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 登录用户 - 案例笔记（每用户每案例一条）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 获取当前用户对某案例的笔记。
     */
    @GetMapping("/{id}/note")
    public Result<CaseNoteVO> getMyCaseNote(@PathVariable Long id) {
        Long userId = requireLogin();
        return Result.success(legalCaseService.getMyCaseNote(id, userId));
    }

    /**
     * 保存或更新笔记；正文为空或仅空白则删除该笔记。
     */
    @PutMapping("/{id}/note")
    public Result<Void> saveMyCaseNote(@PathVariable Long id, @Valid @RequestBody CaseNoteSaveDTO dto) {
        Long userId = requireLogin();
        legalCaseService.saveMyCaseNote(id, userId, dto.getContent());
        return Result.success();
    }

    /**
     * 我的案例笔记列表（含案例标题、内容摘要）。
     */
    @GetMapping("/notes/list")
    public Result<IPage<CaseNoteListItemVO>> listMyCaseNotes(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = requireLogin();
        return Result.success(legalCaseService.getMyCaseNotes(userId, pageNum, pageSize));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 权限校验工具方法
    // ─────────────────────────────────────────────────────────────────────────

    /** 要求用户已登录，返回当前用户ID。未登录则抛出 401。 */
    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    /** 要求当前用户为管理员。未登录抛出 401，非管理员抛出 403。 */
    private void requireAdmin() {
        requireLogin();
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
