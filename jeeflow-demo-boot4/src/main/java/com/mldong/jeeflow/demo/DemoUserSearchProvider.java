package com.mldong.jeeflow.demo;

import com.mldong.jeeflow.spi.IUserSearchProvider;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 演示用户搜索——在 {@link DemoUsers} 的 8 个用户内分页检索（candidatePage 依赖）
 *
 * <p>m_* 条件值统一按关键字处理：对 userId/realName 做包含匹配（演示语义）。</p>
 */
@Component
public class DemoUserSearchProvider implements IUserSearchProvider {

    @Override
    public PageResult<Map<String, Object>> page(PageQuery query) {
        // 收集 m_* 关键字
        List<String> keywords = new ArrayList<>();
        for (PageQuery.Condition c : query.getConditions()) {
            if (c.getValue() != null && !String.valueOf(c.getValue()).isBlank()) {
                keywords.add(String.valueOf(c.getValue()).toLowerCase());
            }
        }
        List<Map<String, Object>> all = new ArrayList<>();
        for (String uid : DemoUsers.USERS.keySet()) {
            Map<String, Object> u = DemoUsers.toMap(uid);
            String realName = String.valueOf(u.get("realName")).toLowerCase();
            boolean hit = keywords.isEmpty()
                    || keywords.stream().allMatch(k -> uid.toLowerCase().contains(k) || realName.contains(k));
            if (hit) all.add(u);
        }
        int pageNum = Math.max(1, query.getPageNum());
        int pageSize = Math.max(1, query.getPageSize());
        int from = Math.min((pageNum - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return PageResult.of(pageNum, pageSize, all.size(), new ArrayList<>(all.subList(from, to)));
    }

    @Override
    public Map<String, Object> findById(String userId) {
        return DemoUsers.toMap(userId);
    }
}
