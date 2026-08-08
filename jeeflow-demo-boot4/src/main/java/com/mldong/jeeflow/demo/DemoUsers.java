package com.mldong.jeeflow.demo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示用户画像注册表——四端（Java/Go/Python/Node）统一同一套 8 个具名用户
 *
 * <p>切换后端不再"换人"：user1 永远是张三，leader 永远是李四（组长）。</p>
 */
public final class DemoUsers {

    /** userId → [realName, postName]（LinkedHashMap 保持展示顺序） */
    public static final Map<String, String[]> USERS = new LinkedHashMap<>();

    static {
        USERS.put("user1", new String[]{"张三", "工程师"});
        USERS.put("userA", new String[]{"孙倩", "工程师"});
        USERS.put("userB", new String[]{"周明", "工程师"});
        USERS.put("userC", new String[]{"吴婷", "工程师"});
        USERS.put("leader", new String[]{"李四", "组长"});
        USERS.put("manager", new String[]{"王五", "经理"});
        USERS.put("director", new String[]{"赵六", "总监"});
        USERS.put("boss", new String[]{"钱七", "总经理"});
    }

    public static final String DEPT_ID = "D01";
    public static final String DEPT_NAME = "研发部";

    /** 单用户信息 Map（userSearch/candidatePage 行结构） */
    public static Map<String, Object> toMap(String userId) {
        Map<String, Object> u = new LinkedHashMap<>();
        String[] p = USERS.get(userId);
        u.put("userId", userId);
        u.put("realName", p != null ? p[0] : "用户" + userId);
        u.put("deptId", DEPT_ID);
        u.put("deptName", DEPT_NAME);
        u.put("postId", "P01");
        u.put("postName", p != null ? p[1] : "工程师");
        return u;
    }

    private DemoUsers() {}
}
