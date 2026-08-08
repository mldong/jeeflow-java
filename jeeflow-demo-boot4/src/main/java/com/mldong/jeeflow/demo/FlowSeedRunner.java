package com.mldong.jeeflow.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.spi.IProcessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动加载流程定义种子——直读共享 flows 目录（四端唯一事实源）
 *
 * <p>与 Go/Python/Node demo 同源同序：按文件名排序 saveDefine，id=1..N，
 * 消除切换后端"换流程/ID 漂移"。{@code /api/reset} 清库后复用 {@link #seed()} 重载。</p>
 */
@Component
public class FlowSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FlowSeedRunner.class);
    private static final String FLOWS_REL = "jeeflow-core/src/test/resources/flows";

    /** 候选路径链：兼容不同启动目录（jeeflow-hub 根 / jeeflow-java / jeeflow-demo-boot4） */
    private static final String[] CANDIDATES = {
            "jeeflow-java/" + FLOWS_REL,
            FLOWS_REL,
            "../" + FLOWS_REL,
            "jeeflow-hub/jeeflow-java/" + FLOWS_REL,
            "../jeeflow-java/" + FLOWS_REL,
    };

    private final IProcessRepository repository;
    private final ObjectMapper json = new ObjectMapper();

    public FlowSeedRunner(IProcessRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        seed();
    }

    /** 加载共享 flows 目录：文件名排序依次 saveDefine，id=1..N。返回加载条数 */
    public int seed() {
        Path flowsDir = findFlowsDir();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(flowsDir, "*.json")) {
            ds.forEach(files::add);
        } catch (IOException e) {
            throw new IllegalStateException("读取 flows 目录失败：" + flowsDir, e);
        }
        files.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        int i = 0;
        for (Path f : files) {
            try {
                byte[] data = Files.readAllBytes(f);
                @SuppressWarnings("unchecked")
                var raw = json.readValue(data, java.util.Map.class);
                ProcessInstance.ProcessDefine define = new ProcessInstance.ProcessDefine();
                define.setId((long) (i + 1));
                define.setName(str(raw.get("name"), f.getFileName().toString()));
                define.setDisplayName(str(raw.get("displayName"), define.getName()));
                String type = str(raw.get("type"), "");
                define.setType(type.isEmpty() ? "approval" : type);
                define.setState(1);
                define.setContent(data);
                repository.saveDefine(define);
                i++;
            } catch (IOException e) {
                log.warn("跳过无法解析的流程文件：{}", f, e);
            }
        }
        log.info("已加载 {} 个流程定义（来源：{}）", i, flowsDir);
        return i;
    }

    private static String str(Object v, String fallback) {
        return v == null || String.valueOf(v).isBlank() ? fallback : String.valueOf(v);
    }

    private static Path findFlowsDir() {
        // 显式覆盖优先（系统属性/环境变量）
        String override = System.getProperty("jeeflow.flows.dir", System.getenv("JEEFLOW_FLOWS_DIR"));
        if (override != null && Files.isDirectory(Paths.get(override))) {
            return Paths.get(override);
        }
        for (String cand : CANDIDATES) {
            Path p = Paths.get(cand);
            if (Files.isDirectory(p)) return p.toAbsolutePath().normalize();
        }
        throw new IllegalStateException(
                "找不到共享 flows 目录（候选：" + String.join(" / ", CANDIDATES)
                        + "），请在 jeeflow-hub 根目录启动或设置 -Djeeflow.flows.dir");
    }
}
