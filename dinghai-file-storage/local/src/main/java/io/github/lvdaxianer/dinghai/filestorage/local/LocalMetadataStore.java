package io.github.lvdaxianer.dinghai.filestorage.local;

import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 以临时文件和原子替换管理本地对象元数据。 */
final class LocalMetadataStore {
    private final LocalObjectPathResolver paths;

    /** 创建与对象路径解析器绑定的元数据存储。 */
    LocalMetadataStore(LocalObjectPathResolver paths) {
        this.paths = paths;
    }

    /** 读取对象元数据属性；sidecar 缺失时返回空属性。 */
    Properties load(ObjectKey key) throws IOException {
        Path metadata = paths.metadata(key);
        if (!Files.isRegularFile(metadata)) {
            return new Properties();
        }
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(metadata)) {
            values.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return values;
    }

    /** 将完整元数据原子持久化。 */
    void write(ObjectMetadata metadata) throws IOException {
        Path target = paths.metadata(metadata.key());
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".metadata-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties(metadata).store(new OutputStreamWriter(output, StandardCharsets.UTF_8), null);
            }
            LocalFileStorageAdapter.atomicMove(temporary, target);
        } finally {
            LocalFileStorageAdapter.deleteTemporary(temporary);
        }
    }

    /** 删除对象的元数据 sidecar。 */
    void delete(ObjectKey key) throws IOException {
        Files.deleteIfExists(paths.metadata(key));
    }

    private static Properties properties(ObjectMetadata metadata) {
        Properties values = new Properties();
        values.setProperty("contentType", metadata.contentType());
        values.setProperty("etag", metadata.etag());
        values.setProperty("lastModified", metadata.lastModified().toString());
        values.setProperty("cacheControl", metadata.cacheControl().value());
        return values;
    }
}
