package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** 文件存储压测的命令行参数。 */
public record FileStorageBenchmarkOptions(Provider provider, Path file, int concurrency,
                                          int readRounds, Path localRoot) {
    /** 压测目标存储类型。 */
    public enum Provider { LOCAL, MINIO }

    /** 将命令行参数转换为经过校验的压测配置。 */
    public static FileStorageBenchmarkOptions parse(String[] arguments) {
        Map<String, String> values = values(arguments);
        Provider provider = Provider.valueOf(required(values, "provider").toUpperCase());
        Path file = Path.of(required(values, "file")).toAbsolutePath().normalize();
        int concurrency = positive(values, "concurrency", 4);
        int readRounds = positive(values, "read-rounds", 1);
        Path localRoot = Path.of(values.getOrDefault("local-root",
                System.getProperty("java.io.tmpdir") + "/dinghai-file-storage-benchmark"));
        validate(file, concurrency, readRounds);
        return new FileStorageBenchmarkOptions(provider, file, concurrency, readRounds, localRoot);
    }

    /** 收集 `--key=value` 格式的参数。 */
    private static Map<String, String> values(String[] arguments) {
        Map<String, String> values = new HashMap<>();
        for (String argument : arguments) {
            String[] pair = argument.split("=", 2);
            if (pair.length != 2 || !pair[0].startsWith("--") || pair[1].isBlank()) {
                throw new IllegalArgumentException("invalid benchmark argument: " + argument);
            } else {
                values.put(pair[0].substring(2), pair[1]);
            }
        }
        return values;
    }

    /** 返回必填参数。 */
    private static String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing benchmark argument: --" + name);
        } else {
            return value;
        }
    }

    /** 返回正整数参数。 */
    private static int positive(Map<String, String> values, String name, int defaultValue) {
        int value = Integer.parseInt(values.getOrDefault(name, Integer.toString(defaultValue)));
        if (value <= 0 || value > 64) {
            throw new IllegalArgumentException(name + " must be between 1 and 64");
        } else {
            return value;
        }
    }

    /** 校验压测输入不会读取目录或不存在的文件。 */
    private static void validate(Path file, int concurrency, int readRounds) {
        if (!Files.isRegularFile(file) || concurrency <= 0 || readRounds <= 0) {
            throw new IllegalArgumentException("benchmark file and limits must be valid");
        }
    }
}
