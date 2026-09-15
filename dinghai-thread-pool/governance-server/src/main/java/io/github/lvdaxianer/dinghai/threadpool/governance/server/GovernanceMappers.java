package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 总控 SQLite 的 MyBatis-Plus Mapper 集合。 */
final class GovernanceMappers {
    private GovernanceMappers() { }
    /** 实例 Mapper。 */ interface Instance extends BaseMapper<GovernanceInstanceEntity> { }
    /** Cursor Mapper。 */ interface Cursor extends BaseMapper<GovernanceCursorEntity> { }
    /** 缺口 Mapper。 */ interface Gap extends BaseMapper<GovernanceCollectionGapEntity> { }
    /** 告警 Mapper。 */ interface Alert extends BaseMapper<GovernanceAlertStateEntity> { }
    /** 线程池历史指标 Mapper。 */ interface Metric extends BaseMapper<GovernanceThreadPoolMetricEntity> { }
}
