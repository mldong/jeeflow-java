package com.mldong.jeeflow.spi;

/**
 * 任务时限动作（spec 04 · 任务时限动作）：引擎写侧登记到框架执行队列的凭证语义。
 *
 * <p>引擎核心不依赖 sys 表——建行/取消经 {@link ITimeActionSink} 交给集成层实现；
 * {@code dueAt} 用 **UTC 毫秒** 承载，避免时区/序列化歧义（队列行原样落 {@code variable.__wfAction}）。</p>
 */
public class TimeAction {

    public static final String TYPE_REMIND = "REMIND";
    public static final String TYPE_AUTO_COMPLETE = "AUTO_COMPLETE";
    public static final String TYPE_CALLBACK = "CALLBACK";

    private String type;
    private long dueAt;
    private Long instanceId;
    private Long taskId;
    private String nodeKey;
    private int occurrenceNo;
    private String idempotencyKey;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public long getDueAt() { return dueAt; }
    public void setDueAt(long dueAt) { this.dueAt = dueAt; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
    public int getOccurrenceNo() { return occurrenceNo; }
    public void setOccurrenceNo(int occurrenceNo) { this.occurrenceNo = occurrenceNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
