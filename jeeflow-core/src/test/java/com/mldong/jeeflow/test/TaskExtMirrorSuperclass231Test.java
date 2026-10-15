package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.parser.ModelParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * issues/231 R63：task 节点 ext 镜像判据必须含父类链（对齐 boot2 ReflectUtil.hasField 血统语义）。
 *
 * <p>改前病灶：java 本地 {@code hasField} 只扫 {@code getDeclaredFields()}（TaskModel 自身声明），
 * 父类 {@code NodeModel}/{@code BaseModel} 声明的 name/displayName/layout/pre/postInterceptors
 * 已被解析进模型，却因判据漏父类链被当"非模型字段"再镜像一份进 ext——boot2 血统基准
 * （Hutool hasField 遍历父类）不镜像。</p>
 */
public class TaskExtMirrorSuperclass231Test {

    private Context savedContext;

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
        SimpleContext ctx = new SimpleContext();
        new Configuration(ctx);
        ctx.put("json", new TestJsonProvider());
    }

    @After
    public void tearDown() {
        if (savedContext != null) {
            ServiceContext.setContext(savedContext);
        }
    }

    private static TaskModel parseTask(String props) {
        String json = ("{'name':'ext-231','displayName':'ext镜像判据','type':'approval','nodes':["
                + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
                + "{'id':'apply','type':'snaker:task','x':300,'y':200,'properties':{" + props + "},'text':{'value':'申请'}},"
                + "{'id':'end','type':'snaker:end','x':500,'y':200,'properties':{},'text':{'value':'结束'}}],"
                + "'edges':["
                + "{'id':'e1','sourceNodeId':'start','targetNodeId':'apply','properties':{}},"
                + "{'id':'e2','sourceNodeId':'apply','targetNodeId':'end','properties':{}}]}")
                .replace('\'', '"');
        ProcessModel model = ModelParser.parse(json.getBytes(StandardCharsets.UTF_8));
        NodeModel node = model.getNode("apply");
        assertNotNull(node);
        return (TaskModel) node;
    }

    /** 父类链声明键（NodeModel/BaseModel）不得再镜像进 ext。 */
    @Test
    public void inheritedModelKeysNotMirroredIntoExt() {
        TaskModel task = parseTask(
                "'preInterceptors':'a.b.PreOne','postInterceptors':'a.b.PostOne','name':'dup-name'");
        assertEquals("preInterceptors 应解析进模型（父类 NodeModel 声明）",
                "a.b.PreOne", task.getPreInterceptors());
        assertNull("preInterceptors 已识别，不得再镜像进 ext（改前漏父类链会镜像）",
                task.getExt().get("preInterceptors"));
        assertNull("postInterceptors 已识别，不得再镜像进 ext", task.getExt().get("postInterceptors"));
        assertNull("name 是 BaseModel 声明键，不得镜像进 ext", task.getExt().get("name"));
    }

    /** 回归守卫：判据收紧不得误伤两类合法镜像——真未知键照进、模型声明键照不进。 */
    @Test
    public void unknownKeysStillMirroredDeclaredKeysStillExcluded() {
        TaskModel task = parseTask(
                "'form':'apply-form','width':100,'height':50,'bizTag':'keep-me'");
        assertEquals("form 是 TaskModel 自身声明键：解析进模型且不进 ext（回归）",
                "apply-form", task.getForm());
        assertNull("form 已识别，不进 ext（回归）", task.getExt().get("form"));
        assertEquals("真未知键 bizTag 应照常镜像进 ext", "keep-me",
                task.getExt().getStr("bizTag"));
        assertEquals("width/height 被解析进 layout 但非模型字段：照常镜像（回归，收紧不得误伤）",
                "300,200,100,50", task.getLayout());
        assertEquals(Integer.valueOf(100), task.getExt().getInt("width"));
        assertEquals(Integer.valueOf(50), task.getExt().getInt("height"));
    }
}
