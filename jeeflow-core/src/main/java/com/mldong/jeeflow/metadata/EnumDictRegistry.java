package com.mldong.jeeflow.metadata;

import com.mldong.jeeflow.enums.CountersignTypeEnum;
import com.mldong.jeeflow.enums.IDictEnum;
import com.mldong.jeeflow.enums.ProcessDefineStateEnum;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.enums.ProcessTaskTypeEnum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 引擎内置枚举字典注册表（v1.4.0，引擎元数据能力）
 *
 * <p>流程定义/实例/任务状态、提交类型等内置枚举只有引擎自己知道，集成方重复定义会
 * 与引擎值漂移。本注册表直接把枚举的 {@code code/message} 暴露为字典
 * （value = code，label = message），枚举新增/变更自动反映，集成方无需同步。</p>
 *
 * <p><b>key 约定</b>：{@code wf_} + 枚举名转下划线（如 ProcessInstanceStateEnum →
 * {@code wf_process_instance_state}），与 boot3/mldong 框架字典 key 完全一致，存量前端零改动。</p>
 *
 * @author mldong
 */
public class EnumDictRegistry {

    /** 内置枚举字典 key 清单（对齐 boot3 字典 key） */
    public static final String DICT_DEFINE_STATE = "wf_process_define_state";
    public static final String DICT_INSTANCE_STATE = "wf_process_instance_state";
    public static final String DICT_SUBMIT_TYPE = "wf_process_submit_type";
    public static final String DICT_TASK_STATE = "wf_process_task_state";
    public static final String DICT_TASK_TYPE = "wf_process_task_type";
    public static final String DICT_TASK_PERFORM_TYPE = "wf_process_task_perform_type";
    public static final String DICT_COUNTERSIGN_TYPE = "wf_countersign_type";

    private static final Map<String, List<DictItem>> DICTS = new LinkedHashMap<>();

    static {
        DICTS.put(DICT_DEFINE_STATE, fromEnum(ProcessDefineStateEnum.class));
        DICTS.put(DICT_INSTANCE_STATE, fromEnum(ProcessInstanceStateEnum.class));
        DICTS.put(DICT_SUBMIT_TYPE, fromEnum(ProcessSubmitTypeEnum.class));
        DICTS.put(DICT_TASK_STATE, fromEnum(ProcessTaskStateEnum.class));
        DICTS.put(DICT_TASK_TYPE, fromEnum(ProcessTaskTypeEnum.class));
        DICTS.put(DICT_TASK_PERFORM_TYPE, fromEnum(ProcessTaskPerformTypeEnum.class));
        DICTS.put(DICT_COUNTERSIGN_TYPE, fromEnum(CountersignTypeEnum.class));
    }

    /** 内置枚举字典 key 清单 */
    public List<String> listDictKeys() {
        return new ArrayList<>(DICTS.keySet());
    }

    /** 按 key 取字典（[{value, label}]），未知 key 返回空列表 */
    public List<DictItem> getDict(String key) {
        List<DictItem> items = DICTS.get(key);
        return items == null ? new ArrayList<>() : items;
    }

    /** 单个字典项 */
    public static class DictItem {
        private final String value;
        private final String label;

        public DictItem(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public String getValue() { return value; }
        public String getLabel() { return label; }
    }

    /** 从「含 getCode()/getMessage() 的枚举」生成字典项 */
    private static <E extends Enum<E> & IDictEnum> List<DictItem> fromEnum(Class<E> enumClass) {
        List<DictItem> items = new ArrayList<>();
        for (E e : enumClass.getEnumConstants()) {
            items.add(new DictItem(String.valueOf(e.getCode()), e.getMessage()));
        }
        return items;
    }
}
