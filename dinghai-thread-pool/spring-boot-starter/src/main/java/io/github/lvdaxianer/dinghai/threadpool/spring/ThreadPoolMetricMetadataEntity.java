package io.github.lvdaxianer.dinghai.threadpool.spring;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 子控 SQLite 元数据实体。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@TableName("thread_pool_metric_metadata")
public class ThreadPoolMetricMetadataEntity {
    @TableId("meta_key")
    private String metaKey;
    private String metaValue;

    /** @return 元数据键 */ public String getMetaKey() { return metaKey; }
    /** @param metaKey 元数据键 */ public void setMetaKey(String metaKey) { this.metaKey = metaKey; }
    /** @return 元数据值 */ public String getMetaValue() { return metaValue; }
    /** @param metaValue 元数据值 */ public void setMetaValue(String metaValue) { this.metaValue = metaValue; }
}
