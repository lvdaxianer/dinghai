package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import java.io.InputStream;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 提供受宿主授权和会话归属保护的 multipart HTTP 生命周期端点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@RestController
@RequestMapping("${dinghai.file-storage.web.path-prefix:/files}")
public class MultipartUploadController {

    private final MultipartUploadService service;

    /**
     * 创建 multipart HTTP 控制器。
     *
     * @param service multipart 会话服务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    MultipartUploadController(MultipartUploadService service) {
        this.service = java.util.Objects.requireNonNull(service, "service");
    }

    /**
     * 创建受授权保护的 multipart 上传会话。
     *
     * @param request 客户端会话创建请求
     * @param principal 当前认证主体
     * @return 已创建的 multipart 会话
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @PostMapping(value = "/multipart", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MultipartUploadSession> initiate(@RequestBody MultipartInitiateRequest request,
                                                            Principal principal) {
        try {
            MultipartUploadSession session = service.initiate(principal, initiateRequest(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(session);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidRequest(exception);
        }
    }

    /**
     * 为指定会话分片返回 provider 原生上传授权。
     *
     * @param uploadId multipart 会话标识
     * @param partNumber 分片编号
     * @param principal 当前认证主体
     * @return provider 分片上传授权
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @PostMapping(value = "/multipart/{uploadId}/parts/{partNumber}/presign",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PresignedUpload presignPart(@PathVariable String uploadId, @PathVariable int partNumber,
                                       Principal principal) {
        return service.presignPart(principal, uploadId, partNumber);
    }

    /**
     * 查询当前会话已确认的分片。
     *
     * @param uploadId multipart 会话标识
     * @param principal 当前认证主体
     * @return 按分片编号排序的已确认分片
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @GetMapping(value = "/multipart/{uploadId}/parts", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MultipartPart> listParts(@PathVariable String uploadId, Principal principal) {
        return service.listParts(principal, uploadId);
    }

    /**
     * 仅由 Local provider 接收 multipart 分片数据。
     *
     * @param uploadId multipart 会话标识
     * @param partNumber 分片编号
     * @param contentLength HTTP 请求体长度
     * @param content 分片输入流
     * @param principal 当前认证主体
     * @return 已确认的分片信息
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @PutMapping(value = "/multipart/{uploadId}/parts/{partNumber}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public MultipartPart uploadPart(@PathVariable String uploadId, @PathVariable int partNumber,
                                    @RequestHeader(value = HttpHeaders.CONTENT_LENGTH,
                                            required = false, defaultValue = "-1") long contentLength,
                                    InputStream content, Principal principal) {
        if (!service.acceptsLocalPartUpload()) {
            throw new FileStorageException(FileStorageErrorCode.MULTIPART_INVALID,
                    "multipart part upload is not available");
        }
        return service.uploadPart(principal, uploadId, partNumber, content, contentLength);
    }

    /**
     * 完成会话并返回确认后的对象元数据。
     *
     * @param uploadId multipart 会话标识
     * @param request 客户端提交的完成清单
     * @param principal 当前认证主体
     * @return 已完成对象的元数据
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @PostMapping(value = "/multipart/{uploadId}/complete", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ObjectMetadata complete(@PathVariable String uploadId,
                                   @RequestBody MultipartCompleteRequest request, Principal principal) {
        try {
            return service.complete(principal, uploadId, completion(request));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidRequest(exception);
        }
    }

    /**
     * 取消会话；重复调用保持成功语义。
     *
     * @param uploadId multipart 会话标识
     * @param principal 当前认证主体
     * @return 空响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @DeleteMapping("/multipart/{uploadId}")
    public ResponseEntity<Void> abort(@PathVariable String uploadId, Principal principal) {
        service.abort(principal, uploadId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 将 multipart 路由和 JSON 绑定错误转换为稳定错误响应。
     *
     * @param exception Spring 绑定异常
     * @return 稳定 multipart 参数错误响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleInvalidMultipartRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", FileStorageErrorCode.MULTIPART_INVALID.name(),
                        "message", "multipart request is invalid"));
    }

    /**
     * 创建稳定的 multipart 参数异常。
     *
     * @param exception 原始参数异常
     * @return 稳定文件存储异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private FileStorageException invalidRequest(RuntimeException exception) {
        return new FileStorageException(FileStorageErrorCode.MULTIPART_INVALID,
                "multipart request is invalid", exception);
    }

    /**
     * 将创建请求转换为已校验的核心请求。
     *
     * @param request HTTP 创建请求
     * @return 核心 multipart 创建请求
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest initiateRequest(
            MultipartInitiateRequest request) {
        return java.util.Objects.requireNonNull(request, "request").toCoreRequest();
    }

    /**
     * 将完成请求转换为已校验的核心清单。
     *
     * @param request HTTP 完成请求
     * @return 核心 multipart 完成清单
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion completion(
            MultipartCompleteRequest request) {
        return java.util.Objects.requireNonNull(request, "request").toCompletion();
    }
}
