package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.interceptor.impl.FormPostAssignmentHandler;
import com.mldong.jeeflow.interceptor.impl.OrgUserAssignmentHandlers;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.spi.IPostUserProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** issues/259 任务节点关联岗位与表单指定岗位。 */
public class FormPostAssignmentHandlerTest {
    private static final Map<String, List<String>> POST_MEMBERS = new HashMap<>();

    static {
        POST_MEMBERS.put("postA", Arrays.asList("userA", "shared"));
        POST_MEMBERS.put("postB", Arrays.asList("userB", "shared"));
        POST_MEMBERS.put("postC", Arrays.asList("userC1", "userC2"));
        POST_MEMBERS.put("0", Arrays.asList("zeroUser"));
    }

    private Context savedContext;

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
        freshStack(true);
    }

    @After
    public void tearDown() {
        if (savedContext != null) ServiceContext.setContext(savedContext);
    }

    private void freshStack(boolean withProvider) {
        SimpleContext ctx = new SimpleContext();
        if (withProvider) {
            ctx.put("post", new IPostUserProvider() {
                @Override
                public List<String> findByPost(String postCode) {
                    return POST_MEMBERS.get(postCode);
                }
            });
        }
        ServiceContext.setContext(ctx);
    }

    private static Execution execOf(String nodeKey, FlowData args) {
        Execution execution = new Execution();
        execution.setOperator("clerk");
        if (nodeKey != null) {
            TaskModel model = new TaskModel();
            model.setName(nodeKey);
            execution.setNodeModel(model);
        }
        execution.setArgs(args);
        return execution;
    }

    private static String formPost(String nodeKey, FlowData args) {
        return new FormPostAssignmentHandler().assign(execOf(nodeKey, args));
    }

    private static String taskPost(String nodeKey) {
        return new OrgUserAssignmentHandlers.TaskPostAssigneeHandler().assign(execOf(nodeKey, null));
    }

    @Test
    public void t01_prefixedWinsOverBare() {
        FlowData args = FlowData.create().set("f_posts", "postA").set("posts", "postB");
        assertEquals("userA,shared", formPost("posts", args));
    }

    @Test
    public void t02_bareFallbackAndMiss() {
        assertEquals("userB,shared", formPost("posts", FlowData.create().set("posts", "postB")));
        assertNull(formPost("posts", FlowData.create().set("other", "postB")));
    }

    @Test
    public void t03_hashRightHalfBareThenPrefixed() {
        FlowData bareFirst = FlowData.create().set("postCodes", "postA").set("f_postCodes", "postB");
        assertEquals("userA,shared", formPost("manager#postCodes", bareFirst));
        assertEquals("userB,shared", formPost("manager#postCodes", FlowData.create().set("f_postCodes", "postB")));
        assertEquals("userA,shared", formPost("a#b#postCodes", FlowData.create().set("postCodes", "postA")));
    }

    @Test
    public void t04_numberSuffixBasePrefixedThenBare() {
        FlowData prefixed = FlowData.create().set("f_posts", "postA").set("posts", "postB");
        assertEquals("userA,shared", formPost("posts_01", prefixed));
        assertEquals("userA,shared", formPost("posts_01", FlowData.create().set("posts", "postA")));
    }

    @Test
    public void t05_numberSuffixTierMatchesLazyRegex() {
        FlowData args = FlowData.create().set("a_b", "postA").set("a", "postB");
        assertEquals("userA,shared", formPost("a_b_01", args));
        assertNull(formPost("_01", FlowData.create().set("f_", "postA")));
        assertNull(formPost("posts_01x", FlowData.create().set("posts", "postA")));
    }

    @Test
    public void t06_keyExistenceNotTruthiness() {
        FlowData args = new FlowData();
        args.put("f_posts", null);
        args.put("posts", "postB");
        assertNull(formPost("posts", args));
    }

    @Test
    public void t07_commaStringResolvesAllPostsInOrder() {
        assertEquals("userA,shared,userB", formPost("posts", FlowData.create().set("posts", "postA, postB")));
    }

    @Test
    public void t08_arrayResolvesAllPostsInOrder() {
        assertEquals("userA,shared,userB", formPost("posts",
                FlowData.create().set("posts", Arrays.asList("postA", "postB"))));
    }

    @Test
    public void t09_arrayElementIsNotResplit() {
        assertNull(formPost("posts", FlowData.create().set("posts", Arrays.asList("postA,postB"))));
    }

    @Test
    public void t09b_primitiveArrayIsReadElementByElement() {
        assertEquals("zeroUser", formPost("posts", FlowData.create().set("posts", new long[]{0L})));
    }

    @Test
    public void t10_duplicatePostsAndUsersAreDeduped() {
        assertEquals("userA,shared,userB", formPost("posts",
                FlowData.create().set("posts", "postA,postB,postA")));
    }

    @Test
    public void t11_zeroPostCodeIsLegal() {
        assertEquals("zeroUser", formPost("posts", FlowData.create().set("posts", 0L)));
    }

    @Test
    public void t12_missingSpiIsEmpty() {
        freshStack(false);
        assertNull(formPost("posts", FlowData.create().set("posts", "postA")));
        assertNull(taskPost("postA"));
    }

    @Test
    public void t13_noNodeIsEmpty() {
        FlowData args = FlowData.create().set("f_", "postA");
        Execution execution = new Execution();
        execution.setArgs(args);
        assertNull(new FormPostAssignmentHandler().assign(execution));
        assertNull(formPost("", args));
        assertNull(taskPost(null));
        assertNull(taskPost(""));
    }

    @Test
    public void t14_emptyAndNullAreEmpty() {
        assertNull(formPost("posts", FlowData.create().set("posts", "")));
        assertNull(formPost("posts", FlowData.create().set("posts", " , ")));
        FlowData nul = new FlowData();
        nul.put("posts", null);
        assertNull(formPost("posts", nul));
        assertNull(formPost("posts", null));
    }

    @Test
    public void t15_spiFailureSkipsPostAndContinues() {
        SimpleContext ctx = new SimpleContext();
        ctx.put("post", new IPostUserProvider() {
            @Override
            public List<String> findByPost(String postCode) {
                if ("postA".equals(postCode)) throw new IllegalStateException("lookup failed");
                return POST_MEMBERS.get(postCode);
            }
        });
        ServiceContext.setContext(ctx);
        assertEquals("userB,shared", formPost("posts", FlowData.create().set("posts", "postA,postB")));
        assertNull(taskPost("postA"));
        assertEquals("userB,shared", taskPost("postB"));
    }

    @Test
    public void t16_taskPostUsesNodeCodeAndKeepsSpiOrder() {
        assertEquals("userA,shared", taskPost("postA"));
        assertEquals("userC1,userC2", taskPost("postC"));
        assertEquals("zeroUser", taskPost("0"));
        assertNull(taskPost("ghost"));
    }

    @Test
    public void t17_formPostSkipsBlankUserIds() {
        SimpleContext ctx = new SimpleContext();
        ctx.put("post", new IPostUserProvider() {
            @Override
            public List<String> findByPost(String postCode) {
                return Arrays.asList(" userA ", "", null, "shared", "userA");
            }
        });
        ServiceContext.setContext(ctx);
        assertEquals("userA,shared", formPost("posts", FlowData.create().set("posts", "postA")));
    }
}
