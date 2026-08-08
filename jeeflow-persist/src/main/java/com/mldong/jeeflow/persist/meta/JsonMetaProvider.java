package com.mldong.jeeflow.persist.meta;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置 JSON 配置加载器（issues/23）——从文件系统/classpath 加载元数据 JSON。
 *
 * <p>配置格式见设计稿 persist-meta-design.md：文件名 = 表名（如 {@code biz_leave.json}），
 * 内容为 TableMeta 的 JSON（storageType 支持名称或 1-5 数字）。</p>
 *
 * <p>加载顺序：① 文件系统目录（构造参数，优先）；② classpath 目录（默认
 * {@code persist-meta/}）。表缓存。</p>
 *
 * @author mldong
 */
public class JsonMetaProvider implements IDynamicMetaProvider {

    /** classpath 默认配置目录 */
    public static final String DEFAULT_CLASSPATH_DIR = "persist-meta";

    private final ObjectMapper mapper;
    private final String dir;                       // 文件系统目录（可选）
    private final String classpathDir;              // classpath 目录（默认 persist-meta/）
    private final ConcurrentHashMap<String, TableMeta> cache = new ConcurrentHashMap<>();

    public JsonMetaProvider() {
        this(null, DEFAULT_CLASSPATH_DIR);
    }

    /**
     * @param dir 文件系统配置目录（如 /etc/jeeflow/persist-meta），null 仅走 classpath
     * @param classpathDir classpath 配置目录（默认 persist-meta/）
     */
    public JsonMetaProvider(String dir, String classpathDir) {
        this.dir = dir;
        this.classpathDir = classpathDir != null ? classpathDir : DEFAULT_CLASSPATH_DIR;
        this.mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public TableMeta loadTableMeta(String tableName) {
        if (tableName == null) return null;
        return cache.computeIfAbsent(tableName, this::load);
    }

    private TableMeta load(String tableName) {
        String json = readConfig(tableName);
        if (json == null) return null;
        try {
            return mapper.readValue(json, TableMeta.class);
        } catch (IOException e) {
            throw new IllegalStateException("解析表元数据失败: " + tableName + " -> " + e.getMessage(), e);
        }
    }

    private String readConfig(String tableName) {
        String fileName = tableName + ".json";
        // ① 文件系统目录优先
        if (dir != null) {
            Path p = Paths.get(dir, fileName);
            if (Files.isRegularFile(p)) {
                try {
                    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("读取表元数据失败: " + p + " -> " + e.getMessage(), e);
                }
            }
        }
        // ② classpath 目录
        String resource = classpathDir + "/" + fileName;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) return null;
            // Java 8 兼容读全量（readAllBytes 为 Java 9+ API，pom target 8 下编译不过）
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取表元数据失败: " + resource + " -> " + e.getMessage(), e);
        }
    }
}
