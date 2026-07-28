package com.mldong.jeeflow.spring;

import com.mldong.jeeflow.spi.IIdGenerator;

/**
 * 雪花算法 ID 生成器
 *
 * @author mldong
 */
public class SnowflakeIdGenerator implements IIdGenerator {

    /** 起始时间戳 (2023-01-01) */
    private static final long EPOCH = 1672531200000L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator() {
        this(1);
    }

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId >= (1L << WORKER_ID_BITS)) {
            throw new IllegalArgumentException("workerId 必须在 0-" + ((1L << WORKER_ID_BITS) - 1) + " 之间");
        }
        this.workerId = workerId;
    }

    @Override
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨，拒绝生成 ID");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 当前毫秒序列用完，等下一毫秒
                while (timestamp <= lastTimestamp) {
                    timestamp = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << (WORKER_ID_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;
    }
}
