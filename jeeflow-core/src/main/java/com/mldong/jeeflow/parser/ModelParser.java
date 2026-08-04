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

/**
 * 模型解析器——将 JSON 流程定义解析为 ProcessModel
 *
 * @author mldong
 */
public final class ModelParser {

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

        List<LfNode> nodes = lfModel.getNodes();
        List<LfEdge> edges = lfModel.getEdges();

        if (nodes == null || nodes.isEmpty() || edges == null || edges.isEmpty()) {
            return processModel;
        }

        // 解析各节点
        for (LfNode node : nodes) {
            String type = node.getType().replace(NodeParser.NODE_NAME_PREFIX, "");
            NodeParser parser = ServiceContext.findByName(type, NodeParser.class);
            if (parser != null) {
                parser.parse(node, edges);
                NodeModel nodeModel = parser.getModel();
                processModel.getNodes().add(nodeModel);
                if (nodeModel instanceof TaskModel) {
                    processModel.getTasks().add((TaskModel) nodeModel);
                }
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
