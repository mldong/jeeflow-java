package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.interceptor.impl.FormDeptLeaderAssignmentHandler;
import com.mldong.jeeflow.interceptor.impl.FormDeptMainLeaderAssignmentHandler;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** issues/258 表单指定 1–N 个部门（分管）领导。 */
public class FormDeptAssignmentHandlerTest {
    private static final Map<String, List<String>> LEADERS = new HashMap<>();
    private static final Map<String, List<String>> MAIN_LEADERS = new HashMap<>();

    static {
        LEADERS.put("deptA", Arrays.asList("leaderA", "shared"));
        LEADERS.put("deptB", Arrays.asList("leaderB", "shared"));
        LEADERS.put("deptC", Arrays.asList("leaderC1", "leaderC2"));
        LEADERS.put("0", Arrays.asList("zeroLeader"));
        MAIN_LEADERS.put("deptA", Arrays.asList("mainA"));
        MAIN_LEADERS.put("deptB", Arrays.asList("mainB"));
        MAIN_LEADERS.put("deptC", Arrays.asList("mainC"));
        MAIN_LEADERS.put("0", Arrays.asList("zeroMain"));
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

    private void freshStack(boolean withOrg) {
        SimpleContext ctx = new SimpleContext();
        if (withOrg) {
            ctx.put("org", new IOrgUserProvider() {
                @Override
                public List<String> findDeptLeaders(String deptId) {
                    return LEADERS.get(deptId);
                }

                @Override
                public List<String> findDeptMainLeaders(String deptId) {
                    return MAIN_LEADERS.get(deptId);
                }

                @Override
                public List<String> findByRole(String roleCode) {
                    return null;
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

    private static String leaders(String nodeKey, FlowData args) {
        return new FormDeptLeaderAssignmentHandler().assign(execOf(nodeKey, args));
    }

    private static String mainLeaders(String nodeKey, FlowData args) {
        return new FormDeptMainLeaderAssignmentHandler().assign(execOf(nodeKey, args));
    }

    @Test
    public void t01_prefixedWinsOverBare() {
        FlowData args = FlowData.create().set("f_depts", "deptA").set("depts", "deptB");
        assertEquals("leaderA,shared", leaders("depts", args));
    }

    @Test
    public void t02_bareFallbackAndMiss() {
        assertEquals("leaderB,shared", leaders("depts", FlowData.create().set("depts", "deptB")));
        assertNull(leaders("depts", FlowData.create().set("other", "deptB")));
    }

    @Test
    public void t03_hashRightHalfBareThenPrefixed() {
        FlowData bareFirst = FlowData.create().set("deptIds", "deptA").set("f_deptIds", "deptB");
        assertEquals("leaderA,shared", leaders("manager#deptIds", bareFirst));
        assertEquals("leaderB,shared", leaders("manager#deptIds", FlowData.create().set("f_deptIds", "deptB")));
        assertEquals("leaderA,shared", leaders("a#b#deptIds", FlowData.create().set("deptIds", "deptA")));
    }

    @Test
    public void t04_numberSuffixBasePrefixedThenBare() {
        FlowData prefixed = FlowData.create().set("f_depts", "deptA").set("depts", "deptB");
        assertEquals("leaderA,shared", leaders("depts_01", prefixed));
        assertEquals("leaderA,shared", leaders("depts_01", FlowData.create().set("depts", "deptA")));
    }

    @Test
    public void t05_numberSuffixTierMatchesLazyRegex() {
        FlowData args = FlowData.create().set("a_b", "deptA").set("a", "deptB");
        assertEquals("leaderA,shared", leaders("a_b_01", args));
        assertNull(leaders("_01", FlowData.create().set("f_", "deptA")));
        assertNull(leaders("depts_01x", FlowData.create().set("depts", "deptA")));
    }

    @Test
    public void t06_keyExistenceNotTruthiness() {
        FlowData args = new FlowData();
        args.put("f_depts", null);
        args.put("depts", "deptB");
        assertNull(leaders("depts", args));
    }

    @Test
    public void t07_commaStringResolvesAllDepartmentsInOrder() {
        assertEquals("leaderA,shared,leaderB", leaders("depts", FlowData.create().set("depts", "deptA, deptB")));
    }

    @Test
    public void t08_arrayResolvesAllDepartmentsInOrder() {
        assertEquals("leaderA,shared,leaderB", leaders("depts",
                FlowData.create().set("depts", Arrays.asList("deptA", "deptB"))));
    }

    @Test
    public void t09_arrayElementIsNotResplit() {
        assertNull(leaders("depts", FlowData.create().set("depts", Arrays.asList("deptA,deptB"))));
    }

    @Test
    public void t09b_primitiveArrayIsReadElementByElement() {
        assertEquals("zeroLeader", leaders("depts", FlowData.create().set("depts", new long[]{0L})));
    }

    @Test
    public void t10_duplicateDepartmentsAndLeadersAreDeduped() {
        assertEquals("leaderA,shared,leaderB", leaders("depts",
                FlowData.create().set("depts", "deptA,deptB,deptA")));
    }

    @Test
    public void t11_zeroDeptIdIsLegal() {
        assertEquals("zeroLeader", leaders("depts", FlowData.create().set("depts", 0L)));
    }

    @Test
    public void t12_mainLeaderVariant() {
        FlowData args = FlowData.create().set("depts", "deptA,deptB");
        assertEquals("mainA,mainB", mainLeaders("depts", args));
    }

    @Test
    public void t20_invalidDepartmentIsSkipped() {
        assertEquals("leaderB,shared", leaders("depts", FlowData.create().set("depts", "ghost,deptB")));
    }

    @Test
    public void t21_emptyAndNullAreEmpty() {
        assertNull(leaders("depts", FlowData.create().set("depts", "")));
        FlowData nul = new FlowData();
        nul.put("depts", null);
        assertNull(leaders("depts", nul));
    }

    @Test
    public void t22_missingOrgSpiIsEmpty() {
        freshStack(false);
        assertNull(leaders("depts", FlowData.create().set("depts", "deptA")));
        assertNull(mainLeaders("depts", FlowData.create().set("depts", "deptA")));
    }

    @Test
    public void t23_noNodeIsEmpty() {
        FlowData args = FlowData.create().set("f_", "deptA");
        Execution execution = new Execution();
        execution.setArgs(args);
        assertNull(new FormDeptLeaderAssignmentHandler().assign(execution));
        assertNull(leaders("", args));
    }

    @Test
    public void t24_nonScalarValueIsEmpty() {
        assertNull(leaders("depts", FlowData.create().set("depts", new HashMap<>())));
    }

    @Test
    public void t25_multipleLeadersKeepSpiOrder() {
        assertEquals("leaderC1,leaderC2", leaders("depts", FlowData.create().set("depts", "deptC")));
    }

    @Test
    public void t26_spiFailureSkipsDepartmentAndContinues() {
        SimpleContext ctx = new SimpleContext();
        ctx.put("org", new IOrgUserProvider() {
            @Override
            public List<String> findDeptLeaders(String deptId) {
                if ("deptA".equals(deptId)) throw new IllegalStateException("lookup failed");
                return LEADERS.get(deptId);
            }

            @Override
            public List<String> findDeptMainLeaders(String deptId) {
                if ("deptA".equals(deptId)) throw new IllegalStateException("lookup failed");
                return MAIN_LEADERS.get(deptId);
            }

            @Override
            public List<String> findByRole(String roleCode) {
                return null;
            }
        });
        ServiceContext.setContext(ctx);
        FlowData args = FlowData.create().set("depts", "deptA,deptB");
        assertEquals("leaderB,shared", leaders("depts", args));
        assertEquals("mainB", mainLeaders("depts", args));
    }
}
