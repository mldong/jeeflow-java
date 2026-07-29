import { i as isArray, e as isString, f as isBoolean, b as buildProps, d as definePropType, g as debugWarn, u as useNamespace, N as NOOP, h as isFunction, t as throwError, r as refDebounced, w as withInstall, a as withNoopInstall } from "./error-DiL_p-4A.js";
import { c as componentSizes, a as addUnit } from "./style-_T5b_00u.js";
import { f as formContextKey, a as formItemContextKey } from "./constants-BXBCHRH4.js";
import { a as useFormSize } from "./use-form-item-BToCvxP0.js";
import { b, u, c } from "./use-form-item-BToCvxP0.js";
import { g as getProp } from "./objects-DdSh0aPF.js";
import { ref, computed, defineComponent, reactive, watch, provide, toRefs, openBlock, createElementBlock, normalizeClass, renderSlot, inject, onMounted, onBeforeUnmount, onUpdated, createVNode, Fragment, nextTick, useSlots, unref, withCtx, createBlock, resolveDynamicComponent, normalizeStyle, createTextVNode, toDisplayString, createCommentVNode, createElementVNode, TransitionGroup } from "vue";
import { b as castArray, c as cloneDeep } from "./index-D9cY2WMP.js";
import { u as useId } from "./index-DuyY_KOz.js";
import { b as useResizeObserver } from "./index-BoUuUOkb.js";
import { S as Schema } from "./index-B_zQ2KpM.js";
const formMetaProps = buildProps({
  /**
  * @description Control the size of components in this form.
  */
  size: {
    type: String,
    values: componentSizes
  },
  /**
  * @description Whether to disable all components in this form. If set to `true`, it will override the `disabled` prop of the inner component.
  */
  disabled: Boolean
});
const formProps = buildProps({
  ...formMetaProps,
  /**
  * @description Data of form component.
  */
  model: Object,
  /**
  * @description Validation rules of form.
  */
  rules: { type: definePropType(Object) },
  /**
  * @description Position of label. If set to `'left'` or `'right'`, `label-width` prop is also required.
  */
  labelPosition: {
    type: String,
    values: [
      "left",
      "right",
      "top"
    ],
    default: "right"
  },
  /**
  * @description Position of asterisk.
  */
  requireAsteriskPosition: {
    type: String,
    values: ["left", "right"],
    default: "left"
  },
  /**
  * @description Width of label, e.g. `'50px'`. All its direct child form items will inherit this value. `auto` is supported.
  */
  labelWidth: {
    type: [String, Number],
    default: ""
  },
  /**
  * @description Suffix of the label.
  */
  labelSuffix: {
    type: String,
    default: ""
  },
  /**
  * @description Whether the form is inline.
  */
  inline: Boolean,
  /**
  * @description Whether to display the error message inline with the form item.
  */
  inlineMessage: Boolean,
  /**
  * @description Whether to display an icon indicating the validation result.
  */
  statusIcon: Boolean,
  /**
  * @description Whether to show the error message.
  */
  showMessage: {
    type: Boolean,
    default: true
  },
  /**
  * @description Whether to trigger validation when the `rules` prop is changed.
  */
  validateOnRuleChange: {
    type: Boolean,
    default: true
  },
  /**
  * @description Whether to hide required fields should have a red asterisk (star) beside their labels.
  */
  hideRequiredAsterisk: Boolean,
  /**
  * @description When validation fails, scroll to the first error form entry.
  */
  scrollToError: Boolean,
  /**
  * @description When validation fails, it scrolls to the first error item based on the scrollIntoView option.
  */
  scrollIntoViewOptions: {
    type: definePropType([Object, Boolean]),
    default: true
  }
});
const formEmits = { validate: (prop, isValid, message) => (isArray(prop) || isString(prop)) && isBoolean(isValid) && isString(message) };
const SCOPE = "ElForm";
function useFormLabelWidth() {
  const potentialLabelWidthArr = ref([]);
  const autoLabelWidth = computed(() => {
    if (!potentialLabelWidthArr.value.length) return "0";
    const max = Math.max(...potentialLabelWidthArr.value);
    return max ? `${max}px` : "";
  });
  function getLabelWidthIndex(width) {
    const index = potentialLabelWidthArr.value.indexOf(width);
    if (index === -1 && autoLabelWidth.value === "0") debugWarn(SCOPE, `unexpected width ${width}`);
    return index;
  }
  function registerLabelWidth(val, oldVal) {
    if (val && oldVal) {
      const index = getLabelWidthIndex(oldVal);
      potentialLabelWidthArr.value.splice(index, 1, val);
    } else if (val) potentialLabelWidthArr.value.push(val);
  }
  function deregisterLabelWidth(val) {
    const index = getLabelWidthIndex(val);
    if (index > -1) potentialLabelWidthArr.value.splice(index, 1);
  }
  return {
    autoLabelWidth,
    registerLabelWidth,
    deregisterLabelWidth
  };
}
const filterFields = (fields, props) => {
  const normalized = castArray(props).map((prop) => isArray(prop) ? prop.join(".") : prop);
  return normalized.length > 0 ? fields.filter((field) => field.propString && normalized.includes(field.propString)) : fields;
};
const COMPONENT_NAME$1 = "ElForm";
var form_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: COMPONENT_NAME$1,
  __name: "form",
  props: formProps,
  emits: formEmits,
  setup(__props, { expose: __expose, emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const formRef = ref();
    const fields = reactive([]);
    const initialValues = /* @__PURE__ */ new Map();
    const formSize = useFormSize();
    const ns = useNamespace("form");
    const formClasses = computed(() => {
      const { labelPosition, inline } = props;
      return [
        ns.b(),
        ns.m(formSize.value || "default"),
        {
          [ns.m(`label-${labelPosition}`)]: labelPosition,
          [ns.m("inline")]: inline
        }
      ];
    });
    const getField = (prop) => {
      return filterFields(fields, [prop])[0];
    };
    const addField = (field) => {
      if (!fields.includes(field)) fields.push(field);
      if (field.propString) if (initialValues.has(field.propString)) field.setInitialValue(initialValues.get(field.propString));
      else initialValues.set(field.propString, cloneDeep(field.fieldValue));
    };
    const removeField = (field, oldPropString) => {
      if (oldPropString) {
        initialValues.delete(oldPropString);
        return;
      }
      const idx = fields.indexOf(field);
      if (idx > -1) {
        fields.splice(idx, 1);
        if (field.propString) initialValues.set(field.propString, cloneDeep(field.getInitialValue()));
      }
    };
    const setInitialValues = (initModel) => {
      if (!props.model) {
        debugWarn(COMPONENT_NAME$1, "model is required for setInitialValues to work.");
        return;
      }
      if (!initModel) {
        debugWarn(COMPONENT_NAME$1, "initModel is required for setInitialValues to work.");
        return;
      }
      for (const key of initialValues.keys()) initialValues.set(key, cloneDeep(getProp(initModel, key).value));
      fields.forEach((field) => {
        if (field.prop) field.setInitialValue(getProp(initModel, field.prop).value);
      });
    };
    const resetFields = (properties = []) => {
      if (!props.model) {
        debugWarn(COMPONENT_NAME$1, "model is required for resetFields to work.");
        return;
      }
      filterFields(fields, properties).forEach((field) => field.resetField());
      const activePropStrings = new Set(fields.map((f) => f.propString).filter(Boolean));
      const propsToCheck = properties.length > 0 ? castArray(properties).map((p) => isArray(p) ? p.join(".") : p) : [...initialValues.keys()];
      for (const propString of propsToCheck) if (!activePropStrings.has(propString) && initialValues.has(propString)) getProp(props.model, propString).value = cloneDeep(initialValues.get(propString));
    };
    const clearValidate = (props2 = []) => {
      filterFields(fields, props2).forEach((field) => field.clearValidate());
    };
    const isValidatable = computed(() => {
      const hasModel = !!props.model;
      if (!hasModel) debugWarn(COMPONENT_NAME$1, "model is required for validate to work.");
      return hasModel;
    });
    const obtainValidateFields = (props2) => {
      if (fields.length === 0) return [];
      const filteredFields = filterFields(fields, props2);
      if (!filteredFields.length) {
        debugWarn(COMPONENT_NAME$1, "please pass correct props!");
        return [];
      }
      return filteredFields;
    };
    const validate = async (callback) => validateField(void 0, callback);
    const doValidateField = async (props2 = []) => {
      if (!isValidatable.value) return false;
      const fields2 = obtainValidateFields(props2);
      if (fields2.length === 0) return true;
      let validationErrors = {};
      for (const field of fields2) try {
        await field.validate("");
        if (field.validateState === "error" && !field.error) field.resetField();
      } catch (fields3) {
        validationErrors = {
          ...validationErrors,
          ...fields3
        };
      }
      if (Object.keys(validationErrors).length === 0) return true;
      return Promise.reject(validationErrors);
    };
    const validateField = async (modelProps = [], callback) => {
      var _a;
      let result = false;
      const shouldThrow = !isFunction(callback);
      try {
        result = await doValidateField(modelProps);
        if (result === true) await (callback == null ? void 0 : callback(result));
        return result;
      } catch (e) {
        if (e instanceof Error) throw e;
        const invalidFields = e;
        if (props.scrollToError) {
          if (formRef.value) (_a = formRef.value.querySelector(`.${ns.b()}-item.is-error`)) == null ? void 0 : _a.scrollIntoView(props.scrollIntoViewOptions);
        }
        !result && await (callback == null ? void 0 : callback(false, invalidFields));
        return shouldThrow && Promise.reject(invalidFields);
      }
    };
    const scrollToField = (prop) => {
      var _a;
      const field = getField(prop);
      if (field) (_a = field.$el) == null ? void 0 : _a.scrollIntoView(props.scrollIntoViewOptions);
    };
    watch(() => props.rules, () => {
      if (props.validateOnRuleChange) validate().catch(NOOP);
    }, {
      deep: true,
      flush: "post"
    });
    provide(formContextKey, reactive({
      ...toRefs(props),
      emit,
      resetFields,
      clearValidate,
      validateField,
      getField,
      addField,
      removeField,
      setInitialValues,
      ...useFormLabelWidth()
    }));
    __expose({
      /**
      * @description Validate the whole form. Receives a callback or returns `Promise`.
      */
      validate,
      /**
      * @description Validate specified fields.
      */
      validateField,
      /**
      * @description Reset specified fields and remove validation result.
      */
      resetFields,
      /**
      * @description Clear validation message for specified fields.
      */
      clearValidate,
      /**
      * @description Scroll to the specified fields.
      */
      scrollToField,
      /**
      * @description Get a field context.
      */
      getField,
      /**
      * @description All fields context.
      */
      fields,
      /**
      * @description Set initial values for form fields. When `resetFields` is called, fields will reset to these values.
      */
      setInitialValues
    });
    return (_ctx, _cache) => {
      return openBlock(), createElementBlock("form", {
        ref_key: "formRef",
        ref: formRef,
        class: normalizeClass(formClasses.value)
      }, [renderSlot(_ctx.$slots, "default")], 2);
    };
  }
});
var form_default = form_vue_vue_type_script_setup_true_lang_default;
const formItemValidateStates = [
  "",
  "error",
  "validating",
  "success"
];
const formItemProps = buildProps({
  /**
  * @description Label text.
  */
  label: String,
  /**
  * @description Width of label, e.g. `'50px'`. `'auto'` is supported.
  */
  labelWidth: { type: [String, Number] },
  /**
  * @description Position of label. If set to `'left'` or `'right'`, `label-width` prop is also required. The default is extend from `form label-position`.
  */
  labelPosition: {
    type: String,
    values: [
      "left",
      "right",
      "top",
      ""
    ],
    default: ""
  },
  /**
  * @description  A key of `model`. It could be an array of property paths (e.g `['a', 'b', '0']`). In the use of `validate` and `resetFields` method, the attribute is required.
  */
  prop: { type: definePropType([String, Array]) },
  /**
  * @description Whether the field is required or not, will be determined by validation rules if omitted.
  */
  required: {
    type: Boolean,
    default: void 0
  },
  /**
  * @description Validation rules of form, see the [following table](#formitemrule), more advanced usage at [async-validator](https://github.com/yiminghe/async-validator).
  */
  rules: { type: definePropType([Object, Array]) },
  /**
  * @description Field error message, set its value and the field will validate error and show this message immediately.
  */
  error: String,
  /**
  * @description Validation state of formItem.
  */
  validateStatus: {
    type: String,
    values: formItemValidateStates
  },
  /**
  * @description Same as for in native label.
  */
  for: String,
  /**
  * @description Inline style validate message.
  */
  inlineMessage: {
    type: Boolean,
    default: void 0
  },
  /**
  * @description Whether to show the error message.
  */
  showMessage: {
    type: Boolean,
    default: true
  },
  /**
  * @description Control the size of components in this form-item.
  */
  size: {
    type: String,
    values: componentSizes
  }
});
const COMPONENT_NAME = "ElLabelWrap";
var form_label_wrap_default = /* @__PURE__ */ defineComponent({
  name: COMPONENT_NAME,
  props: {
    isAutoWidth: Boolean,
    updateAll: Boolean
  },
  setup(props, { slots }) {
    const formContext = inject(formContextKey, void 0);
    const formItemContext = inject(formItemContextKey);
    if (!formItemContext) throwError(COMPONENT_NAME, "usage: <el-form-item><label-wrap /></el-form-item>");
    const ns = useNamespace("form");
    const el = ref();
    const computedWidth = ref(0);
    const getLabelWidth = () => {
      var _a;
      if ((_a = el.value) == null ? void 0 : _a.firstElementChild) {
        const width = window.getComputedStyle(el.value.firstElementChild).width;
        return Math.ceil(Number.parseFloat(width));
      } else return 0;
    };
    const updateLabelWidth = (action = "update") => {
      nextTick(() => {
        if (slots.default && props.isAutoWidth) {
          if (action === "update") computedWidth.value = getLabelWidth();
          else if (action === "remove") formContext == null ? void 0 : formContext.deregisterLabelWidth(computedWidth.value);
        }
      });
    };
    const updateLabelWidthFn = () => updateLabelWidth("update");
    onMounted(() => {
      updateLabelWidthFn();
    });
    onBeforeUnmount(() => {
      updateLabelWidth("remove");
    });
    onUpdated(() => updateLabelWidthFn());
    watch(computedWidth, (val, oldVal) => {
      if (props.updateAll) formContext == null ? void 0 : formContext.registerLabelWidth(val, oldVal);
    });
    useResizeObserver(computed(() => {
      var _a;
      return ((_a = el.value) == null ? void 0 : _a.firstElementChild) ?? null;
    }), updateLabelWidthFn);
    return () => {
      var _a, _b;
      if (!slots) return null;
      const { isAutoWidth } = props;
      if (isAutoWidth) {
        const autoLabelWidth = formContext == null ? void 0 : formContext.autoLabelWidth;
        const hasLabel = formItemContext == null ? void 0 : formItemContext.hasLabel;
        const style = {};
        if (hasLabel && autoLabelWidth && autoLabelWidth !== "auto") {
          const marginWidth = Math.max(0, Number.parseInt(autoLabelWidth, 10) - computedWidth.value);
          const marginPosition = (formItemContext.labelPosition || formContext.labelPosition) === "left" ? "marginRight" : "marginLeft";
          if (marginWidth) style[marginPosition] = `${marginWidth}px`;
        }
        return createVNode("div", {
          "ref": el,
          "class": [ns.be("item", "label-wrap")],
          "style": style
        }, [(_a = slots.default) == null ? void 0 : _a.call(slots)]);
      } else return createVNode(Fragment, { "ref": el }, [(_b = slots.default) == null ? void 0 : _b.call(slots)]);
    };
  }
});
const _hoisted_1 = ["role", "aria-labelledby"];
var form_item_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: "ElFormItem",
  __name: "form-item",
  props: formItemProps,
  setup(__props, { expose: __expose }) {
    const props = __props;
    const slots = useSlots();
    const formContext = inject(formContextKey, void 0);
    const parentFormItemContext = inject(formItemContextKey, void 0);
    const _size = useFormSize(void 0, { formItem: false });
    const ns = useNamespace("form-item");
    const labelId = useId().value;
    const inputIds = ref([]);
    const validateState = ref("");
    const validateStateDebounced = refDebounced(validateState, 100);
    const validateMessage = ref("");
    const formItemRef = ref();
    let initialValue = void 0;
    let isResettingField = false;
    const labelPosition = computed(() => props.labelPosition || (formContext == null ? void 0 : formContext.labelPosition));
    const labelStyle = computed(() => {
      if (labelPosition.value === "top") return {};
      return { width: addUnit(props.labelWidth ?? (formContext == null ? void 0 : formContext.labelWidth)) };
    });
    const contentStyle = computed(() => {
      if (labelPosition.value === "top" || (formContext == null ? void 0 : formContext.inline)) return {};
      if (!props.label && !props.labelWidth && isNested) return {};
      const labelWidth = addUnit(props.labelWidth ?? (formContext == null ? void 0 : formContext.labelWidth));
      if (!props.label && !slots.label) return { marginLeft: labelWidth };
      return {};
    });
    const formItemClasses = computed(() => [
      ns.b(),
      ns.m(_size.value),
      ns.is("error", validateState.value === "error"),
      ns.is("validating", validateState.value === "validating"),
      ns.is("success", validateState.value === "success"),
      ns.is("required", isRequired.value || props.required),
      ns.is("no-asterisk", formContext == null ? void 0 : formContext.hideRequiredAsterisk),
      (formContext == null ? void 0 : formContext.requireAsteriskPosition) === "right" ? "asterisk-right" : "asterisk-left",
      {
        [ns.m("feedback")]: formContext == null ? void 0 : formContext.statusIcon,
        [ns.m(`label-${labelPosition.value}`)]: labelPosition.value
      }
    ]);
    const _inlineMessage = computed(() => isBoolean(props.inlineMessage) ? props.inlineMessage : (formContext == null ? void 0 : formContext.inlineMessage) || false);
    const validateClasses = computed(() => [ns.e("error"), { [ns.em("error", "inline")]: _inlineMessage.value }]);
    const propString = computed(() => {
      if (!props.prop) return "";
      return isArray(props.prop) ? props.prop.join(".") : props.prop;
    });
    const hasLabel = computed(() => {
      return !!(props.label || slots.label);
    });
    const labelFor = computed(() => {
      return props.for ?? (inputIds.value.length === 1 ? inputIds.value[0] : void 0);
    });
    const isGroup = computed(() => {
      return !labelFor.value && hasLabel.value;
    });
    const isNested = !!parentFormItemContext;
    const fieldValue = computed(() => {
      const model = formContext == null ? void 0 : formContext.model;
      if (!model || !props.prop) return;
      return getProp(model, props.prop).value;
    });
    const normalizedRules = computed(() => {
      const { required } = props;
      const rules = [];
      if (props.rules) rules.push(...castArray(props.rules));
      const formRules = formContext == null ? void 0 : formContext.rules;
      if (formRules && props.prop) {
        const _rules = getProp(formRules, props.prop).value;
        if (_rules) rules.push(...castArray(_rules));
      }
      if (required !== void 0) {
        const requiredRules = rules.map((rule, i) => [rule, i]).filter(([rule]) => "required" in rule);
        if (requiredRules.length > 0) for (const [rule, i] of requiredRules) {
          if (rule.required === required) continue;
          rules[i] = {
            ...rule,
            required
          };
        }
        else rules.push({ required });
      }
      return rules;
    });
    const validateEnabled = computed(() => normalizedRules.value.length > 0);
    const getFilteredRule = (trigger) => {
      return normalizedRules.value.filter((rule) => {
        if (!rule.trigger || !trigger) return true;
        if (isArray(rule.trigger)) return rule.trigger.includes(trigger);
        else return rule.trigger === trigger;
      }).map(({ trigger: trigger2, ...rule }) => rule);
    };
    const isRequired = computed(() => normalizedRules.value.some((rule) => rule.required));
    const shouldShowError = computed(() => validateStateDebounced.value === "error" && props.showMessage && ((formContext == null ? void 0 : formContext.showMessage) ?? true));
    const currentLabel = computed(() => `${props.label || ""}${(formContext == null ? void 0 : formContext.labelSuffix) || ""}`);
    const setValidationState = (state) => {
      validateState.value = state;
    };
    const onValidationFailed = (error) => {
      var _a;
      const { errors, fields } = error;
      if (!errors || !fields) console.error(error);
      setValidationState("error");
      validateMessage.value = errors ? ((_a = errors == null ? void 0 : errors[0]) == null ? void 0 : _a.message) ?? `${props.prop} is required` : "";
      formContext == null ? void 0 : formContext.emit("validate", props.prop, false, validateMessage.value);
    };
    const onValidationSucceeded = () => {
      setValidationState("success");
      formContext == null ? void 0 : formContext.emit("validate", props.prop, true, "");
    };
    const doValidate = async (rules) => {
      const modelName = propString.value;
      return new Schema({ [modelName]: rules }).validate({ [modelName]: fieldValue.value }, { firstFields: true }).then(() => {
        onValidationSucceeded();
        return true;
      }).catch((err) => {
        onValidationFailed(err);
        return Promise.reject(err);
      });
    };
    const validate = async (trigger, callback) => {
      if (isResettingField || !props.prop) return false;
      const hasCallback = isFunction(callback);
      if (!validateEnabled.value) {
        callback == null ? void 0 : callback(false);
        return false;
      }
      const rules = getFilteredRule(trigger);
      if (rules.length === 0) {
        callback == null ? void 0 : callback(true);
        return true;
      }
      setValidationState("validating");
      return doValidate(rules).then(() => {
        callback == null ? void 0 : callback(true);
        return true;
      }).catch((err) => {
        const { fields } = err;
        callback == null ? void 0 : callback(false, fields);
        return hasCallback ? false : Promise.reject(fields);
      });
    };
    const clearValidate = () => {
      setValidationState("");
      validateMessage.value = "";
      isResettingField = false;
    };
    const resetField = async () => {
      const model = formContext == null ? void 0 : formContext.model;
      if (!model || !props.prop) return;
      const computedValue = getProp(model, props.prop);
      isResettingField = true;
      computedValue.value = cloneDeep(initialValue);
      await nextTick();
      clearValidate();
      isResettingField = false;
    };
    const addInputId = (id) => {
      if (!inputIds.value.includes(id)) inputIds.value.push(id);
    };
    const removeInputId = (id) => {
      inputIds.value = inputIds.value.filter((listId) => listId !== id);
    };
    const setInitialValue = (value) => {
      initialValue = cloneDeep(value);
    };
    const getInitialValue = () => initialValue;
    watch(() => props.error, (val) => {
      validateMessage.value = val || "";
      setValidationState(val ? "error" : "");
    }, { immediate: true });
    watch(() => props.validateStatus, (val) => setValidationState(val || ""));
    const context = reactive({
      ...toRefs(props),
      $el: formItemRef,
      size: _size,
      validateMessage,
      validateState,
      labelId,
      inputIds,
      isGroup,
      hasLabel,
      fieldValue,
      addInputId,
      removeInputId,
      resetField,
      clearValidate,
      validate,
      propString,
      setInitialValue,
      getInitialValue
    });
    provide(formItemContextKey, context);
    watch(propString, (newPropString, oldPropString) => {
      if (!formContext || !oldPropString) return;
      formContext.removeField(context, oldPropString);
      if (newPropString) {
        setInitialValue(fieldValue.value);
        formContext.addField(context);
      }
    });
    onMounted(() => {
      if (props.prop) {
        setInitialValue(fieldValue.value);
        formContext == null ? void 0 : formContext.addField(context);
      }
    });
    onBeforeUnmount(() => {
      formContext == null ? void 0 : formContext.removeField(context);
    });
    __expose({
      /**
      * @description Form item size.
      */
      size: _size,
      /**
      * @description Validation message.
      */
      validateMessage,
      /**
      * @description Validation state.
      */
      validateState,
      /**
      * @description Validate form item.
      */
      validate,
      /**
      * @description Remove validation status of the field.
      */
      clearValidate,
      /**
      * @description Reset current field and remove validation result.
      */
      resetField,
      /**
      * @description Set initial value for this field. When `resetField` is called, the field will reset to this value.
      */
      setInitialValue
    });
    return (_ctx, _cache) => {
      var _a;
      return openBlock(), createElementBlock("div", {
        ref_key: "formItemRef",
        ref: formItemRef,
        class: normalizeClass(formItemClasses.value),
        role: isGroup.value ? "group" : void 0,
        "aria-labelledby": isGroup.value ? unref(labelId) : void 0
      }, [createVNode(unref(form_label_wrap_default), {
        "is-auto-width": labelStyle.value.width === "auto",
        "update-all": ((_a = unref(formContext)) == null ? void 0 : _a.labelWidth) === "auto"
      }, {
        default: withCtx(() => [!!(__props.label || _ctx.$slots.label) ? (openBlock(), createBlock(resolveDynamicComponent(labelFor.value ? "label" : "div"), {
          key: 0,
          id: unref(labelId),
          for: labelFor.value,
          class: normalizeClass(unref(ns).e("label")),
          style: normalizeStyle(labelStyle.value)
        }, {
          default: withCtx(() => [renderSlot(_ctx.$slots, "label", { label: currentLabel.value }, () => [createTextVNode(toDisplayString(currentLabel.value), 1)])]),
          _: 3
        }, 8, [
          "id",
          "for",
          "class",
          "style"
        ])) : createCommentVNode("v-if", true)]),
        _: 3
      }, 8, ["is-auto-width", "update-all"]), createElementVNode("div", {
        class: normalizeClass(unref(ns).e("content")),
        style: normalizeStyle(contentStyle.value)
      }, [renderSlot(_ctx.$slots, "default"), createVNode(TransitionGroup, { name: `${unref(ns).namespace.value}-zoom-in-top` }, {
        default: withCtx(() => [shouldShowError.value ? renderSlot(_ctx.$slots, "error", {
          key: 0,
          error: validateMessage.value
        }, () => [createElementVNode("div", { class: normalizeClass(validateClasses.value) }, toDisplayString(validateMessage.value), 3)]) : createCommentVNode("v-if", true)]),
        _: 3
      }, 8, ["name"])], 6)], 10, _hoisted_1);
    };
  }
});
var form_item_default = form_item_vue_vue_type_script_setup_true_lang_default;
const ElForm = withInstall(form_default, { FormItem: form_item_default });
withNoopInstall(form_item_default);
export {
  ElForm,
  ElForm as default,
  formContextKey,
  formEmits,
  formItemContextKey,
  formItemProps,
  formItemValidateStates,
  formMetaProps,
  formProps,
  b as useFormDisabled,
  u as useFormItem,
  c as useFormItemInputId,
  useFormSize
};
//# sourceMappingURL=index-CQpQqZOK.js.map
