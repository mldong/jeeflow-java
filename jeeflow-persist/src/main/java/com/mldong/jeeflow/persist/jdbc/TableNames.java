package com.mldong.jeeflow.persist.jdbc;

/**
 * 表名安全校验（Writer/Reader 共用）——非空、非 sys_ 前缀、无非法字符
 *
 * @author mldong
 */
public final class TableNames {

    public static final String SYS_PREFIX = "sys_";

    private TableNames() {
    }

    /** 校验表名；非法时抛 IllegalArgumentException */
    public static void validate(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        String t = tableName.trim();
        if (t.toLowerCase().startsWith(SYS_PREFIX)) {
            throw new IllegalArgumentException("拒绝写入系统表: " + t);
        }
        for (char c : t.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                throw new IllegalArgumentException("表名含非法字符: " + t);
            }
        }
    }
}
