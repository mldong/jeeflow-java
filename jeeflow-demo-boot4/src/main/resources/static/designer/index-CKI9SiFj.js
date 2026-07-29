import { c as classNames, P as PropTypes, b as _objectSpread2, K as findDOMNode, _ as _extends } from "./index-IfV1sCLI.js";
import { defineComponent, shallowRef, createVNode, ref, watch, computed, reactive, getCurrentInstance, onMounted, onUpdated, onUnmounted } from "vue";
import { I as Icon } from "./AntdIcon-DuXnwIrR.js";
import { i as index } from "./ResizeObserver.es-C80vHMkG.js";
function getStatusClassNames(prefixCls, status, hasFeedback) {
  return classNames({
    [`${prefixCls}-status-success`]: status === "success",
    [`${prefixCls}-status-warning`]: status === "warning",
    [`${prefixCls}-status-error`]: status === "error",
    [`${prefixCls}-status-validating`]: status === "validating",
    [`${prefixCls}-has-feedback`]: hasFeedback
  });
}
const getMergedStatus = (contextStatus, customStatus) => customStatus || contextStatus;
var __rest$1 = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
const BaseInputInner = defineComponent({
  compatConfig: {
    MODE: 3
  },
  // inheritAttrs: false,
  props: {
    disabled: PropTypes.looseBool,
    type: PropTypes.string,
    value: PropTypes.any,
    tag: {
      type: String,
      default: "input"
    },
    size: PropTypes.string,
    onChange: Function,
    onInput: Function,
    onBlur: Function,
    onFocus: Function,
    onKeydown: Function,
    onCompositionstart: Function,
    onCompositionend: Function,
    onKeyup: Function,
    onPaste: Function,
    onMousedown: Function
  },
  emits: ["change", "input", "blur", "keydown", "focus", "compositionstart", "compositionend", "keyup", "paste", "mousedown"],
  setup(props, _ref) {
    let {
      expose
    } = _ref;
    const inputRef = shallowRef(null);
    const focus = () => {
      if (inputRef.value) {
        inputRef.value.focus();
      }
    };
    const blur = () => {
      if (inputRef.value) {
        inputRef.value.blur();
      }
    };
    const setSelectionRange = (start, end, direction) => {
      var _a;
      (_a = inputRef.value) === null || _a === void 0 ? void 0 : _a.setSelectionRange(start, end, direction);
    };
    const select = () => {
      var _a;
      (_a = inputRef.value) === null || _a === void 0 ? void 0 : _a.select();
    };
    expose({
      focus,
      blur,
      input: inputRef,
      setSelectionRange,
      select,
      getSelectionStart: () => {
        var _a;
        return (_a = inputRef.value) === null || _a === void 0 ? void 0 : _a.selectionStart;
      },
      getSelectionEnd: () => {
        var _a;
        return (_a = inputRef.value) === null || _a === void 0 ? void 0 : _a.selectionEnd;
      },
      getScrollTop: () => {
        var _a;
        return (_a = inputRef.value) === null || _a === void 0 ? void 0 : _a.scrollTop;
      }
    });
    return () => {
      const {
        tag: Tag,
        value
      } = props, restProps = __rest$1(props, ["tag", "value"]);
      return createVNode(Tag, _objectSpread2(_objectSpread2({}, restProps), {}, {
        "ref": inputRef,
        "value": value
      }), null);
    };
  }
});
function styleObjectToString(style) {
  return Object.keys(style).reduce((acc, name) => {
    const styleValue = style[name];
    if (typeof styleValue === "undefined" || styleValue === null) {
      return acc;
    }
    acc += `${name}: ${style[name]};`;
    return acc;
  }, "");
}
var __rest = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
const BaseInput = defineComponent({
  compatConfig: {
    MODE: 3
  },
  inheritAttrs: false,
  props: {
    disabled: PropTypes.looseBool,
    type: PropTypes.string,
    value: PropTypes.any,
    lazy: PropTypes.bool.def(true),
    tag: {
      type: String,
      default: "input"
    },
    size: PropTypes.string,
    style: PropTypes.oneOfType([String, Object]),
    class: PropTypes.string
  },
  emits: ["change", "input", "blur", "keydown", "focus", "compositionstart", "compositionend", "keyup", "paste", "mousedown"],
  setup(props, _ref) {
    let {
      emit,
      attrs,
      expose
    } = _ref;
    const inputRef = shallowRef(null);
    const renderValue = ref();
    const isComposing = ref(false);
    watch([() => props.value, isComposing], () => {
      if (isComposing.value) return;
      renderValue.value = props.value;
    }, {
      immediate: true
    });
    const handleChange = (e) => {
      emit("change", e);
    };
    const onCompositionstart = (e) => {
      isComposing.value = true;
      e.target.composing = true;
      emit("compositionstart", e);
    };
    const onCompositionend = (e) => {
      isComposing.value = false;
      e.target.composing = false;
      emit("compositionend", e);
      const event = document.createEvent("HTMLEvents");
      event.initEvent("input", true, true);
      e.target.dispatchEvent(event);
      handleChange(e);
    };
    const handleInput = (e) => {
      if (isComposing.value && props.lazy) {
        renderValue.value = e.target.value;
        return;
      }
      emit("input", e);
    };
    const handleBlur = (e) => {
      emit("blur", e);
    };
    const handleFocus = (e) => {
      emit("focus", e);
    };
    const focus = () => {
      if (inputRef.value) {
        inputRef.value.focus();
      }
    };
    const blur = () => {
      if (inputRef.value) {
        inputRef.value.blur();
      }
    };
    const handleKeyDown = (e) => {
      emit("keydown", e);
    };
    const handleKeyUp = (e) => {
      emit("keyup", e);
    };
    const setSelectionRange = (start, end, direction) => {
      var _a;
      (_a = inputRef.value) === null || _a === void 0 ? void 0 : _a.setSelectionRange(start, end, direction);
    };
    const select = () => {
      var _a;
      (_a = inputRef.value) === null || _a === void 0 ? void 0 : _a.select();
    };
    expose({
      focus,
      blur,
      input: computed(() => {
        var _a;
        return (_a = inputRef.value) === null || _a === void 0 ? void 0 : _a.input;
      }),
      setSelectionRange,
      select,
      getSelectionStart: () => {
        var _a;
        return (_a = inputRef.value) === null || _a === void 0 ? void 0 : _a.getSelectionStart();
      },
      getSelectionEnd: () => {
        var _a;
        return (_a = inputRef.value) === null || _a === void 0 ? void 0 : _a.getSelectionEnd();
      },
      getScrollTop: () => {
        var _a;
        return (_a = inputRef.value) === null || _a === void 0 ? void 0 : _a.getScrollTop();
      }
    });
    const handleMousedown = (e) => {
      emit("mousedown", e);
    };
    const handlePaste = (e) => {
      emit("paste", e);
    };
    const styleString = computed(() => {
      return props.style && typeof props.style !== "string" ? styleObjectToString(props.style) : props.style;
    });
    return () => {
      const {
        style,
        lazy
      } = props, restProps = __rest(props, ["style", "lazy"]);
      return createVNode(BaseInputInner, _objectSpread2(_objectSpread2(_objectSpread2({}, restProps), attrs), {}, {
        "style": styleString.value,
        "onInput": handleInput,
        "onChange": handleChange,
        "onBlur": handleBlur,
        "onFocus": handleFocus,
        "ref": inputRef,
        "value": renderValue.value,
        "onCompositionstart": onCompositionstart,
        "onCompositionend": onCompositionend,
        "onKeyup": handleKeyUp,
        "onKeydown": handleKeyDown,
        "onPaste": handlePaste,
        "onMousedown": handleMousedown
      }), null);
    };
  }
});
var SearchOutlined$1 = { "icon": { "tag": "svg", "attrs": { "viewBox": "64 64 896 896", "focusable": "false" }, "children": [{ "tag": "path", "attrs": { "d": "M909.6 854.5L649.9 594.8C690.2 542.7 712 479 712 412c0-80.2-31.3-155.4-87.9-212.1-56.6-56.7-132-87.9-212.1-87.9s-155.5 31.3-212.1 87.9C143.2 256.5 112 331.8 112 412c0 80.1 31.3 155.5 87.9 212.1C256.5 680.8 331.8 712 412 712c67 0 130.6-21.8 182.7-62l259.7 259.6a8.2 8.2 0 0011.6 0l43.6-43.5a8.2 8.2 0 000-11.6zM570.4 570.4C528 612.7 471.8 636 412 636s-116-23.3-158.4-65.6C211.3 528 188 471.8 188 412s23.3-116.1 65.6-158.4C296 211.3 352.2 188 412 188s116.1 23.2 158.4 65.6S636 352.2 636 412s-23.3 116.1-65.6 158.4z" } }] }, "name": "search", "theme": "outlined" };
function _objectSpread(target) {
  for (var i = 1; i < arguments.length; i++) {
    var source = arguments[i] != null ? Object(arguments[i]) : {};
    var ownKeys = Object.keys(source);
    if (typeof Object.getOwnPropertySymbols === "function") {
      ownKeys = ownKeys.concat(Object.getOwnPropertySymbols(source).filter(function(sym) {
        return Object.getOwnPropertyDescriptor(source, sym).enumerable;
      }));
    }
    ownKeys.forEach(function(key) {
      _defineProperty(target, key, source[key]);
    });
  }
  return target;
}
function _defineProperty(obj, key, value) {
  if (key in obj) {
    Object.defineProperty(obj, key, { value, enumerable: true, configurable: true, writable: true });
  } else {
    obj[key] = value;
  }
  return obj;
}
var SearchOutlined = function SearchOutlined2(props, context) {
  var p = _objectSpread({}, props, context.attrs);
  return createVNode(Icon, _objectSpread({}, p, {
    "icon": SearchOutlined$1
  }), null);
};
SearchOutlined.displayName = "SearchOutlined";
SearchOutlined.inheritAttrs = false;
const ResizeObserver = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "ResizeObserver",
  props: {
    disabled: Boolean,
    onResize: Function
  },
  emits: ["resize"],
  setup(props, _ref) {
    let {
      slots
    } = _ref;
    const state = reactive({
      width: 0,
      height: 0,
      offsetHeight: 0,
      offsetWidth: 0
    });
    let currentElement = null;
    let resizeObserver = null;
    const destroyObserver = () => {
      if (resizeObserver) {
        resizeObserver.disconnect();
        resizeObserver = null;
      }
    };
    const onResize = (entries) => {
      const {
        onResize: onResize2
      } = props;
      const target = entries[0].target;
      const {
        width,
        height
      } = target.getBoundingClientRect();
      const {
        offsetWidth,
        offsetHeight
      } = target;
      const fixedWidth = Math.floor(width);
      const fixedHeight = Math.floor(height);
      if (state.width !== fixedWidth || state.height !== fixedHeight || state.offsetWidth !== offsetWidth || state.offsetHeight !== offsetHeight) {
        const size = {
          width: fixedWidth,
          height: fixedHeight,
          offsetWidth,
          offsetHeight
        };
        _extends(state, size);
        if (onResize2) {
          Promise.resolve().then(() => {
            onResize2(_extends(_extends({}, size), {
              offsetWidth,
              offsetHeight
            }), target);
          });
        }
      }
    };
    const instance = getCurrentInstance();
    const registerObserver = () => {
      const {
        disabled
      } = props;
      if (disabled) {
        destroyObserver();
        return;
      }
      const element = findDOMNode(instance);
      const elementChanged = element !== currentElement;
      if (elementChanged) {
        destroyObserver();
        currentElement = element;
      }
      if (!resizeObserver && element) {
        resizeObserver = new index(onResize);
        resizeObserver.observe(element);
      }
    };
    onMounted(() => {
      registerObserver();
    });
    onUpdated(() => {
      registerObserver();
    });
    onUnmounted(() => {
      destroyObserver();
    });
    watch(() => props.disabled, () => {
      registerObserver();
    }, {
      flush: "post"
    });
    return () => {
      var _a;
      return (_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots)[0];
    };
  }
});
export {
  BaseInput as B,
  ResizeObserver as R,
  SearchOutlined as S,
  getStatusClassNames as a,
  getMergedStatus as g
};
//# sourceMappingURL=index-CKI9SiFj.js.map
