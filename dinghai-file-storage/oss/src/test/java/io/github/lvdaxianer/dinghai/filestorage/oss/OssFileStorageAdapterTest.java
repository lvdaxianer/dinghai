package io.github.lvdaxianer.dinghai.filestorage.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阿里云 OSS 适配器的最小契约测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-14
 */
public class OssFileStorageAdapterTest {

    /**
     * 验证 OSS 模块暴露后端中立的适配器入口。
     *
     * 前置条件：模块类路径包含公开适配器类型。
     * 执行步骤：按完整限定名反射加载适配器。
     * 断言结果：类型可以被正常发现和加载。
     *
     * 该检查保护模块发布时的公共类名契约。
     */
    @Test
    public void exposesOssFileStorageAdapter() throws Exception {
        assertNotNull(Class.forName(
                "io.github.lvdaxianer.dinghai.filestorage.oss.OssFileStorageAdapter"));
    }

    /**
     * 验证缺失 endpoint 会在配置构造阶段失败。
     *
     * 前置条件：endpoint 为空，其他凭据和 bucket 有效。
     * 执行步骤：创建 OSS 连接配置记录。
     * 断言结果：抛出参数校验异常且包含诊断消息。
     *
     * 该检查防止应用以不完整配置启动并延迟失败。
     */
    @Test
    public void rejectsIncompleteConfiguration() {
        try {
            new OssStorageConfiguration("", "uploads", "access", "secret");
            fail("expected configuration validation failure");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /**
     * 验证非正网络超时会被配置校验拒绝。
     *
     * 前置条件：连接超时为零，其他超时为正值。
     * 执行步骤：创建包含显式超时的 OSS 配置。
     * 断言结果：抛出参数校验异常且包含诊断消息。
     *
     * 该检查防止 SDK 调用失去整体时限控制。
     */
    @Test
    public void rejectsNonPositiveTimeout() {
        try {
            new OssStorageConfiguration("https://oss-cn-hangzhou.aliyuncs.com", "uploads",
                    "access", "secret", Duration.ZERO, Duration.ofSeconds(1),
                    Duration.ofSeconds(1));
            fail("expected timeout validation failure");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /**
     * 验证原生 POST policy 绑定对象和上传约束。
     *
     * 前置条件：SDK mock 返回固定 policy 和签名。
     * 执行步骤：生成带 MIME、缓存头和长度上限的上传授权。
     * 断言结果：表单字段和 SDK policy 条件均完整。
     *
     * 该检查保护浏览器直传的权限边界与请求约束。
     */
    @Test
    public void createsConstrainedPostPolicy() {
        OSS client = mock(OSS.class);
        when(client.generatePostPolicy(any(), any())).thenReturn("policy");
        when(client.calculatePostSignature("policy")).thenReturn("signature");
        OssFileStorageAdapter adapter = adapter(client);

        PresignedUpload result = adapter.presignUpload(new PresignUploadRequest(
                ObjectKey.of("users/avatar.png"), Duration.ofMinutes(5), "image/png", 1024,
                new CacheControl("public, max-age=60")));

        assertEquals("POST", result.method());
        assertEquals("public, max-age=60", result.requiredHeaders().get("Cache-Control"));
        assertEquals("users/avatar.png", result.formFields().get("key"));
        assertEquals("image/png", result.formFields().get("Content-Type"));
        assertEquals("public, max-age=60", result.formFields().get("Cache-Control"));
        assertEquals("access", result.formFields().get("OSSAccessKeyId"));
        assertEquals("policy", result.formFields().get("policy"));
        assertEquals("signature", result.formFields().get("Signature"));
        assertTrue(result.url().startsWith("https://uploads."));
        ArgumentCaptor<com.aliyun.oss.model.PolicyConditions> conditions =
                ArgumentCaptor.forClass(com.aliyun.oss.model.PolicyConditions.class);
        verify(client).generatePostPolicy(any(), conditions.capture());
        String policyConditions = conditions.getValue().jsonize().replace("\\/", "/");
        assertTrue(policyConditions.contains("users/avatar.png"));
        assertTrue(policyConditions.contains("image/png"));
        assertTrue(policyConditions.contains("public, max-age=60"));
        assertTrue(policyConditions.contains("content-length-range"));
    }

    /**
     * 验证自定义 OSS 域名不会被错误改写。
     *
     * 前置条件：endpoint 为非阿里云 CNAME 域名。
     * 执行步骤：生成原生 POST policy 上传授权。
     * 断言结果：返回地址保留调用方配置的自定义域名。
     *
     * 该检查保证 CNAME 部署的上传 action 可直接使用。
     */
    @Test
    public void preservesCustomDomainForPostPolicyAction() {
        OSS client = mock(OSS.class);
        when(client.generatePostPolicy(any(), any())).thenReturn("policy");
        when(client.calculatePostSignature("policy")).thenReturn("signature");
        OssFileStorageAdapter adapter = new OssFileStorageAdapter(client, "uploads",
                "https://files.example.com", "access");

        PresignedUpload result = adapter.presignUpload(new PresignUploadRequest(
                ObjectKey.of("report.pdf"), Duration.ofMinutes(5), "application/pdf", 1024));

        assertEquals("https://files.example.com", result.url());
    }

    /**
     * 验证原生下载 URL 绑定 GET 和响应覆盖头。
     *
     * 前置条件：SDK mock 返回可解析的预签名 URL。
     * 执行步骤：生成指定处置方式和缓存策略的下载授权。
     * 断言结果：方法、URL 和 SDK 请求响应头均符合输入。
     *
     * 该检查保护下载响应的缓存与浏览器展示语义。
     */
    @Test
    public void createsConstrainedDownloadUrl() throws Exception {
        OSS client = mock(OSS.class);
        when(client.generatePresignedUrl(any())).thenReturn(
                URI.create("https://oss.example/url").toURL());
        OssFileStorageAdapter adapter = adapter(client);

        PresignedDownload result = adapter.presignDownload(new PresignDownloadRequest(
                ObjectKey.of("reports/a.pdf"), Duration.ofMinutes(5),
                PresignDownloadRequest.ResponseDisposition.INLINE,
                new CacheControl("private, max-age=30")));

        assertEquals("GET", result.method());
        assertEquals("https://oss.example/url", result.url());
        ArgumentCaptor<com.aliyun.oss.model.GeneratePresignedUrlRequest> arguments =
                ArgumentCaptor.forClass(com.aliyun.oss.model.GeneratePresignedUrlRequest.class);
        verify(client).generatePresignedUrl(arguments.capture());
        assertEquals(com.aliyun.oss.HttpMethod.GET, arguments.getValue().getMethod());
        assertEquals("private, max-age=30",
                arguments.getValue().getResponseHeaders().getCacheControl());
    }

    /**
     * 验证写入使用固定 bucket 并返回服务端 metadata。
     *
     * 前置条件：SDK mock 可确认已写入对象的元数据。
     * 执行步骤：上传带长度、类型和缓存头的对象。
     * 断言结果：请求 bucket 固定且返回值来自确认查询。
     *
     * 该检查避免调用方输入改变存储边界或本地伪造 metadata。
     */
    @Test
    public void writesToFixedBucketAndReturnsConfirmedMetadata() {
        OSS client = mock(OSS.class);
        ObjectKey key = ObjectKey.of("reports/monthly.pdf");
        com.aliyun.oss.model.ObjectMetadata providerMetadata = metadata();
        when(client.getObjectMetadata("uploads", key.value())).thenReturn(providerMetadata);

        ObjectMetadata result = adapter(client).put(new io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest(
                key, new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "application/pdf",
                new CacheControl("public, max-age=60")));

        assertEquals(key, result.key());
        assertEquals(3L, result.contentLength());
        assertEquals("application/pdf", result.contentType());
        verify(client).putObject(org.mockito.ArgumentMatchers.eq("uploads"),
                org.mockito.ArgumentMatchers.eq(key.value()),
                org.mockito.ArgumentMatchers.<java.io.InputStream>any(), any());
        verify(client).getObjectMetadata("uploads", key.value());
    }

    /**
     * 验证未知长度流不会写入负内容长度。
     *
     * 前置条件：上传请求使用 -1 表示未知长度。
     * 执行步骤：捕获传递给 OSS SDK 的 metadata。
     * 断言结果：SDK metadata 不包含非法负长度。
     *
     * 该检查保护流式上传在 OSS SDK 中的兼容性。
     */
    @Test
    public void acceptsUnknownUploadLength() {
        OSS client = mock(OSS.class);
        ObjectKey key = ObjectKey.of("stream.bin");
        com.aliyun.oss.model.ObjectMetadata providerMetadata = metadata();
        when(client.getObjectMetadata("uploads", key.value())).thenReturn(providerMetadata);

        adapter(client).put(new io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest(key,
                new ByteArrayInputStream(new byte[]{1, 2, 3}), -1L,
                "application/octet-stream", CacheControl.privateNoCache()));

        ArgumentCaptor<com.aliyun.oss.model.ObjectMetadata> metadata = ArgumentCaptor.forClass(
                com.aliyun.oss.model.ObjectMetadata.class);
        verify(client).putObject(org.mockito.ArgumentMatchers.eq("uploads"),
                org.mockito.ArgumentMatchers.eq(key.value()),
                org.mockito.ArgumentMatchers.<java.io.InputStream>any(), metadata.capture());
        assertEquals(0L, metadata.getValue().getContentLength());
    }

    /**
     * 验证 metadata 查询的缺失对象返回空结果。
     *
     * 前置条件：SDK 返回 NoSuchKey 且消息包含敏感内容。
     * 执行步骤：调用适配器 stat 查询对象。
     * 断言结果：返回 Optional.empty 且不抛出远端异常。
     *
     * 该检查稳定化对象不存在的公共契约。
     */
    @Test
    public void returnsEmptyForMissingObject() {
        OSS client = mock(OSS.class);
        ObjectKey key = ObjectKey.of("missing.txt");
        when(client.getObjectMetadata("uploads", key.value())).thenThrow(
                ossException("NoSuchKey", "sensitive sdk body"));

        assertTrue(adapter(client).stat(key).isEmpty());
    }

    /**
     * 验证读取返回可关闭流和后端中立 metadata。
     *
     * 前置条件：SDK mock 提供对象流和完整 metadata。
     * 执行步骤：获取对象并读取全部内容后关闭结果。
     * 断言结果：内容长度和 ETag 与服务端值一致。
     *
     * 该检查保护流所有权和响应资源释放语义。
     */
    @Test
    public void getsObjectWithConfirmedMetadata() throws IOException {
        OSS client = mock(OSS.class);
        ObjectKey key = ObjectKey.of("reports/monthly.pdf");
        com.aliyun.oss.model.ObjectMetadata providerMetadata = metadata();
        com.aliyun.oss.model.OSSObject object = new com.aliyun.oss.model.OSSObject();
        object.setObjectContent(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(client.getObjectMetadata("uploads", key.value())).thenReturn(providerMetadata);
        when(client.getObject("uploads", key.value())).thenReturn(object);

        try (var result = adapter(client).get(key).orElseThrow()) {
            assertEquals(3, result.stream().readAllBytes().length);
            assertEquals("etag-1", result.metadata().etag());
        }
    }

    /**
     * 验证 stat 与读取之间的竞态删除仍返回空结果。
     *
     * 前置条件：stat 成功，随后 getObject 返回 NoSuchKey。
     * 执行步骤：调用适配器 get 读取对象。
     * 断言结果：适配器将竞态缺失转换为 Optional.empty。
     *
     * 该检查覆盖外部对象存储的并发删除窗口。
     */
    @Test
    public void returnsEmptyWhenObjectDisappearsBeforeRead() {
        OSS client = mock(OSS.class);
        ObjectKey key = ObjectKey.of("reports/disappeared.pdf");
        com.aliyun.oss.model.ObjectMetadata providerMetadata = metadata();
        when(client.getObjectMetadata("uploads", key.value())).thenReturn(providerMetadata);
        when(client.getObject("uploads", key.value())).thenThrow(
                ossException("NoSuchKey", "sensitive sdk body"));

        assertTrue(adapter(client).get(key).isEmpty());
    }

    /**
     * 验证写入后确认失败会抛出稳定存储错误。
     *
     * 前置条件：putObject 成功但确认查询返回 NoSuchKey。
     * 执行步骤：执行一次零长度对象上传。
     * 断言结果：返回 STORAGE_UNAVAILABLE 而不是裸 SDK 异常。
     *
     * 该检查保护外部成功与本地确认不一致时的恢复边界。
     */
    @Test
    public void mapsMissingObjectAfterWriteToStableFailure() {
        OSS client = mock(OSS.class);
        ObjectKey key = ObjectKey.of("reports/unconfirmed.pdf");
        when(client.getObjectMetadata("uploads", key.value())).thenThrow(
                ossException("NoSuchKey", "sensitive sdk body"));

        try {
            adapter(client).put(new io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest(
                    key, new ByteArrayInputStream(new byte[0]), 0L, "application/pdf"));
            fail("expected stable storage failure");
        } catch (FileStorageException expected) {
            assertEquals(FileStorageErrorCode.STORAGE_UNAVAILABLE, expected.errorCode());
        }
    }

    /**
     * 验证删除缺失对象返回 false 且不发送删除请求。
     *
     * 前置条件：存在性查询返回 NoSuchKey。
     * 执行步骤：调用适配器 delete。
     * 断言结果：返回 false 并验证 SDK deleteObject 未被调用。
     *
     * 该检查保持删除操作的幂等和请求预算语义。
     */
    @Test
    public void returnsFalseWhenDeletingMissingObject() {
        OSS client = mock(OSS.class);
        ObjectKey key = ObjectKey.of("missing.txt");
        when(client.getObjectMetadata("uploads", key.value())).thenThrow(
                ossException("NoSuchKey", "sensitive sdk body"));

        assertTrue(!adapter(client).delete(key));
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).deleteObject(any(), any());
    }

    /**
     * 验证删除既有对象使用固定 bucket 并返回 true。
     *
     * 前置条件：存在性查询返回完整 metadata。
     * 执行步骤：调用适配器 delete 删除目标对象。
     * 断言结果：SDK 收到固定 bucket 与目标 key。
     *
     * 该检查保护删除操作的路由边界和成功结果。
     */
    @Test
    public void deletesExistingObject() {
        OSS client = mock(OSS.class);
        ObjectKey key = ObjectKey.of("reports/monthly.pdf");
        com.aliyun.oss.model.ObjectMetadata providerMetadata = metadata();
        when(client.getObjectMetadata("uploads", key.value())).thenReturn(providerMetadata);

        assertTrue(adapter(client).delete(key));
        verify(client).deleteObject("uploads", key.value());
    }

    /**
     * 验证认证失败映射稳定错误码且不泄露 SDK 消息。
     *
     * 前置条件：SDK 返回 AccessDenied 和内部 token 地址。
     * 执行步骤：调用适配器 stat 触发错误映射。
     * 断言结果：错误码为认证失败，公共消息为固定文本。
     *
     * 该检查保护凭据、endpoint 和内部响应不进入调用方边界。
     */
    @Test
    public void mapsAuthenticationFailuresWithoutLeakingSdkDetails() {
        OSS client = mock(OSS.class);
        ObjectKey key = ObjectKey.of("private.txt");
        when(client.getObjectMetadata("uploads", key.value())).thenThrow(
                ossException("AccessDenied", "secret=https://internal.example/token"));

        try {
            adapter(client).stat(key);
            fail("expected mapped storage failure");
        } catch (FileStorageException expected) {
            assertEquals(FileStorageErrorCode.AUTHENTICATION_FAILED, expected.errorCode());
            assertEquals("OSS operation failed", expected.getMessage());
        }
    }

    /**
     * 验证已分类异常再次映射时保留原实例。
     *
     * 前置条件：已有稳定认证错误码的 FileStorageException。
     * 执行步骤：再次调用 OSS 异常映射器。
     * 断言结果：错误实例和错误码均不被覆盖。
     *
     * 该检查防止异常处理链发生二次包装。
     */
    @Test
    public void preservesAlreadyMappedStorageFailure() {
        FileStorageException original = new FileStorageException(
                FileStorageErrorCode.AUTHENTICATION_FAILED, "stable failure");

        FileStorageException result = OssExceptionMapper.map(original);

        assertTrue(result == original);
    }

    /**
     * 验证分页列举惰性读取完整对象 metadata。
     *
     * 前置条件：列表 SDK 返回一页摘要，stat 返回完整属性。
     * 执行步骤：消费适配器 list 流并收集对象。
     * 断言结果：对象属性完整且 stat 使用固定 bucket 查询。
     *
     * 该检查保护迁移所需的 MIME 和缓存策略不被摘要丢失。
     */
    @Test
    public void listsCompleteObjectMetadataLazily() {
        OSS client = mock(OSS.class);
        ObjectListing listing = new ObjectListing();
        OSSObjectSummary summary = new OSSObjectSummary();
        summary.setKey("reports/monthly.pdf");
        listing.setObjectSummaries(List.of(summary));
        listing.setTruncated(false);
        com.aliyun.oss.model.ObjectMetadata providerMetadata = metadata();
        when(client.listObjects(org.mockito.ArgumentMatchers.<com.aliyun.oss.model.ListObjectsRequest>any()))
                .thenReturn(listing);
        when(client.getObjectMetadata("uploads", summary.getKey())).thenReturn(providerMetadata);

        List<ObjectMetadata> objects = adapter(client).list().toList();

        assertEquals(1, objects.size());
        assertEquals(summary.getKey(), objects.getFirst().key().value());
        assertEquals(new CacheControl("public, max-age=60"), objects.getFirst().cacheControl());
        verify(client).getObjectMetadata("uploads", summary.getKey());
    }

    /**
     * 验证 marker 分页遵循 OSS 返回的 nextMarker。
     *
     * 前置条件：SDK 返回两页对象摘要，第一页声明仍有下一页。
     * 执行步骤：完整消费适配器的 list 流。
     * 断言结果：第二次 SDK 请求携带第一页的 nextMarker 且对象顺序不变。
     *
     * 该检查防止多页 bucket 的迁移遗漏后续对象。
     */
    @Test
    public void listsFollowingPagesWithNextMarker() {
        OSS client = mock(OSS.class);
        ObjectListing firstPage = listing("reports/first.pdf", true, "page-two");
        ObjectListing secondPage = listing("reports/second.pdf", false, null);
        com.aliyun.oss.model.ObjectMetadata providerMetadata = metadata();
        when(client.listObjects(org.mockito.ArgumentMatchers.<com.aliyun.oss.model.ListObjectsRequest>any()))
                .thenAnswer(invocation -> nextPage(invocation.getArgument(0), firstPage, secondPage));
        when(client.getObjectMetadata(org.mockito.ArgumentMatchers.eq("uploads"), any()))
                .thenReturn(providerMetadata);

        List<ObjectMetadata> objects = adapter(client).list().toList();

        assertEquals(List.of("reports/first.pdf", "reports/second.pdf"),
                objects.stream().map(item -> item.key().value()).toList());
    }

    /**
     * 创建包含完整必填字段的 OSS 模拟元数据。
     *
     * @return OSS SDK 元数据 mock
     */
    private static com.aliyun.oss.model.ObjectMetadata metadata() {
        com.aliyun.oss.model.ObjectMetadata metadata = mock(
                com.aliyun.oss.model.ObjectMetadata.class);
        when(metadata.getContentLength()).thenReturn(3L);
        when(metadata.getContentType()).thenReturn("application/pdf");
        when(metadata.getCacheControl()).thenReturn("public, max-age=60");
        when(metadata.getETag()).thenReturn("etag-1");
        when(metadata.getLastModified()).thenReturn(java.util.Date.from(Instant.EPOCH));
        return metadata;
    }

    /**
     * 创建包含一个摘要对象的 OSS 列表页。
     *
     * @param key 该页的对象 key
     * @param truncated 是否仍有下一页
     * @param nextMarker 下一页 marker
     * @return 可供 SDK mock 返回的列表页
     */
    private static ObjectListing listing(String key, boolean truncated, String nextMarker) {
        OSSObjectSummary summary = new OSSObjectSummary();
        summary.setKey(key);
        ObjectListing page = new ObjectListing();
        page.setObjectSummaries(List.of(summary));
        page.setTruncated(truncated);
        page.setNextMarker(nextMarker);
        return page;
    }

    /**
     * 根据 OSS 请求 marker 返回对应测试页。
     *
     * @param request SDK 列表请求
     * @param firstPage 首次请求返回页
     * @param secondPage nextMarker 请求返回页
     * @return 与请求 marker 对应的列表页
     */
    private static ObjectListing nextPage(com.aliyun.oss.model.ListObjectsRequest request,
                                          ObjectListing firstPage, ObjectListing secondPage) {
        if (request.getMarker() == null) {
            return firstPage;
        }
        assertEquals("page-two", request.getMarker());
        return secondPage;
    }

    /**
     * 创建带 OSS 服务端错误码的 SDK 异常。
     *
     * @param code OSS 错误码
     * @param message SDK 原始消息
     * @return 可供异常映射测试使用的异常
     */
    private static OSSException ossException(String code, String message) {
        return new OSSException(message, code, "request-id", "host-id", null, null, null);
    }

    /**
     * 创建固定测试 bucket 的适配器。
     *
     * @param client OSS SDK mock
     * @return 测试适配器
     */
    private static OssFileStorageAdapter adapter(OSS client) {
        return new OssFileStorageAdapter(client, "uploads",
                "https://oss-cn-hangzhou.aliyuncs.com", "access");
    }
}
