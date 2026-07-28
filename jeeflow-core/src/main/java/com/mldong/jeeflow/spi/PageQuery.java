package com.mldong.jeeflow.spi;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页查询参数（通用）
 *
 * <p>条件列表支持多字段/多操作符自由组合，列名在仓库层过白名单校验。</p>
 *
 * @author mldong
 */
public class PageQuery {

    private int pageNum = 1;
    private int pageSize = 10;
    private String orderBy;
    private final List<Condition> conditions = new ArrayList<>();

    public PageQuery() {}

    public PageQuery(int pageNum, int pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    /** 添加查询条件 */
    public PageQuery add(String column, String operator, Object value) {
        this.conditions.add(new Condition(column, operator, value));
        return this;
    }

    public int getPageNum() { return pageNum; }
    public void setPageNum(int pageNum) { this.pageNum = pageNum; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }
    public List<Condition> getConditions() { return conditions; }

    /**
     * 单个查询条件
     */
    public static class Condition {
        private String column;    // e.g. "t.task_name" or "pi.business_no"
        private String operator;  // EQ / LIKE / GT / LT / IN / BT / NE
        private Object value;

        public Condition() {}
        public Condition(String column, String operator, Object value) {
            this.column = column;
            this.operator = operator;
            this.value = value;
        }

        public String getColumn() { return column; }
        public void setColumn(String column) { this.column = column; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }
}
