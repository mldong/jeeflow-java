import { j as isClient, e as isString, b as buildProps, d as definePropType, g as debugWarn, k as isNumber, u as useNamespace, N as NOOP, l as isObject, w as withInstall } from "./error-DiL_p-4A.js";
import { U as UPDATE_MODEL_EVENT, C as CHANGE_EVENT, I as INPUT_EVENT } from "./event-BZTOGHfp.js";
import { i as iconPropType, a as circle_close_default, V as ValidateComponentsMap, v as view_default, h as hide_default, E as ElIcon } from "./index-D5MsDfVU.js";
import { m as mutable } from "./typescript-D6L75muK.js";
import { u as useSizeProp } from "./style-_T5b_00u.js";
import { u as useAriaProps } from "./index-C-31KauU.js";
import { markRaw, computed, getCurrentInstance, defineComponent, useAttrs as useAttrs$1, useSlots, shallowRef, ref, watch, nextTick, onMounted, onBeforeUnmount, toRef, openBlock, createElementBlock, normalizeStyle, normalizeClass, unref, createCommentVNode, Fragment, renderSlot, createElementVNode, createBlock, withCtx, resolveDynamicComponent, mergeProps, withModifiers, toDisplayString } from "vue";
import { b as useResizeObserver } from "./index-BoUuUOkb.js";
import { g as fromPairs, h as isNil } from "./index-D9cY2WMP.js";
import { u as useFocusController, a as useComposition } from "./index-CRD3I8pG.js";
import { u as useFormItem, c as useFormItemInputId, a as useFormSize, b as useFormDisabled } from "./use-form-item-BToCvxP0.js";
import { i as isFirefox } from "./browser-B2sbqLnv.js";
const rAF = (fn) => isClient ? window.requestAnimationFrame(fn) : setTimeout(fn, 16);
const cAF = (handle) => isClient ? window.cancelAnimationFrame(handle) : clearTimeout(handle);
const inputProps = buildProps({
  /**
  * @description native input id
  */
  id: {
    type: String,
    default: void 0
  },
  /**
  * @description input box size
  */
  size: useSizeProp,
  /**
  * @description whether to disable
  */
  disabled: {
    type: Boolean,
    default: void 0
  },
  /**
  * @description binding value
  */
  modelValue: {
    type: definePropType([
      String,
      Number,
      Object
    ]),
    default: ""
  },
  /**
  * @description v-model modifiers, reference [Vue modifiers](https://vuejs.org/guide/essentials/forms.html#modifiers)
  */
  modelModifiers: {
    type: definePropType(Object),
    default: () => ({})
  },
  /**
  * @description same as `maxlength` in native input
  */
  maxlength: { type: [String, Number] },
  /**
  * @description same as `minlength` in native input
  */
  minlength: { type: [String, Number] },
  /**
  * @description type of input, see more in [MDN](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input#Form_%3Cinput%3E_types)
  */
  type: {
    type: definePropType(String),
    default: "text"
  },
  /**
  * @description control the resizability
  */
  resize: {
    type: String,
    values: [
      "none",
      "both",
      "horizontal",
      "vertical"
    ]
  },
  /**
  * @description whether textarea has an adaptive height
  */
  autosize: {
    type: definePropType([Boolean, Object]),
    default: false
  },
  /**
  * @description native input autocomplete
  */
  autocomplete: {
    type: definePropType(String),
    default: "off"
  },
  /**
  * @description format content
  */
  formatter: { type: Function },
  /**
  * @description parse content
  */
  parser: { type: Function },
  /**
  * @description placeholder
  */
  placeholder: { type: String },
  /**
  * @description native input form
  */
  form: { type: String },
  /**
  * @description native input readonly
  */
  readonly: Boolean,
  /**
  * @description whether to show clear button
  */
  clearable: Boolean,
  /**
  * @description custom clear icon component
  */
  clearIcon: {
    type: iconPropType,
    default: circle_close_default
  },
  /**
  * @description toggleable password input
  */
  showPassword: Boolean,
  /**
  * @description word count
  */
  showWordLimit: Boolean,
  /**
  * @description word count position, valid when `show-word-limit` is true
  */
  wordLimitPosition: {
    type: String,
    values: ["inside", "outside"],
    default: "inside"
  },
  /**
  * @description suffix icon
  */
  suffixIcon: { type: iconPropType },
  /**
  * @description prefix icon
  */
  prefixIcon: { type: iconPropType },
  /**
  * @description container role, internal properties provided for use by the picker component
  */
  containerRole: {
    type: String,
    default: void 0
  },
  /**
  * @description input tabindex
  */
  tabindex: {
    type: [String, Number],
    default: 0
  },
  /**
  * @description whether to trigger form validation
  */
  validateEvent: {
    type: Boolean,
    default: true
  },
  /**
  * @description input or textarea element style
  */
  inputStyle: {
    type: definePropType([
      Object,
      Array,
      String,
      Boolean
    ]),
    default: () => mutable({})
  },
  /**
  * @description Count graphemes of input value. If it's set, native maxlength and minlength won't be used.
  */
  countGraphemes: { type: definePropType(Function) },
  /**
  * @description native input autofocus
  */
  autofocus: Boolean,
  rows: {
    type: Number,
    default: 2
  },
  ...useAriaProps(["ariaLabel"]),
  /**
  * @description native input mode for virtual keyboards
  */
  inputmode: {
    type: definePropType(String),
    default: void 0
  },
  /**
  * @description same as `name` in native input
  */
  name: String
});
const inputEmits = {
  [UPDATE_MODEL_EVENT]: (value) => isString(value),
  input: (value) => isString(value),
  change: (value, evt) => isString(value) && (evt instanceof Event || evt === void 0),
  focus: (evt) => evt instanceof FocusEvent,
  blur: (evt) => evt instanceof FocusEvent,
  clear: (evt) => evt === void 0 || evt instanceof MouseEvent,
  mouseleave: (evt) => evt instanceof MouseEvent,
  mouseenter: (evt) => evt instanceof MouseEvent,
  keydown: (evt) => evt instanceof Event,
  compositionstart: (evt) => evt instanceof CompositionEvent,
  compositionupdate: (evt) => evt instanceof CompositionEvent,
  compositionend: (evt) => evt instanceof CompositionEvent
};
({
  clearIcon: markRaw(circle_close_default)
});
const DEFAULT_EXCLUDE_KEYS = ["class", "style"];
const LISTENER_PREFIX = /^on[A-Z]/;
const useAttrs = (params = {}) => {
  const { excludeListeners = false, excludeKeys } = params;
  const allExcludeKeys = computed(() => {
    return ((excludeKeys == null ? void 0 : excludeKeys.value) || []).concat(DEFAULT_EXCLUDE_KEYS);
  });
  const instance = getCurrentInstance();
  if (!instance) {
    debugWarn("use-attrs", "getCurrentInstance() returned null. useAttrs() must be called at the top of a setup function");
    return computed(() => ({}));
  }
  return computed(() => {
    var _a;
    return fromPairs(Object.entries((_a = instance.proxy) == null ? void 0 : _a.$attrs).filter(([key]) => !allExcludeKeys.value.includes(key) && !(excludeListeners && LISTENER_PREFIX.test(key))));
  });
};
function useCursor(input) {
  let selectionInfo;
  function recordCursor() {
    if (input.value == void 0) return;
    const { selectionStart, selectionEnd, value } = input.value;
    if (selectionStart == null || selectionEnd == null) return;
    selectionInfo = {
      selectionStart,
      selectionEnd,
      value,
      beforeTxt: value.slice(0, Math.max(0, selectionStart)),
      afterTxt: value.slice(Math.max(0, selectionEnd))
    };
  }
  function setCursor() {
    if (input.value == void 0 || selectionInfo == void 0) return;
    const { value } = input.value;
    const { beforeTxt, afterTxt, selectionStart } = selectionInfo;
    if (beforeTxt == void 0 || afterTxt == void 0 || selectionStart == void 0) return;
    let startPos = value.length;
    if (value.endsWith(afterTxt)) startPos = value.length - afterTxt.length;
    else if (value.startsWith(beforeTxt)) startPos = beforeTxt.length;
    else {
      const beforeLastChar = beforeTxt[selectionStart - 1];
      const newIndex = value.indexOf(beforeLastChar, selectionStart - 1);
      if (newIndex !== -1) startPos = newIndex + 1;
    }
    input.value.setSelectionRange(startPos, startPos);
  }
  return [recordCursor, setCursor];
}
let hiddenTextarea = void 0;
const HIDDEN_STYLE = {
  height: "0",
  visibility: "hidden",
  overflow: isFirefox() ? "" : "hidden",
  position: "absolute",
  "z-index": "-1000",
  top: "0",
  right: "0"
};
const CONTEXT_STYLE = [
  "letter-spacing",
  "line-height",
  "padding-top",
  "padding-bottom",
  "font-family",
  "font-weight",
  "font-size",
  "text-rendering",
  "text-transform",
  "width",
  "text-indent",
  "padding-left",
  "padding-right",
  "border-width",
  "box-sizing",
  "word-break"
];
const looseToNumber = (val) => {
  const n = Number.parseFloat(val);
  return Number.isNaN(n) ? val : n;
};
function calculateNodeStyling(targetElement) {
  const style = window.getComputedStyle(targetElement);
  const boxSizing = style.getPropertyValue("box-sizing");
  const paddingSize = Number.parseFloat(style.getPropertyValue("padding-bottom")) + Number.parseFloat(style.getPropertyValue("padding-top"));
  const borderSize = Number.parseFloat(style.getPropertyValue("border-bottom-width")) + Number.parseFloat(style.getPropertyValue("border-top-width"));
  return {
    contextStyle: CONTEXT_STYLE.map((name) => [name, style.getPropertyValue(name)]),
    paddingSize,
    borderSize,
    boxSizing
  };
}
function calcTextareaHeight(targetElement, minRows = 1, maxRows) {
  var _a;
  if (!hiddenTextarea) {
    hiddenTextarea = document.createElement("textarea");
    let hostNode = document.body;
    if (!isFirefox() && targetElement.parentNode) hostNode = targetElement.parentNode;
    hostNode.appendChild(hiddenTextarea);
  }
  const { paddingSize, borderSize, boxSizing, contextStyle } = calculateNodeStyling(targetElement);
  contextStyle.forEach(([key, value]) => hiddenTextarea == null ? void 0 : hiddenTextarea.style.setProperty(key, value));
  Object.entries(HIDDEN_STYLE).forEach(([key, value]) => hiddenTextarea == null ? void 0 : hiddenTextarea.style.setProperty(key, value, "important"));
  hiddenTextarea.value = targetElement.value || targetElement.placeholder || "";
  let height = hiddenTextarea.scrollHeight;
  const result = {};
  if (boxSizing === "border-box") height = height + borderSize;
  else if (boxSizing === "content-box") height = height - paddingSize;
  hiddenTextarea.value = "";
  const singleRowHeight = hiddenTextarea.scrollHeight - paddingSize;
  if (isNumber(minRows)) {
    let minHeight = singleRowHeight * minRows;
    if (boxSizing === "border-box") minHeight = minHeight + paddingSize + borderSize;
    height = Math.max(minHeight, height);
    result.minHeight = `${minHeight}px`;
  }
  if (isNumber(maxRows)) {
    let maxHeight = singleRowHeight * maxRows;
    if (boxSizing === "border-box") maxHeight = maxHeight + paddingSize + borderSize;
    height = Math.min(maxHeight, height);
  }
  result.height = `${height}px`;
  (_a = hiddenTextarea.parentNode) == null ? void 0 : _a.removeChild(hiddenTextarea);
  hiddenTextarea = void 0;
  return result;
}
const _hoisted_1 = [
  "id",
  "name",
  "minlength",
  "maxlength",
  "type",
  "disabled",
  "readonly",
  "autocomplete",
  "tabindex",
  "aria-label",
  "placeholder",
  "form",
  "autofocus",
  "role",
  "inputmode"
];
const _hoisted_2 = [
  "id",
  "name",
  "minlength",
  "maxlength",
  "tabindex",
  "disabled",
  "readonly",
  "autocomplete",
  "aria-label",
  "placeholder",
  "form",
  "autofocus",
  "rows",
  "role",
  "inputmode"
];
const COMPONENT_NAME = "ElInput";
var input_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: COMPONENT_NAME,
  inheritAttrs: false,
  __name: "input",
  props: inputProps,
  emits: inputEmits,
  setup(__props, { expose: __expose, emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const rawAttrs = useAttrs$1();
    const slots = useSlots();
    const containerKls = computed(() => [
      props.type === "textarea" ? nsTextarea.b() : nsInput.b(),
      nsInput.m(inputSize.value),
      nsInput.is("disabled", inputDisabled.value),
      nsInput.is("exceed", inputExceed.value),
      {
        [nsInput.b("group")]: slots.prepend || slots.append,
        [nsInput.m("prefix")]: slots.prefix || props.prefixIcon,
        [nsInput.m("suffix")]: slots.suffix || props.suffixIcon || props.clearable || props.showPassword,
        [nsInput.bm("suffix", "password-clear")]: showClear.value && showPwdVisible.value,
        [nsInput.b("hidden")]: props.type === "hidden"
      },
      rawAttrs.class
    ]);
    const wrapperKls = computed(() => [nsInput.e("wrapper"), nsInput.is("focus", isFocused.value)]);
    const attrs = useAttrs();
    const maxlength = computed(() => {
      var _a;
      return (_a = props.maxlength) == null ? void 0 : _a.toString();
    });
    const { form: elForm, formItem: elFormItem } = useFormItem();
    const { inputId } = useFormItemInputId(props, { formItemContext: elFormItem });
    const inputSize = useFormSize();
    const inputDisabled = useFormDisabled();
    const nsInput = useNamespace("input");
    const nsTextarea = useNamespace("textarea");
    const input = shallowRef();
    const textarea = shallowRef();
    const hovering = ref(false);
    const passwordVisible = ref(false);
    const countStyle = ref();
    const clearIconStyle = ref();
    const textareaCalcStyle = shallowRef(props.inputStyle);
    const saveValue = ref("");
    const textareaHeight = ref();
    const _ref = computed(() => input.value || textarea.value);
    const { wrapperRef, isFocused, handleFocus, handleBlur } = useFocusController(_ref, {
      disabled: inputDisabled,
      afterBlur() {
        var _a;
        if (props.validateEvent) (_a = elFormItem == null ? void 0 : elFormItem.validate) == null ? void 0 : _a.call(elFormItem, "blur").catch(NOOP);
      }
    });
    const needStatusIcon = computed(() => (elForm == null ? void 0 : elForm.statusIcon) ?? false);
    const validateState = computed(() => (elFormItem == null ? void 0 : elFormItem.validateState) || "");
    const validateIcon = computed(() => validateState.value && ValidateComponentsMap[validateState.value]);
    const passwordIcon = computed(() => passwordVisible.value ? view_default : hide_default);
    const containerStyle = computed(() => [rawAttrs.style]);
    const textareaStyle = computed(() => [
      props.inputStyle,
      textareaCalcStyle.value,
      { resize: props.resize },
      textareaHeight.value ? { height: textareaHeight.value } : void 0
    ]);
    const nativeInputValue = computed(() => isNil(props.modelValue) ? "" : String(props.modelValue));
    const renderClear = computed(() => props.clearable && !inputDisabled.value && !props.readonly);
    const showClear = computed(() => renderClear.value && !!nativeInputValue.value && (isFocused.value || hovering.value));
    const showPwdVisible = computed(() => props.showPassword && !inputDisabled.value && !!nativeInputValue.value);
    const isWordLimitVisible = computed(() => props.showWordLimit && !!maxlength.value && (props.type === "text" || props.type === "textarea") && !inputDisabled.value && !props.readonly && !props.showPassword);
    const textLength = computed(() => {
      if (props.countGraphemes && props.showWordLimit) return props.countGraphemes(nativeInputValue.value);
      return nativeInputValue.value.length;
    });
    const inputExceed = computed(() => !!isWordLimitVisible.value && textLength.value > Number(maxlength.value));
    const suffixVisible = computed(() => !!slots.suffix || !!props.suffixIcon || props.clearable || props.showPassword || isWordLimitVisible.value || !!validateState.value && needStatusIcon.value);
    const hasModelModifiers = computed(() => !!Object.keys(props.modelModifiers).length);
    const [recordCursor, setCursor] = useCursor(input);
    let rAFId;
    useResizeObserver(textarea, (entries) => {
      onceInitSizeTextarea();
      if (!isWordLimitVisible.value && !renderClear.value || props.resize !== "both" && props.resize !== "horizontal") return;
      const { width } = entries[0].target.getBoundingClientRect();
      const updateStyle = () => {
        rAFId = void 0;
        countStyle.value = {
          /** right: 100% - (width - right(10)) */
          right: `calc(100% - ${width - 10}px)`
        };
        clearIconStyle.value = {
          /** right: 100% - (width - right(11)) */
          right: `calc(100% - ${width - 11}px)`
        };
      };
      rAFId && cAF(rAFId);
      rAFId = rAF(updateStyle);
    });
    const resizeTextarea = () => {
      const { type, autosize } = props;
      if (!isClient || type !== "textarea" || !textarea.value) return;
      if (autosize) {
        const minRows = isObject(autosize) ? autosize.minRows : void 0;
        const maxRows = isObject(autosize) ? autosize.maxRows : void 0;
        const textareaStyle2 = calcTextareaHeight(textarea.value, minRows, maxRows);
        textareaCalcStyle.value = {
          overflowY: "hidden",
          ...textareaStyle2
        };
        nextTick(() => {
          textarea.value.offsetHeight;
          textareaCalcStyle.value = textareaStyle2;
        });
      } else textareaCalcStyle.value = { minHeight: calcTextareaHeight(textarea.value).minHeight };
    };
    const createOnceInitResize = (resizeTextarea2) => {
      let isInit = false;
      return () => {
        var _a;
        if (isInit || !props.autosize) {
          if (props.resize !== "none") setTimeout(() => {
            var _a2;
            textareaHeight.value = (_a2 = textarea.value) == null ? void 0 : _a2.style.height;
          });
          return;
        }
        if (!(((_a = textarea.value) == null ? void 0 : _a.offsetParent) === null)) {
          setTimeout(resizeTextarea2);
          isInit = true;
        }
      };
    };
    const onceInitSizeTextarea = createOnceInitResize(resizeTextarea);
    const setNativeInputValue = () => {
      const input2 = _ref.value;
      const formatterValue = props.formatter ? props.formatter(nativeInputValue.value) : nativeInputValue.value;
      if (!input2 || input2.value === formatterValue || props.type === "file") return;
      input2.value = formatterValue;
    };
    const formatValue = (value) => {
      const { trim, number } = props.modelModifiers;
      if (trim) value = value.trim();
      if (number) value = `${looseToNumber(value)}`;
      if (props.formatter && props.parser) value = props.parser(value);
      return value;
    };
    const handleInput = async (event) => {
      if (isComposing.value) return;
      const { lazy } = props.modelModifiers;
      let { value } = event.target;
      let shouldForceNativeUpdate = false;
      if (lazy) {
        emit(INPUT_EVENT, value);
        return;
      }
      value = formatValue(value);
      if (props.countGraphemes && maxlength.value != null) {
        const limit = Number(maxlength.value);
        const graphemes = props.countGraphemes(value);
        const saveGraphemes = props.countGraphemes(saveValue.value);
        if (graphemes > limit && graphemes > saveGraphemes) if (saveGraphemes > limit) {
          value = saveValue.value;
          shouldForceNativeUpdate = true;
        } else {
          const prevValue = saveValue.value;
          const nextValue = value;
          let prefixLen = 0;
          while (prefixLen < prevValue.length && prefixLen < nextValue.length && prevValue[prefixLen] === nextValue[prefixLen]) prefixLen++;
          let prevSuffixIndex = prevValue.length;
          let nextSuffixIndex = nextValue.length;
          while (prevSuffixIndex > prefixLen && nextSuffixIndex > prefixLen && prevValue[prevSuffixIndex - 1] === nextValue[nextSuffixIndex - 1]) {
            prevSuffixIndex--;
            nextSuffixIndex--;
          }
          const before = nextValue.slice(0, prefixLen);
          const removed = prevValue.slice(prefixLen, prevSuffixIndex);
          const inserted = nextValue.slice(prefixLen, nextSuffixIndex);
          const after = nextValue.slice(nextSuffixIndex);
          const baseCount = saveGraphemes - props.countGraphemes(removed);
          const availableInserted = Math.max(0, limit - baseCount);
          let acceptedInserted = "";
          if (availableInserted > 0) if (typeof Intl !== "undefined" && "Segmenter" in Intl) {
            const segmenter = new Intl.Segmenter(void 0, { granularity: "grapheme" });
            for (const { segment } of segmenter.segment(inserted)) {
              const candidate = acceptedInserted + segment;
              if (props.countGraphemes(candidate) > availableInserted) break;
              acceptedInserted = candidate;
            }
          } else for (const char of Array.from(inserted)) {
            const candidate = acceptedInserted + char;
            if (props.countGraphemes(candidate) > availableInserted) break;
            acceptedInserted = candidate;
          }
          value = before + acceptedInserted + after;
          shouldForceNativeUpdate = true;
        }
      }
      if (String(value) === nativeInputValue.value) {
        if (props.formatter || shouldForceNativeUpdate) {
          const target = event.target;
          const blockedValue = target.value;
          const selectionStart = target.selectionStart;
          const selectionEnd = target.selectionEnd;
          setNativeInputValue();
          if (shouldForceNativeUpdate && _ref.value && selectionStart != null && selectionEnd != null) {
            const restoredValue = _ref.value.value;
            const afterTxt = blockedValue.slice(Math.max(0, selectionEnd));
            let caretPos = Math.min(selectionStart, restoredValue.length);
            if (afterTxt && restoredValue.endsWith(afterTxt)) caretPos = restoredValue.length - afterTxt.length;
            _ref.value.setSelectionRange(caretPos, caretPos);
          }
        }
        return;
      }
      saveValue.value = value;
      recordCursor();
      emit(UPDATE_MODEL_EVENT, value);
      emit(INPUT_EVENT, value);
      await nextTick();
      if (props.formatter && props.parser || !hasModelModifiers.value) setNativeInputValue();
      setCursor();
    };
    const handleChange = async (event) => {
      let { value } = event.target;
      value = formatValue(value);
      if (props.modelModifiers.lazy) emit(UPDATE_MODEL_EVENT, value);
      emit(CHANGE_EVENT, value, event);
      await nextTick();
      setNativeInputValue();
    };
    const { isComposing, handleCompositionStart, handleCompositionUpdate, handleCompositionEnd } = useComposition({
      emit,
      afterComposition: handleInput
    });
    const handlePasswordVisible = () => {
      passwordVisible.value = !passwordVisible.value;
    };
    const focus = () => {
      var _a;
      return (_a = _ref.value) == null ? void 0 : _a.focus();
    };
    const blur = () => {
      var _a;
      return (_a = _ref.value) == null ? void 0 : _a.blur();
    };
    const handleMouseLeave = (evt) => {
      hovering.value = false;
      emit("mouseleave", evt);
    };
    const handleMouseEnter = (evt) => {
      hovering.value = true;
      emit("mouseenter", evt);
    };
    const handleKeydown = (evt) => {
      emit("keydown", evt);
    };
    const select = () => {
      var _a;
      (_a = _ref.value) == null ? void 0 : _a.select();
    };
    const clear = (evt) => {
      emit(UPDATE_MODEL_EVENT, "");
      emit(CHANGE_EVENT, "");
      emit("clear", evt);
      emit(INPUT_EVENT, "");
    };
    watch(() => props.modelValue, () => {
      var _a;
      nextTick(() => {
        resizeTextarea();
        if (props.autosize) textareaHeight.value = void 0;
      });
      if (props.validateEvent) (_a = elFormItem == null ? void 0 : elFormItem.validate) == null ? void 0 : _a.call(elFormItem, "change").catch(NOOP);
    });
    watch(() => nativeInputValue.value, (val) => {
      saveValue.value = val;
    }, { immediate: true });
    watch(nativeInputValue, (newValue) => {
      if (!_ref.value) return;
      const { trim, number } = props.modelModifiers;
      const elValue = _ref.value.value;
      const displayValue = (number || props.type === "number") && !/^0\d/.test(elValue) ? `${looseToNumber(elValue)}` : elValue;
      if (displayValue === newValue) return;
      if (document.activeElement === _ref.value && _ref.value.type !== "range") {
        if (trim && displayValue.trim() === newValue) return;
      }
      setNativeInputValue();
    });
    watch(() => props.type, async () => {
      await nextTick();
      setNativeInputValue();
      resizeTextarea();
    });
    onMounted(() => {
      if (!props.formatter && props.parser) debugWarn(COMPONENT_NAME, "If you set the parser, you also need to set the formatter.");
      setNativeInputValue();
      nextTick(resizeTextarea);
    });
    onBeforeUnmount(() => {
      rAFId && cAF(rAFId);
    });
    __expose({
      /** @description HTML input element */
      input,
      /** @description HTML textarea element */
      textarea,
      /** @description HTML element, input or textarea */
      ref: _ref,
      /** @description style of textarea. */
      textareaStyle,
      /** @description from props (used on unit test) */
      autosize: toRef(props, "autosize"),
      /** @description is input composing */
      isComposing,
      /** @description whether the password is visible */
      passwordVisible,
      /** @description HTML input element native method */
      focus,
      /** @description HTML input element native method */
      blur,
      /** @description HTML input element native method */
      select,
      /** @description clear input value */
      clear,
      /** @description resize textarea. */
      resizeTextarea
    });
    return (_ctx, _cache) => {
      return openBlock(), createElementBlock("div", {
        class: normalizeClass([containerKls.value, {
          [unref(nsInput).bm("group", "append")]: _ctx.$slots.append,
          [unref(nsInput).bm("group", "prepend")]: _ctx.$slots.prepend
        }]),
        style: normalizeStyle(containerStyle.value),
        onMouseenter: handleMouseEnter,
        onMouseleave: handleMouseLeave
      }, [createCommentVNode(" input "), __props.type !== "textarea" ? (openBlock(), createElementBlock(Fragment, { key: 0 }, [
        createCommentVNode(" prepend slot "),
        _ctx.$slots.prepend ? (openBlock(), createElementBlock("div", {
          key: 0,
          class: normalizeClass(unref(nsInput).be("group", "prepend"))
        }, [renderSlot(_ctx.$slots, "prepend")], 2)) : createCommentVNode("v-if", true),
        createElementVNode("div", {
          ref_key: "wrapperRef",
          ref: wrapperRef,
          class: normalizeClass(wrapperKls.value)
        }, [
          createCommentVNode(" prefix slot "),
          _ctx.$slots.prefix || __props.prefixIcon ? (openBlock(), createElementBlock("span", {
            key: 0,
            class: normalizeClass(unref(nsInput).e("prefix"))
          }, [createElementVNode("span", { class: normalizeClass(unref(nsInput).e("prefix-inner")) }, [renderSlot(_ctx.$slots, "prefix"), __props.prefixIcon ? (openBlock(), createBlock(unref(ElIcon), {
            key: 0,
            class: normalizeClass(unref(nsInput).e("icon"))
          }, {
            default: withCtx(() => [(openBlock(), createBlock(resolveDynamicComponent(__props.prefixIcon)))]),
            _: 1
          }, 8, ["class"])) : createCommentVNode("v-if", true)], 2)], 2)) : createCommentVNode("v-if", true),
          createElementVNode("input", mergeProps({
            id: unref(inputId),
            ref_key: "input",
            ref: input,
            class: unref(nsInput).e("inner")
          }, unref(attrs), {
            name: __props.name,
            minlength: __props.countGraphemes ? void 0 : __props.minlength,
            maxlength: __props.countGraphemes ? void 0 : maxlength.value,
            type: __props.showPassword ? passwordVisible.value ? "text" : "password" : __props.type,
            disabled: unref(inputDisabled),
            readonly: __props.readonly,
            autocomplete: __props.autocomplete,
            tabindex: __props.tabindex,
            "aria-label": __props.ariaLabel,
            placeholder: __props.placeholder,
            style: __props.inputStyle,
            form: __props.form,
            autofocus: __props.autofocus,
            role: __props.containerRole,
            inputmode: __props.inputmode,
            onCompositionstart: _cache[0] || (_cache[0] = (...args) => unref(handleCompositionStart) && unref(handleCompositionStart)(...args)),
            onCompositionupdate: _cache[1] || (_cache[1] = (...args) => unref(handleCompositionUpdate) && unref(handleCompositionUpdate)(...args)),
            onCompositionend: _cache[2] || (_cache[2] = (...args) => unref(handleCompositionEnd) && unref(handleCompositionEnd)(...args)),
            onInput: handleInput,
            onChange: handleChange,
            onKeydown: handleKeydown
          }), null, 16, _hoisted_1),
          createCommentVNode(" suffix slot "),
          suffixVisible.value ? (openBlock(), createElementBlock("span", {
            key: 1,
            class: normalizeClass(unref(nsInput).e("suffix"))
          }, [createElementVNode("span", { class: normalizeClass(unref(nsInput).e("suffix-inner")) }, [
            renderClear.value ? (openBlock(), createBlock(unref(ElIcon), {
              key: 0,
              class: normalizeClass([unref(nsInput).e("icon"), unref(nsInput).e("clear")]),
              style: normalizeStyle({ visibility: showClear.value ? "visible" : "hidden" }),
              onMousedown: withModifiers(unref(NOOP), ["prevent"]),
              onClick: clear
            }, {
              default: withCtx(() => [(openBlock(), createBlock(resolveDynamicComponent(__props.clearIcon)))]),
              _: 1
            }, 8, [
              "class",
              "style",
              "onMousedown"
            ])) : createCommentVNode("v-if", true),
            !showClear.value || !showPwdVisible.value || !isWordLimitVisible.value ? (openBlock(), createElementBlock(Fragment, { key: 1 }, [renderSlot(_ctx.$slots, "suffix"), __props.suffixIcon ? (openBlock(), createBlock(unref(ElIcon), {
              key: 0,
              class: normalizeClass(unref(nsInput).e("icon"))
            }, {
              default: withCtx(() => [(openBlock(), createBlock(resolveDynamicComponent(__props.suffixIcon)))]),
              _: 1
            }, 8, ["class"])) : createCommentVNode("v-if", true)], 64)) : createCommentVNode("v-if", true),
            showPwdVisible.value ? (openBlock(), createBlock(unref(ElIcon), {
              key: 2,
              class: normalizeClass([unref(nsInput).e("icon"), unref(nsInput).e("password")]),
              onClick: handlePasswordVisible,
              onMousedown: withModifiers(unref(NOOP), ["prevent"]),
              onMouseup: withModifiers(unref(NOOP), ["prevent"])
            }, {
              default: withCtx(() => [renderSlot(_ctx.$slots, "password-icon", { visible: passwordVisible.value }, () => [(openBlock(), createBlock(resolveDynamicComponent(passwordIcon.value)))])]),
              _: 3
            }, 8, [
              "class",
              "onMousedown",
              "onMouseup"
            ])) : createCommentVNode("v-if", true),
            isWordLimitVisible.value ? (openBlock(), createElementBlock("span", {
              key: 3,
              class: normalizeClass([unref(nsInput).e("count"), unref(nsInput).is("outside", __props.wordLimitPosition === "outside")])
            }, [createElementVNode("span", { class: normalizeClass(unref(nsInput).e("count-inner")) }, toDisplayString(textLength.value) + " / " + toDisplayString(maxlength.value), 3)], 2)) : createCommentVNode("v-if", true),
            validateState.value && validateIcon.value && needStatusIcon.value ? (openBlock(), createBlock(unref(ElIcon), {
              key: 4,
              class: normalizeClass([
                unref(nsInput).e("icon"),
                unref(nsInput).e("validateIcon"),
                unref(nsInput).is("loading", validateState.value === "validating")
              ])
            }, {
              default: withCtx(() => [(openBlock(), createBlock(resolveDynamicComponent(validateIcon.value)))]),
              _: 1
            }, 8, ["class"])) : createCommentVNode("v-if", true)
          ], 2)], 2)) : createCommentVNode("v-if", true)
        ], 2),
        createCommentVNode(" append slot "),
        _ctx.$slots.append ? (openBlock(), createElementBlock("div", {
          key: 1,
          class: normalizeClass(unref(nsInput).be("group", "append"))
        }, [renderSlot(_ctx.$slots, "append")], 2)) : createCommentVNode("v-if", true)
      ], 64)) : (openBlock(), createElementBlock(Fragment, { key: 1 }, [
        createCommentVNode(" textarea "),
        createElementVNode("textarea", mergeProps({
          id: unref(inputId),
          ref_key: "textarea",
          ref: textarea,
          class: [
            unref(nsTextarea).e("inner"),
            unref(nsInput).is("focus", unref(isFocused)),
            unref(nsTextarea).is("clearable", __props.clearable)
          ]
        }, unref(attrs), {
          name: __props.name,
          minlength: __props.countGraphemes ? void 0 : __props.minlength,
          maxlength: __props.countGraphemes ? void 0 : maxlength.value,
          tabindex: __props.tabindex,
          disabled: unref(inputDisabled),
          readonly: __props.readonly,
          autocomplete: __props.autocomplete,
          style: textareaStyle.value,
          "aria-label": __props.ariaLabel,
          placeholder: __props.placeholder,
          form: __props.form,
          autofocus: __props.autofocus,
          rows: __props.rows,
          role: __props.containerRole,
          inputmode: __props.inputmode,
          onCompositionstart: _cache[3] || (_cache[3] = (...args) => unref(handleCompositionStart) && unref(handleCompositionStart)(...args)),
          onCompositionupdate: _cache[4] || (_cache[4] = (...args) => unref(handleCompositionUpdate) && unref(handleCompositionUpdate)(...args)),
          onCompositionend: _cache[5] || (_cache[5] = (...args) => unref(handleCompositionEnd) && unref(handleCompositionEnd)(...args)),
          onInput: handleInput,
          onFocus: _cache[6] || (_cache[6] = (...args) => unref(handleFocus) && unref(handleFocus)(...args)),
          onBlur: _cache[7] || (_cache[7] = (...args) => unref(handleBlur) && unref(handleBlur)(...args)),
          onChange: handleChange,
          onKeydown: handleKeydown
        }), null, 16, _hoisted_2),
        showClear.value ? (openBlock(), createBlock(unref(ElIcon), {
          key: 0,
          class: normalizeClass([unref(nsTextarea).e("icon"), unref(nsTextarea).e("clear")]),
          style: normalizeStyle(clearIconStyle.value),
          onMousedown: withModifiers(unref(NOOP), ["prevent"]),
          onClick: clear
        }, {
          default: withCtx(() => [(openBlock(), createBlock(resolveDynamicComponent(__props.clearIcon)))]),
          _: 1
        }, 8, [
          "class",
          "style",
          "onMousedown"
        ])) : createCommentVNode("v-if", true),
        isWordLimitVisible.value ? (openBlock(), createElementBlock("span", {
          key: 1,
          style: normalizeStyle(countStyle.value),
          class: normalizeClass([unref(nsInput).e("count"), unref(nsInput).is("outside", __props.wordLimitPosition === "outside")])
        }, toDisplayString(textLength.value) + " / " + toDisplayString(maxlength.value), 7)) : createCommentVNode("v-if", true)
      ], 64))], 38);
    };
  }
});
var input_default = input_vue_vue_type_script_setup_true_lang_default;
const ElInput = withInstall(input_default);
export {
  ElInput,
  ElInput as default,
  inputEmits,
  inputProps
};
//# sourceMappingURL=index-DUQ77wOq.js.map
