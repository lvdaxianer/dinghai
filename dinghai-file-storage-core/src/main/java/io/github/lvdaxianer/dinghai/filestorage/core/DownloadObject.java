package io.github.lvdaxianer.dinghai.filestorage.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 流式下载对象，关闭对象即关闭底层流。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public final class DownloadObject implements AutoCloseable {
    private final InputStream stream;
    private final ObjectMetadata metadata;

    /** 创建下载对象。 */
    public DownloadObject(InputStream stream, ObjectMetadata metadata) {
        this.stream = Objects.requireNonNull(stream, "stream");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    /** 获取内容流。 */
    public InputStream stream() {
        return stream;
    }

    /** 获取内容流（兼容常见命名）。 */
    public InputStream inputStream() {
        return stream;
    }

    /** 获取对象元数据。 */
    public ObjectMetadata metadata() {
        return metadata;
    }

    /** 关闭底层流。 */
    @Override
    public void close() throws IOException {
        stream.close();
    }
}
