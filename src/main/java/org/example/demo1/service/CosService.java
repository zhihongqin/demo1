package org.example.demo1.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.config.TencentCosProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 腾讯云 COS 上传（智能问答附件）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CosService {

    private static final long MAX_BYTES = 20L * 1024 * 1024;
    /** 问答附件：图片类，需通过魔数校验 */
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic");
    /** 问答附件：文书类，需通过魔数或文本编码校验 */
    private static final Set<String> DOCUMENT_EXT = Set.of("pdf", "doc", "docx", "txt");
    private static final Set<String> ALLOWED_EXT = Set.of(
            "pdf", "doc", "docx", "txt",
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic"
    );

    private final TencentCosProperties props;

    private volatile COSClient client;

    private COSClient cosClient() {
        if (!StringUtils.hasText(props.getSecretId()) || !StringUtils.hasText(props.getSecretKey())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "未配置 COS 密钥，无法上传文件");
        }
        if (!StringUtils.hasText(props.getBucket()) || !StringUtils.hasText(props.getPublicBaseUrl())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "未配置 COS 存储桶或公网地址");
        }
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    COSCredentials cred = new BasicCOSCredentials(props.getSecretId(), props.getSecretKey());
                    ClientConfig config = new ClientConfig(new Region(props.getRegion()));
                    client = new COSClient(cred, config);
                }
            }
        }
        return client;
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.shutdown();
            client = null;
        }
    }

    /**
     * 上传文件到 COS，返回公网 URL
     */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件大小不能超过 20MB");
        }

        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件名无效");
        }
        if (original.contains("..") || original.contains("/") || original.contains("\\")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件名不合法");
        }

        String ext = extension(original);
        String extLower = ext.toLowerCase(Locale.ROOT);
        if (ext.isEmpty() || !ALLOWED_EXT.contains(extLower)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "仅支持文档（pdf、doc、docx、txt）或图片（jpg、png、gif、webp、bmp、heic）");
        }

        final byte[] payload;
        try {
            payload = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "读取文件失败");
        }
        if (IMAGE_EXT.contains(extLower) && !verifyImageSignature(payload, extLower)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "图片文件内容与类型不符或已损坏，请重新选择");
        }
        if (DOCUMENT_EXT.contains(extLower) && !verifyDocumentSignature(payload, extLower)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文档文件内容与类型不符或已损坏，请重新选择");
        }

        String safeBase = sanitizeBaseName(original);
        String prefix = props.getPathPrefix();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        String key = prefix + UUID.randomUUID() + "_" + safeBase + "." + extLower;

        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(payload.length);
        String mime = resolveContentType(file.getContentType(), extLower);
        if (StringUtils.hasText(mime)) {
            meta.setContentType(mime);
        }

        try (InputStream is = new ByteArrayInputStream(payload)) {
            cosClient().putObject(new PutObjectRequest(props.getBucket(), key, is, meta));
        } catch (IOException e) {
            log.error("COS 上传 IO 异常", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "上传失败");
        }

        String base = props.getPublicBaseUrl().replaceAll("/+$", "");
        String url = base + "/" + key;
        log.info("COS 上传成功: key={}, size={}", key, payload.length);
        return url;
    }

    /**
     * 客户端常带 octet-stream 或空 MIME，按扩展名补全，便于 COS / 下游识别图片。
     */
    private static String resolveContentType(String fromMultipart, String extLower) {
        if (StringUtils.hasText(fromMultipart)) {
            String m = fromMultipart.strip();
            if (!m.isEmpty() && !"application/octet-stream".equalsIgnoreCase(m)) {
                return m;
            }
        }
        return switch (extLower) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "heic" -> "image/heic";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain; charset=UTF-8";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> null;
        };
    }

    private static boolean verifyDocumentSignature(byte[] data, String extLower) {
        if (data == null) {
            return false;
        }
        return switch (extLower) {
            case "pdf" -> verifyPdfSignature(data);
            case "doc" -> verifyOleCompoundSignature(data);
            case "docx" -> verifyZipLocalHeaderSignature(data);
            case "txt" -> verifyPlainTextPayload(data);
            default -> false;
        };
    }

    /**
     * PDF：允许首部少量空白后须出现 %PDF（与常见实现一致，兼容带前导空白的文件）。
     */
    private static boolean verifyPdfSignature(byte[] data) {
        if (data.length < 5) {
            return false;
        }
        int i = 0;
        int limit = Math.min(data.length, 1024);
        while (i < limit && isPdfLeadingWhitespace(data[i])) {
            i++;
        }
        return i + 4 < data.length
                && data[i] == '%'
                && data[i + 1] == 'P'
                && data[i + 2] == 'D'
                && data[i + 3] == 'F';
    }

    private static boolean isPdfLeadingWhitespace(byte b) {
        return b == 0x09 || b == 0x0A || b == 0x0C || b == 0x0D || b == 0x20;
    }

    /** 老版 Word（.doc）：Microsoft OLE Compound Document */
    private static boolean verifyOleCompoundSignature(byte[] data) {
        if (data.length < 8) {
            return false;
        }
        return (data[0] & 0xFF) == 0xD0
                && (data[1] & 0xFF) == 0xCF
                && (data[2] & 0xFF) == 0x11
                && (data[3] & 0xFF) == 0xE0
                && (data[4] & 0xFF) == 0xA1
                && (data[5] & 0xFF) == 0xB1
                && (data[6] & 0xFF) == 0x1A
                && (data[7] & 0xFF) == 0xE1;
    }

    /** .docx 等为 ZIP，本地文件头以 PK\x03\x04 开头（空归档可为 PK\x05\x06） */
    private static boolean verifyZipLocalHeaderSignature(byte[] data) {
        if (data.length < 4) {
            return false;
        }
        if (data[0] != 'P' || data[1] != 'K') {
            return false;
        }
        int third = data[2] & 0xFF;
        int fourth = data[3] & 0xFF;
        return (third == 0x03 && fourth == 0x04)
                || (third == 0x05 && fourth == 0x06)
                || (third == 0x07 && fourth == 0x08);
    }

    /**
     * 纯文本：须非空；支持 UTF-8（可选 BOM）、UTF-16 BOM、或 GBK（兼容中文 Windows 记事本）。
     */
    private static boolean verifyPlainTextPayload(byte[] data) {
        if (data.length == 0) {
            return false;
        }
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xEF
                && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            return strictDecode(data, 3, data.length - 3, StandardCharsets.UTF_8);
        }
        if (data.length >= 2 && data[0] == (byte) 0xFF && data[1] == (byte) 0xFE) {
            return strictDecode(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
        }
        if (data.length >= 2 && data[0] == (byte) 0xFE && data[1] == (byte) 0xFF) {
            return strictDecode(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
        }
        if (strictDecode(data, 0, data.length, StandardCharsets.UTF_8)) {
            return true;
        }
        return strictDecode(data, 0, data.length, Charset.forName("GBK"));
    }

    private static boolean strictDecode(byte[] data, int offset, int length, Charset charset) {
        if (length <= 0) {
            return false;
        }
        try {
            charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, offset, length));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /**
     * 按扩展名校验图片文件头，降低伪造扩展名风险。
     */
    private static boolean verifyImageSignature(byte[] data, String extLower) {
        if (data == null || data.length < 12) {
            return false;
        }
        return switch (extLower) {
            case "jpg", "jpeg" -> data.length >= 3
                    && (data[0] & 0xFF) == 0xFF
                    && (data[1] & 0xFF) == 0xD8
                    && (data[2] & 0xFF) == 0xFF;
            case "png" -> data.length >= 8
                    && data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47
                    && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A;
            case "gif" -> data.length >= 6
                    && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8'
                    && (data[4] == '7' || data[4] == '9') && data[5] == 'a';
            case "webp" -> data.length >= 12
                    && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
            case "bmp" -> data.length >= 2 && data[0] == 'B' && data[1] == 'M';
            case "heic" -> isHeicContainer(data);
            default -> false;
        };
    }

    /** ISO BMFF：偏移 4 起为 ftyp，品牌区常见 heic / mif1 / msf1 等 */
    private static boolean isHeicContainer(byte[] data) {
        if (data.length < 12) {
            return false;
        }
        if (data[4] != 'f' || data[5] != 't' || data[6] != 'y' || data[7] != 'p') {
            return false;
        }
        int asciiLen = Math.min(32, data.length - 8);
        if (asciiLen <= 0) {
            return false;
        }
        String brands = new String(data, 8, asciiLen, StandardCharsets.US_ASCII);
        return brands.contains("heic") || brands.contains("heix") || brands.contains("mif1")
                || brands.contains("msf1") || brands.contains("hevc") || brands.contains("hevx");
    }

    private static String extension(String filename) {
        int i = filename.lastIndexOf('.');
        if (i < 0 || i >= filename.length() - 1) {
            return "";
        }
        return filename.substring(i + 1);
    }

    /** 去掉扩展名后只保留安全字符，限制长度 */
    private static String sanitizeBaseName(String filename) {
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String cleaned = base.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5._-]", "_");
        if (cleaned.isBlank()) {
            cleaned = "file";
        }
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }
}
