package com.mldong.jeeflow.repository;

import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.ProcessDesign;
import com.mldong.jeeflow.domain.ProcessDesignHis;
import com.mldong.jeeflow.domain.ProcessSurrogate;
import com.mldong.jeeflow.spi.IIdGenerator;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * 扩展仓储 JDBC 集成测试（v1.1.0）——H2 验证三表 CRUD / 分页 / 委托生效
 */
public class JdbcProcessExtRepositoryTest {

    private JdbcProcessExtRepository extRepo;

    @Before
    public void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_ext_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        String ddl = new String(Files.readAllBytes(
                Paths.get("src/test/resources/schema-h2.sql")), StandardCharsets.UTF_8);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) stmt.execute(trimmed);
            }
        }

        // 初始化 ServiceContext（SimpleContext，注册 json + idGen）
        ServiceContext.setContext(new com.mldong.jeeflow.context.SimpleContext());
        AtomicLong seq = new AtomicLong(1);
        ServiceContext.put("idGen", new IIdGenerator() {
            @Override public long nextId() { return seq.getAndIncrement(); }
        });

        // json provider（初始化 ServiceContext，nextId 兜底路径依赖）
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ServiceContext.put("json", new com.mldong.jeeflow.json.IJsonProvider() {
            @Override public String toJson(Object obj) {
                try { return mapper.writeValueAsString(obj); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public <T> T fromJson(String json, Class<T> type) {
                try { return mapper.readValue(json, type); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public <T> T fromJson(String json, com.mldong.jeeflow.json.TypeReference<T> typeRef) {
                try { return mapper.readValue(json, mapper.constructType(typeRef.getType())); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public boolean isJson(String str) {
                return str != null && (str.trim().startsWith("{") || str.trim().startsWith("["));
            }
        });

        extRepo = new JdbcProcessExtRepository(ds);
    }

    // ═══ 流程设计 ═══

    @Test
    public void testDesignCrudAndHis() {
        // save + 快照
        ProcessDesign d = new ProcessDesign();
        d.setName("leave");
        d.setDisplayName("请假流程");
        d.setType("approval");
        d.setIsDeployed(0);
        d.setCreateUser("tester");
        d.setUpdateUser("tester");
        extRepo.saveDesign(d);
        assertNotNull(d.getId());

        ProcessDesignHis his1 = new ProcessDesignHis();
        his1.setProcessDesignId(d.getId());
        his1.setContent("{\"v\":1}".getBytes(StandardCharsets.UTF_8));
        his1.setCreateUser("tester");
        extRepo.saveDesignHis(his1);
        ProcessDesignHis his2 = new ProcessDesignHis();
        his2.setProcessDesignId(d.getId());
        his2.setContent("{\"v\":2}".getBytes(StandardCharsets.UTF_8));
        his2.setCreateUser("tester");
        extRepo.saveDesignHis(his2);

        // 查询 + 历史（倒序：最新在前）
        ProcessDesign loaded = extRepo.findDesignById(d.getId());
        assertEquals("leave", loaded.getName());
        List<ProcessDesignHis> hisList = extRepo.listDesignHis(d.getId());
        assertEquals(2, hisList.size());
        assertEquals("{\"v\":2}", new String(hisList.get(0).getContent(), StandardCharsets.UTF_8));

        // update
        loaded.setDisplayName("请假流程 v2");
        loaded.setIsDeployed(1);
        extRepo.updateDesign(loaded);
        assertEquals("请假流程 v2", extRepo.findDesignById(d.getId()).getDisplayName());
        assertEquals(Integer.valueOf(1), extRepo.findDesignById(d.getId()).getIsDeployed());

        // 分页（别名 t）
        PageResult<ProcessDesign> page = extRepo.pageDesigns(new PageQuery(1, 10)
                .add("t.name", "EQ", "leave"));
        assertEquals(1, page.getRecordCount());

        // remove（连带历史）
        extRepo.removeDesign(d.getId());
        assertNull(extRepo.findDesignById(d.getId()));
        assertEquals(0, extRepo.listDesignHis(d.getId()).size());
    }

    // ═══ 委托代理 ═══

    @Test
    public void testSurrogateCrudAndGet() {
        // 全流程委托（processName 空）
        ProcessSurrogate all = new ProcessSurrogate();
        all.setOperator("zhangsan");
        all.setSurrogate("lisi");
        all.setEnabled(1);
        extRepo.saveSurrogate(all);

        // 指定流程委托（时间窗外）
        ProcessSurrogate spec = new ProcessSurrogate();
        spec.setOperator("zhangsan");
        spec.setSurrogate("wangwu");
        spec.setProcessName("leave");
        spec.setStartTime(LocalDateTime.now().minusDays(10));
        spec.setEndTime(LocalDateTime.now().minusDays(5)); // 已过期
        spec.setEnabled(1);
        extRepo.saveSurrogate(spec);

        LocalDateTime now = LocalDateTime.now();
        // leave 流程：精确匹配已过期 → 兜底全流程委托 lisi
        ProcessSurrogate hit = extRepo.getSurrogate("zhangsan", "leave", now);
        assertNotNull(hit);
        assertEquals("lisi", hit.getSurrogate());
        // 其他流程：全流程委托
        assertEquals("lisi", extRepo.getSurrogate("zhangsan", "other", now).getSurrogate());
        // 无委托
        assertNull(extRepo.getSurrogate("wangwu", "leave", now));

        // 时间窗内精确匹配
        ProcessSurrogate active = new ProcessSurrogate();
        active.setOperator("zhaoliu");
        active.setSurrogate("sunqi");
        active.setProcessName("leave");
        active.setStartTime(now.minusDays(1));
        active.setEndTime(now.plusDays(1));
        active.setEnabled(1);
        extRepo.saveSurrogate(active);
        assertEquals("sunqi", extRepo.getSurrogate("zhaoliu", "leave", now).getSurrogate());

        // 停用不生效
        ProcessSurrogate disabled = new ProcessSurrogate();
        disabled.setOperator("zhouba");
        disabled.setSurrogate("wujiu");
        disabled.setEnabled(0);
        extRepo.saveSurrogate(disabled);
        assertNull(extRepo.getSurrogate("zhouba", "leave", now));

        // 分页
        PageResult<ProcessSurrogate> page = extRepo.pageSurrogates(new PageQuery(1, 10)
                .add("t.operator", "EQ", "zhangsan"));
        assertEquals(2, page.getRecordCount());

        // remove
        extRepo.removeSurrogate(all.getId());
        assertNull(extRepo.findSurrogateById(all.getId()));
    }
}
