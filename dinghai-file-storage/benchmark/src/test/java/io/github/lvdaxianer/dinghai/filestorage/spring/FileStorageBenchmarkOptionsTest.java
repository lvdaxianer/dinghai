package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

/** 文件压测命令行参数测试。 */
public class FileStorageBenchmarkOptionsTest {

    /** 临时源文件目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 可解析 Local 并发压测的完整参数。 */
    @Test
    public void shouldParseLocalConcurrentBenchmarkOptions() throws Exception {
        Path source = temporaryFolder.newFile("source.bin").toPath();
        Files.writeString(source, "benchmark");
        FileStorageBenchmarkOptions options = FileStorageBenchmarkOptions.parse(new String[]{
                "--provider=local", "--file=" + source, "--concurrency=4", "--read-rounds=2"
        });

        assertEquals(FileStorageBenchmarkOptions.Provider.LOCAL, options.provider());
        assertEquals(source, options.file());
        assertEquals(4, options.concurrency());
        assertEquals(2, options.readRounds());
    }

    /** Local 并发压测应使用独立临时目录完成上传和读取。 */
    @Test
    public void shouldRunConcurrentLocalBenchmark() throws Exception {
        Path source = temporaryFolder.newFile("concurrent.bin").toPath();
        Files.writeString(source, "concurrent benchmark content");
        FileStorageBenchmarkOptions options = new FileStorageBenchmarkOptions(
                FileStorageBenchmarkOptions.Provider.LOCAL, source, 2, 2,
                temporaryFolder.newFolder("benchmark").toPath());

        new FileStorageBenchmark().run(options);
    }
}
