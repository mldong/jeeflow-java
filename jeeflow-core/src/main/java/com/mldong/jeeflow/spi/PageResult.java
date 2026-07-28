package com.mldong.jeeflow.spi;

import java.util.List;

/**
 * 分页查询结果
 *
 * @author mldong
 */
public class PageResult<T> {

    private int pageNum;
    private int pageSize;
    private int recordCount;
    private List<T> rows;

    public PageResult() {}

    public PageResult(int pageNum, int pageSize, int recordCount, List<T> rows) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.recordCount = recordCount;
        this.rows = rows;
    }

    public static <T> PageResult<T> of(int pageNum, int pageSize, int recordCount, List<T> rows) {
        return new PageResult<>(pageNum, pageSize, recordCount, rows);
    }

    public int getPageNum() { return pageNum; }
    public void setPageNum(int pageNum) { this.pageNum = pageNum; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getRecordCount() { return recordCount; }
    public void setRecordCount(int recordCount) { this.recordCount = recordCount; }
    public int getTotalPage() {
        return recordCount == 0 ? 0 : (recordCount + pageSize - 1) / pageSize;
    }
    public List<T> getRows() { return rows; }
    public void setRows(List<T> rows) { this.rows = rows; }
}
