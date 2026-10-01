package com.mldong.jeeflow.parser;

import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.model.TransitionModel;
import com.mldong.jeeflow.model.logicflow.LfEdge;
import com.mldong.jeeflow.model.logicflow.LfModel;
import com.mldong.jeeflow.model.logicflow.LfNode;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

/**
 * 模型解析器——将 JSON 流程定义解析为 ProcessModel
 *
 * @author mldong
 */
public final class ModelParser {

    /** JUL 而非 slf4j：core 零外部运行时依赖（同 {@code ProcessPublisher} 的口径）。 */
    private static final Logger log = Logger.getLogger(ModelParser.class.getName());

    private ModelParser() {
    }

    /**
     * 将 JSON 字节解析为流程模型
     */
    public static ProcessModel parse(byte[] bytes) {
        IJsonProvider json = ServiceContext.find(IJsonProvider.class);
        if (json == null) {
            throw new RuntimeException("未注册 IJsonProvider，无法解析流程定义。请在 ServiceContext 中注册 JSON 实现。");
        }
        String jsonStr;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            jsonStr = sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("读取流程定义 JSON 失败", e);
        }

        LfModel lfModel = json.fromJson(jsonStr, LfModel.class);
        ProcessModel processModel = new ProcessModel();

        // 流程定义基本信息（无论有无节点都要解析——空设计稿保存时 updateDefine 的 name/type 同步依赖，issues/27 验证发现）
        processModel.setName(lfModel.getName());
        processModel.setDisplayName(lfModel.getDisplayName());
        processModel.setType(lfModel.getType());
        processModel.setInstanceUrl(lfModel.getInstanceUrl());
        processModel.setInstanceNoClass(lfModel.getInstanceNoClass());
        processModel.setPostInterceptors(lfModel.getPostInterceptors());
        processModel.setPreInterceptors(lfModel.getPreInterceptors());
        processModel.setRelTableName(lfModel.getRelTableName());
        processModel.setPersistMode(lfModel.getPersistMode());
        // issues/137 A · 裁定 A（批二 §3-4）· **这一行就是基准自己的断链**：
        // `JeeflowEngineImpl.java:93-96` 读的是 `ProcessModel.getExpireTime()`（流程定义**顶层**的
        // 「期望完成时间」表达式，spec 02:21/55），而本方法此前逐字段搬了 9 项、**唯独漏了这一项**
        // （`LfModel:15/27` 那个字段一直在，全 src/main 里 `ProcessModel.setExpireTime` 零调用者）
        // ⇒ 引擎那句 `isNotEmpty` 守卫永远读到 null、`wf_process_instance.expire_time` 在参考实现里
        // **恒 NULL**——"形状是 A、链路断在解析这一跳"。批二 §3-4 的 go 腿普查查出来的
        // （go 无独立解析阶段、根键由结构体直接收，落地后反超基准），c# 同病（`ModelParser.cs:81-92`
        // 的初始化器同样没有 `ExpireTime`）⇒ §3-4 真正的整改面是**八栈**而不是案文写的六栈。
        // 取证格＝`InstanceExpireTimeOnStartTest#parserCarriesRootExpireTimeIntoProcessModel`
        // （摘掉本行 ⇒ 该文件 10 格里 8 格红，且红的全是"配了该有值"的那几格；剩下两格是"该留 NULL"，
        // 在断链状态下反而恒绿——这正是本案为什么必须有解析层那一格）。
        processModel.setExpireTime(lfModel.getExpireTime());

        List<LfNode> nodes = lfModel.getNodes();
        List<LfEdge> edges = lfModel.getEdges();

        if (nodes == null || nodes.isEmpty() || edges == null || edges.isEmpty()) {
            return processModel;
        }

        // 解析各节点
        for (LfNode node : nodes) {
            String type = node.getType().replace(NodeParser.NODE_NAME_PREFIX, "");
            NodeParser parser = ServiceContext.findByName(type, NodeParser.class);
            if (parser == null) {
                // issues/141 G4 义务 2（spec 02「未知档不得静默丢节点」）：类型表查不到解析器时，
                // 先留一条可诊断记录（节点 id ＋ 实得类型串，前缀原样一并带上）再跳过。
                // 旧形状是无声丢节点＋连带丢它的出边——php 的 `snaker:custom` 缺档就是这么被吞掉的，
                // 照 spec 写小写 `snaker:subprocess` 而在只认大写的栈里拿不到子流程节点也是同一形状。
                // 注：本仓查表是 SimpleContext 的 HashMap get（大小写敏感），spec 02 义务 1 的
                // 「查表前大小写归一」还没落地（要先补别名再谈归一化，见 Configuration 注释）。
                log.warning(String.format(
                        "流程定义里的节点类型没有对应解析器，该节点及其出边将被跳过: nodeId=%s, type=%s, lookupKey=%s",
                        node.getId(), node.getType(), type));
                continue;
            }
            parser.parse(node, edges);
            NodeModel nodeModel = parser.getModel();
            processModel.getNodes().add(nodeModel);
            if (nodeModel instanceof TaskModel) {
                processModel.getTasks().add((TaskModel) nodeModel);
            }
        }

        // 构造输入边、输出边的 source/target 引用
        for (NodeModel node : processModel.getNodes()) {
            for (TransitionModel transition : node.getOutputs()) {
                String to = transition.getTo();
                for (NodeModel node2 : processModel.getNodes()) {
                    if (to.equalsIgnoreCase(node2.getName())) {
                        node2.getInputs().add(transition);
                        transition.setTarget(node2);
                    }
                }
            }
        }

        return processModel;
    }
}
