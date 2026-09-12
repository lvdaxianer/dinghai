package io.github.lvdaxianer.dinghai.filestorage.core;

import java.nio.file.Path;
import java.util.Optional;

/** 为可安全直接传输的本地文件提供可选来源。 */
public interface LocalFileSource {
    /** 返回经过来源边界校验的普通文件，无法提供时返回空。 */
    Optional<Path> localFile(ObjectKey key);
}
