import { i as isArray, b as buildProps, t as throwError, l as isObject, u as useNamespace, m as isFocusable, n as isIOS, k as isNumber, d as definePropType, j as isClient, g as debugWarn, w as withInstall, o as isElement, N as NOOP, p as isEmpty, q as isUndefined, h as isFunction, s as isPlainObject, v as useDebounceFn, e as isString, a as withNoopInstall } from "./error-DiL_p-4A.js";
import { _ as _plugin_vue_export_helper_default } from "./index-Blbk90Jx.js";
import { u as useId } from "./index-DuyY_KOz.js";
import { isVNode, inject, computed, getCurrentInstance, toRaw, watch, defineComponent, unref, reactive, toRefs, onBeforeUnmount, nextTick, withDirectives, openBlock, createElementBlock, mergeProps, toHandlerKey, withModifiers, renderSlot, createElementVNode, toDisplayString, vShow, ref, provide, onMounted, normalizeClass, shallowRef, toRef, createBlock, Transition, withCtx, normalizeStyle, Fragment, createVNode, onActivated, onUpdated, resolveDynamicComponent, createCommentVNode, useSlots, watchEffect, resolveComponent, resolveDirective, renderList, createTextVNode } from "vue";
import { b as castArray, j as get, i as isEqual, h as isNil, k as clamp, l as findLastIndex } from "./index-D9cY2WMP.js";
import { C as CHANGE_EVENT, U as UPDATE_MODEL_EVENT } from "./event-BZTOGHfp.js";
import { b as arrow_down_default, i as iconPropType, a as circle_close_default, E as ElIcon, c as close_default, V as ValidateComponentsMap } from "./index-D5MsDfVU.js";
import { c as componentSizes, u as useSizeProp, a as addUnit } from "./style-_T5b_00u.js";
import { a as useEmptyValuesProps, u as useLocale, b as useEmptyValues } from "./index-Dd4aCaME.js";
import { u as useAriaProps } from "./index-C-31KauU.js";
import { u as useTooltipContentProps, E as Ee, a as ElTooltip } from "./index-Ck348kPa.js";
import { c as useMutationObserver, b as useResizeObserver, u as useEventListener } from "./index-BoUuUOkb.js";
import { a as useFormSize, u as useFormItem, c as useFormItemInputId, b as useFormDisabled } from "./use-form-item-BToCvxP0.js";
import { g as getEventCode, E as EVENT_CODE } from "./event-Ka8Z03Z9.js";
import { s as scrollIntoView } from "./scroll-BL4Qux_Z.js";
import { a as useComposition, u as useFocusController } from "./index-CRD3I8pG.js";
const escapeStringRegexp = (string = "") => string.replace(/[|\\{}()[\]^$+*?.]/g, "\\$&").replace(/-/g, "\\x2d");
const flattedChildren = (children) => {
  const vNodes = isArray(children) ? children : [children];
  const result = [];
  vNodes.forEach((child) => {
    var _a;
    if (isArray(child)) result.push(...flattedChildren(child));
    else if (isVNode(child) && ((_a = child.component) == null ? void 0 : _a.subTree)) result.push(child, ...flattedChildren(child.component.subTree));
    else if (isVNode(child) && isArray(child.children)) result.push(...flattedChildren(child.children));
    else if (isVNode(child) && child.shapeFlag === 2) result.push(...flattedChildren(child.type()));
    else result.push(child);
  });
  return result;
};
const selectGroupKey = Symbol("ElSelectGroup");
const selectKey = Symbol("ElSelect");
const COMPONENT_NAME$3 = "ElOption";
const optionProps = buildProps({
  /**
  * @description value of option
  */
  value: {
    type: [
      String,
      Number,
      Boolean,
      Object
    ],
    required: true
  },
  /**
  * @description label of option, same as `value` if omitted
  */
  label: { type: [String, Number] },
  created: Boolean,
  /**
  * @description whether option is disabled
  */
  disabled: Boolean
});
function useOption(props, states) {
  const select = inject(selectKey);
  if (!select) throwError(COMPONENT_NAME$3, "usage: <el-select><el-option /></el-select/>");
  const selectGroup = inject(selectGroupKey, { disabled: false });
  const itemSelected = computed(() => {
    return contains(castArray(select.props.modelValue), props.value);
  });
  const limitReached = computed(() => {
    if (select.props.multiple) {
      const modelValue = castArray(select.props.modelValue ?? []);
      return !itemSelected.value && modelValue.length >= select.props.multipleLimit && select.props.multipleLimit > 0;
    } else return false;
  });
  const currentLabel = computed(() => {
    return props.label ?? (isObject(props.value) ? "" : props.value);
  });
  const currentValue = computed(() => {
    return props.value || props.label || "";
  });
  const isDisabled = computed(() => {
    return props.disabled || states.groupDisabled || limitReached.value;
  });
  const instance = getCurrentInstance();
  const contains = (arr = [], target) => {
    if (!isObject(props.value)) return arr && arr.includes(target);
    else {
      const valueKey = select.props.valueKey;
      return arr && arr.some((item) => {
        return toRaw(get(item, valueKey)) === get(target, valueKey);
      });
    }
  };
  const hoverItem = () => {
    if (!isDisabled.value) select.states.hoveringIndex = select.optionsArray.indexOf(instance.proxy);
  };
  const updateOption = (query) => {
    states.visible = new RegExp(escapeStringRegexp(query), "i").test(String(currentLabel.value)) || props.created;
  };
  watch(() => currentLabel.value, () => {
    if (!props.created && !select.props.remote) select.setSelected();
  });
  watch(() => props.value, (val, oldVal) => {
    const { remote, valueKey } = select.props;
    if (remote ? val !== oldVal : !isEqual(val, oldVal)) {
      select.onOptionDestroy(oldVal, instance.proxy);
      select.onOptionCreate(instance.proxy);
    }
    if (!props.created && !remote) {
      if (valueKey && isObject(val) && isObject(oldVal) && val[valueKey] === oldVal[valueKey]) return;
      select.setSelected();
    }
  });
  watch(() => selectGroup.disabled, () => {
    states.groupDisabled = selectGroup.disabled;
  }, { immediate: true });
  return {
    select,
    currentLabel,
    currentValue,
    itemSelected,
    isDisabled,
    hoverItem,
    updateOption
  };
}
var option_vue_vue_type_script_lang_default = defineComponent({
  name: COMPONENT_NAME$3,
  componentName: COMPONENT_NAME$3,
  props: optionProps,
  setup(props) {
    const ns = useNamespace("select");
    const id = useId();
    const containerKls = computed(() => [
      ns.be("dropdown", "item"),
      ns.is("disabled", unref(isDisabled)),
      ns.is("selected", unref(itemSelected)),
      ns.is("hovering", unref(hover))
    ]);
    const states = reactive({
      index: -1,
      groupDisabled: false,
      visible: true,
      hover: false
    });
    const mouseMoveEventName = isIOS ? null : "mousemove";
    const { currentLabel, itemSelected, isDisabled, select, hoverItem, updateOption } = useOption(props, states);
    const { visible, hover } = toRefs(states);
    const vm = getCurrentInstance().proxy;
    select.onOptionCreate(vm);
    onBeforeUnmount(() => {
      const key = vm.value;
      nextTick(() => {
        const { selected: selectedOptions } = select.states;
        const doesSelected = selectedOptions.some((item) => {
          return item.value === vm.value;
        });
        if (select.states.cachedOptions.get(key) === vm && !doesSelected) select.states.cachedOptions.delete(key);
      });
      select.onOptionDestroy(key, vm);
    });
    function selectOptionClick() {
      if (!isDisabled.value) select.handleOptionSelect(vm);
    }
    const handleMousedown = (event) => {
      let target = event.target;
      const currentTarget = event.currentTarget;
      while (target && target !== currentTarget) {
        if (isFocusable(target)) return;
        target = target.parentElement;
      }
      event.preventDefault();
    };
    return {
      ns,
      id,
      containerKls,
      currentLabel,
      itemSelected,
      isDisabled,
      select,
      visible,
      hover,
      states,
      mouseMoveEventName,
      hoverItem,
      handleMousedown,
      updateOption,
      selectOptionClick
    };
  }
});
const _hoisted_1$3 = [
  "id",
  "aria-disabled",
  "aria-selected"
];
function _sfc_render$3(_ctx, _cache, $props, $setup, $data, $options) {
  return withDirectives((openBlock(), createElementBlock("li", mergeProps({
    id: _ctx.id,
    class: _ctx.containerKls,
    role: "option",
    "aria-disabled": _ctx.isDisabled || void 0,
    "aria-selected": _ctx.itemSelected
  }, { [toHandlerKey(_ctx.mouseMoveEventName)]: _cache[0] || (_cache[0] = (...args) => _ctx.hoverItem && _ctx.hoverItem(...args)) }, {
    onMousedown: _cache[1] || (_cache[1] = (...args) => _ctx.handleMousedown && _ctx.handleMousedown(...args)),
    onClick: _cache[2] || (_cache[2] = withModifiers((...args) => _ctx.selectOptionClick && _ctx.selectOptionClick(...args), ["stop"]))
  }), [renderSlot(_ctx.$slots, "default", {}, () => [createElementVNode("span", null, toDisplayString(_ctx.currentLabel), 1)])], 16, _hoisted_1$3)), [[vShow, _ctx.visible]]);
}
var option_default = /* @__PURE__ */ _plugin_vue_export_helper_default(option_vue_vue_type_script_lang_default, [["render", _sfc_render$3]]);
const scrollbarProps = buildProps({
  /**
  * @description trigger distance(px)
  */
  distance: {
    type: Number,
    default: 0
  },
  /**
  * @description height of scrollbar
  */
  height: {
    type: [String, Number],
    default: ""
  },
  /**
  * @description max height of scrollbar
  */
  maxHeight: {
    type: [String, Number],
    default: ""
  },
  /**
  * @description whether to use the native scrollbar
  */
  native: Boolean,
  /**
  * @description style of wrap
  */
  wrapStyle: {
    type: definePropType([
      String,
      Object,
      Array,
      Boolean
    ]),
    default: ""
  },
  /**
  * @description class of wrap
  */
  wrapClass: {
    type: [String, Array],
    default: ""
  },
  /**
  * @description class of view
  */
  viewClass: {
    type: [String, Array],
    default: ""
  },
  /**
  * @description style of view
  */
  viewStyle: {
    type: definePropType([
      String,
      Object,
      Array,
      Boolean
    ]),
    default: ""
  },
  /**
  * @description do not respond to container size changes, if the container size does not change, it is better to set it to optimize performance
  */
  noresize: Boolean,
  /**
  * @description element tag of the view
  */
  tag: {
    type: String,
    default: "div"
  },
  /**
  * @description always show
  */
  always: Boolean,
  /**
  * @description minimum size of scrollbar
  */
  minSize: {
    type: Number,
    default: 20
  },
  /**
  * @description Wrap tabindex
  */
  tabindex: {
    type: [String, Number],
    default: void 0
  },
  /**
  * @description id of view
  */
  id: String,
  /**
  * @description role of view
  */
  role: String,
  ...useAriaProps(["ariaLabel", "ariaOrientation"])
});
const scrollbarEmits = {
  "end-reached": (direction) => [
    "left",
    "right",
    "top",
    "bottom"
  ].includes(direction),
  scroll: ({ scrollTop, scrollLeft }) => [scrollTop, scrollLeft].every(isNumber)
};
const tagProps = buildProps({
  /**
  * @description type of Tag
  */
  type: {
    type: String,
    values: [
      "primary",
      "success",
      "info",
      "warning",
      "danger"
    ],
    default: "primary"
  },
  /**
  * @description whether Tag can be removed
  */
  closable: Boolean,
  /**
  * @description whether to disable animations
  */
  disableTransitions: Boolean,
  /**
  * @description whether Tag has a highlighted border
  */
  hit: Boolean,
  /**
  * @description background color of the Tag
  */
  color: String,
  /**
  * @description size of Tag
  */
  size: {
    type: String,
    values: componentSizes
  },
  /**
  * @description theme of Tag
  */
  effect: {
    type: String,
    values: [
      "dark",
      "light",
      "plain"
    ],
    default: "light"
  },
  /**
  * @description whether Tag is rounded
  */
  round: Boolean
});
const tagEmits = {
  close: (evt) => evt instanceof MouseEvent,
  click: (evt) => evt instanceof MouseEvent
};
const defaultProps = {
  label: "label",
  value: "value",
  disabled: "disabled",
  options: "options"
};
function useProps(props) {
  const aliasProps = ref({
    ...defaultProps,
    ...props.props
  });
  let cache = { ...props.props };
  watch(() => props.props, (val) => {
    if (!isEqual(val, cache)) {
      aliasProps.value = {
        ...defaultProps,
        ...val
      };
      cache = { ...val };
    }
  }, { deep: true });
  const getLabel = (option) => get(option, aliasProps.value.label);
  const getValue = (option) => get(option, aliasProps.value.value);
  const getDisabled = (option) => get(option, aliasProps.value.disabled);
  const getOptions = (option) => get(option, aliasProps.value.options);
  return {
    aliasProps,
    getLabel,
    getValue,
    getDisabled,
    getOptions
  };
}
const selectProps = buildProps({
  /**
  * @description the name attribute of select input
  */
  name: String,
  /**
  * @description native input id
  */
  id: String,
  /**
  * @description binding value
  */
  modelValue: {
    type: definePropType([
      Array,
      String,
      Number,
      Boolean,
      Object
    ]),
    default: void 0
  },
  /**
  * @description the autocomplete attribute of select input
  */
  autocomplete: {
    type: String,
    default: "off"
  },
  /**
  * @description for non-filterable Select, this prop decides if the option menu pops up when the input is focused
  */
  automaticDropdown: Boolean,
  /**
  * @description size of Input
  */
  size: useSizeProp,
  /**
  * @description tooltip theme, built-in theme: `dark` / `light`
  */
  effect: {
    type: definePropType(String),
    default: "light"
  },
  /**
  * @description whether Select is disabled
  */
  disabled: {
    type: Boolean,
    default: void 0
  },
  /**
  * @description whether select can be cleared
  */
  clearable: Boolean,
  /**
  * @description whether Select is filterable
  */
  filterable: Boolean,
  /**
  * @description whether creating new items is allowed. To use this, `filterable` must be true
  */
  allowCreate: Boolean,
  /**
  * @description whether Select is loading data from server
  */
  loading: Boolean,
  /**
  * @description custom class name for Select's dropdown
  */
  popperClass: {
    type: String,
    default: ""
  },
  /**
  * @description custom style for Select's dropdown
  */
  popperStyle: { type: definePropType([String, Object]) },
  /**
  * @description [popper.js](https://popper.js.org/docs/v2/) parameters
  */
  popperOptions: {
    type: definePropType(Object),
    default: () => ({})
  },
  /**
  * @description whether options are loaded from server
  */
  remote: Boolean,
  /**
  * @description debounce delay during remote search, in milliseconds
  */
  debounce: {
    type: Number,
    default: 300
  },
  /**
  * @description displayed text while loading data from server, default is 'Loading'
  */
  loadingText: String,
  /**
  * @description displayed text when no data matches the filtering query, you can also use slot `empty`, default is 'No matching data'
  */
  noMatchText: String,
  /**
  * @description displayed text when there is no options, you can also use slot `empty`, default is 'No data'
  */
  noDataText: String,
  /**
  * @description function that gets called when the input value changes. Its parameter is the current input value. To use this, `filterable` must be true
  */
  remoteMethod: { type: definePropType(Function) },
  /**
  * @description custom filter method, the first parameter is the current input value. To use this, `filterable` must be true
  */
  filterMethod: { type: definePropType(Function) },
  /**
  * @description whether multiple-select is activated
  */
  multiple: Boolean,
  /**
  * @description maximum number of options user can select when `multiple` is `true`. No limit when set to 0
  */
  multipleLimit: {
    type: Number,
    default: 0
  },
  /**
  * @description placeholder, default is 'Select'
  */
  placeholder: { type: String },
  /**
  * @description select first matching option on enter key. Use with `filterable` or `remote`
  */
  defaultFirstOption: Boolean,
  /**
  * @description when `multiple` and `filter` is true, whether to reserve current keyword after selecting an option
  */
  reserveKeyword: {
    type: Boolean,
    default: true
  },
  /**
  * @description unique identity key name for value, required when value is an object
  */
  valueKey: {
    type: String,
    default: "value"
  },
  /**
  * @description whether to collapse tags to a text when multiple selecting
  */
  collapseTags: Boolean,
  /**
  * @description whether show all selected tags when mouse hover text of collapse-tags. To use this, `collapse-tags` must be true
  */
  collapseTagsTooltip: Boolean,
  /**
  * @description configuration object for the collapse-tags tooltip. To use this, `collapse-tags` and `collapse-tags-tooltip` must be true
  */
  tagTooltip: {
    type: definePropType(Object),
    default: () => ({})
  },
  /**
  * @description the max tags number to be shown. To use this, `collapse-tags` must be true
  */
  maxCollapseTags: {
    type: Number,
    default: 1
  },
  /**
  * @description whether select dropdown is teleported, if `true` it will be teleported to where `append-to` sets
  */
  teleported: useTooltipContentProps.teleported,
  /**
  * @description when select dropdown is inactive and `persistent` is `false`, select dropdown will be destroyed
  */
  persistent: {
    type: Boolean,
    default: true
  },
  /**
  * @description custom clear icon component
  */
  clearIcon: {
    type: iconPropType,
    default: circle_close_default
  },
  /**
  * @description whether the width of the dropdown is the same as the input
  */
  fitInputWidth: Boolean,
  /**
  * @description custom suffix icon component
  */
  suffixIcon: {
    type: iconPropType,
    default: arrow_down_default
  },
  /**
  * @description tag type
  */
  tagType: {
    ...tagProps.type,
    default: "info"
  },
  /**
  * @description tag effect
  */
  tagEffect: {
    ...tagProps.effect,
    default: "light"
  },
  /**
  * @description whether to trigger form validation
  */
  validateEvent: {
    type: Boolean,
    default: true
  },
  /**
  * @description in remote search method show suffix icon
  */
  remoteShowSuffix: Boolean,
  /**
  * @description determines whether the arrow is displayed
  */
  showArrow: {
    type: Boolean,
    default: true
  },
  /**
  * @description offset of the dropdown
  */
  offset: {
    type: Number,
    default: 12
  },
  /**
  * @description position of dropdown
  */
  placement: {
    type: definePropType(String),
    values: Ee,
    default: "bottom-start"
  },
  /**
  * @description list of possible positions for dropdown
  */
  fallbackPlacements: {
    type: definePropType(Array),
    default: [
      "bottom-start",
      "top-start",
      "right",
      "left"
    ]
  },
  /**
  * @description tabindex for input
  */
  tabindex: {
    type: [String, Number],
    default: 0
  },
  /**
  * @description which element the selection dropdown appends to
  */
  appendTo: useTooltipContentProps.appendTo,
  options: { type: definePropType(Array) },
  props: {
    type: definePropType(Object),
    default: () => defaultProps
  },
  ...useEmptyValuesProps,
  ...useAriaProps(["ariaLabel"])
});
const selectEmits = {
  [UPDATE_MODEL_EVENT]: (val) => true,
  [CHANGE_EVENT]: (val) => true,
  "popup-scroll": scrollbarEmits.scroll,
  "end-reached": scrollbarEmits["end-reached"],
  "remove-tag": (val) => true,
  "visible-change": (visible) => true,
  focus: (evt) => evt instanceof FocusEvent,
  blur: (evt) => evt instanceof FocusEvent,
  clear: () => true
};
var option_group_vue_vue_type_script_lang_default = defineComponent({
  name: "ElOptionGroup",
  componentName: "ElOptionGroup",
  props: {
    /**
    * @description name of the group
    */
    label: String,
    /**
    * @description whether to disable all options in this group
    */
    disabled: Boolean
  },
  setup(props) {
    const ns = useNamespace("select");
    const groupRef = ref();
    const instance = getCurrentInstance();
    const children = ref([]);
    provide(selectGroupKey, reactive({ ...toRefs(props) }));
    const visible = computed(() => children.value.some((option) => option.visible === true));
    const isOption = (node) => {
      var _a;
      return node.type.name === "ElOption" && !!((_a = node.component) == null ? void 0 : _a.proxy);
    };
    const flattedChildren2 = (node) => {
      const nodes = castArray(node);
      const children2 = [];
      nodes.forEach((child) => {
        var _a;
        if (!isVNode(child)) return;
        if (isOption(child)) children2.push(child.component.proxy);
        else if (isArray(child.children) && child.children.length) children2.push(...flattedChildren2(child.children));
        else if ((_a = child.component) == null ? void 0 : _a.subTree) children2.push(...flattedChildren2(child.component.subTree));
      });
      return children2;
    };
    const updateChildren = () => {
      children.value = flattedChildren2(instance.subTree);
    };
    onMounted(() => {
      updateChildren();
    });
    useMutationObserver(groupRef, updateChildren, {
      attributes: true,
      subtree: true,
      childList: true
    });
    return {
      groupRef,
      visible,
      ns
    };
  }
});
function _sfc_render$2(_ctx, _cache, $props, $setup, $data, $options) {
  return withDirectives((openBlock(), createElementBlock("ul", {
    ref: "groupRef",
    class: normalizeClass(_ctx.ns.be("group", "wrap"))
  }, [createElementVNode("li", { class: normalizeClass(_ctx.ns.be("group", "title")) }, toDisplayString(_ctx.label), 3), createElementVNode("li", null, [createElementVNode("ul", { class: normalizeClass(_ctx.ns.b("group")) }, [renderSlot(_ctx.$slots, "default")], 2)])], 2)), [[vShow, _ctx.visible]]);
}
var option_group_default = /* @__PURE__ */ _plugin_vue_export_helper_default(option_group_vue_vue_type_script_lang_default, [["render", _sfc_render$2]]);
function useCalcInputWidth() {
  const calculatorRef = shallowRef();
  const calculatorWidth = ref(0);
  const inputStyle = computed(() => ({ minWidth: `${Math.max(calculatorWidth.value, 11)}px` }));
  const resetCalculatorWidth = () => {
    var _a;
    calculatorWidth.value = ((_a = calculatorRef.value) == null ? void 0 : _a.getBoundingClientRect().width) ?? 0;
  };
  useResizeObserver(calculatorRef, resetCalculatorWidth);
  return {
    calculatorRef,
    calculatorWidth,
    inputStyle
  };
}
const BAR_MAP = {
  vertical: {
    offset: "offsetHeight",
    scroll: "scrollTop",
    scrollSize: "scrollHeight",
    size: "height",
    key: "vertical",
    axis: "Y",
    client: "clientY",
    direction: "top"
  },
  horizontal: {
    offset: "offsetWidth",
    scroll: "scrollLeft",
    scrollSize: "scrollWidth",
    size: "width",
    key: "horizontal",
    axis: "X",
    client: "clientX",
    direction: "left"
  }
};
const renderThumbStyle = ({ move, size, bar }) => ({
  [bar.size]: size,
  transform: `translate${bar.axis}(${move}%)`
});
const thumbProps = buildProps({
  vertical: Boolean,
  size: String,
  move: Number,
  ratio: {
    type: Number,
    required: true
  },
  always: Boolean
});
const scrollbarContextKey = Symbol("scrollbarContextKey");
function isGreaterThan(a, b, epsilon = 0.03) {
  return a - b > epsilon;
}
const barProps = buildProps({
  always: {
    type: Boolean,
    default: true
  },
  minSize: {
    type: Number,
    required: true
  }
});
const COMPONENT_NAME$2 = "Thumb";
var thumb_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  __name: "thumb",
  props: thumbProps,
  setup(__props) {
    const props = __props;
    const scrollbar = inject(scrollbarContextKey);
    const ns = useNamespace("scrollbar");
    if (!scrollbar) throwError(COMPONENT_NAME$2, "can not inject scrollbar context");
    const instance = ref();
    const thumb = ref();
    const thumbState = ref({});
    const visible = ref(false);
    let cursorDown = false;
    let cursorLeave = false;
    let baseScrollHeight = 0;
    let baseScrollWidth = 0;
    let originalOnSelectStart = isClient ? document.onselectstart : null;
    const bar = computed(() => BAR_MAP[props.vertical ? "vertical" : "horizontal"]);
    const thumbStyle = computed(() => renderThumbStyle({
      size: props.size,
      move: props.move,
      bar: bar.value
    }));
    const offsetRatio = computed(() => instance.value[bar.value.offset] ** 2 / scrollbar.wrapElement[bar.value.scrollSize] / props.ratio / thumb.value[bar.value.offset]);
    const clickThumbHandler = (e) => {
      var _a;
      e.stopPropagation();
      if (e.ctrlKey || [1, 2].includes(e.button)) return;
      (_a = window.getSelection()) == null ? void 0 : _a.removeAllRanges();
      startDrag(e);
      const el = e.currentTarget;
      if (!el) return;
      thumbState.value[bar.value.axis] = el[bar.value.offset] - (e[bar.value.client] - el.getBoundingClientRect()[bar.value.direction]);
    };
    const clickTrackHandler = (e) => {
      if (!thumb.value || !instance.value || !scrollbar.wrapElement) return;
      const thumbPositionPercentage = (Math.abs(e.target.getBoundingClientRect()[bar.value.direction] - e[bar.value.client]) - thumb.value[bar.value.offset] / 2) * 100 * offsetRatio.value / instance.value[bar.value.offset];
      scrollbar.wrapElement[bar.value.scroll] = thumbPositionPercentage * scrollbar.wrapElement[bar.value.scrollSize] / 100;
    };
    const startDrag = (e) => {
      e.stopImmediatePropagation();
      cursorDown = true;
      baseScrollHeight = scrollbar.wrapElement.scrollHeight;
      baseScrollWidth = scrollbar.wrapElement.scrollWidth;
      document.addEventListener("mousemove", mouseMoveDocumentHandler);
      document.addEventListener("mouseup", mouseUpDocumentHandler);
      originalOnSelectStart = document.onselectstart;
      document.onselectstart = () => false;
    };
    const mouseMoveDocumentHandler = (e) => {
      if (!instance.value || !thumb.value) return;
      if (cursorDown === false) return;
      const prevPage = thumbState.value[bar.value.axis];
      if (!prevPage) return;
      const thumbPositionPercentage = ((instance.value.getBoundingClientRect()[bar.value.direction] - e[bar.value.client]) * -1 - (thumb.value[bar.value.offset] - prevPage)) * 100 * offsetRatio.value / instance.value[bar.value.offset];
      if (bar.value.scroll === "scrollLeft") scrollbar.wrapElement[bar.value.scroll] = thumbPositionPercentage * baseScrollWidth / 100;
      else scrollbar.wrapElement[bar.value.scroll] = thumbPositionPercentage * baseScrollHeight / 100;
    };
    const mouseUpDocumentHandler = () => {
      cursorDown = false;
      thumbState.value[bar.value.axis] = 0;
      document.removeEventListener("mousemove", mouseMoveDocumentHandler);
      document.removeEventListener("mouseup", mouseUpDocumentHandler);
      restoreOnselectstart();
      if (cursorLeave) visible.value = false;
    };
    const mouseMoveScrollbarHandler = () => {
      cursorLeave = false;
      visible.value = !!props.size;
    };
    const mouseLeaveScrollbarHandler = () => {
      cursorLeave = true;
      visible.value = cursorDown;
    };
    onBeforeUnmount(() => {
      restoreOnselectstart();
      document.removeEventListener("mouseup", mouseUpDocumentHandler);
    });
    const restoreOnselectstart = () => {
      if (document.onselectstart !== originalOnSelectStart) document.onselectstart = originalOnSelectStart;
    };
    useEventListener(toRef(scrollbar, "scrollbarElement"), "mousemove", mouseMoveScrollbarHandler);
    useEventListener(toRef(scrollbar, "scrollbarElement"), "mouseleave", mouseLeaveScrollbarHandler);
    return (_ctx, _cache) => {
      return openBlock(), createBlock(Transition, {
        name: unref(ns).b("fade"),
        persisted: ""
      }, {
        default: withCtx(() => [withDirectives(createElementVNode("div", {
          ref_key: "instance",
          ref: instance,
          class: normalizeClass([unref(ns).e("bar"), unref(ns).is(bar.value.key)]),
          onMousedown: clickTrackHandler,
          onClick: _cache[0] || (_cache[0] = withModifiers(() => {
          }, ["stop"]))
        }, [createElementVNode("div", {
          ref_key: "thumb",
          ref: thumb,
          class: normalizeClass(unref(ns).e("thumb")),
          style: normalizeStyle(thumbStyle.value),
          onMousedown: clickThumbHandler
        }, null, 38)], 34), [[vShow, __props.always || visible.value]])]),
        _: 1
      }, 8, ["name"]);
    };
  }
});
var thumb_default = thumb_vue_vue_type_script_setup_true_lang_default;
var bar_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  __name: "bar",
  props: barProps,
  setup(__props, { expose: __expose }) {
    const props = __props;
    const scrollbar = inject(scrollbarContextKey);
    const moveX = ref(0);
    const moveY = ref(0);
    const sizeWidth = ref("");
    const sizeHeight = ref("");
    const ratioY = ref(1);
    const ratioX = ref(1);
    const handleScroll = (wrap) => {
      if (wrap) {
        const offsetHeight = wrap.offsetHeight - 4;
        const offsetWidth = wrap.offsetWidth - 4;
        moveY.value = wrap.scrollTop * 100 / offsetHeight * ratioY.value;
        moveX.value = wrap.scrollLeft * 100 / offsetWidth * ratioX.value;
      }
    };
    const update = () => {
      const wrap = scrollbar == null ? void 0 : scrollbar.wrapElement;
      if (!wrap) return;
      const offsetHeight = wrap.offsetHeight - 4;
      const offsetWidth = wrap.offsetWidth - 4;
      const originalHeight = offsetHeight ** 2 / wrap.scrollHeight;
      const originalWidth = offsetWidth ** 2 / wrap.scrollWidth;
      const height = Math.max(originalHeight, props.minSize);
      const width = Math.max(originalWidth, props.minSize);
      ratioY.value = originalHeight / (offsetHeight - originalHeight) / (height / (offsetHeight - height));
      ratioX.value = originalWidth / (offsetWidth - originalWidth) / (width / (offsetWidth - width));
      sizeHeight.value = height + 4 < offsetHeight ? `${height}px` : "";
      sizeWidth.value = width + 4 < offsetWidth ? `${width}px` : "";
    };
    __expose({
      handleScroll,
      update
    });
    return (_ctx, _cache) => {
      return openBlock(), createElementBlock(Fragment, null, [createVNode(thumb_default, {
        move: moveX.value,
        ratio: ratioX.value,
        size: sizeWidth.value,
        always: __props.always
      }, null, 8, [
        "move",
        "ratio",
        "size",
        "always"
      ]), createVNode(thumb_default, {
        move: moveY.value,
        ratio: ratioY.value,
        size: sizeHeight.value,
        vertical: "",
        always: __props.always
      }, null, 8, [
        "move",
        "ratio",
        "size",
        "always"
      ])], 64);
    };
  }
});
var bar_default = bar_vue_vue_type_script_setup_true_lang_default;
const _hoisted_1$2 = ["tabindex"];
const COMPONENT_NAME$1 = "ElScrollbar";
var scrollbar_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: COMPONENT_NAME$1,
  __name: "scrollbar",
  props: scrollbarProps,
  emits: scrollbarEmits,
  setup(__props, { expose: __expose, emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const ns = useNamespace("scrollbar");
    let stopResizeObserver = void 0;
    let stopWrapResizeObserver = void 0;
    let stopResizeListener = void 0;
    let wrapScrollTop = 0;
    let wrapScrollLeft = 0;
    let direction = "";
    const distanceScrollState = {
      bottom: false,
      top: false,
      right: false,
      left: false
    };
    const scrollbarRef = ref();
    const wrapRef = ref();
    const resizeRef = ref();
    const barRef = ref();
    const wrapStyle = computed(() => {
      const style = {};
      const height = addUnit(props.height);
      const maxHeight = addUnit(props.maxHeight);
      if (height) style.height = height;
      if (maxHeight) style.maxHeight = maxHeight;
      return [props.wrapStyle, style];
    });
    const wrapKls = computed(() => {
      return [
        props.wrapClass,
        ns.e("wrap"),
        { [ns.em("wrap", "hidden-default")]: !props.native }
      ];
    });
    const resizeKls = computed(() => {
      return [ns.e("view"), props.viewClass];
    });
    const shouldSkipDirection = (direction2) => {
      return distanceScrollState[direction2] ?? false;
    };
    const DIRECTION_PAIRS = {
      top: "bottom",
      bottom: "top",
      left: "right",
      right: "left"
    };
    const updateTriggerStatus = (arrivedStates) => {
      const oppositeDirection = DIRECTION_PAIRS[direction];
      if (!oppositeDirection) return;
      const arrived = arrivedStates[direction];
      const oppositeArrived = arrivedStates[oppositeDirection];
      if (arrived && !distanceScrollState[direction]) distanceScrollState[direction] = true;
      if (!oppositeArrived && distanceScrollState[oppositeDirection]) distanceScrollState[oppositeDirection] = false;
    };
    const handleScroll = () => {
      var _a;
      if (wrapRef.value) {
        (_a = barRef.value) == null ? void 0 : _a.handleScroll(wrapRef.value);
        const prevTop = wrapScrollTop;
        const prevLeft = wrapScrollLeft;
        wrapScrollTop = wrapRef.value.scrollTop;
        wrapScrollLeft = wrapRef.value.scrollLeft;
        const arrivedStates = {
          bottom: !isGreaterThan(wrapRef.value.scrollHeight - props.distance, wrapRef.value.clientHeight + wrapScrollTop),
          top: wrapScrollTop <= props.distance && prevTop !== 0,
          right: !isGreaterThan(wrapRef.value.scrollWidth - props.distance, wrapRef.value.clientWidth + wrapScrollLeft) && prevLeft !== wrapScrollLeft,
          left: wrapScrollLeft <= props.distance && prevLeft !== 0
        };
        emit("scroll", {
          scrollTop: wrapScrollTop,
          scrollLeft: wrapScrollLeft
        });
        if (prevTop !== wrapScrollTop) direction = wrapScrollTop > prevTop ? "bottom" : "top";
        if (prevLeft !== wrapScrollLeft) direction = wrapScrollLeft > prevLeft ? "right" : "left";
        if (props.distance > 0) {
          if (shouldSkipDirection(direction)) return;
          updateTriggerStatus(arrivedStates);
        }
        if (arrivedStates[direction]) emit("end-reached", direction);
      }
    };
    function scrollTo(arg1, arg2) {
      if (isObject(arg1)) wrapRef.value.scrollTo(arg1);
      else if (isNumber(arg1) && isNumber(arg2)) wrapRef.value.scrollTo(arg1, arg2);
    }
    const setScrollTop = (value) => {
      if (!isNumber(value)) {
        debugWarn(COMPONENT_NAME$1, "value must be a number");
        return;
      }
      wrapRef.value.scrollTop = value;
    };
    const setScrollLeft = (value) => {
      if (!isNumber(value)) {
        debugWarn(COMPONENT_NAME$1, "value must be a number");
        return;
      }
      wrapRef.value.scrollLeft = value;
    };
    const update = () => {
      var _a, _b;
      (_a = barRef.value) == null ? void 0 : _a.update();
      distanceScrollState[direction] = false;
      if (wrapRef.value) (_b = barRef.value) == null ? void 0 : _b.handleScroll(wrapRef.value);
    };
    watch(() => props.noresize, (noresize) => {
      if (noresize) {
        stopResizeObserver == null ? void 0 : stopResizeObserver();
        stopWrapResizeObserver == null ? void 0 : stopWrapResizeObserver();
        stopResizeListener == null ? void 0 : stopResizeListener();
      } else {
        ({ stop: stopResizeObserver } = useResizeObserver(resizeRef, update));
        ({ stop: stopWrapResizeObserver } = useResizeObserver(wrapRef, update));
        stopResizeListener = useEventListener("resize", update);
      }
    }, { immediate: true });
    watch(() => [props.maxHeight, props.height], () => {
      if (!props.native) nextTick(() => {
        update();
      });
    });
    provide(scrollbarContextKey, reactive({
      scrollbarElement: scrollbarRef,
      wrapElement: wrapRef
    }));
    onActivated(() => {
      if (wrapRef.value) {
        wrapRef.value.scrollTop = wrapScrollTop;
        wrapRef.value.scrollLeft = wrapScrollLeft;
      }
    });
    onMounted(() => {
      if (!props.native) nextTick(() => {
        update();
      });
    });
    onUpdated(() => update());
    __expose({
      /** @description scrollbar wrap ref */
      wrapRef,
      /** @description update scrollbar state manually */
      update,
      /** @description scrolls to a particular set of coordinates */
      scrollTo,
      /** @description set distance to scroll top */
      setScrollTop,
      /** @description set distance to scroll left */
      setScrollLeft,
      /** @description handle scroll event */
      handleScroll
    });
    return (_ctx, _cache) => {
      return openBlock(), createElementBlock("div", {
        ref_key: "scrollbarRef",
        ref: scrollbarRef,
        class: normalizeClass(unref(ns).b())
      }, [createElementVNode("div", {
        ref_key: "wrapRef",
        ref: wrapRef,
        class: normalizeClass(wrapKls.value),
        style: normalizeStyle(wrapStyle.value),
        tabindex: __props.tabindex,
        onScroll: handleScroll
      }, [(openBlock(), createBlock(resolveDynamicComponent(__props.tag), {
        id: __props.id,
        ref_key: "resizeRef",
        ref: resizeRef,
        class: normalizeClass(resizeKls.value),
        style: normalizeStyle(__props.viewStyle),
        role: __props.role,
        "aria-label": __props.ariaLabel,
        "aria-orientation": __props.ariaOrientation
      }, {
        default: withCtx(() => [renderSlot(_ctx.$slots, "default")]),
        _: 3
      }, 8, [
        "id",
        "class",
        "style",
        "role",
        "aria-label",
        "aria-orientation"
      ]))], 46, _hoisted_1$2), !__props.native ? (openBlock(), createBlock(bar_default, {
        key: 0,
        ref_key: "barRef",
        ref: barRef,
        always: __props.always,
        "min-size": __props.minSize
      }, null, 8, ["always", "min-size"])) : createCommentVNode("v-if", true)], 2);
    };
  }
});
var scrollbar_default = scrollbar_vue_vue_type_script_setup_true_lang_default;
const ElScrollbar = withInstall(scrollbar_default);
const nodeList = /* @__PURE__ */ new Map();
if (isClient) {
  let startClick;
  document.addEventListener("mousedown", (e) => startClick = e);
  document.addEventListener("mouseup", (e) => {
    if (startClick) {
      for (const handlers of nodeList.values()) for (const { documentHandler } of handlers) documentHandler(e, startClick);
      startClick = void 0;
    }
  });
}
function createDocumentHandler(el, binding) {
  let excludes = [];
  if (isArray(binding.arg)) excludes = binding.arg;
  else if (isElement(binding.arg)) excludes.push(binding.arg);
  return function(mouseup, mousedown) {
    const popperRef = binding.instance.popperRef;
    const mouseUpTarget = mouseup.target;
    const mouseDownTarget = mousedown == null ? void 0 : mousedown.target;
    const isBound = !binding || !binding.instance;
    const isTargetExists = !mouseUpTarget || !mouseDownTarget;
    const isContainedByEl = el.contains(mouseUpTarget) || el.contains(mouseDownTarget);
    const isSelf = el === mouseUpTarget;
    const isTargetExcluded = excludes.length && excludes.some((item) => item == null ? void 0 : item.contains(mouseUpTarget)) || excludes.length && excludes.includes(mouseDownTarget);
    const isContainedByPopper = popperRef && (popperRef.contains(mouseUpTarget) || popperRef.contains(mouseDownTarget));
    if (isBound || isTargetExists || isContainedByEl || isSelf || isTargetExcluded || isContainedByPopper) return;
    binding.value(mouseup, mousedown);
  };
}
const ClickOutside = {
  beforeMount(el, binding) {
    if (!nodeList.has(el)) nodeList.set(el, []);
    nodeList.get(el).push({
      documentHandler: createDocumentHandler(el, binding),
      bindingFn: binding.value
    });
  },
  updated(el, binding) {
    if (!nodeList.has(el)) nodeList.set(el, []);
    const handlers = nodeList.get(el);
    const oldHandlerIndex = handlers.findIndex((item) => item.bindingFn === binding.oldValue);
    const newHandler = {
      documentHandler: createDocumentHandler(el, binding),
      bindingFn: binding.value
    };
    if (oldHandlerIndex >= 0) handlers.splice(oldHandlerIndex, 1, newHandler);
    else handlers.push(newHandler);
  },
  unmounted(el) {
    nodeList.delete(el);
  }
};
const _hoisted_1$1 = ["aria-label"];
const _hoisted_2$1 = ["aria-label"];
var tag_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: "ElTag",
  __name: "tag",
  props: tagProps,
  emits: tagEmits,
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const tagSize = useFormSize();
    const { t } = useLocale();
    const ns = useNamespace("tag");
    const containerKls = computed(() => {
      const { type, hit, effect, closable, round } = props;
      return [
        ns.b(),
        ns.is("closable", closable),
        ns.m(type || "primary"),
        ns.m(tagSize.value),
        ns.m(effect),
        ns.is("hit", hit),
        ns.is("round", round)
      ];
    });
    const handleClose = (event) => {
      emit("close", event);
    };
    const handleClick = (event) => {
      emit("click", event);
    };
    const handleVNodeMounted = (vnode) => {
      var _a, _b, _c;
      if ((_c = (_b = (_a = vnode == null ? void 0 : vnode.component) == null ? void 0 : _a.subTree) == null ? void 0 : _b.component) == null ? void 0 : _c.bum) vnode.component.subTree.component.bum = null;
    };
    return (_ctx, _cache) => {
      return __props.disableTransitions ? (openBlock(), createElementBlock("span", {
        key: 0,
        class: normalizeClass(containerKls.value),
        style: normalizeStyle({ backgroundColor: __props.color }),
        onClick: handleClick
      }, [createElementVNode("span", { class: normalizeClass(unref(ns).e("content")) }, [renderSlot(_ctx.$slots, "default")], 2), __props.closable ? (openBlock(), createElementBlock("button", {
        key: 0,
        "aria-label": unref(t)("el.tag.close"),
        class: normalizeClass(unref(ns).e("close")),
        type: "button",
        onClick: withModifiers(handleClose, ["stop"])
      }, [createVNode(unref(ElIcon), null, {
        default: withCtx(() => [createVNode(unref(close_default))]),
        _: 1
      })], 10, _hoisted_1$1)) : createCommentVNode("v-if", true)], 6)) : (openBlock(), createBlock(Transition, {
        key: 1,
        name: `${unref(ns).namespace.value}-zoom-in-center`,
        appear: "",
        onVnodeMounted: handleVNodeMounted
      }, {
        default: withCtx(() => [createElementVNode("span", {
          class: normalizeClass(containerKls.value),
          style: normalizeStyle({ backgroundColor: __props.color }),
          onClick: handleClick
        }, [createElementVNode("span", { class: normalizeClass(unref(ns).e("content")) }, [renderSlot(_ctx.$slots, "default")], 2), __props.closable ? (openBlock(), createElementBlock("button", {
          key: 0,
          "aria-label": unref(t)("el.tag.close"),
          class: normalizeClass(unref(ns).e("close")),
          type: "button",
          onClick: withModifiers(handleClose, ["stop"])
        }, [createVNode(unref(ElIcon), null, {
          default: withCtx(() => [createVNode(unref(close_default))]),
          _: 1
        })], 10, _hoisted_2$1)) : createCommentVNode("v-if", true)], 6)]),
        _: 3
      }, 8, ["name"]));
    };
  }
});
var tag_default = tag_vue_vue_type_script_setup_true_lang_default;
const ElTag = withInstall(tag_default);
var select_dropdown_vue_vue_type_script_lang_default = defineComponent({
  name: "ElSelectDropdown",
  componentName: "ElSelectDropdown",
  setup() {
    const select = inject(selectKey);
    const ns = useNamespace("select");
    const popperClass = computed(() => select.props.popperClass);
    const isMultiple = computed(() => select.props.multiple);
    const isFitInputWidth = computed(() => select.props.fitInputWidth);
    const minWidth = ref("");
    function updateMinWidth() {
      var _a;
      const offsetWidth = (_a = select.selectRef) == null ? void 0 : _a.offsetWidth;
      if (offsetWidth) minWidth.value = `${offsetWidth - 2}px`;
      else minWidth.value = "";
    }
    onMounted(() => {
      updateMinWidth();
      useResizeObserver(select.selectRef, updateMinWidth);
    });
    return {
      ns,
      minWidth,
      popperClass,
      isMultiple,
      isFitInputWidth
    };
  }
});
function _sfc_render$1(_ctx, _cache, $props, $setup, $data, $options) {
  return openBlock(), createElementBlock("div", {
    class: normalizeClass([
      _ctx.ns.b("dropdown"),
      _ctx.ns.is("multiple", _ctx.isMultiple),
      _ctx.popperClass
    ]),
    style: normalizeStyle({ [_ctx.isFitInputWidth ? "width" : "minWidth"]: _ctx.minWidth })
  }, [
    _ctx.$slots.header ? (openBlock(), createElementBlock("div", {
      key: 0,
      class: normalizeClass(_ctx.ns.be("dropdown", "header"))
    }, [renderSlot(_ctx.$slots, "header")], 2)) : createCommentVNode("v-if", true),
    renderSlot(_ctx.$slots, "default"),
    _ctx.$slots.footer ? (openBlock(), createElementBlock("div", {
      key: 1,
      class: normalizeClass(_ctx.ns.be("dropdown", "footer"))
    }, [renderSlot(_ctx.$slots, "footer")], 2)) : createCommentVNode("v-if", true)
  ], 6);
}
var select_dropdown_default = /* @__PURE__ */ _plugin_vue_export_helper_default(select_dropdown_vue_vue_type_script_lang_default, [["render", _sfc_render$1]]);
const useSelect = (props, emit) => {
  const { t } = useLocale();
  const slots = useSlots();
  const contentId = useId();
  const nsSelect = useNamespace("select");
  const nsInput = useNamespace("input");
  const states = reactive({
    inputValue: "",
    options: /* @__PURE__ */ new Map(),
    cachedOptions: /* @__PURE__ */ new Map(),
    optionValues: [],
    selected: [],
    selectionWidth: 0,
    collapseItemWidth: 0,
    selectedLabel: "",
    hoveringIndex: -1,
    previousQuery: null,
    inputHovering: false,
    menuVisibleOnFocus: false,
    isBeforeHide: false
  });
  const selectRef = ref();
  const selectionRef = ref();
  const tooltipRef = ref();
  const tagTooltipRef = ref();
  const inputRef = ref();
  const prefixRef = ref();
  const suffixRef = ref();
  const menuRef = ref();
  const tagMenuRef = ref();
  const collapseItemRef = ref();
  const scrollbarRef = ref();
  const expanded = ref(false);
  const hoverOption = ref();
  const debouncing = ref(false);
  const { form, formItem } = useFormItem();
  const { inputId } = useFormItemInputId(props, { formItemContext: formItem });
  const { valueOnClear, isEmptyValue } = useEmptyValues(props);
  const { isComposing, handleCompositionStart, handleCompositionUpdate, handleCompositionEnd } = useComposition({ afterComposition: (e) => onInput(e) });
  const selectDisabled = useFormDisabled();
  const { wrapperRef, isFocused, handleBlur } = useFocusController(inputRef, {
    disabled: selectDisabled,
    afterFocus() {
      if (props.automaticDropdown && !expanded.value) {
        expanded.value = true;
        states.menuVisibleOnFocus = true;
      }
    },
    beforeBlur(event) {
      var _a, _b;
      return ((_a = tooltipRef.value) == null ? void 0 : _a.isFocusInsideContent(event)) || ((_b = tagTooltipRef.value) == null ? void 0 : _b.isFocusInsideContent(event));
    },
    afterBlur() {
      var _a;
      expanded.value = false;
      states.menuVisibleOnFocus = false;
      if (props.validateEvent) (_a = formItem == null ? void 0 : formItem.validate) == null ? void 0 : _a.call(formItem, "blur").catch(NOOP);
    }
  });
  const hasModelValue = computed(() => {
    return isArray(props.modelValue) ? props.modelValue.length > 0 : !isEmptyValue(props.modelValue);
  });
  const needStatusIcon = computed(() => (form == null ? void 0 : form.statusIcon) ?? false);
  const showClearBtn = computed(() => {
    return props.clearable && !selectDisabled.value && hasModelValue.value && (isFocused.value || states.inputHovering);
  });
  const iconComponent = computed(() => props.remote && props.filterable && !props.remoteShowSuffix ? "" : props.suffixIcon);
  const iconReverse = computed(() => nsSelect.is("reverse", !!(iconComponent.value && expanded.value)));
  const validateState = computed(() => (formItem == null ? void 0 : formItem.validateState) || "");
  const validateIcon = computed(() => validateState.value && ValidateComponentsMap[validateState.value]);
  const debounce = computed(() => props.remote ? props.debounce : 0);
  const isRemoteSearchEmpty = computed(() => props.remote && !states.inputValue && states.options.size === 0);
  const emptyText = computed(() => {
    if (props.loading) return props.loadingText || t("el.select.loading");
    else {
      if (props.filterable && states.inputValue && states.options.size > 0 && filteredOptionsCount.value === 0) return props.noMatchText || t("el.select.noMatch");
      if (states.options.size === 0) return props.noDataText || t("el.select.noData");
    }
    return null;
  });
  const filteredOptionsCount = computed(() => optionsArray.value.filter((option) => option.visible).length);
  const optionsArray = computed(() => {
    const list = Array.from(states.options.values());
    const newList = [];
    states.optionValues.forEach((item) => {
      const index = list.findIndex((i) => i.value === item);
      if (index > -1) newList.push(list[index]);
    });
    return newList.length >= list.length ? newList : list;
  });
  const cachedOptionsArray = computed(() => Array.from(states.cachedOptions.values()));
  const showNewOption = computed(() => {
    const hasExistingOption = optionsArray.value.filter((option) => {
      return !option.created;
    }).some((option) => {
      return option.currentLabel === states.inputValue;
    });
    return props.filterable && props.allowCreate && states.inputValue !== "" && !hasExistingOption;
  });
  const updateOptions = () => {
    if (props.filterable && isFunction(props.filterMethod)) return;
    if (props.filterable && props.remote && isFunction(props.remoteMethod)) return;
    optionsArray.value.forEach((option) => {
      var _a;
      (_a = option.updateOption) == null ? void 0 : _a.call(option, states.inputValue);
    });
  };
  const selectSize = useFormSize();
  const collapseTagSize = computed(() => ["small"].includes(selectSize.value) ? "small" : "default");
  const dropdownMenuVisible = computed({
    get() {
      return expanded.value && (props.loading || !isRemoteSearchEmpty.value || props.remote && !!slots.empty) && (!debouncing.value || !isEmpty(states.previousQuery) || states.options.size > 0);
    },
    set(val) {
      expanded.value = val;
    }
  });
  const shouldShowPlaceholder = computed(() => {
    if (props.multiple && !isUndefined(props.modelValue)) return castArray(props.modelValue).length === 0 && !states.inputValue;
    const value = isArray(props.modelValue) ? props.modelValue[0] : props.modelValue;
    return props.filterable || isUndefined(value) ? !states.inputValue : true;
  });
  const currentPlaceholder = computed(() => {
    const _placeholder = props.placeholder ?? t("el.select.placeholder");
    return props.multiple || !hasModelValue.value ? _placeholder : states.selectedLabel;
  });
  const mouseEnterEventName = isIOS ? null : "mouseenter";
  watch(() => props.modelValue, (val, oldVal) => {
    if (props.multiple) {
      if (props.filterable && !props.reserveKeyword) {
        states.inputValue = "";
        handleQueryChange("");
      }
    }
    setSelected();
    if (!isEqual(val, oldVal) && props.validateEvent) formItem == null ? void 0 : formItem.validate("change").catch(NOOP);
  }, {
    flush: "post",
    deep: true
  });
  watch(() => expanded.value, (val) => {
    if (val) handleQueryChange(states.inputValue);
    else {
      states.inputValue = "";
      states.previousQuery = null;
      states.isBeforeHide = true;
      states.menuVisibleOnFocus = false;
    }
  });
  watch(() => states.options.entries(), () => {
    if (!isClient) return;
    setSelected();
    if (props.defaultFirstOption && (props.filterable || props.remote) && filteredOptionsCount.value) checkDefaultFirstOption();
  }, { flush: "post" });
  watch([() => states.hoveringIndex, optionsArray], ([val]) => {
    if (isNumber(val) && val > -1) hoverOption.value = optionsArray.value[val] || {};
    else hoverOption.value = {};
    optionsArray.value.forEach((option) => {
      option.hover = hoverOption.value === option;
    });
  });
  watchEffect(() => {
    if (states.isBeforeHide) return;
    updateOptions();
  });
  const handleQueryChange = (val) => {
    if (states.previousQuery === val || isComposing.value) return;
    states.previousQuery = val;
    if (props.filterable && isFunction(props.filterMethod)) props.filterMethod(val);
    else if (props.filterable && props.remote && isFunction(props.remoteMethod)) props.remoteMethod(val);
    if (props.defaultFirstOption && (props.filterable || props.remote) && filteredOptionsCount.value) nextTick(checkDefaultFirstOption);
    else nextTick(updateHoveringIndex);
  };
  const checkDefaultFirstOption = () => {
    const optionsInDropdown = optionsArray.value.filter((n) => n.visible && !n.disabled && !n.states.groupDisabled);
    const userCreatedOption = optionsInDropdown.find((n) => n.created);
    const firstOriginOption = optionsInDropdown[0];
    states.hoveringIndex = getValueIndex(optionsArray.value.map((item) => item.value), userCreatedOption || firstOriginOption);
  };
  const setSelected = () => {
    if (!props.multiple) {
      const option = getOption(isArray(props.modelValue) ? props.modelValue[0] : props.modelValue);
      states.selectedLabel = option.currentLabel;
      states.selected = [option];
      return;
    } else states.selectedLabel = "";
    const result = [];
    if (!isUndefined(props.modelValue)) castArray(props.modelValue).forEach((value) => {
      result.push(getOption(value));
    });
    states.selected = result;
  };
  const getOption = (value) => {
    let option;
    const isObjectValue = isPlainObject(value);
    for (let i = states.cachedOptions.size - 1; i >= 0; i--) {
      const cachedOption = cachedOptionsArray.value[i];
      if (isObjectValue ? get(cachedOption.value, props.valueKey) === get(value, props.valueKey) : cachedOption.value === value) {
        option = {
          index: optionsArray.value.filter((opt) => !opt.created).indexOf(cachedOption),
          value,
          currentLabel: cachedOption.currentLabel,
          get isDisabled() {
            return cachedOption.isDisabled;
          }
        };
        break;
      }
    }
    if (option) return option;
    const existingSelected = states.selected.find((item) => isObjectValue ? get(item.value, props.valueKey) === get(value, props.valueKey) : item.value === value);
    return {
      index: -1,
      value,
      currentLabel: isObjectValue ? value.label : existingSelected ? existingSelected.currentLabel : value ?? ""
    };
  };
  const updateHoveringIndex = () => {
    const length = states.selected.length;
    if (length > 0) {
      const lastOption = states.selected[length - 1];
      states.hoveringIndex = optionsArray.value.findIndex((item) => getValueKey(lastOption) === getValueKey(item));
    } else states.hoveringIndex = -1;
  };
  const resetSelectionWidth = () => {
    states.selectionWidth = Number.parseFloat(window.getComputedStyle(selectionRef.value).width);
  };
  const resetCollapseItemWidth = () => {
    states.collapseItemWidth = collapseItemRef.value.getBoundingClientRect().width;
  };
  const updateTooltip = () => {
    var _a, _b;
    (_b = (_a = tooltipRef.value) == null ? void 0 : _a.updatePopper) == null ? void 0 : _b.call(_a);
  };
  const updateTagTooltip = () => {
    var _a, _b;
    (_b = (_a = tagTooltipRef.value) == null ? void 0 : _a.updatePopper) == null ? void 0 : _b.call(_a);
  };
  const onInputChange = () => {
    if (states.inputValue.length > 0 && !expanded.value) expanded.value = true;
    handleQueryChange(states.inputValue);
  };
  const onInput = (event) => {
    states.inputValue = event.target.value;
    if (props.remote) {
      debouncing.value = true;
      debouncedOnInputChange();
    } else return onInputChange();
  };
  const debouncedOnInputChange = useDebounceFn(() => {
    onInputChange();
    debouncing.value = false;
  }, debounce);
  const emitChange = (val) => {
    if (!isEqual(props.modelValue, val)) emit(CHANGE_EVENT, val);
  };
  const getLastNotDisabledIndex = (value) => findLastIndex(value, (it) => {
    const option = states.cachedOptions.get(it);
    return !(option == null ? void 0 : option.disabled) && !(option == null ? void 0 : option.states.groupDisabled);
  });
  const deletePrevTag = (e) => {
    const code = getEventCode(e);
    if (!props.multiple) return;
    if (code === EVENT_CODE.delete) return;
    if (e.target.value.length <= 0) {
      const value = castArray(props.modelValue).slice();
      const lastNotDisabledIndex = getLastNotDisabledIndex(value);
      if (lastNotDisabledIndex < 0) return;
      const removeTagValue = value[lastNotDisabledIndex];
      value.splice(lastNotDisabledIndex, 1);
      emit(UPDATE_MODEL_EVENT, value);
      emitChange(value);
      emit("remove-tag", removeTagValue);
    }
  };
  const deleteTag = (event, tag) => {
    const index = states.selected.indexOf(tag);
    if (index > -1 && !selectDisabled.value) {
      const value = castArray(props.modelValue).slice();
      value.splice(index, 1);
      emit(UPDATE_MODEL_EVENT, value);
      emitChange(value);
      emit("remove-tag", tag.value);
    }
    event.stopPropagation();
    focus();
  };
  const deleteSelected = (event) => {
    event.stopPropagation();
    const value = props.multiple ? [] : valueOnClear.value;
    if (props.multiple) {
      for (const item of states.selected) if (item.isDisabled) value.push(item.value);
    }
    emit(UPDATE_MODEL_EVENT, value);
    emitChange(value);
    states.hoveringIndex = -1;
    expanded.value = false;
    emit("clear");
    focus();
  };
  const handleOptionSelect = (option) => {
    if (props.multiple) {
      const value = castArray(props.modelValue ?? []).slice();
      const optionIndex = getValueIndex(value, option);
      if (optionIndex > -1) value.splice(optionIndex, 1);
      else if (props.multipleLimit <= 0 || value.length < props.multipleLimit) value.push(option.value);
      emit(UPDATE_MODEL_EVENT, value);
      emitChange(value);
      if (option.created) handleQueryChange("");
      if (props.filterable && (option.created || !props.reserveKeyword)) states.inputValue = "";
    } else {
      !isEqual(props.modelValue, option.value) && emit("update:modelValue", option.value);
      emitChange(option.value);
      expanded.value = false;
    }
    focus();
    if (expanded.value) return;
    nextTick(() => {
      scrollToOption(option);
    });
  };
  const getValueIndex = (arr, option) => {
    if (isUndefined(option)) return -1;
    if (!isObject(option.value)) return arr.indexOf(option.value);
    return arr.findIndex((item) => {
      return isEqual(get(item, props.valueKey), getValueKey(option));
    });
  };
  const scrollToOption = (option) => {
    var _a, _b, _c, _d, _e;
    const targetOption = isArray(option) ? option[option.length - 1] : option;
    let target = null;
    if (!isNil(targetOption == null ? void 0 : targetOption.value)) {
      const options = optionsArray.value.filter((item) => item.value === targetOption.value);
      if (options.length > 0) target = options[0].$el;
    }
    if (tooltipRef.value && target) {
      const menu = (_d = (_c = (_b = (_a = tooltipRef.value) == null ? void 0 : _a.popperRef) == null ? void 0 : _b.contentRef) == null ? void 0 : _c.querySelector) == null ? void 0 : _d.call(_c, `.${nsSelect.be("dropdown", "wrap")}`);
      if (menu) scrollIntoView(menu, target);
    }
    (_e = scrollbarRef.value) == null ? void 0 : _e.handleScroll();
  };
  const onOptionCreate = (vm) => {
    states.options.set(vm.value, vm);
    states.cachedOptions.set(vm.value, vm);
  };
  const onOptionDestroy = (key, vm) => {
    if (states.options.get(key) === vm) states.options.delete(key);
  };
  const popperRef = computed(() => {
    var _a, _b;
    return (_b = (_a = tooltipRef.value) == null ? void 0 : _a.popperRef) == null ? void 0 : _b.contentRef;
  });
  const handleMenuEnter = () => {
    states.isBeforeHide = false;
    nextTick(() => {
      var _a;
      (_a = scrollbarRef.value) == null ? void 0 : _a.update();
      scrollToOption(states.selected);
    });
  };
  const focus = () => {
    var _a;
    (_a = inputRef.value) == null ? void 0 : _a.focus();
  };
  const blur = () => {
    var _a;
    if (expanded.value) {
      expanded.value = false;
      nextTick(() => {
        var _a2;
        return (_a2 = inputRef.value) == null ? void 0 : _a2.blur();
      });
      return;
    }
    (_a = inputRef.value) == null ? void 0 : _a.blur();
  };
  const handleClearClick = (event) => {
    deleteSelected(event);
  };
  const handleClickOutside = (event) => {
    expanded.value = false;
    if (isFocused.value) {
      const _event = new FocusEvent("blur", event);
      nextTick(() => handleBlur(_event));
    }
  };
  const handleEsc = () => {
    if (states.inputValue.length > 0) states.inputValue = "";
    else expanded.value = false;
  };
  const toggleMenu = (event) => {
    var _a;
    if (selectDisabled.value || props.filterable && expanded.value && event && !((_a = suffixRef.value) == null ? void 0 : _a.contains(event.target))) return;
    if (isIOS) states.inputHovering = true;
    if (states.menuVisibleOnFocus) states.menuVisibleOnFocus = false;
    else expanded.value = !expanded.value;
  };
  const selectOption = () => {
    if (!expanded.value) toggleMenu();
    else {
      const option = optionsArray.value[states.hoveringIndex];
      if (option && !option.isDisabled) handleOptionSelect(option);
    }
  };
  const getValueKey = (item) => {
    return isObject(item.value) ? get(item.value, props.valueKey) : item.value;
  };
  const optionsAllDisabled = computed(() => optionsArray.value.filter((option) => option.visible).every((option) => option.isDisabled));
  const showTagList = computed(() => {
    if (!props.multiple) return [];
    return props.collapseTags ? states.selected.slice(0, props.maxCollapseTags) : states.selected;
  });
  const collapseTagList = computed(() => {
    if (!props.multiple) return [];
    return props.collapseTags ? states.selected.slice(props.maxCollapseTags) : [];
  });
  const navigateOptions = (direction) => {
    if (!expanded.value) {
      expanded.value = true;
      return;
    }
    if (states.options.size === 0 || filteredOptionsCount.value === 0 || isComposing.value) return;
    if (!optionsAllDisabled.value) {
      if (direction === "next") {
        states.hoveringIndex++;
        if (states.hoveringIndex === states.options.size) states.hoveringIndex = 0;
      } else if (direction === "prev") {
        states.hoveringIndex--;
        if (states.hoveringIndex < 0) states.hoveringIndex = states.options.size - 1;
      }
      const option = optionsArray.value[states.hoveringIndex];
      if (option.isDisabled || !option.visible) navigateOptions(direction);
      nextTick(() => scrollToOption(hoverOption.value));
    }
  };
  const findFocusableIndex = (arr, start, step, len) => {
    for (let i = start; i >= 0 && i < len; i += step) {
      const obj = arr[i];
      if (!(obj == null ? void 0 : obj.isDisabled) && (obj == null ? void 0 : obj.visible)) return i;
    }
    return null;
  };
  const focusOption = (targetIndex, mode) => {
    const len = states.options.size;
    if (len === 0) return;
    const start = clamp(targetIndex, 0, len - 1);
    const options = optionsArray.value;
    const direction = mode === "up" ? -1 : 1;
    const newIndex = findFocusableIndex(options, start, direction, len) ?? findFocusableIndex(options, start - direction, -direction, len);
    if (newIndex != null) {
      states.hoveringIndex = newIndex;
      nextTick(() => scrollToOption(hoverOption.value));
    }
  };
  const handleKeydown = (e) => {
    const code = getEventCode(e);
    let isPreventDefault = true;
    switch (code) {
      case EVENT_CODE.up:
        navigateOptions("prev");
        break;
      case EVENT_CODE.down:
        navigateOptions("next");
        break;
      case EVENT_CODE.enter:
      case EVENT_CODE.numpadEnter:
        if (!isComposing.value) selectOption();
        break;
      case EVENT_CODE.esc:
        handleEsc();
        break;
      case EVENT_CODE.backspace:
        isPreventDefault = false;
        deletePrevTag(e);
        return;
      case EVENT_CODE.home:
        if (!expanded.value) return;
        focusOption(0, "down");
        break;
      case EVENT_CODE.end:
        if (!expanded.value) return;
        focusOption(states.options.size - 1, "up");
        break;
      case EVENT_CODE.pageUp:
        if (!expanded.value) return;
        focusOption(states.hoveringIndex - 10, "up");
        break;
      case EVENT_CODE.pageDown:
        if (!expanded.value) return;
        focusOption(states.hoveringIndex + 10, "down");
        break;
      default:
        isPreventDefault = false;
        break;
    }
    if (isPreventDefault) {
      e.preventDefault();
      e.stopPropagation();
    }
  };
  const getGapWidth = () => {
    if (!selectionRef.value) return 0;
    const style = window.getComputedStyle(selectionRef.value);
    return Number.parseFloat(style.gap || "6px");
  };
  const tagStyle = computed(() => {
    const gapWidth = getGapWidth();
    const inputSlotWidth = props.filterable ? gapWidth + 11 : 0;
    return { maxWidth: `${collapseItemRef.value && props.maxCollapseTags === 1 ? states.selectionWidth - states.collapseItemWidth - gapWidth - inputSlotWidth : states.selectionWidth - inputSlotWidth}px` };
  });
  const collapseTagStyle = computed(() => {
    return { maxWidth: `${states.selectionWidth}px` };
  });
  const popupScroll = (data) => {
    emit("popup-scroll", data);
  };
  const endReached = (direction) => {
    emit("end-reached", direction);
  };
  useResizeObserver(selectionRef, resetSelectionWidth);
  useResizeObserver(wrapperRef, updateTooltip);
  useResizeObserver(tagMenuRef, updateTagTooltip);
  useResizeObserver(collapseItemRef, resetCollapseItemWidth);
  let stop;
  watch(() => dropdownMenuVisible.value, (newVal) => {
    if (newVal) stop = useResizeObserver(menuRef, updateTooltip).stop;
    else {
      stop == null ? void 0 : stop();
      stop = void 0;
    }
    emit("visible-change", newVal);
  });
  onMounted(() => {
    setSelected();
  });
  return {
    inputId,
    contentId,
    nsSelect,
    nsInput,
    states,
    isFocused,
    expanded,
    optionsArray,
    hoverOption,
    selectSize,
    filteredOptionsCount,
    updateTooltip,
    updateTagTooltip,
    debouncedOnInputChange,
    onInput,
    deletePrevTag,
    deleteTag,
    deleteSelected,
    handleOptionSelect,
    scrollToOption,
    hasModelValue,
    shouldShowPlaceholder,
    currentPlaceholder,
    mouseEnterEventName,
    needStatusIcon,
    showClearBtn,
    iconComponent,
    iconReverse,
    validateState,
    validateIcon,
    showNewOption,
    updateOptions,
    collapseTagSize,
    setSelected,
    selectDisabled,
    emptyText,
    handleCompositionStart,
    handleCompositionUpdate,
    handleCompositionEnd,
    handleKeydown,
    onOptionCreate,
    onOptionDestroy,
    handleMenuEnter,
    focus,
    blur,
    handleClearClick,
    handleClickOutside,
    handleEsc,
    toggleMenu,
    selectOption,
    getValueKey,
    navigateOptions,
    dropdownMenuVisible,
    showTagList,
    collapseTagList,
    popupScroll,
    getOption,
    endReached,
    tagStyle,
    collapseTagStyle,
    popperRef,
    inputRef,
    tooltipRef,
    tagTooltipRef,
    prefixRef,
    suffixRef,
    selectRef,
    wrapperRef,
    selectionRef,
    scrollbarRef,
    menuRef,
    tagMenuRef,
    collapseItemRef
  };
};
var options_default = defineComponent({
  name: "ElOptions",
  setup(_, { slots }) {
    const select = inject(selectKey);
    let cachedValueList = [];
    return () => {
      var _a, _b;
      const children = (_a = slots.default) == null ? void 0 : _a.call(slots);
      const valueList = [];
      function filterOptions(children2) {
        if (!isArray(children2)) return;
        children2.forEach((item) => {
          var _a2, _b2, _c, _d;
          const name = (_a2 = (item == null ? void 0 : item.type) || {}) == null ? void 0 : _a2.name;
          if (name === "ElOptionGroup") filterOptions(!isString(item.children) && !isArray(item.children) && isFunction((_b2 = item.children) == null ? void 0 : _b2.default) ? (_c = item.children) == null ? void 0 : _c.default() : item.children);
          else if (name === "ElOption") valueList.push((_d = item.props) == null ? void 0 : _d.value);
          else if (isArray(item.children)) filterOptions(item.children);
        });
      }
      if (children.length) filterOptions((_b = children[0]) == null ? void 0 : _b.children);
      if (!isEqual(valueList, cachedValueList)) {
        cachedValueList = valueList;
        if (select) select.states.optionValues = valueList;
      }
      return children;
    };
  }
});
const COMPONENT_NAME = "ElSelect";
const warnHandlerMap = /* @__PURE__ */ new WeakMap();
const createSelectWarnHandler = (appContext) => {
  return (...args) => {
    var _a, _b;
    const message = args[0];
    if (!message || message.includes('Slot "default" invoked outside of the render function') && ((_a = args[2]) == null ? void 0 : _a.includes("ElTreeSelect"))) return;
    const original = (_b = warnHandlerMap.get(appContext)) == null ? void 0 : _b.originalWarnHandler;
    if (original) {
      original(...args);
      return;
    }
    console.warn(...args);
  };
};
const getWarnHandlerRecord = (appContext) => {
  let record = warnHandlerMap.get(appContext);
  if (!record) {
    record = {
      originalWarnHandler: appContext.config.warnHandler,
      handler: createSelectWarnHandler(appContext),
      count: 0
    };
    warnHandlerMap.set(appContext, record);
  }
  return record;
};
var select_vue_vue_type_script_lang_default = defineComponent({
  name: COMPONENT_NAME,
  componentName: COMPONENT_NAME,
  components: {
    ElSelectMenu: select_dropdown_default,
    ElOption: option_default,
    ElOptions: options_default,
    ElOptionGroup: option_group_default,
    ElTag,
    ElScrollbar,
    ElTooltip,
    ElIcon
  },
  directives: { ClickOutside },
  props: selectProps,
  emits: selectEmits,
  setup(props, { emit, slots }) {
    const instance = getCurrentInstance();
    const warnRecord = getWarnHandlerRecord(instance.appContext);
    warnRecord.count += 1;
    instance.appContext.config.warnHandler = warnRecord.handler;
    const modelValue = computed(() => {
      const { modelValue: rawModelValue, multiple } = props;
      const fallback = multiple ? [] : void 0;
      if (isArray(rawModelValue)) return multiple ? rawModelValue : fallback;
      return multiple ? fallback : rawModelValue;
    });
    const _props = reactive({
      ...toRefs(props),
      modelValue
    });
    const API = useSelect(_props, emit);
    const { calculatorRef, inputStyle } = useCalcInputWidth();
    const { getLabel, getValue, getOptions, getDisabled } = useProps(props);
    const getOptionProps = (option) => ({
      label: getLabel(option),
      value: getValue(option),
      disabled: getDisabled(option)
    });
    const flatTreeSelectData = (data) => {
      return data.reduce((acc, item) => {
        acc.push(item);
        if (item.children && item.children.length > 0) acc.push(...flatTreeSelectData(item.children));
        return acc;
      }, []);
    };
    const manuallyRenderSlots = (vnodes) => {
      flattedChildren(vnodes || []).forEach((item) => {
        var _a;
        if (isObject(item) && (item.type.name === "ElOption" || item.type.name === "ElTree")) {
          const _name = item.type.name;
          if (_name === "ElTree") flatTreeSelectData(((_a = item.props) == null ? void 0 : _a.data) || []).forEach((treeItem) => {
            treeItem.currentLabel = treeItem.label ?? (isObject(treeItem.value) ? "" : treeItem.value);
            API.onOptionCreate(treeItem);
          });
          else if (_name === "ElOption") {
            const obj = { ...item.props };
            obj.currentLabel = obj.label ?? (isObject(obj.value) ? "" : obj.value);
            API.onOptionCreate(obj);
          }
        }
      });
    };
    watch(() => {
      var _a;
      return [props.persistent || API.expanded.value || !slots.default ? void 0 : (_a = slots.default) == null ? void 0 : _a.call(slots), modelValue.value];
    }, () => {
      var _a;
      if (props.persistent || API.expanded.value) return;
      if (!slots.default) return;
      API.states.options.clear();
      manuallyRenderSlots((_a = slots.default) == null ? void 0 : _a.call(slots));
    }, { immediate: true });
    provide(selectKey, reactive({
      props: _props,
      states: API.states,
      selectRef: API.selectRef,
      optionsArray: API.optionsArray,
      setSelected: API.setSelected,
      handleOptionSelect: API.handleOptionSelect,
      onOptionCreate: API.onOptionCreate,
      onOptionDestroy: API.onOptionDestroy
    }));
    const selectedLabel = computed(() => {
      if (!props.multiple) return API.states.selectedLabel;
      return API.states.selected.map((i) => i.currentLabel);
    });
    onBeforeUnmount(() => {
      const record = warnHandlerMap.get(instance.appContext);
      if (!record) return;
      record.count -= 1;
      if (record.count <= 0) {
        instance.appContext.config.warnHandler = record.originalWarnHandler;
        warnHandlerMap.delete(instance.appContext);
      }
    });
    return {
      ...API,
      modelValue,
      selectedLabel,
      calculatorRef,
      inputStyle,
      getLabel,
      getValue,
      getOptions,
      getDisabled,
      getOptionProps
    };
  }
});
const _hoisted_1 = [
  "id",
  "value",
  "name",
  "disabled",
  "autocomplete",
  "tabindex",
  "readonly",
  "aria-activedescendant",
  "aria-controls",
  "aria-expanded",
  "aria-label"
];
const _hoisted_2 = ["textContent"];
const _hoisted_3 = { key: 1 };
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  const _component_el_tag = resolveComponent("el-tag");
  const _component_el_tooltip = resolveComponent("el-tooltip");
  const _component_el_icon = resolveComponent("el-icon");
  const _component_el_option = resolveComponent("el-option");
  const _component_el_option_group = resolveComponent("el-option-group");
  const _component_el_options = resolveComponent("el-options");
  const _component_el_scrollbar = resolveComponent("el-scrollbar");
  const _component_el_select_menu = resolveComponent("el-select-menu");
  const _directive_click_outside = resolveDirective("click-outside");
  return withDirectives((openBlock(), createElementBlock("div", mergeProps({
    ref: "selectRef",
    class: [_ctx.nsSelect.b(), _ctx.nsSelect.m(_ctx.selectSize)]
  }, { [toHandlerKey(_ctx.mouseEnterEventName)]: _cache[11] || (_cache[11] = ($event) => _ctx.states.inputHovering = true) }, { onMouseleave: _cache[12] || (_cache[12] = ($event) => _ctx.states.inputHovering = false) }), [createVNode(_component_el_tooltip, {
    ref: "tooltipRef",
    visible: _ctx.dropdownMenuVisible,
    placement: _ctx.placement,
    teleported: _ctx.teleported,
    "popper-class": [_ctx.nsSelect.e("popper"), _ctx.popperClass],
    "popper-style": _ctx.popperStyle,
    "popper-options": _ctx.popperOptions,
    "fallback-placements": _ctx.fallbackPlacements,
    effect: _ctx.effect,
    pure: "",
    trigger: "click",
    transition: `${_ctx.nsSelect.namespace.value}-zoom-in-top`,
    "stop-popper-mouse-event": false,
    "gpu-acceleration": false,
    persistent: _ctx.persistent,
    "append-to": _ctx.appendTo,
    "show-arrow": _ctx.showArrow,
    offset: _ctx.offset,
    onBeforeShow: _ctx.handleMenuEnter,
    onHide: _cache[10] || (_cache[10] = ($event) => _ctx.states.isBeforeHide = false)
  }, {
    default: withCtx(() => {
      var _a;
      return [createElementVNode("div", {
        ref: "wrapperRef",
        class: normalizeClass([
          _ctx.nsSelect.e("wrapper"),
          _ctx.nsSelect.is("focused", _ctx.isFocused),
          _ctx.nsSelect.is("hovering", _ctx.states.inputHovering),
          _ctx.nsSelect.is("filterable", _ctx.filterable),
          _ctx.nsSelect.is("disabled", _ctx.selectDisabled)
        ]),
        onClick: _cache[7] || (_cache[7] = withModifiers((...args) => _ctx.toggleMenu && _ctx.toggleMenu(...args), ["prevent"]))
      }, [
        _ctx.$slots.prefix ? (openBlock(), createElementBlock("div", {
          key: 0,
          ref: "prefixRef",
          class: normalizeClass(_ctx.nsSelect.e("prefix"))
        }, [renderSlot(_ctx.$slots, "prefix")], 2)) : createCommentVNode("v-if", true),
        createElementVNode("div", {
          ref: "selectionRef",
          class: normalizeClass([_ctx.nsSelect.e("selection"), _ctx.nsSelect.is("near", _ctx.multiple && !_ctx.$slots.prefix && !!_ctx.states.selected.length)])
        }, [
          _ctx.multiple ? renderSlot(_ctx.$slots, "tag", {
            key: 0,
            data: _ctx.states.selected,
            deleteTag: _ctx.deleteTag,
            selectDisabled: _ctx.selectDisabled
          }, () => {
            var _a2, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l, _m;
            return [(openBlock(true), createElementBlock(Fragment, null, renderList(_ctx.showTagList, (item) => {
              return openBlock(), createElementBlock("div", {
                key: _ctx.getValueKey(item),
                class: normalizeClass(_ctx.nsSelect.e("selected-item"))
              }, [createVNode(_component_el_tag, {
                closable: !_ctx.selectDisabled && !item.isDisabled,
                size: _ctx.collapseTagSize,
                type: _ctx.tagType,
                effect: _ctx.tagEffect,
                "disable-transitions": "",
                style: normalizeStyle(_ctx.tagStyle),
                onClose: ($event) => _ctx.deleteTag($event, item)
              }, {
                default: withCtx(() => [createElementVNode("span", { class: normalizeClass(_ctx.nsSelect.e("tags-text")) }, [renderSlot(_ctx.$slots, "label", {
                  index: item.index,
                  label: item.currentLabel,
                  value: item.value
                }, () => [createTextVNode(toDisplayString(item.currentLabel), 1)])], 2)]),
                _: 2
              }, 1032, [
                "closable",
                "size",
                "type",
                "effect",
                "style",
                "onClose"
              ])], 2);
            }), 128)), _ctx.collapseTags && _ctx.states.selected.length > _ctx.maxCollapseTags ? (openBlock(), createBlock(_component_el_tooltip, {
              key: 0,
              ref: "tagTooltipRef",
              disabled: _ctx.dropdownMenuVisible || !_ctx.collapseTagsTooltip,
              "fallback-placements": ((_a2 = _ctx.tagTooltip) == null ? void 0 : _a2.fallbackPlacements) ?? [
                "bottom",
                "top",
                "right",
                "left"
              ],
              effect: ((_b = _ctx.tagTooltip) == null ? void 0 : _b.effect) ?? _ctx.effect,
              placement: ((_c = _ctx.tagTooltip) == null ? void 0 : _c.placement) ?? "bottom",
              "popper-class": ((_d = _ctx.tagTooltip) == null ? void 0 : _d.popperClass) ?? _ctx.popperClass,
              "popper-style": ((_e = _ctx.tagTooltip) == null ? void 0 : _e.popperStyle) ?? _ctx.popperStyle,
              teleported: ((_f = _ctx.tagTooltip) == null ? void 0 : _f.teleported) ?? _ctx.teleported,
              "append-to": ((_g = _ctx.tagTooltip) == null ? void 0 : _g.appendTo) ?? _ctx.appendTo,
              "popper-options": ((_h = _ctx.tagTooltip) == null ? void 0 : _h.popperOptions) ?? _ctx.popperOptions,
              transition: (_i = _ctx.tagTooltip) == null ? void 0 : _i.transition,
              "show-after": (_j = _ctx.tagTooltip) == null ? void 0 : _j.showAfter,
              "hide-after": (_k = _ctx.tagTooltip) == null ? void 0 : _k.hideAfter,
              "auto-close": (_l = _ctx.tagTooltip) == null ? void 0 : _l.autoClose,
              offset: (_m = _ctx.tagTooltip) == null ? void 0 : _m.offset
            }, {
              default: withCtx(() => [createElementVNode("div", {
                ref: "collapseItemRef",
                class: normalizeClass(_ctx.nsSelect.e("selected-item"))
              }, [createVNode(_component_el_tag, {
                closable: false,
                size: _ctx.collapseTagSize,
                type: _ctx.tagType,
                effect: _ctx.tagEffect,
                "disable-transitions": "",
                style: normalizeStyle(_ctx.collapseTagStyle)
              }, {
                default: withCtx(() => [createElementVNode("span", { class: normalizeClass(_ctx.nsSelect.e("tags-text")) }, " + " + toDisplayString(_ctx.states.selected.length - _ctx.maxCollapseTags), 3)]),
                _: 1
              }, 8, [
                "size",
                "type",
                "effect",
                "style"
              ])], 2)]),
              content: withCtx(() => [createElementVNode("div", {
                ref: "tagMenuRef",
                class: normalizeClass(_ctx.nsSelect.e("selection"))
              }, [(openBlock(true), createElementBlock(Fragment, null, renderList(_ctx.collapseTagList, (item) => {
                return openBlock(), createElementBlock("div", {
                  key: _ctx.getValueKey(item),
                  class: normalizeClass(_ctx.nsSelect.e("selected-item"))
                }, [createVNode(_component_el_tag, {
                  class: "in-tooltip",
                  closable: !_ctx.selectDisabled && !item.isDisabled,
                  size: _ctx.collapseTagSize,
                  type: _ctx.tagType,
                  effect: _ctx.tagEffect,
                  "disable-transitions": "",
                  onClose: ($event) => _ctx.deleteTag($event, item)
                }, {
                  default: withCtx(() => [createElementVNode("span", { class: normalizeClass(_ctx.nsSelect.e("tags-text")) }, [renderSlot(_ctx.$slots, "label", {
                    index: item.index,
                    label: item.currentLabel,
                    value: item.value
                  }, () => [createTextVNode(toDisplayString(item.currentLabel), 1)])], 2)]),
                  _: 2
                }, 1032, [
                  "closable",
                  "size",
                  "type",
                  "effect",
                  "onClose"
                ])], 2);
              }), 128))], 2)]),
              _: 3
            }, 8, [
              "disabled",
              "fallback-placements",
              "effect",
              "placement",
              "popper-class",
              "popper-style",
              "teleported",
              "append-to",
              "popper-options",
              "transition",
              "show-after",
              "hide-after",
              "auto-close",
              "offset"
            ])) : createCommentVNode("v-if", true)];
          }) : createCommentVNode("v-if", true),
          createElementVNode("div", { class: normalizeClass([
            _ctx.nsSelect.e("selected-item"),
            _ctx.nsSelect.e("input-wrapper"),
            _ctx.nsSelect.is("hidden", !_ctx.filterable || _ctx.selectDisabled || _ctx.multiple && !_ctx.states.inputValue && !_ctx.isFocused)
          ]) }, [createElementVNode("input", {
            id: _ctx.inputId,
            ref: "inputRef",
            value: _ctx.states.inputValue,
            type: "text",
            name: _ctx.name,
            class: normalizeClass([_ctx.nsSelect.e("input"), _ctx.nsSelect.is(_ctx.selectSize)]),
            disabled: _ctx.selectDisabled,
            autocomplete: _ctx.autocomplete,
            style: normalizeStyle(_ctx.inputStyle),
            tabindex: _ctx.tabindex,
            role: "combobox",
            readonly: !_ctx.filterable,
            spellcheck: "false",
            "aria-activedescendant": ((_a = _ctx.hoverOption) == null ? void 0 : _a.id) || "",
            "aria-controls": _ctx.contentId,
            "aria-expanded": _ctx.dropdownMenuVisible,
            "aria-label": _ctx.ariaLabel,
            "aria-autocomplete": "none",
            "aria-haspopup": "listbox",
            onKeydown: _cache[0] || (_cache[0] = (...args) => _ctx.handleKeydown && _ctx.handleKeydown(...args)),
            onCompositionstart: _cache[1] || (_cache[1] = (...args) => _ctx.handleCompositionStart && _ctx.handleCompositionStart(...args)),
            onCompositionupdate: _cache[2] || (_cache[2] = (...args) => _ctx.handleCompositionUpdate && _ctx.handleCompositionUpdate(...args)),
            onCompositionend: _cache[3] || (_cache[3] = (...args) => _ctx.handleCompositionEnd && _ctx.handleCompositionEnd(...args)),
            onInput: _cache[4] || (_cache[4] = (...args) => _ctx.onInput && _ctx.onInput(...args)),
            onChange: _cache[5] || (_cache[5] = withModifiers(() => {
            }, ["stop"])),
            onClick: _cache[6] || (_cache[6] = withModifiers((...args) => _ctx.toggleMenu && _ctx.toggleMenu(...args), ["stop"]))
          }, null, 46, _hoisted_1), _ctx.filterable ? (openBlock(), createElementBlock("span", {
            key: 0,
            ref: "calculatorRef",
            "aria-hidden": "true",
            class: normalizeClass(_ctx.nsSelect.e("input-calculator")),
            textContent: toDisplayString(_ctx.states.inputValue)
          }, null, 10, _hoisted_2)) : createCommentVNode("v-if", true)], 2),
          _ctx.shouldShowPlaceholder ? (openBlock(), createElementBlock("div", {
            key: 1,
            class: normalizeClass([
              _ctx.nsSelect.e("selected-item"),
              _ctx.nsSelect.e("placeholder"),
              _ctx.nsSelect.is("transparent", !_ctx.hasModelValue || _ctx.expanded && !_ctx.states.inputValue)
            ])
          }, [_ctx.hasModelValue ? renderSlot(_ctx.$slots, "label", {
            key: 0,
            index: _ctx.getOption(_ctx.modelValue).index,
            label: _ctx.currentPlaceholder,
            value: _ctx.modelValue
          }, () => [createElementVNode("span", null, toDisplayString(_ctx.currentPlaceholder), 1)]) : (openBlock(), createElementBlock("span", _hoisted_3, toDisplayString(_ctx.currentPlaceholder), 1))], 2)) : createCommentVNode("v-if", true)
        ], 2),
        createElementVNode("div", {
          ref: "suffixRef",
          class: normalizeClass(_ctx.nsSelect.e("suffix"))
        }, [
          _ctx.iconComponent && !_ctx.showClearBtn ? (openBlock(), createBlock(_component_el_icon, {
            key: 0,
            class: normalizeClass([
              _ctx.nsSelect.e("caret"),
              _ctx.nsSelect.e("icon"),
              _ctx.iconReverse
            ])
          }, {
            default: withCtx(() => [(openBlock(), createBlock(resolveDynamicComponent(_ctx.iconComponent)))]),
            _: 1
          }, 8, ["class"])) : createCommentVNode("v-if", true),
          _ctx.showClearBtn && _ctx.clearIcon ? (openBlock(), createBlock(_component_el_icon, {
            key: 1,
            class: normalizeClass([
              _ctx.nsSelect.e("caret"),
              _ctx.nsSelect.e("icon"),
              _ctx.nsSelect.e("clear")
            ]),
            onClick: _ctx.handleClearClick
          }, {
            default: withCtx(() => [(openBlock(), createBlock(resolveDynamicComponent(_ctx.clearIcon)))]),
            _: 1
          }, 8, ["class", "onClick"])) : createCommentVNode("v-if", true),
          _ctx.validateState && _ctx.validateIcon && _ctx.needStatusIcon ? (openBlock(), createBlock(_component_el_icon, {
            key: 2,
            class: normalizeClass([
              _ctx.nsInput.e("icon"),
              _ctx.nsInput.e("validateIcon"),
              _ctx.nsInput.is("loading", _ctx.validateState === "validating")
            ])
          }, {
            default: withCtx(() => [(openBlock(), createBlock(resolveDynamicComponent(_ctx.validateIcon)))]),
            _: 1
          }, 8, ["class"])) : createCommentVNode("v-if", true)
        ], 2)
      ], 2)];
    }),
    content: withCtx(() => [createVNode(_component_el_select_menu, { ref: "menuRef" }, {
      default: withCtx(() => [
        _ctx.$slots.header ? (openBlock(), createElementBlock("div", {
          key: 0,
          class: normalizeClass(_ctx.nsSelect.be("dropdown", "header")),
          onClick: _cache[8] || (_cache[8] = withModifiers(() => {
          }, ["stop"]))
        }, [renderSlot(_ctx.$slots, "header")], 2)) : createCommentVNode("v-if", true),
        withDirectives(createVNode(_component_el_scrollbar, {
          id: _ctx.contentId,
          ref: "scrollbarRef",
          tag: "ul",
          "wrap-class": _ctx.nsSelect.be("dropdown", "wrap"),
          "view-class": _ctx.nsSelect.be("dropdown", "list"),
          class: normalizeClass([_ctx.nsSelect.is("empty", _ctx.filteredOptionsCount === 0)]),
          role: "listbox",
          "aria-label": _ctx.ariaLabel,
          "aria-orientation": "vertical",
          onScroll: _ctx.popupScroll,
          onEndReached: _ctx.endReached
        }, {
          default: withCtx(() => [_ctx.showNewOption ? (openBlock(), createBlock(_component_el_option, {
            key: 0,
            value: _ctx.states.inputValue,
            created: true
          }, null, 8, ["value"])) : createCommentVNode("v-if", true), createVNode(_component_el_options, null, {
            default: withCtx(() => [renderSlot(_ctx.$slots, "default", {}, () => [(openBlock(true), createElementBlock(Fragment, null, renderList(_ctx.options, (option, index) => {
              var _a;
              return openBlock(), createElementBlock(Fragment, { key: index }, [((_a = _ctx.getOptions(option)) == null ? void 0 : _a.length) ? (openBlock(), createBlock(_component_el_option_group, {
                key: 0,
                label: _ctx.getLabel(option),
                disabled: _ctx.getDisabled(option)
              }, {
                default: withCtx(() => [(openBlock(true), createElementBlock(Fragment, null, renderList(_ctx.getOptions(option), (item) => {
                  return openBlock(), createBlock(_component_el_option, mergeProps({ key: _ctx.getValue(item) }, { ref_for: true }, _ctx.getOptionProps(item)), null, 16);
                }), 128))]),
                _: 2
              }, 1032, ["label", "disabled"])) : (openBlock(), createBlock(_component_el_option, mergeProps({
                key: 1,
                ref_for: true
              }, _ctx.getOptionProps(option)), null, 16))], 64);
            }), 128))])]),
            _: 3
          })]),
          _: 3
        }, 8, [
          "id",
          "wrap-class",
          "view-class",
          "class",
          "aria-label",
          "onScroll",
          "onEndReached"
        ]), [[vShow, _ctx.states.options.size > 0 && !_ctx.loading]]),
        _ctx.$slots.loading && _ctx.loading ? (openBlock(), createElementBlock("div", {
          key: 1,
          class: normalizeClass(_ctx.nsSelect.be("dropdown", "loading"))
        }, [renderSlot(_ctx.$slots, "loading")], 2)) : _ctx.loading || _ctx.filteredOptionsCount === 0 ? (openBlock(), createElementBlock("div", {
          key: 2,
          class: normalizeClass(_ctx.nsSelect.be("dropdown", "empty"))
        }, [renderSlot(_ctx.$slots, "empty", {}, () => [createElementVNode("span", null, toDisplayString(_ctx.emptyText), 1)])], 2)) : createCommentVNode("v-if", true),
        _ctx.$slots.footer ? (openBlock(), createElementBlock("div", {
          key: 3,
          class: normalizeClass(_ctx.nsSelect.be("dropdown", "footer")),
          onClick: _cache[9] || (_cache[9] = withModifiers(() => {
          }, ["stop"]))
        }, [renderSlot(_ctx.$slots, "footer")], 2)) : createCommentVNode("v-if", true)
      ]),
      _: 3
    }, 512)]),
    _: 3
  }, 8, [
    "visible",
    "placement",
    "teleported",
    "popper-class",
    "popper-style",
    "popper-options",
    "fallback-placements",
    "effect",
    "transition",
    "persistent",
    "append-to",
    "show-arrow",
    "offset",
    "onBeforeShow"
  ])], 16)), [[
    _directive_click_outside,
    _ctx.handleClickOutside,
    _ctx.popperRef
  ]]);
}
var select_default = /* @__PURE__ */ _plugin_vue_export_helper_default(select_vue_vue_type_script_lang_default, [["render", _sfc_render]]);
const ElSelect = withInstall(select_default, {
  Option: option_default,
  OptionGroup: option_group_default
});
withNoopInstall(option_default);
withNoopInstall(option_group_default);
export {
  ElSelect,
  ElSelect as default,
  selectEmits,
  selectGroupKey,
  selectKey,
  selectProps
};
//# sourceMappingURL=index-DbJvgbor.js.map
