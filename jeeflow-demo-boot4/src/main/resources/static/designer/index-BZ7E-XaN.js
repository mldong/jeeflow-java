import { _ as _extends, q as warning, P as PropTypes, b as _objectSpread2, c as classNames, r as isValidElement, D as flattenChildren, E as canUseDom, F as warningOnce, G as noteOnce, y as resetComponent, H as textEllipsis, m as merge, I as resetIcon, g as genComponentStyleHook, u as useConfigInject, C as useInjectDisabled, J as DefaultRenderEmpty, f as functionType, s as stringType, j as booleanType, k as someType } from "./index-IfV1sCLI.js";
import { defineComponent, computed, ref, createVNode, isVNode, cloneVNode, inject, provide, onUnmounted, shallowRef, watch, watchEffect, onMounted, createTextVNode, Fragment, onBeforeUnmount, isRef, reactive, toRefs, toRaw, nextTick, onUpdated, unref, toRef } from "vue";
import { T as Trigger } from "./Trigger-CpaAYR4S.js";
import { K as KeyCode } from "./KeyCode-CnYuZtHO.js";
import { c as cloneElement } from "./vnode-B1ULLVwf.js";
import { B as BaseInput, R as ResizeObserver, S as SearchOutlined, g as getMergedStatus, a as getStatusClassNames } from "./index-CKI9SiFj.js";
import { p as pickAttrs } from "./pickAttrs-BpQ0uBJu.js";
import { i as initDefaultProps, w as wrapperRaf } from "./raf-DXHxwK-q.js";
import { i as initMotion, s as supportsPassive } from "./motion-Oa66PlhN.js";
import { o as omit } from "./omit-CQMssqPV.js";
import { u as useState, g as genCompactItemStyle } from "./compact-item-CMgVrMrj.js";
import { I as Icon } from "./AntdIcon-DuXnwIrR.js";
import { L as LoadingOutlined } from "./LoadingOutlined-ZmO9GVlJ.js";
import { C as CloseOutlined } from "./CloseOutlined-nOZPpOLa.js";
import { C as CloseCircleFilled } from "./CloseCircleFilled-CzkqoH7Y.js";
import { u as useInjectFormItemContext, b as FormItemInputContext } from "./FormItemContext-CwA3WYnI.js";
import { a as getTransitionName, c as getTransitionDirection } from "./transition-AJOM0T1w.js";
import { u as useCompactItemContext } from "./Compact-rSArJkpr.js";
import { K as Keyframe } from "./motionUtil-C3pKw7LX.js";
import { d as devWarning } from "./createContext-E4gSbaZA.js";
const moveDownIn = new Keyframe("antMoveDownIn", {
  "0%": {
    transform: "translate3d(0, 100%, 0)",
    transformOrigin: "0 0",
    opacity: 0
  },
  "100%": {
    transform: "translate3d(0, 0, 0)",
    transformOrigin: "0 0",
    opacity: 1
  }
});
const moveDownOut = new Keyframe("antMoveDownOut", {
  "0%": {
    transform: "translate3d(0, 0, 0)",
    transformOrigin: "0 0",
    opacity: 1
  },
  "100%": {
    transform: "translate3d(0, 100%, 0)",
    transformOrigin: "0 0",
    opacity: 0
  }
});
const moveLeftIn = new Keyframe("antMoveLeftIn", {
  "0%": {
    transform: "translate3d(-100%, 0, 0)",
    transformOrigin: "0 0",
    opacity: 0
  },
  "100%": {
    transform: "translate3d(0, 0, 0)",
    transformOrigin: "0 0",
    opacity: 1
  }
});
const moveLeftOut = new Keyframe("antMoveLeftOut", {
  "0%": {
    transform: "translate3d(0, 0, 0)",
    transformOrigin: "0 0",
    opacity: 1
  },
  "100%": {
    transform: "translate3d(-100%, 0, 0)",
    transformOrigin: "0 0",
    opacity: 0
  }
});
const moveRightIn = new Keyframe("antMoveRightIn", {
  "0%": {
    transform: "translate3d(100%, 0, 0)",
    transformOrigin: "0 0",
    opacity: 0
  },
  "100%": {
    transform: "translate3d(0, 0, 0)",
    transformOrigin: "0 0",
    opacity: 1
  }
});
const moveRightOut = new Keyframe("antMoveRightOut", {
  "0%": {
    transform: "translate3d(0, 0, 0)",
    transformOrigin: "0 0",
    opacity: 1
  },
  "100%": {
    transform: "translate3d(100%, 0, 0)",
    transformOrigin: "0 0",
    opacity: 0
  }
});
const moveUpIn = new Keyframe("antMoveUpIn", {
  "0%": {
    transform: "translate3d(0, -100%, 0)",
    transformOrigin: "0 0",
    opacity: 0
  },
  "100%": {
    transform: "translate3d(0, 0, 0)",
    transformOrigin: "0 0",
    opacity: 1
  }
});
const moveUpOut = new Keyframe("antMoveUpOut", {
  "0%": {
    transform: "translate3d(0, 0, 0)",
    transformOrigin: "0 0",
    opacity: 1
  },
  "100%": {
    transform: "translate3d(0, -100%, 0)",
    transformOrigin: "0 0",
    opacity: 0
  }
});
const moveMotion = {
  "move-up": {
    inKeyframes: moveUpIn,
    outKeyframes: moveUpOut
  },
  "move-down": {
    inKeyframes: moveDownIn,
    outKeyframes: moveDownOut
  },
  "move-left": {
    inKeyframes: moveLeftIn,
    outKeyframes: moveLeftOut
  },
  "move-right": {
    inKeyframes: moveRightIn,
    outKeyframes: moveRightOut
  }
};
const initMoveMotion = (token, motionName) => {
  const {
    antCls
  } = token;
  const motionCls = `${antCls}-${motionName}`;
  const {
    inKeyframes,
    outKeyframes
  } = moveMotion[motionName];
  return [initMotion(motionCls, inKeyframes, outKeyframes, token.motionDurationMid), {
    [`
        ${motionCls}-enter,
        ${motionCls}-appear
      `]: {
      opacity: 0,
      animationTimingFunction: token.motionEaseOutCirc
    },
    [`${motionCls}-leave`]: {
      animationTimingFunction: token.motionEaseInOutCirc
    }
  }];
};
const slideUpIn = new Keyframe("antSlideUpIn", {
  "0%": {
    transform: "scaleY(0.8)",
    transformOrigin: "0% 0%",
    opacity: 0
  },
  "100%": {
    transform: "scaleY(1)",
    transformOrigin: "0% 0%",
    opacity: 1
  }
});
const slideUpOut = new Keyframe("antSlideUpOut", {
  "0%": {
    transform: "scaleY(1)",
    transformOrigin: "0% 0%",
    opacity: 1
  },
  "100%": {
    transform: "scaleY(0.8)",
    transformOrigin: "0% 0%",
    opacity: 0
  }
});
const slideDownIn = new Keyframe("antSlideDownIn", {
  "0%": {
    transform: "scaleY(0.8)",
    transformOrigin: "100% 100%",
    opacity: 0
  },
  "100%": {
    transform: "scaleY(1)",
    transformOrigin: "100% 100%",
    opacity: 1
  }
});
const slideDownOut = new Keyframe("antSlideDownOut", {
  "0%": {
    transform: "scaleY(1)",
    transformOrigin: "100% 100%",
    opacity: 1
  },
  "100%": {
    transform: "scaleY(0.8)",
    transformOrigin: "100% 100%",
    opacity: 0
  }
});
const slideLeftIn = new Keyframe("antSlideLeftIn", {
  "0%": {
    transform: "scaleX(0.8)",
    transformOrigin: "0% 0%",
    opacity: 0
  },
  "100%": {
    transform: "scaleX(1)",
    transformOrigin: "0% 0%",
    opacity: 1
  }
});
const slideLeftOut = new Keyframe("antSlideLeftOut", {
  "0%": {
    transform: "scaleX(1)",
    transformOrigin: "0% 0%",
    opacity: 1
  },
  "100%": {
    transform: "scaleX(0.8)",
    transformOrigin: "0% 0%",
    opacity: 0
  }
});
const slideRightIn = new Keyframe("antSlideRightIn", {
  "0%": {
    transform: "scaleX(0.8)",
    transformOrigin: "100% 0%",
    opacity: 0
  },
  "100%": {
    transform: "scaleX(1)",
    transformOrigin: "100% 0%",
    opacity: 1
  }
});
const slideRightOut = new Keyframe("antSlideRightOut", {
  "0%": {
    transform: "scaleX(1)",
    transformOrigin: "100% 0%",
    opacity: 1
  },
  "100%": {
    transform: "scaleX(0.8)",
    transformOrigin: "100% 0%",
    opacity: 0
  }
});
const slideMotion = {
  "slide-up": {
    inKeyframes: slideUpIn,
    outKeyframes: slideUpOut
  },
  "slide-down": {
    inKeyframes: slideDownIn,
    outKeyframes: slideDownOut
  },
  "slide-left": {
    inKeyframes: slideLeftIn,
    outKeyframes: slideLeftOut
  },
  "slide-right": {
    inKeyframes: slideRightIn,
    outKeyframes: slideRightOut
  }
};
const initSlideMotion = (token, motionName) => {
  const {
    antCls
  } = token;
  const motionCls = `${antCls}-${motionName}`;
  const {
    inKeyframes,
    outKeyframes
  } = slideMotion[motionName];
  return [initMotion(motionCls, inKeyframes, outKeyframes, token.motionDurationMid), {
    [`
      ${motionCls}-enter,
      ${motionCls}-appear
    `]: {
      transform: "scale(0)",
      transformOrigin: "0% 0%",
      opacity: 0,
      animationTimingFunction: token.motionEaseOutQuint
    },
    [`${motionCls}-leave`]: {
      animationTimingFunction: token.motionEaseInQuint
    }
  }];
};
function getKey(data, index) {
  const {
    key
  } = data;
  let value;
  if ("value" in data) {
    ({
      value
    } = data);
  }
  if (key !== null && key !== void 0) {
    return key;
  }
  if (value !== void 0) {
    return value;
  }
  return `rc-index-key-${index}`;
}
function fillFieldNames(fieldNames, childrenAsData) {
  const {
    label,
    value,
    options
  } = fieldNames || {};
  return {
    label: label || (childrenAsData ? "children" : "label"),
    value: value || "value",
    options: options || "options"
  };
}
function flattenOptions(options) {
  let {
    fieldNames,
    childrenAsData
  } = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
  const flattenList = [];
  const {
    label: fieldLabel,
    value: fieldValue,
    options: fieldOptions
  } = fillFieldNames(fieldNames, false);
  function dig(list, isGroupOption) {
    list.forEach((data) => {
      const label = data[fieldLabel];
      if (isGroupOption || !(fieldOptions in data)) {
        const value = data[fieldValue];
        flattenList.push({
          key: getKey(data, flattenList.length),
          groupOption: isGroupOption,
          data,
          label,
          value
        });
      } else {
        let grpLabel = label;
        if (grpLabel === void 0 && childrenAsData) {
          grpLabel = data.label;
        }
        flattenList.push({
          key: getKey(data, flattenList.length),
          group: true,
          data,
          label: grpLabel
        });
        dig(data[fieldOptions], true);
      }
    });
  }
  dig(options, false);
  return flattenList;
}
function injectPropsWithOption(option) {
  const newOption = _extends({}, option);
  if (!("props" in newOption)) {
    Object.defineProperty(newOption, "props", {
      get() {
        warning(false, "Return type is option instead of Option instance. Please read value directly instead of reading from `props`.");
        return newOption;
      }
    });
  }
  return newOption;
}
function getSeparatedContent(text, tokens) {
  if (!tokens || !tokens.length) {
    return null;
  }
  let match = false;
  function separate(str, _ref) {
    let [token, ...restTokens] = _ref;
    if (!token) {
      return [str];
    }
    const list2 = str.split(token);
    match = match || list2.length > 1;
    return list2.reduce((prevList, unitStr) => [...prevList, ...separate(unitStr, restTokens)], []).filter((unit) => unit);
  }
  const list = separate(text, tokens);
  return match ? list : null;
}
var __rest$7 = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
const getBuiltInPlacements = (dropdownMatchSelectWidth) => {
  const adjustX = dropdownMatchSelectWidth === true ? 0 : 1;
  return {
    bottomLeft: {
      points: ["tl", "bl"],
      offset: [0, 4],
      overflow: {
        adjustX,
        adjustY: 1
      }
    },
    bottomRight: {
      points: ["tr", "br"],
      offset: [0, 4],
      overflow: {
        adjustX,
        adjustY: 1
      }
    },
    topLeft: {
      points: ["bl", "tl"],
      offset: [0, -4],
      overflow: {
        adjustX,
        adjustY: 1
      }
    },
    topRight: {
      points: ["br", "tr"],
      offset: [0, -4],
      overflow: {
        adjustX,
        adjustY: 1
      }
    }
  };
};
const SelectTrigger = defineComponent({
  name: "SelectTrigger",
  inheritAttrs: false,
  props: {
    dropdownAlign: Object,
    visible: {
      type: Boolean,
      default: void 0
    },
    disabled: {
      type: Boolean,
      default: void 0
    },
    dropdownClassName: String,
    dropdownStyle: PropTypes.object,
    placement: String,
    empty: {
      type: Boolean,
      default: void 0
    },
    prefixCls: String,
    popupClassName: String,
    animation: String,
    transitionName: String,
    getPopupContainer: Function,
    dropdownRender: Function,
    containerWidth: Number,
    dropdownMatchSelectWidth: PropTypes.oneOfType([Number, Boolean]).def(true),
    popupElement: PropTypes.any,
    direction: String,
    getTriggerDOMNode: Function,
    onPopupVisibleChange: Function,
    onPopupMouseEnter: Function,
    onPopupFocusin: Function,
    onPopupFocusout: Function
  },
  setup(props2, _ref) {
    let {
      slots,
      attrs,
      expose
    } = _ref;
    const builtInPlacements = computed(() => {
      const {
        dropdownMatchSelectWidth
      } = props2;
      return getBuiltInPlacements(dropdownMatchSelectWidth);
    });
    const popupRef = ref();
    expose({
      getPopupElement: () => {
        return popupRef.value;
      }
    });
    return () => {
      const _a = _extends(_extends({}, props2), attrs), {
        empty = false
      } = _a, restProps = __rest$7(_a, ["empty"]);
      const {
        visible,
        dropdownAlign,
        prefixCls,
        popupElement,
        dropdownClassName,
        dropdownStyle,
        direction = "ltr",
        placement,
        dropdownMatchSelectWidth,
        containerWidth,
        dropdownRender,
        animation,
        transitionName,
        getPopupContainer,
        getTriggerDOMNode,
        onPopupVisibleChange,
        onPopupMouseEnter,
        onPopupFocusin,
        onPopupFocusout
      } = restProps;
      const dropdownPrefixCls = `${prefixCls}-dropdown`;
      let popupNode = popupElement;
      if (dropdownRender) {
        popupNode = dropdownRender({
          menuNode: popupElement,
          props: props2
        });
      }
      const mergedTransitionName = animation ? `${dropdownPrefixCls}-${animation}` : transitionName;
      const popupStyle = _extends({
        minWidth: `${containerWidth}px`
      }, dropdownStyle);
      if (typeof dropdownMatchSelectWidth === "number") {
        popupStyle.width = `${dropdownMatchSelectWidth}px`;
      } else if (dropdownMatchSelectWidth) {
        popupStyle.width = `${containerWidth}px`;
      }
      return createVNode(Trigger, _objectSpread2(_objectSpread2({}, props2), {}, {
        "showAction": onPopupVisibleChange ? ["click"] : [],
        "hideAction": onPopupVisibleChange ? ["click"] : [],
        "popupPlacement": placement || (direction === "rtl" ? "bottomRight" : "bottomLeft"),
        "builtinPlacements": builtInPlacements.value,
        "prefixCls": dropdownPrefixCls,
        "popupTransitionName": mergedTransitionName,
        "popupAlign": dropdownAlign,
        "popupVisible": visible,
        "getPopupContainer": getPopupContainer,
        "popupClassName": classNames(dropdownClassName, {
          [`${dropdownPrefixCls}-empty`]: empty
        }),
        "popupStyle": popupStyle,
        "getTriggerDOMNode": getTriggerDOMNode,
        "onPopupVisibleChange": onPopupVisibleChange
      }), {
        default: slots.default,
        popup: () => createVNode("div", {
          "ref": popupRef,
          "onMouseenter": onPopupMouseEnter,
          "onFocusin": onPopupFocusin,
          "onFocusout": onPopupFocusout
        }, [popupNode])
      });
    };
  }
});
const TransBtn = (props2, _ref) => {
  let {
    slots
  } = _ref;
  var _a;
  const {
    class: className,
    customizeIcon,
    customizeIconProps,
    onMousedown,
    onClick
  } = props2;
  let icon;
  if (typeof customizeIcon === "function") {
    icon = customizeIcon(customizeIconProps);
  } else {
    icon = isVNode(customizeIcon) ? cloneVNode(customizeIcon) : customizeIcon;
  }
  return createVNode("span", {
    "class": className,
    "onMousedown": (event) => {
      event.preventDefault();
      if (onMousedown) {
        onMousedown(event);
      }
    },
    "style": {
      userSelect: "none",
      WebkitUserSelect: "none"
    },
    "unselectable": "on",
    "onClick": onClick,
    "aria-hidden": true
  }, [icon !== void 0 ? icon : createVNode("span", {
    "class": className.split(/\s+/).map((cls) => `${cls}-icon`)
  }, [(_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots)])]);
};
TransBtn.inheritAttrs = false;
TransBtn.displayName = "TransBtn";
TransBtn.props = {
  class: String,
  customizeIcon: PropTypes.any,
  customizeIconProps: PropTypes.any,
  onMousedown: Function,
  onClick: Function
};
const inputProps = {
  inputRef: PropTypes.any,
  prefixCls: String,
  id: String,
  inputElement: PropTypes.VueNode,
  disabled: {
    type: Boolean,
    default: void 0
  },
  autofocus: {
    type: Boolean,
    default: void 0
  },
  autocomplete: String,
  editable: {
    type: Boolean,
    default: void 0
  },
  activeDescendantId: String,
  value: String,
  open: {
    type: Boolean,
    default: void 0
  },
  tabindex: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  /** Pass accessibility props to input */
  attrs: PropTypes.object,
  onKeydown: {
    type: Function
  },
  onMousedown: {
    type: Function
  },
  onChange: {
    type: Function
  },
  onPaste: {
    type: Function
  },
  onCompositionstart: {
    type: Function
  },
  onCompositionend: {
    type: Function
  },
  onFocus: {
    type: Function
  },
  onBlur: {
    type: Function
  }
};
const Input = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "SelectInput",
  inheritAttrs: false,
  props: inputProps,
  setup(props2) {
    let blurTimeout = null;
    const VCSelectContainerEvent = inject("VCSelectContainerEvent");
    return () => {
      var _a;
      const {
        prefixCls,
        id,
        inputElement,
        disabled,
        tabindex,
        autofocus,
        autocomplete,
        editable,
        activeDescendantId,
        value,
        onKeydown,
        onMousedown,
        onChange,
        onPaste,
        onCompositionstart,
        onCompositionend,
        onFocus,
        onBlur,
        open: open2,
        inputRef,
        attrs
      } = props2;
      let inputNode = inputElement || createVNode(BaseInput, null, null);
      const inputProps2 = inputNode.props || {};
      const {
        onKeydown: onOriginKeyDown,
        onInput: onOriginInput,
        onFocus: onOriginFocus,
        onBlur: onOriginBlur,
        onMousedown: onOriginMouseDown,
        onCompositionstart: onOriginCompositionStart,
        onCompositionend: onOriginCompositionEnd,
        style
      } = inputProps2;
      inputNode = cloneElement(inputNode, _extends(_extends(_extends(_extends(_extends({
        type: "search"
      }, inputProps2), {
        id,
        ref: inputRef,
        disabled,
        tabindex,
        lazy: false,
        autocomplete: autocomplete || "off",
        autofocus,
        class: classNames(`${prefixCls}-selection-search-input`, (_a = inputNode === null || inputNode === void 0 ? void 0 : inputNode.props) === null || _a === void 0 ? void 0 : _a.class),
        role: "combobox",
        "aria-expanded": open2,
        "aria-haspopup": "listbox",
        "aria-owns": `${id}_list`,
        "aria-autocomplete": "list",
        "aria-controls": `${id}_list`,
        "aria-activedescendant": activeDescendantId
      }), attrs), {
        value: editable ? value : "",
        readonly: !editable,
        unselectable: !editable ? "on" : null,
        style: _extends(_extends({}, style), {
          opacity: editable ? null : 0
        }),
        onKeydown: (event) => {
          onKeydown(event);
          if (onOriginKeyDown) {
            onOriginKeyDown(event);
          }
        },
        onMousedown: (event) => {
          onMousedown(event);
          if (onOriginMouseDown) {
            onOriginMouseDown(event);
          }
        },
        onInput: (event) => {
          onChange(event);
          if (onOriginInput) {
            onOriginInput(event);
          }
        },
        onCompositionstart(event) {
          onCompositionstart(event);
          if (onOriginCompositionStart) {
            onOriginCompositionStart(event);
          }
        },
        onCompositionend(event) {
          onCompositionend(event);
          if (onOriginCompositionEnd) {
            onOriginCompositionEnd(event);
          }
        },
        onPaste,
        onFocus: function() {
          clearTimeout(blurTimeout);
          onOriginFocus && onOriginFocus(arguments.length <= 0 ? void 0 : arguments[0]);
          onFocus && onFocus(arguments.length <= 0 ? void 0 : arguments[0]);
          VCSelectContainerEvent === null || VCSelectContainerEvent === void 0 ? void 0 : VCSelectContainerEvent.focus(arguments.length <= 0 ? void 0 : arguments[0]);
        },
        onBlur: function() {
          for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
            args[_key] = arguments[_key];
          }
          blurTimeout = setTimeout(() => {
            onOriginBlur && onOriginBlur(args[0]);
            onBlur && onBlur(args[0]);
            VCSelectContainerEvent === null || VCSelectContainerEvent === void 0 ? void 0 : VCSelectContainerEvent.blur(args[0]);
          }, 100);
        }
      }), inputNode.type === "textarea" ? {} : {
        type: "search"
      }), true, true);
      return inputNode;
    };
  }
});
const OverflowContextProviderKey = Symbol("OverflowContextProviderKey");
const OverflowContextProvider = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "OverflowContextProvider",
  inheritAttrs: false,
  props: {
    value: {
      type: Object
    }
  },
  setup(props2, _ref) {
    let {
      slots
    } = _ref;
    provide(OverflowContextProviderKey, computed(() => props2.value));
    return () => {
      var _a;
      return (_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots);
    };
  }
});
const useInjectOverflowContext = () => {
  return inject(OverflowContextProviderKey, computed(() => null));
};
var __rest$6 = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
const UNDEFINED = void 0;
const Item$1 = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "Item",
  props: {
    prefixCls: String,
    item: PropTypes.any,
    renderItem: Function,
    responsive: Boolean,
    itemKey: {
      type: [String, Number]
    },
    registerSize: Function,
    display: Boolean,
    order: Number,
    component: PropTypes.any,
    invalidate: Boolean
  },
  setup(props2, _ref) {
    let {
      slots,
      expose
    } = _ref;
    const mergedHidden = computed(() => props2.responsive && !props2.display);
    const itemNodeRef = ref();
    expose({
      itemNodeRef
    });
    function internalRegisterSize(width) {
      props2.registerSize(props2.itemKey, width);
    }
    onUnmounted(() => {
      internalRegisterSize(null);
    });
    return () => {
      var _a;
      const {
        prefixCls,
        invalidate,
        item,
        renderItem,
        responsive,
        registerSize,
        itemKey,
        display,
        order,
        component: Component = "div"
      } = props2, restProps = __rest$6(props2, ["prefixCls", "invalidate", "item", "renderItem", "responsive", "registerSize", "itemKey", "display", "order", "component"]);
      const children = (_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots);
      const childNode = renderItem && item !== UNDEFINED ? renderItem(item) : children;
      let overflowStyle;
      if (!invalidate) {
        overflowStyle = {
          opacity: mergedHidden.value ? 0 : 1,
          height: mergedHidden.value ? 0 : UNDEFINED,
          overflowY: mergedHidden.value ? "hidden" : UNDEFINED,
          order: responsive ? order : UNDEFINED,
          pointerEvents: mergedHidden.value ? "none" : UNDEFINED,
          position: mergedHidden.value ? "absolute" : UNDEFINED
        };
      }
      const overflowProps2 = {};
      if (mergedHidden.value) {
        overflowProps2["aria-hidden"] = true;
      }
      return createVNode(ResizeObserver, {
        "disabled": !responsive,
        "onResize": (_ref2) => {
          let {
            offsetWidth
          } = _ref2;
          internalRegisterSize(offsetWidth);
        }
      }, {
        default: () => createVNode(Component, _objectSpread2(_objectSpread2(_objectSpread2({
          "class": classNames(!invalidate && prefixCls),
          "style": overflowStyle
        }, overflowProps2), restProps), {}, {
          "ref": itemNodeRef
        }), {
          default: () => [childNode]
        })
      });
    };
  }
});
var __rest$5 = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
const RawItem = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "RawItem",
  inheritAttrs: false,
  props: {
    component: PropTypes.any,
    title: PropTypes.any,
    id: String,
    onMouseenter: {
      type: Function
    },
    onMouseleave: {
      type: Function
    },
    onClick: {
      type: Function
    },
    onKeydown: {
      type: Function
    },
    onFocus: {
      type: Function
    },
    role: String,
    tabindex: Number
  },
  setup(props2, _ref) {
    let {
      slots,
      attrs
    } = _ref;
    const context = useInjectOverflowContext();
    return () => {
      var _a;
      if (!context.value) {
        const {
          component: Component = "div"
        } = props2, restProps2 = __rest$5(props2, ["component"]);
        return createVNode(Component, _objectSpread2(_objectSpread2({}, restProps2), attrs), {
          default: () => [(_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots)]
        });
      }
      const _b = context.value, {
        className: contextClassName
      } = _b, restContext = __rest$5(_b, ["className"]);
      const {
        class: className
      } = attrs, restProps = __rest$5(attrs, ["class"]);
      return createVNode(OverflowContextProvider, {
        "value": null
      }, {
        default: () => [createVNode(Item$1, _objectSpread2(_objectSpread2(_objectSpread2({
          "class": classNames(contextClassName, className)
        }, restContext), restProps), props2), slots)]
      });
    };
  }
});
var __rest$4 = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
const RESPONSIVE = "responsive";
const INVALIDATE = "invalidate";
function defaultRenderRest(omittedItems) {
  return `+ ${omittedItems.length} ...`;
}
const overflowProps = () => {
  return {
    id: String,
    prefixCls: String,
    data: Array,
    itemKey: [String, Number, Function],
    /** Used for `responsive`. It will limit render node to avoid perf issue */
    itemWidth: {
      type: Number,
      default: 10
    },
    renderItem: Function,
    /** @private Do not use in your production. Render raw node that need wrap Item by developer self */
    renderRawItem: Function,
    maxCount: [Number, String],
    renderRest: Function,
    /** @private Do not use in your production. Render raw node that need wrap Item by developer self */
    renderRawRest: Function,
    suffix: PropTypes.any,
    component: String,
    itemComponent: PropTypes.any,
    /** @private This API may be refactor since not well design */
    onVisibleChange: Function,
    /** When set to `full`, ssr will render full items by default and remove at client side */
    ssr: String,
    onMousedown: Function,
    role: String
  };
};
const Overflow = defineComponent({
  name: "Overflow",
  inheritAttrs: false,
  props: overflowProps(),
  emits: ["visibleChange"],
  setup(props2, _ref) {
    let {
      attrs,
      emit,
      slots
    } = _ref;
    const fullySSR = computed(() => props2.ssr === "full");
    const containerWidth = shallowRef(null);
    const mergedContainerWidth = computed(() => containerWidth.value || 0);
    const itemWidths = shallowRef(/* @__PURE__ */ new Map());
    const prevRestWidth = shallowRef(0);
    const restWidth = shallowRef(0);
    const suffixWidth = shallowRef(0);
    const suffixFixedStart = shallowRef(null);
    const displayCount = shallowRef(null);
    const mergedDisplayCount = computed(() => {
      if (displayCount.value === null && fullySSR.value) {
        return Number.MAX_SAFE_INTEGER;
      }
      return displayCount.value || 0;
    });
    const restReady = shallowRef(false);
    const itemPrefixCls = computed(() => `${props2.prefixCls}-item`);
    const mergedRestWidth = computed(() => Math.max(prevRestWidth.value, restWidth.value));
    const isResponsive = computed(() => !!(props2.data.length && props2.maxCount === RESPONSIVE));
    const invalidate = computed(() => props2.maxCount === INVALIDATE);
    const showRest = computed(() => isResponsive.value || typeof props2.maxCount === "number" && props2.data.length > props2.maxCount);
    const mergedData = computed(() => {
      let items = props2.data;
      if (isResponsive.value) {
        if (containerWidth.value === null && fullySSR.value) {
          items = props2.data;
        } else {
          items = props2.data.slice(0, Math.min(props2.data.length, mergedContainerWidth.value / props2.itemWidth));
        }
      } else if (typeof props2.maxCount === "number") {
        items = props2.data.slice(0, props2.maxCount);
      }
      return items;
    });
    const omittedItems = computed(() => {
      if (isResponsive.value) {
        return props2.data.slice(mergedDisplayCount.value + 1);
      }
      return props2.data.slice(mergedData.value.length);
    });
    const getKey2 = (item, index) => {
      var _a;
      if (typeof props2.itemKey === "function") {
        return props2.itemKey(item);
      }
      return (_a = props2.itemKey && (item === null || item === void 0 ? void 0 : item[props2.itemKey])) !== null && _a !== void 0 ? _a : index;
    };
    const mergedRenderItem = computed(() => props2.renderItem || ((item) => item));
    const updateDisplayCount = (count, notReady) => {
      displayCount.value = count;
      if (!notReady) {
        restReady.value = count < props2.data.length - 1;
        emit("visibleChange", count);
      }
    };
    const onOverflowResize = (_, element) => {
      containerWidth.value = element.clientWidth;
    };
    const registerSize = (key, width) => {
      const clone = new Map(itemWidths.value);
      if (width === null) {
        clone.delete(key);
      } else {
        clone.set(key, width);
      }
      itemWidths.value = clone;
    };
    const registerOverflowSize = (_, width) => {
      prevRestWidth.value = restWidth.value;
      restWidth.value = width;
    };
    const registerSuffixSize = (_, width) => {
      suffixWidth.value = width;
    };
    const getItemWidth = (index) => {
      return itemWidths.value.get(getKey2(mergedData.value[index], index));
    };
    watch([mergedContainerWidth, itemWidths, restWidth, suffixWidth, () => props2.itemKey, mergedData], () => {
      if (mergedContainerWidth.value && mergedRestWidth.value && mergedData.value) {
        let totalWidth = suffixWidth.value;
        const len = mergedData.value.length;
        const lastIndex = len - 1;
        if (!len) {
          updateDisplayCount(0);
          suffixFixedStart.value = null;
          return;
        }
        for (let i = 0; i < len; i += 1) {
          const currentItemWidth = getItemWidth(i);
          if (currentItemWidth === void 0) {
            updateDisplayCount(i - 1, true);
            break;
          }
          totalWidth += currentItemWidth;
          if (
            // Only one means `totalWidth` is the final width
            lastIndex === 0 && totalWidth <= mergedContainerWidth.value || // Last two width will be the final width
            i === lastIndex - 1 && totalWidth + getItemWidth(lastIndex) <= mergedContainerWidth.value
          ) {
            updateDisplayCount(lastIndex);
            suffixFixedStart.value = null;
            break;
          } else if (totalWidth + mergedRestWidth.value > mergedContainerWidth.value) {
            updateDisplayCount(i - 1);
            suffixFixedStart.value = totalWidth - currentItemWidth - suffixWidth.value + restWidth.value;
            break;
          }
        }
        if (props2.suffix && getItemWidth(0) + suffixWidth.value > mergedContainerWidth.value) {
          suffixFixedStart.value = null;
        }
      }
    });
    return () => {
      const displayRest = restReady.value && !!omittedItems.value.length;
      const {
        itemComponent,
        renderRawItem,
        renderRawRest,
        renderRest,
        prefixCls = "rc-overflow",
        suffix,
        component: Component = "div",
        id,
        onMousedown
      } = props2;
      const {
        class: className,
        style
      } = attrs, restAttrs = __rest$4(attrs, ["class", "style"]);
      let suffixStyle = {};
      if (suffixFixedStart.value !== null && isResponsive.value) {
        suffixStyle = {
          position: "absolute",
          left: `${suffixFixedStart.value}px`,
          top: 0
        };
      }
      const itemSharedProps = {
        prefixCls: itemPrefixCls.value,
        responsive: isResponsive.value,
        component: itemComponent,
        invalidate: invalidate.value
      };
      const internalRenderItemNode = renderRawItem ? (item, index) => {
        const key = getKey2(item, index);
        return createVNode(OverflowContextProvider, {
          "key": key,
          "value": _extends(_extends({}, itemSharedProps), {
            order: index,
            item,
            itemKey: key,
            registerSize,
            display: index <= mergedDisplayCount.value
          })
        }, {
          default: () => [renderRawItem(item, index)]
        });
      } : (item, index) => {
        const key = getKey2(item, index);
        return createVNode(Item$1, _objectSpread2(_objectSpread2({}, itemSharedProps), {}, {
          "order": index,
          "key": key,
          "item": item,
          "renderItem": mergedRenderItem.value,
          "itemKey": key,
          "registerSize": registerSize,
          "display": index <= mergedDisplayCount.value
        }), null);
      };
      let restNode = () => null;
      const restContextProps = {
        order: displayRest ? mergedDisplayCount.value : Number.MAX_SAFE_INTEGER,
        className: `${itemPrefixCls.value} ${itemPrefixCls.value}-rest`,
        registerSize: registerOverflowSize,
        display: displayRest
      };
      if (!renderRawRest) {
        const mergedRenderRest = renderRest || defaultRenderRest;
        restNode = () => createVNode(Item$1, _objectSpread2(_objectSpread2({}, itemSharedProps), restContextProps), {
          default: () => typeof mergedRenderRest === "function" ? mergedRenderRest(omittedItems.value) : mergedRenderRest
        });
      } else if (renderRawRest) {
        restNode = () => createVNode(OverflowContextProvider, {
          "value": _extends(_extends({}, itemSharedProps), restContextProps)
        }, {
          default: () => [renderRawRest(omittedItems.value)]
        });
      }
      const overflowNode = () => {
        var _a;
        return createVNode(Component, _objectSpread2({
          "id": id,
          "class": classNames(!invalidate.value && prefixCls, className),
          "style": style,
          "onMousedown": onMousedown,
          "role": props2.role
        }, restAttrs), {
          default: () => [mergedData.value.map(internalRenderItemNode), showRest.value ? restNode() : null, suffix && createVNode(Item$1, _objectSpread2(_objectSpread2({}, itemSharedProps), {}, {
            "order": mergedDisplayCount.value,
            "class": `${itemPrefixCls.value}-suffix`,
            "registerSize": registerSuffixSize,
            "display": true,
            "style": suffixStyle
          }), {
            default: () => suffix
          }), (_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots)]
        });
      };
      return createVNode(ResizeObserver, {
        "disabled": !isResponsive.value,
        "onResize": onOverflowResize
      }, {
        default: overflowNode
      });
    };
  }
});
Overflow.Item = RawItem;
Overflow.RESPONSIVE = RESPONSIVE;
Overflow.INVALIDATE = INVALIDATE;
const TreeSelectLegacyContextPropsKey = Symbol("TreeSelectLegacyContextPropsKey");
function useInjectLegacySelectContext() {
  return inject(TreeSelectLegacyContextPropsKey, {});
}
const props$1 = {
  id: String,
  prefixCls: String,
  values: PropTypes.array,
  open: {
    type: Boolean,
    default: void 0
  },
  searchValue: String,
  inputRef: PropTypes.any,
  placeholder: PropTypes.any,
  disabled: {
    type: Boolean,
    default: void 0
  },
  mode: String,
  showSearch: {
    type: Boolean,
    default: void 0
  },
  autofocus: {
    type: Boolean,
    default: void 0
  },
  autocomplete: String,
  activeDescendantId: String,
  tabindex: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  compositionStatus: Boolean,
  removeIcon: PropTypes.any,
  choiceTransitionName: String,
  maxTagCount: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  maxTagTextLength: Number,
  maxTagPlaceholder: PropTypes.any.def(() => (omittedValues) => `+ ${omittedValues.length} ...`),
  tagRender: Function,
  onToggleOpen: {
    type: Function
  },
  onRemove: Function,
  onInputChange: Function,
  onInputPaste: Function,
  onInputKeyDown: Function,
  onInputMouseDown: Function,
  onInputCompositionStart: Function,
  onInputCompositionEnd: Function
};
const onPreventMouseDown = (event) => {
  event.preventDefault();
  event.stopPropagation();
};
const SelectSelector = defineComponent({
  name: "MultipleSelectSelector",
  inheritAttrs: false,
  props: props$1,
  setup(props2) {
    const measureRef = shallowRef();
    const inputWidth = shallowRef(0);
    const focused = shallowRef(false);
    const legacyTreeSelectContext = useInjectLegacySelectContext();
    const selectionPrefixCls = computed(() => `${props2.prefixCls}-selection`);
    const inputValue = computed(() => props2.open || props2.mode === "tags" ? props2.searchValue : "");
    const inputEditable = computed(() => props2.mode === "tags" || props2.showSearch && (props2.open || focused.value));
    const targetValue = ref("");
    watchEffect(() => {
      targetValue.value = inputValue.value;
    });
    onMounted(() => {
      watch(targetValue, () => {
        inputWidth.value = measureRef.value.scrollWidth;
      }, {
        flush: "post",
        immediate: true
      });
    });
    function defaultRenderSelector(title, content, itemDisabled, closable, onClose) {
      return createVNode("span", {
        "class": classNames(`${selectionPrefixCls.value}-item`, {
          [`${selectionPrefixCls.value}-item-disabled`]: itemDisabled
        }),
        "title": typeof title === "string" || typeof title === "number" ? title.toString() : void 0
      }, [createVNode("span", {
        "class": `${selectionPrefixCls.value}-item-content`
      }, [content]), closable && createVNode(TransBtn, {
        "class": `${selectionPrefixCls.value}-item-remove`,
        "onMousedown": onPreventMouseDown,
        "onClick": onClose,
        "customizeIcon": props2.removeIcon
      }, {
        default: () => [createTextVNode("×")]
      })]);
    }
    function customizeRenderSelector(value, content, itemDisabled, closable, onClose, option) {
      var _a;
      const onMouseDown = (e) => {
        onPreventMouseDown(e);
        props2.onToggleOpen(!open);
      };
      let originData = option;
      if (legacyTreeSelectContext.keyEntities) {
        originData = ((_a = legacyTreeSelectContext.keyEntities[value]) === null || _a === void 0 ? void 0 : _a.node) || {};
      }
      return createVNode("span", {
        "key": value,
        "onMousedown": onMouseDown
      }, [props2.tagRender({
        label: content,
        value,
        disabled: itemDisabled,
        closable,
        onClose,
        option: originData
      })]);
    }
    function renderItem(valueItem) {
      const {
        disabled: itemDisabled,
        label,
        value,
        option
      } = valueItem;
      const closable = !props2.disabled && !itemDisabled;
      let displayLabel = label;
      if (typeof props2.maxTagTextLength === "number") {
        if (typeof label === "string" || typeof label === "number") {
          const strLabel = String(displayLabel);
          if (strLabel.length > props2.maxTagTextLength) {
            displayLabel = `${strLabel.slice(0, props2.maxTagTextLength)}...`;
          }
        }
      }
      const onClose = (event) => {
        var _a;
        if (event) event.stopPropagation();
        (_a = props2.onRemove) === null || _a === void 0 ? void 0 : _a.call(props2, valueItem);
      };
      return typeof props2.tagRender === "function" ? customizeRenderSelector(value, displayLabel, itemDisabled, closable, onClose, option) : defaultRenderSelector(label, displayLabel, itemDisabled, closable, onClose);
    }
    function renderRest(omittedValues) {
      const {
        maxTagPlaceholder = (omittedValues2) => `+ ${omittedValues2.length} ...`
      } = props2;
      const content = typeof maxTagPlaceholder === "function" ? maxTagPlaceholder(omittedValues) : maxTagPlaceholder;
      return defaultRenderSelector(content, content, false);
    }
    const handleInput = (e) => {
      const composing = e.target.composing;
      targetValue.value = e.target.value;
      if (!composing) {
        props2.onInputChange(e);
      }
    };
    return () => {
      const {
        id,
        prefixCls,
        values,
        open: open2,
        inputRef,
        placeholder,
        disabled,
        autofocus,
        autocomplete,
        activeDescendantId,
        tabindex,
        compositionStatus,
        onInputPaste,
        onInputKeyDown,
        onInputMouseDown,
        onInputCompositionStart,
        onInputCompositionEnd
      } = props2;
      const inputNode = createVNode("div", {
        "class": `${selectionPrefixCls.value}-search`,
        "style": {
          width: inputWidth.value + "px"
        },
        "key": "input"
      }, [createVNode(Input, {
        "inputRef": inputRef,
        "open": open2,
        "prefixCls": prefixCls,
        "id": id,
        "inputElement": null,
        "disabled": disabled,
        "autofocus": autofocus,
        "autocomplete": autocomplete,
        "editable": inputEditable.value,
        "activeDescendantId": activeDescendantId,
        "value": targetValue.value,
        "onKeydown": onInputKeyDown,
        "onMousedown": onInputMouseDown,
        "onChange": handleInput,
        "onPaste": onInputPaste,
        "onCompositionstart": onInputCompositionStart,
        "onCompositionend": onInputCompositionEnd,
        "tabindex": tabindex,
        "attrs": pickAttrs(props2, true),
        "onFocus": () => focused.value = true,
        "onBlur": () => focused.value = false
      }, null), createVNode("span", {
        "ref": measureRef,
        "class": `${selectionPrefixCls.value}-search-mirror`,
        "aria-hidden": true
      }, [targetValue.value, createTextVNode(" ")])]);
      const selectionNode = createVNode(Overflow, {
        "prefixCls": `${selectionPrefixCls.value}-overflow`,
        "data": values,
        "renderItem": renderItem,
        "renderRest": renderRest,
        "suffix": inputNode,
        "itemKey": "key",
        "maxCount": props2.maxTagCount,
        "key": "overflow"
      }, null);
      return createVNode(Fragment, null, [selectionNode, !values.length && !inputValue.value && !compositionStatus && createVNode("span", {
        "class": `${selectionPrefixCls.value}-placeholder`
      }, [placeholder])]);
    };
  }
});
const props = {
  inputElement: PropTypes.any,
  id: String,
  prefixCls: String,
  values: PropTypes.array,
  open: {
    type: Boolean,
    default: void 0
  },
  searchValue: String,
  inputRef: PropTypes.any,
  placeholder: PropTypes.any,
  compositionStatus: {
    type: Boolean,
    default: void 0
  },
  disabled: {
    type: Boolean,
    default: void 0
  },
  mode: String,
  showSearch: {
    type: Boolean,
    default: void 0
  },
  autofocus: {
    type: Boolean,
    default: void 0
  },
  autocomplete: String,
  activeDescendantId: String,
  tabindex: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  activeValue: String,
  backfill: {
    type: Boolean,
    default: void 0
  },
  optionLabelRender: Function,
  onInputChange: Function,
  onInputPaste: Function,
  onInputKeyDown: Function,
  onInputMouseDown: Function,
  onInputCompositionStart: Function,
  onInputCompositionEnd: Function
};
const SingleSelector = defineComponent({
  name: "SingleSelector",
  setup(props2) {
    const inputChanged = shallowRef(false);
    const combobox = computed(() => props2.mode === "combobox");
    const inputEditable = computed(() => combobox.value || props2.showSearch);
    const inputValue = computed(() => {
      let inputValue2 = props2.searchValue || "";
      if (combobox.value && props2.activeValue && !inputChanged.value) {
        inputValue2 = props2.activeValue;
      }
      return inputValue2;
    });
    const legacyTreeSelectContext = useInjectLegacySelectContext();
    watch([combobox, () => props2.activeValue], () => {
      if (combobox.value) {
        inputChanged.value = false;
      }
    }, {
      immediate: true
    });
    const hasTextInput = computed(() => props2.mode !== "combobox" && !props2.open && !props2.showSearch ? false : !!inputValue.value || props2.compositionStatus);
    const title = computed(() => {
      const item = props2.values[0];
      return item && (typeof item.label === "string" || typeof item.label === "number") ? item.label.toString() : void 0;
    });
    const renderPlaceholder = () => {
      if (props2.values[0]) {
        return null;
      }
      const hiddenStyle = hasTextInput.value ? {
        visibility: "hidden"
      } : void 0;
      return createVNode("span", {
        "class": `${props2.prefixCls}-selection-placeholder`,
        "style": hiddenStyle
      }, [props2.placeholder]);
    };
    const handleInput = (e) => {
      const composing = e.target.composing;
      if (!composing) {
        inputChanged.value = true;
        props2.onInputChange(e);
      }
    };
    return () => {
      var _a, _b, _c, _d;
      const {
        inputElement,
        prefixCls,
        id,
        values,
        inputRef,
        disabled,
        autofocus,
        autocomplete,
        activeDescendantId,
        open: open2,
        tabindex,
        optionLabelRender,
        onInputKeyDown,
        onInputMouseDown,
        onInputPaste,
        onInputCompositionStart,
        onInputCompositionEnd
      } = props2;
      const item = values[0];
      let titleNode = null;
      if (item && legacyTreeSelectContext.customSlots) {
        const key = (_a = item.key) !== null && _a !== void 0 ? _a : item.value;
        const originData = ((_b = legacyTreeSelectContext.keyEntities[key]) === null || _b === void 0 ? void 0 : _b.node) || {};
        titleNode = legacyTreeSelectContext.customSlots[(_c = originData.slots) === null || _c === void 0 ? void 0 : _c.title] || legacyTreeSelectContext.customSlots.title || item.label;
        if (typeof titleNode === "function") {
          titleNode = titleNode(originData);
        }
      } else {
        titleNode = optionLabelRender && item ? optionLabelRender(item.option) : item === null || item === void 0 ? void 0 : item.label;
      }
      return createVNode(Fragment, null, [createVNode("span", {
        "class": `${prefixCls}-selection-search`
      }, [createVNode(Input, {
        "inputRef": inputRef,
        "prefixCls": prefixCls,
        "id": id,
        "open": open2,
        "inputElement": inputElement,
        "disabled": disabled,
        "autofocus": autofocus,
        "autocomplete": autocomplete,
        "editable": inputEditable.value,
        "activeDescendantId": activeDescendantId,
        "value": inputValue.value,
        "onKeydown": onInputKeyDown,
        "onMousedown": onInputMouseDown,
        "onChange": handleInput,
        "onPaste": onInputPaste,
        "onCompositionstart": onInputCompositionStart,
        "onCompositionend": onInputCompositionEnd,
        "tabindex": tabindex,
        "attrs": pickAttrs(props2, true)
      }, null)]), !combobox.value && item && !hasTextInput.value && createVNode("span", {
        "class": `${prefixCls}-selection-item`,
        "title": title.value
      }, [createVNode(Fragment, {
        "key": (_d = item.key) !== null && _d !== void 0 ? _d : item.value
      }, [titleNode])]), renderPlaceholder()]);
    };
  }
});
SingleSelector.props = props;
SingleSelector.inheritAttrs = false;
function isValidateOpenKey(currentKeyCode) {
  return ![
    // System function button
    KeyCode.ESC,
    KeyCode.SHIFT,
    KeyCode.BACKSPACE,
    KeyCode.TAB,
    KeyCode.WIN_KEY,
    KeyCode.ALT,
    KeyCode.META,
    KeyCode.WIN_KEY_RIGHT,
    KeyCode.CTRL,
    KeyCode.SEMICOLON,
    KeyCode.EQUALS,
    KeyCode.CAPS_LOCK,
    KeyCode.CONTEXT_MENU,
    // F1-F12
    KeyCode.F1,
    KeyCode.F2,
    KeyCode.F3,
    KeyCode.F4,
    KeyCode.F5,
    KeyCode.F6,
    KeyCode.F7,
    KeyCode.F8,
    KeyCode.F9,
    KeyCode.F10,
    KeyCode.F11,
    KeyCode.F12
  ].includes(currentKeyCode);
}
function useLock() {
  let duration = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : 250;
  let lock = null;
  let timeout;
  onBeforeUnmount(() => {
    clearTimeout(timeout);
  });
  function doLock(locked) {
    if (locked || lock === null) {
      lock = locked;
    }
    clearTimeout(timeout);
    timeout = setTimeout(() => {
      lock = null;
    }, duration);
  }
  return [() => lock, doLock];
}
function createRef() {
  const func = (node) => {
    func.current = node;
  };
  return func;
}
const Selector = defineComponent({
  name: "Selector",
  inheritAttrs: false,
  props: {
    id: String,
    prefixCls: String,
    showSearch: {
      type: Boolean,
      default: void 0
    },
    open: {
      type: Boolean,
      default: void 0
    },
    /** Display in the Selector value, it's not same as `value` prop */
    values: PropTypes.array,
    multiple: {
      type: Boolean,
      default: void 0
    },
    mode: String,
    searchValue: String,
    activeValue: String,
    inputElement: PropTypes.any,
    autofocus: {
      type: Boolean,
      default: void 0
    },
    activeDescendantId: String,
    tabindex: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    disabled: {
      type: Boolean,
      default: void 0
    },
    placeholder: PropTypes.any,
    removeIcon: PropTypes.any,
    // Tags
    maxTagCount: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    maxTagTextLength: Number,
    maxTagPlaceholder: PropTypes.any,
    tagRender: Function,
    optionLabelRender: Function,
    /** Check if `tokenSeparators` contains `\n` or `\r\n` */
    tokenWithEnter: {
      type: Boolean,
      default: void 0
    },
    // Motion
    choiceTransitionName: String,
    onToggleOpen: {
      type: Function
    },
    /** `onSearch` returns go next step boolean to check if need do toggle open */
    onSearch: Function,
    onSearchSubmit: Function,
    onRemove: Function,
    onInputKeyDown: {
      type: Function
    },
    /**
     * @private get real dom for trigger align.
     * This may be removed after React provides replacement of `findDOMNode`
     */
    domRef: Function
  },
  setup(props2, _ref) {
    let {
      expose
    } = _ref;
    const inputRef = createRef();
    const compositionStatus = ref(false);
    const [getInputMouseDown, setInputMouseDown] = useLock(0);
    const onInternalInputKeyDown = (event) => {
      const {
        which
      } = event;
      if (which === KeyCode.UP || which === KeyCode.DOWN) {
        event.preventDefault();
      }
      if (props2.onInputKeyDown) {
        props2.onInputKeyDown(event);
      }
      if (which === KeyCode.ENTER && props2.mode === "tags" && !compositionStatus.value && !props2.open) {
        props2.onSearchSubmit(event.target.value);
      }
      if (isValidateOpenKey(which)) {
        props2.onToggleOpen(true);
      }
    };
    const onInternalInputMouseDown = () => {
      setInputMouseDown(true);
    };
    let pastedText = null;
    const triggerOnSearch = (value) => {
      if (props2.onSearch(value, true, compositionStatus.value) !== false) {
        props2.onToggleOpen(true);
      }
    };
    const onInputCompositionStart = () => {
      compositionStatus.value = true;
    };
    const onInputCompositionEnd = (e) => {
      compositionStatus.value = false;
      if (props2.mode !== "combobox") {
        triggerOnSearch(e.target.value);
      }
    };
    const onInputChange = (event) => {
      let {
        target: {
          value
        }
      } = event;
      if (props2.tokenWithEnter && pastedText && /[\r\n]/.test(pastedText)) {
        const replacedText = pastedText.replace(/[\r\n]+$/, "").replace(/\r\n/g, " ").replace(/[\r\n]/g, " ");
        value = value.replace(replacedText, pastedText);
      }
      pastedText = null;
      triggerOnSearch(value);
    };
    const onInputPaste = (e) => {
      const {
        clipboardData
      } = e;
      const value = clipboardData.getData("text");
      pastedText = value;
    };
    const onClick = (_ref2) => {
      let {
        target
      } = _ref2;
      if (target !== inputRef.current) {
        const isIE = document.body.style.msTouchAction !== void 0;
        if (isIE) {
          setTimeout(() => {
            inputRef.current.focus();
          });
        } else {
          inputRef.current.focus();
        }
      }
    };
    const onMousedown = (event) => {
      const inputMouseDown = getInputMouseDown();
      if (event.target !== inputRef.current && !inputMouseDown) {
        event.preventDefault();
      }
      if (props2.mode !== "combobox" && (!props2.showSearch || !inputMouseDown) || !props2.open) {
        if (props2.open) {
          props2.onSearch("", true, false);
        }
        props2.onToggleOpen();
      }
    };
    expose({
      focus: () => {
        inputRef.current.focus();
      },
      blur: () => {
        inputRef.current.blur();
      }
    });
    return () => {
      const {
        prefixCls,
        domRef,
        mode
      } = props2;
      const sharedProps = {
        inputRef,
        onInputKeyDown: onInternalInputKeyDown,
        onInputMouseDown: onInternalInputMouseDown,
        onInputChange,
        onInputPaste,
        compositionStatus: compositionStatus.value,
        onInputCompositionStart,
        onInputCompositionEnd
      };
      const selectNode = mode === "multiple" || mode === "tags" ? createVNode(SelectSelector, _objectSpread2(_objectSpread2({}, props2), sharedProps), null) : createVNode(SingleSelector, _objectSpread2(_objectSpread2({}, props2), sharedProps), null);
      return createVNode("div", {
        "ref": domRef,
        "class": `${prefixCls}-selector`,
        "onClick": onClick,
        "onMousedown": onMousedown
      }, [selectNode]);
    };
  }
});
function useSelectTriggerControl(refs, open2, triggerOpen) {
  function onGlobalMouseDown(event) {
    var _a, _b, _c;
    let target = event.target;
    if (target.shadowRoot && event.composed) {
      target = event.composedPath()[0] || target;
    }
    const elements = [(_a = refs[0]) === null || _a === void 0 ? void 0 : _a.value, (_c = (_b = refs[1]) === null || _b === void 0 ? void 0 : _b.value) === null || _c === void 0 ? void 0 : _c.getPopupElement()];
    if (open2.value && elements.every((element) => element && !element.contains(target) && element !== target)) {
      triggerOpen(false);
    }
  }
  onMounted(() => {
    window.addEventListener("mousedown", onGlobalMouseDown);
  });
  onBeforeUnmount(() => {
    window.removeEventListener("mousedown", onGlobalMouseDown);
  });
}
function useDelayReset() {
  let timeout = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : 10;
  const bool = shallowRef(false);
  let delay;
  const cancelLatest = () => {
    clearTimeout(delay);
  };
  onMounted(() => {
    cancelLatest();
  });
  const delaySetBool = (value, callback) => {
    cancelLatest();
    delay = setTimeout(() => {
      bool.value = value;
      if (callback) {
        callback();
      }
    }, timeout);
  };
  return [bool, delaySetBool, cancelLatest];
}
const BaseSelectContextKey = Symbol("BaseSelectContextKey");
function useProvideBaseSelectProps(props2) {
  return provide(BaseSelectContextKey, props2);
}
function useBaseProps() {
  return inject(BaseSelectContextKey, {});
}
const isMobile = () => {
  if (typeof navigator === "undefined" || typeof window === "undefined") {
    return false;
  }
  const agent = navigator.userAgent || navigator.vendor || window.opera;
  return /(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino|android|ipad|playbook|silk/i.test(agent) || /1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw-(n|u)|c55\/|capi|ccwa|cdm-|cell|chtm|cldc|cmd-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc-s|devi|dica|dmob|do(c|p)o|ds(12|-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(-|_)|g1 u|g560|gene|gf-5|g-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd-(m|p|t)|hei-|hi(pt|ta)|hp( i|ip)|hs-c|ht(c(-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i-(20|go|ma)|i230|iac( |-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|-[a-w])|libw|lynx|m1-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|-([1-8]|c))|phil|pire|pl(ay|uc)|pn-2|po(ck|rt|se)|prox|psio|pt-g|qa-a|qc(07|12|21|32|60|-[2-7]|i-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h-|oo|p-)|sdk\/|se(c(-|0|1)|47|mc|nd|ri)|sgh-|shar|sie(-|m)|sk-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h-|v-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl-|tdg-|tel(i|m)|tim-|t-mo|to(pl|sh)|ts(70|m-|m3|m5)|tx-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas-|your|zeto|zte-/i.test(agent === null || agent === void 0 ? void 0 : agent.substring(0, 4));
};
function toReactive(objectRef) {
  if (!isRef(objectRef)) return reactive(objectRef);
  const proxy = new Proxy({}, {
    get(_, p, receiver) {
      return Reflect.get(objectRef.value, p, receiver);
    },
    set(_, p, value) {
      objectRef.value[p] = value;
      return true;
    },
    deleteProperty(_, p) {
      return Reflect.deleteProperty(objectRef.value, p);
    },
    has(_, p) {
      return Reflect.has(objectRef.value, p);
    },
    ownKeys() {
      return Object.keys(objectRef.value);
    },
    getOwnPropertyDescriptor() {
      return {
        enumerable: true,
        configurable: true
      };
    }
  });
  return reactive(proxy);
}
var __rest$3 = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
const DEFAULT_OMIT_PROPS = ["value", "onChange", "removeIcon", "placeholder", "autofocus", "maxTagCount", "maxTagTextLength", "maxTagPlaceholder", "choiceTransitionName", "onInputKeyDown", "onPopupScroll", "tabindex", "OptionList", "notFoundContent"];
const baseSelectPrivateProps = () => {
  return {
    prefixCls: String,
    id: String,
    omitDomProps: Array,
    // >>> Value
    displayValues: Array,
    onDisplayValuesChange: Function,
    // >>> Active
    /** Current dropdown list active item string value */
    activeValue: String,
    /** Link search input with target element */
    activeDescendantId: String,
    onActiveValueChange: Function,
    // >>> Search
    searchValue: String,
    /** Trigger onSearch, return false to prevent trigger open event */
    onSearch: Function,
    /** Trigger when search text match the `tokenSeparators`. Will provide split content */
    onSearchSplit: Function,
    maxLength: Number,
    OptionList: PropTypes.any,
    /** Tell if provided `options` is empty */
    emptyOptions: Boolean
  };
};
const baseSelectPropsWithoutPrivate = () => {
  return {
    showSearch: {
      type: Boolean,
      default: void 0
    },
    tagRender: {
      type: Function
    },
    optionLabelRender: {
      type: Function
    },
    direction: {
      type: String
    },
    // MISC
    tabindex: Number,
    autofocus: Boolean,
    notFoundContent: PropTypes.any,
    placeholder: PropTypes.any,
    onClear: Function,
    choiceTransitionName: String,
    // >>> Mode
    mode: String,
    // >>> Status
    disabled: {
      type: Boolean,
      default: void 0
    },
    loading: {
      type: Boolean,
      default: void 0
    },
    // >>> Open
    open: {
      type: Boolean,
      default: void 0
    },
    defaultOpen: {
      type: Boolean,
      default: void 0
    },
    onDropdownVisibleChange: {
      type: Function
    },
    // >>> Customize Input
    /** @private Internal usage. Do not use in your production. */
    getInputElement: {
      type: Function
    },
    /** @private Internal usage. Do not use in your production. */
    getRawInputElement: {
      type: Function
    },
    // >>> Selector
    maxTagTextLength: Number,
    maxTagCount: {
      type: [String, Number]
    },
    maxTagPlaceholder: PropTypes.any,
    // >>> Search
    tokenSeparators: {
      type: Array
    },
    // >>> Icons
    allowClear: {
      type: Boolean,
      default: void 0
    },
    showArrow: {
      type: Boolean,
      default: void 0
    },
    inputIcon: PropTypes.any,
    /** Clear all icon */
    clearIcon: PropTypes.any,
    /** Selector remove icon */
    removeIcon: PropTypes.any,
    // >>> Dropdown
    animation: String,
    transitionName: String,
    dropdownStyle: {
      type: Object
    },
    dropdownClassName: String,
    dropdownMatchSelectWidth: {
      type: [Boolean, Number],
      default: void 0
    },
    dropdownRender: {
      type: Function
    },
    dropdownAlign: Object,
    placement: {
      type: String
    },
    getPopupContainer: {
      type: Function
    },
    // >>> Focus
    showAction: {
      type: Array
    },
    onBlur: {
      type: Function
    },
    onFocus: {
      type: Function
    },
    // >>> Rest Events
    onKeyup: Function,
    onKeydown: Function,
    onMousedown: Function,
    onPopupScroll: Function,
    onInputKeyDown: Function,
    onMouseenter: Function,
    onMouseleave: Function,
    onClick: Function
  };
};
const baseSelectProps = () => {
  return _extends(_extends({}, baseSelectPrivateProps()), baseSelectPropsWithoutPrivate());
};
function isMultiple(mode) {
  return mode === "tags" || mode === "multiple";
}
const BaseSelect = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "BaseSelect",
  inheritAttrs: false,
  props: initDefaultProps(baseSelectProps(), {
    showAction: [],
    notFoundContent: "Not Found"
  }),
  setup(props2, _ref) {
    let {
      attrs,
      expose,
      slots
    } = _ref;
    const multiple = computed(() => isMultiple(props2.mode));
    const mergedShowSearch = computed(() => props2.showSearch !== void 0 ? props2.showSearch : multiple.value || props2.mode === "combobox");
    const mobile = shallowRef(false);
    onMounted(() => {
      mobile.value = isMobile();
    });
    const legacyTreeSelectContext = useInjectLegacySelectContext();
    const containerRef = shallowRef(null);
    const selectorDomRef = createRef();
    const triggerRef = shallowRef(null);
    const selectorRef = shallowRef(null);
    const listRef = shallowRef(null);
    const blurRef = ref(false);
    const [mockFocused, setMockFocused, cancelSetMockFocused] = useDelayReset();
    const focus = () => {
      var _a;
      (_a = selectorRef.value) === null || _a === void 0 ? void 0 : _a.focus();
    };
    const blur = () => {
      var _a;
      (_a = selectorRef.value) === null || _a === void 0 ? void 0 : _a.blur();
    };
    expose({
      focus,
      blur,
      scrollTo: (arg) => {
        var _a;
        return (_a = listRef.value) === null || _a === void 0 ? void 0 : _a.scrollTo(arg);
      }
    });
    const mergedSearchValue = computed(() => {
      var _a;
      if (props2.mode !== "combobox") {
        return props2.searchValue;
      }
      const val = (_a = props2.displayValues[0]) === null || _a === void 0 ? void 0 : _a.value;
      return typeof val === "string" || typeof val === "number" ? String(val) : "";
    });
    const initOpen = props2.open !== void 0 ? props2.open : props2.defaultOpen;
    const innerOpen = shallowRef(initOpen);
    const mergedOpen = shallowRef(initOpen);
    const setInnerOpen = (val) => {
      innerOpen.value = props2.open !== void 0 ? props2.open : val;
      mergedOpen.value = innerOpen.value;
    };
    watch(() => props2.open, () => {
      setInnerOpen(props2.open);
    });
    const emptyListContent = computed(() => !props2.notFoundContent && props2.emptyOptions);
    watchEffect(() => {
      mergedOpen.value = innerOpen.value;
      if (props2.disabled || emptyListContent.value && mergedOpen.value && props2.mode === "combobox") {
        mergedOpen.value = false;
      }
    });
    const triggerOpen = computed(() => emptyListContent.value ? false : mergedOpen.value);
    const onToggleOpen = (newOpen) => {
      const nextOpen = newOpen !== void 0 ? newOpen : !mergedOpen.value;
      if (mergedOpen.value !== nextOpen && !props2.disabled) {
        setInnerOpen(nextOpen);
        props2.onDropdownVisibleChange && props2.onDropdownVisibleChange(nextOpen);
        if (!nextOpen && popupFocused.value) {
          popupFocused.value = false;
          setMockFocused(false, () => {
            focusRef.value = false;
            blurRef.value = false;
          });
        }
      }
    };
    const tokenWithEnter = computed(() => (props2.tokenSeparators || []).some((tokenSeparator) => ["\n", "\r\n"].includes(tokenSeparator)));
    const onInternalSearch = (searchText, fromTyping, isCompositing) => {
      var _a, _b;
      let ret = true;
      let newSearchText = searchText;
      (_a = props2.onActiveValueChange) === null || _a === void 0 ? void 0 : _a.call(props2, null);
      const patchLabels = isCompositing ? null : getSeparatedContent(searchText, props2.tokenSeparators);
      if (props2.mode !== "combobox" && patchLabels) {
        newSearchText = "";
        (_b = props2.onSearchSplit) === null || _b === void 0 ? void 0 : _b.call(props2, patchLabels);
        onToggleOpen(false);
        ret = false;
      }
      if (props2.onSearch && mergedSearchValue.value !== newSearchText) {
        props2.onSearch(newSearchText, {
          source: fromTyping ? "typing" : "effect"
        });
      }
      return ret;
    };
    const onInternalSearchSubmit = (searchText) => {
      var _a;
      if (!searchText || !searchText.trim()) {
        return;
      }
      (_a = props2.onSearch) === null || _a === void 0 ? void 0 : _a.call(props2, searchText, {
        source: "submit"
      });
    };
    watch(mergedOpen, () => {
      if (!mergedOpen.value && !multiple.value && props2.mode !== "combobox") {
        onInternalSearch("", false, false);
      }
    }, {
      immediate: true,
      flush: "post"
    });
    watch(() => props2.disabled, () => {
      if (innerOpen.value && !!props2.disabled) {
        setInnerOpen(false);
      }
      if (props2.disabled && !blurRef.value) {
        setMockFocused(false);
      }
    }, {
      immediate: true
    });
    const [getClearLock, setClearLock] = useLock();
    const onInternalKeyDown = function(event) {
      var _a;
      const clearLock = getClearLock();
      const {
        which
      } = event;
      if (which === KeyCode.ENTER) {
        if (props2.mode !== "combobox") {
          event.preventDefault();
        }
        if (!mergedOpen.value) {
          onToggleOpen(true);
        }
      }
      setClearLock(!!mergedSearchValue.value);
      if (which === KeyCode.BACKSPACE && !clearLock && multiple.value && !mergedSearchValue.value && props2.displayValues.length) {
        const cloneDisplayValues = [...props2.displayValues];
        let removedDisplayValue = null;
        for (let i = cloneDisplayValues.length - 1; i >= 0; i -= 1) {
          const current = cloneDisplayValues[i];
          if (!current.disabled) {
            cloneDisplayValues.splice(i, 1);
            removedDisplayValue = current;
            break;
          }
        }
        if (removedDisplayValue) {
          props2.onDisplayValuesChange(cloneDisplayValues, {
            type: "remove",
            values: [removedDisplayValue]
          });
        }
      }
      for (var _len = arguments.length, rest = new Array(_len > 1 ? _len - 1 : 0), _key = 1; _key < _len; _key++) {
        rest[_key - 1] = arguments[_key];
      }
      if (mergedOpen.value && listRef.value) {
        listRef.value.onKeydown(event, ...rest);
      }
      (_a = props2.onKeydown) === null || _a === void 0 ? void 0 : _a.call(props2, event, ...rest);
    };
    const onInternalKeyUp = function(event) {
      for (var _len2 = arguments.length, rest = new Array(_len2 > 1 ? _len2 - 1 : 0), _key2 = 1; _key2 < _len2; _key2++) {
        rest[_key2 - 1] = arguments[_key2];
      }
      if (mergedOpen.value && listRef.value) {
        listRef.value.onKeyup(event, ...rest);
      }
      if (props2.onKeyup) {
        props2.onKeyup(event, ...rest);
      }
    };
    const onSelectorRemove = (val) => {
      const newValues = props2.displayValues.filter((i) => i !== val);
      props2.onDisplayValuesChange(newValues, {
        type: "remove",
        values: [val]
      });
    };
    const focusRef = shallowRef(false);
    const onContainerFocus = function() {
      setMockFocused(true);
      if (!props2.disabled) {
        if (props2.onFocus && !focusRef.value) {
          props2.onFocus(...arguments);
        }
        if (props2.showAction && props2.showAction.includes("focus")) {
          onToggleOpen(true);
        }
      }
      focusRef.value = true;
    };
    const popupFocused = ref(false);
    const onContainerBlur = function() {
      if (popupFocused.value) {
        return;
      }
      blurRef.value = true;
      setMockFocused(false, () => {
        focusRef.value = false;
        blurRef.value = false;
        onToggleOpen(false);
      });
      if (props2.disabled) {
        return;
      }
      const searchVal = mergedSearchValue.value;
      if (searchVal) {
        if (props2.mode === "tags") {
          props2.onSearch(searchVal, {
            source: "submit"
          });
        } else if (props2.mode === "multiple") {
          props2.onSearch("", {
            source: "blur"
          });
        }
      }
      if (props2.onBlur) {
        props2.onBlur(...arguments);
      }
    };
    const onPopupFocusin = () => {
      popupFocused.value = true;
    };
    const onPopupFocusout = () => {
      popupFocused.value = false;
    };
    provide("VCSelectContainerEvent", {
      focus: onContainerFocus,
      blur: onContainerBlur
    });
    const activeTimeoutIds = [];
    onMounted(() => {
      activeTimeoutIds.forEach((timeoutId) => clearTimeout(timeoutId));
      activeTimeoutIds.splice(0, activeTimeoutIds.length);
    });
    onBeforeUnmount(() => {
      activeTimeoutIds.forEach((timeoutId) => clearTimeout(timeoutId));
      activeTimeoutIds.splice(0, activeTimeoutIds.length);
    });
    const onInternalMouseDown = function(event) {
      var _a, _b;
      const {
        target
      } = event;
      const popupElement = (_a = triggerRef.value) === null || _a === void 0 ? void 0 : _a.getPopupElement();
      if (popupElement && popupElement.contains(target)) {
        const timeoutId = setTimeout(() => {
          var _a2;
          const index = activeTimeoutIds.indexOf(timeoutId);
          if (index !== -1) {
            activeTimeoutIds.splice(index, 1);
          }
          cancelSetMockFocused();
          if (!mobile.value && !popupElement.contains(document.activeElement)) {
            (_a2 = selectorRef.value) === null || _a2 === void 0 ? void 0 : _a2.focus();
          }
        });
        activeTimeoutIds.push(timeoutId);
      }
      for (var _len3 = arguments.length, restArgs = new Array(_len3 > 1 ? _len3 - 1 : 0), _key3 = 1; _key3 < _len3; _key3++) {
        restArgs[_key3 - 1] = arguments[_key3];
      }
      (_b = props2.onMousedown) === null || _b === void 0 ? void 0 : _b.call(props2, event, ...restArgs);
    };
    const containerWidth = shallowRef(null);
    const onPopupMouseEnter = () => {
    };
    onMounted(() => {
      watch(triggerOpen, () => {
        var _a;
        if (triggerOpen.value) {
          const newWidth = Math.ceil((_a = containerRef.value) === null || _a === void 0 ? void 0 : _a.offsetWidth);
          if (containerWidth.value !== newWidth && !Number.isNaN(newWidth)) {
            containerWidth.value = newWidth;
          }
        }
      }, {
        immediate: true,
        flush: "post"
      });
    });
    useSelectTriggerControl([containerRef, triggerRef], triggerOpen, onToggleOpen);
    useProvideBaseSelectProps(toReactive(_extends(_extends({}, toRefs(props2)), {
      open: mergedOpen,
      triggerOpen,
      showSearch: mergedShowSearch,
      multiple,
      toggleOpen: onToggleOpen
    })));
    return () => {
      const _a = _extends(_extends({}, props2), attrs), {
        prefixCls,
        id,
        open: open2,
        defaultOpen,
        mode,
        // Search related
        showSearch,
        searchValue,
        onSearch,
        // Icons
        allowClear,
        clearIcon,
        showArrow,
        inputIcon,
        // Others
        disabled,
        loading,
        getInputElement,
        getPopupContainer,
        placement,
        // Dropdown
        animation,
        transitionName,
        dropdownStyle,
        dropdownClassName,
        dropdownMatchSelectWidth,
        dropdownRender,
        dropdownAlign,
        showAction,
        direction,
        // Tags
        tokenSeparators,
        tagRender,
        optionLabelRender,
        // Events
        onPopupScroll,
        onDropdownVisibleChange,
        onFocus,
        onBlur,
        onKeyup,
        onKeydown,
        onMousedown,
        onClear,
        omitDomProps,
        getRawInputElement,
        displayValues,
        onDisplayValuesChange,
        emptyOptions,
        activeDescendantId,
        activeValue,
        OptionList: OptionList2
      } = _a, restProps = __rest$3(_a, ["prefixCls", "id", "open", "defaultOpen", "mode", "showSearch", "searchValue", "onSearch", "allowClear", "clearIcon", "showArrow", "inputIcon", "disabled", "loading", "getInputElement", "getPopupContainer", "placement", "animation", "transitionName", "dropdownStyle", "dropdownClassName", "dropdownMatchSelectWidth", "dropdownRender", "dropdownAlign", "showAction", "direction", "tokenSeparators", "tagRender", "optionLabelRender", "onPopupScroll", "onDropdownVisibleChange", "onFocus", "onBlur", "onKeyup", "onKeydown", "onMousedown", "onClear", "omitDomProps", "getRawInputElement", "displayValues", "onDisplayValuesChange", "emptyOptions", "activeDescendantId", "activeValue", "OptionList"]);
      const customizeInputElement = mode === "combobox" && getInputElement && getInputElement() || null;
      const customizeRawInputElement = typeof getRawInputElement === "function" && getRawInputElement();
      const domProps = _extends({}, restProps);
      let onTriggerVisibleChange;
      if (customizeRawInputElement) {
        onTriggerVisibleChange = (newOpen) => {
          onToggleOpen(newOpen);
        };
      }
      DEFAULT_OMIT_PROPS.forEach((propName) => {
        delete domProps[propName];
      });
      omitDomProps === null || omitDomProps === void 0 ? void 0 : omitDomProps.forEach((propName) => {
        delete domProps[propName];
      });
      const mergedShowArrow = showArrow !== void 0 ? showArrow : loading || !multiple.value && mode !== "combobox";
      let arrowNode;
      if (mergedShowArrow) {
        arrowNode = createVNode(TransBtn, {
          "class": classNames(`${prefixCls}-arrow`, {
            [`${prefixCls}-arrow-loading`]: loading
          }),
          "customizeIcon": inputIcon,
          "customizeIconProps": {
            loading,
            searchValue: mergedSearchValue.value,
            open: mergedOpen.value,
            focused: mockFocused.value,
            showSearch: mergedShowSearch.value
          }
        }, null);
      }
      let clearNode;
      const onClearMouseDown = () => {
        onClear === null || onClear === void 0 ? void 0 : onClear();
        onDisplayValuesChange([], {
          type: "clear",
          values: displayValues
        });
        onInternalSearch("", false, false);
      };
      if (!disabled && allowClear && (displayValues.length || mergedSearchValue.value)) {
        clearNode = createVNode(TransBtn, {
          "class": `${prefixCls}-clear`,
          "onMousedown": onClearMouseDown,
          "customizeIcon": clearIcon
        }, {
          default: () => [createTextVNode("×")]
        });
      }
      const optionList = createVNode(OptionList2, {
        "ref": listRef
      }, _extends(_extends({}, legacyTreeSelectContext.customSlots), {
        option: slots.option
      }));
      const mergedClassName = classNames(prefixCls, attrs.class, {
        [`${prefixCls}-focused`]: mockFocused.value,
        [`${prefixCls}-multiple`]: multiple.value,
        [`${prefixCls}-single`]: !multiple.value,
        [`${prefixCls}-allow-clear`]: allowClear,
        [`${prefixCls}-show-arrow`]: mergedShowArrow,
        [`${prefixCls}-disabled`]: disabled,
        [`${prefixCls}-loading`]: loading,
        [`${prefixCls}-open`]: mergedOpen.value,
        [`${prefixCls}-customize-input`]: customizeInputElement,
        [`${prefixCls}-show-search`]: mergedShowSearch.value
      });
      const selectorNode = createVNode(SelectTrigger, {
        "ref": triggerRef,
        "disabled": disabled,
        "prefixCls": prefixCls,
        "visible": triggerOpen.value,
        "popupElement": optionList,
        "containerWidth": containerWidth.value,
        "animation": animation,
        "transitionName": transitionName,
        "dropdownStyle": dropdownStyle,
        "dropdownClassName": dropdownClassName,
        "direction": direction,
        "dropdownMatchSelectWidth": dropdownMatchSelectWidth,
        "dropdownRender": dropdownRender,
        "dropdownAlign": dropdownAlign,
        "placement": placement,
        "getPopupContainer": getPopupContainer,
        "empty": emptyOptions,
        "getTriggerDOMNode": () => selectorDomRef.current,
        "onPopupVisibleChange": onTriggerVisibleChange,
        "onPopupMouseEnter": onPopupMouseEnter,
        "onPopupFocusin": onPopupFocusin,
        "onPopupFocusout": onPopupFocusout
      }, {
        default: () => {
          return customizeRawInputElement ? isValidElement(customizeRawInputElement) && cloneElement(customizeRawInputElement, {
            ref: selectorDomRef
          }, false, true) : createVNode(Selector, _objectSpread2(_objectSpread2({}, props2), {}, {
            "domRef": selectorDomRef,
            "prefixCls": prefixCls,
            "inputElement": customizeInputElement,
            "ref": selectorRef,
            "id": id,
            "showSearch": mergedShowSearch.value,
            "mode": mode,
            "activeDescendantId": activeDescendantId,
            "tagRender": tagRender,
            "optionLabelRender": optionLabelRender,
            "values": displayValues,
            "open": mergedOpen.value,
            "onToggleOpen": onToggleOpen,
            "activeValue": activeValue,
            "searchValue": mergedSearchValue.value,
            "onSearch": onInternalSearch,
            "onSearchSubmit": onInternalSearchSubmit,
            "onRemove": onSelectorRemove,
            "tokenWithEnter": tokenWithEnter.value
          }), null);
        }
      });
      let renderNode;
      if (customizeRawInputElement) {
        renderNode = selectorNode;
      } else {
        renderNode = createVNode("div", _objectSpread2(_objectSpread2({}, domProps), {}, {
          "class": mergedClassName,
          "ref": containerRef,
          "onMousedown": onInternalMouseDown,
          "onKeydown": onInternalKeyDown,
          "onKeyup": onInternalKeyUp
        }), [mockFocused.value && !mergedOpen.value && createVNode("span", {
          "style": {
            width: 0,
            height: 0,
            position: "absolute",
            overflow: "hidden",
            opacity: 0
          },
          "aria-live": "polite"
        }, [`${displayValues.map((_ref2) => {
          let {
            label,
            value
          } = _ref2;
          return ["number", "string"].includes(typeof label) ? label : value;
        }).join(", ")}`]), selectorNode, arrowNode, clearNode]);
      }
      return renderNode;
    };
  }
});
const Filter = (_ref, _ref2) => {
  let {
    height,
    offset,
    prefixCls,
    onInnerResize
  } = _ref;
  let {
    slots
  } = _ref2;
  var _a;
  let outerStyle = {};
  let innerStyle = {
    display: "flex",
    flexDirection: "column"
  };
  if (offset !== void 0) {
    outerStyle = {
      height: `${height}px`,
      position: "relative",
      overflow: "hidden"
    };
    innerStyle = _extends(_extends({}, innerStyle), {
      transform: `translateY(${offset}px)`,
      position: "absolute",
      left: 0,
      right: 0,
      top: 0
    });
  }
  return createVNode("div", {
    "style": outerStyle
  }, [createVNode(ResizeObserver, {
    "onResize": (_ref3) => {
      let {
        offsetHeight
      } = _ref3;
      if (offsetHeight && onInnerResize) {
        onInnerResize();
      }
    }
  }, {
    default: () => [createVNode("div", {
      "style": innerStyle,
      "class": classNames({
        [`${prefixCls}-holder-inner`]: prefixCls
      })
    }, [(_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots)])]
  })]);
};
Filter.displayName = "Filter";
Filter.inheritAttrs = false;
Filter.props = {
  prefixCls: String,
  /** Virtual filler height. Should be `count * itemMinHeight` */
  height: Number,
  /** Set offset of visible items. Should be the top of start item position */
  offset: Number,
  onInnerResize: Function
};
const Item = (_ref, _ref2) => {
  let {
    setRef
  } = _ref;
  let {
    slots
  } = _ref2;
  var _a;
  const children = flattenChildren((_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots));
  return children && children.length ? cloneVNode(children[0], {
    ref: setRef
  }) : children;
};
Item.props = {
  setRef: {
    type: Function,
    default: () => {
    }
  }
};
const MIN_SIZE = 20;
function getPageY(e) {
  return "touches" in e ? e.touches[0].pageY : e.pageY;
}
const ScrollBar = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "ScrollBar",
  inheritAttrs: false,
  props: {
    prefixCls: String,
    scrollTop: Number,
    scrollHeight: Number,
    height: Number,
    count: Number,
    onScroll: {
      type: Function
    },
    onStartMove: {
      type: Function
    },
    onStopMove: {
      type: Function
    }
  },
  setup() {
    return {
      moveRaf: null,
      scrollbarRef: createRef(),
      thumbRef: createRef(),
      visibleTimeout: null,
      state: reactive({
        dragging: false,
        pageY: null,
        startTop: null,
        visible: false
      })
    };
  },
  watch: {
    scrollTop: {
      handler() {
        this.delayHidden();
      },
      flush: "post"
    }
  },
  mounted() {
    var _a, _b;
    (_a = this.scrollbarRef.current) === null || _a === void 0 ? void 0 : _a.addEventListener("touchstart", this.onScrollbarTouchStart, supportsPassive ? {
      passive: false
    } : false);
    (_b = this.thumbRef.current) === null || _b === void 0 ? void 0 : _b.addEventListener("touchstart", this.onMouseDown, supportsPassive ? {
      passive: false
    } : false);
  },
  beforeUnmount() {
    this.removeEvents();
    clearTimeout(this.visibleTimeout);
  },
  methods: {
    delayHidden() {
      clearTimeout(this.visibleTimeout);
      this.state.visible = true;
      this.visibleTimeout = setTimeout(() => {
        this.state.visible = false;
      }, 2e3);
    },
    onScrollbarTouchStart(e) {
      e.preventDefault();
    },
    onContainerMouseDown(e) {
      e.stopPropagation();
      e.preventDefault();
    },
    // ======================= Clean =======================
    patchEvents() {
      window.addEventListener("mousemove", this.onMouseMove);
      window.addEventListener("mouseup", this.onMouseUp);
      this.thumbRef.current.addEventListener("touchmove", this.onMouseMove, supportsPassive ? {
        passive: false
      } : false);
      this.thumbRef.current.addEventListener("touchend", this.onMouseUp);
    },
    removeEvents() {
      window.removeEventListener("mousemove", this.onMouseMove);
      window.removeEventListener("mouseup", this.onMouseUp);
      this.scrollbarRef.current.removeEventListener("touchstart", this.onScrollbarTouchStart, supportsPassive ? {
        passive: false
      } : false);
      if (this.thumbRef.current) {
        this.thumbRef.current.removeEventListener("touchstart", this.onMouseDown, supportsPassive ? {
          passive: false
        } : false);
        this.thumbRef.current.removeEventListener("touchmove", this.onMouseMove, supportsPassive ? {
          passive: false
        } : false);
        this.thumbRef.current.removeEventListener("touchend", this.onMouseUp);
      }
      wrapperRaf.cancel(this.moveRaf);
    },
    // ======================= Thumb =======================
    onMouseDown(e) {
      const {
        onStartMove
      } = this.$props;
      _extends(this.state, {
        dragging: true,
        pageY: getPageY(e),
        startTop: this.getTop()
      });
      onStartMove();
      this.patchEvents();
      e.stopPropagation();
      e.preventDefault();
    },
    onMouseMove(e) {
      const {
        dragging,
        pageY,
        startTop
      } = this.state;
      const {
        onScroll
      } = this.$props;
      wrapperRaf.cancel(this.moveRaf);
      if (dragging) {
        const offsetY = getPageY(e) - pageY;
        const newTop = startTop + offsetY;
        const enableScrollRange = this.getEnableScrollRange();
        const enableHeightRange = this.getEnableHeightRange();
        const ptg = enableHeightRange ? newTop / enableHeightRange : 0;
        const newScrollTop = Math.ceil(ptg * enableScrollRange);
        this.moveRaf = wrapperRaf(() => {
          onScroll(newScrollTop);
        });
      }
    },
    onMouseUp() {
      const {
        onStopMove
      } = this.$props;
      this.state.dragging = false;
      onStopMove();
      this.removeEvents();
    },
    // ===================== Calculate =====================
    getSpinHeight() {
      const {
        height,
        scrollHeight
      } = this.$props;
      let baseHeight = height / scrollHeight * 100;
      baseHeight = Math.max(baseHeight, MIN_SIZE);
      baseHeight = Math.min(baseHeight, height / 2);
      return Math.floor(baseHeight);
    },
    getEnableScrollRange() {
      const {
        scrollHeight,
        height
      } = this.$props;
      return scrollHeight - height || 0;
    },
    getEnableHeightRange() {
      const {
        height
      } = this.$props;
      const spinHeight = this.getSpinHeight();
      return height - spinHeight || 0;
    },
    getTop() {
      const {
        scrollTop
      } = this.$props;
      const enableScrollRange = this.getEnableScrollRange();
      const enableHeightRange = this.getEnableHeightRange();
      if (scrollTop === 0 || enableScrollRange === 0) {
        return 0;
      }
      const ptg = scrollTop / enableScrollRange;
      return ptg * enableHeightRange;
    },
    // Not show scrollbar when height is large than scrollHeight
    showScroll() {
      const {
        height,
        scrollHeight
      } = this.$props;
      return scrollHeight > height;
    }
  },
  render() {
    const {
      dragging,
      visible
    } = this.state;
    const {
      prefixCls
    } = this.$props;
    const spinHeight = this.getSpinHeight() + "px";
    const top = this.getTop() + "px";
    const canScroll = this.showScroll();
    const mergedVisible = canScroll && visible;
    return createVNode("div", {
      "ref": this.scrollbarRef,
      "class": classNames(`${prefixCls}-scrollbar`, {
        [`${prefixCls}-scrollbar-show`]: canScroll
      }),
      "style": {
        width: "8px",
        top: 0,
        bottom: 0,
        right: 0,
        position: "absolute",
        display: mergedVisible ? void 0 : "none"
      },
      "onMousedown": this.onContainerMouseDown,
      "onMousemove": this.delayHidden
    }, [createVNode("div", {
      "ref": this.thumbRef,
      "class": classNames(`${prefixCls}-scrollbar-thumb`, {
        [`${prefixCls}-scrollbar-thumb-moving`]: dragging
      }),
      "style": {
        width: "100%",
        height: spinHeight,
        top,
        left: 0,
        position: "absolute",
        background: "rgba(0, 0, 0, 0.5)",
        borderRadius: "99px",
        cursor: "pointer",
        userSelect: "none"
      },
      "onMousedown": this.onMouseDown
    }, null)]);
  }
});
function useHeights(mergedData, getKey2, onItemAdd, onItemRemove) {
  const instance = /* @__PURE__ */ new Map();
  const heights = /* @__PURE__ */ new Map();
  const updatedMark = ref(Symbol("update"));
  watch(mergedData, () => {
    updatedMark.value = Symbol("update");
  });
  let collectRaf = void 0;
  function cancelRaf() {
    wrapperRaf.cancel(collectRaf);
  }
  function collectHeight() {
    cancelRaf();
    collectRaf = wrapperRaf(() => {
      instance.forEach((element, key) => {
        if (element && element.offsetParent) {
          const {
            offsetHeight
          } = element;
          if (heights.get(key) !== offsetHeight) {
            updatedMark.value = Symbol("update");
            heights.set(key, element.offsetHeight);
          }
        }
      });
    });
  }
  function setInstance(item, ins) {
    const key = getKey2(item);
    instance.get(key);
    if (ins) {
      instance.set(key, ins.$el || ins);
      collectHeight();
    } else {
      instance.delete(key);
    }
  }
  onUnmounted(() => {
    cancelRaf();
  });
  return [setInstance, collectHeight, heights, updatedMark];
}
function useScrollTo(containerRef, mergedData, heights, props2, getKey2, collectHeight, syncScrollTop, triggerFlash) {
  let scroll;
  return (arg) => {
    if (arg === null || arg === void 0) {
      triggerFlash();
      return;
    }
    wrapperRaf.cancel(scroll);
    const data = mergedData.value;
    const itemHeight = props2.itemHeight;
    if (typeof arg === "number") {
      syncScrollTop(arg);
    } else if (arg && typeof arg === "object") {
      let index;
      const {
        align
      } = arg;
      if ("index" in arg) {
        ({
          index
        } = arg);
      } else {
        index = data.findIndex((item) => getKey2(item) === arg.key);
      }
      const {
        offset = 0
      } = arg;
      const syncScroll = (times, targetAlign) => {
        if (times < 0 || !containerRef.value) return;
        const height = containerRef.value.clientHeight;
        let needCollectHeight = false;
        let newTargetAlign = targetAlign;
        if (height) {
          const mergedAlign = targetAlign || align;
          let stackTop = 0;
          let itemTop = 0;
          let itemBottom = 0;
          const maxLen = Math.min(data.length, index);
          for (let i = 0; i <= maxLen; i += 1) {
            const key = getKey2(data[i]);
            itemTop = stackTop;
            const cacheHeight = heights.get(key);
            itemBottom = itemTop + (cacheHeight === void 0 ? itemHeight : cacheHeight);
            stackTop = itemBottom;
            if (i === index && cacheHeight === void 0) {
              needCollectHeight = true;
            }
          }
          const scrollTop = containerRef.value.scrollTop;
          let targetTop = null;
          switch (mergedAlign) {
            case "top":
              targetTop = itemTop - offset;
              break;
            case "bottom":
              targetTop = itemBottom - height + offset;
              break;
            default: {
              const scrollBottom = scrollTop + height;
              if (itemTop < scrollTop) {
                newTargetAlign = "top";
              } else if (itemBottom > scrollBottom) {
                newTargetAlign = "bottom";
              }
            }
          }
          if (targetTop !== null && targetTop !== scrollTop) {
            syncScrollTop(targetTop);
          }
        }
        scroll = wrapperRaf(() => {
          if (needCollectHeight) {
            collectHeight();
          }
          syncScroll(times - 1, newTargetAlign);
        }, 2);
      };
      syncScroll(5);
    }
  };
}
const isFF = typeof navigator === "object" && /Firefox/i.test(navigator.userAgent);
const useOriginScroll = (isScrollAtTop, isScrollAtBottom) => {
  let lock = false;
  let lockTimeout = null;
  function lockScroll() {
    clearTimeout(lockTimeout);
    lock = true;
    lockTimeout = setTimeout(() => {
      lock = false;
    }, 50);
  }
  return function(deltaY) {
    let smoothOffset = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : false;
    const originScroll = (
      // Pass origin wheel when on the top
      deltaY < 0 && isScrollAtTop.value || // Pass origin wheel when on the bottom
      deltaY > 0 && isScrollAtBottom.value
    );
    if (smoothOffset && originScroll) {
      clearTimeout(lockTimeout);
      lock = false;
    } else if (!originScroll || lock) {
      lockScroll();
    }
    return !lock && originScroll;
  };
};
function useFrameWheel(inVirtual, isScrollAtTop, isScrollAtBottom, onWheelDelta) {
  let offsetRef = 0;
  let nextFrame = null;
  let wheelValue = null;
  let isMouseScroll = false;
  const originScroll = useOriginScroll(isScrollAtTop, isScrollAtBottom);
  function onWheel(event) {
    if (!inVirtual.value) return;
    wrapperRaf.cancel(nextFrame);
    const {
      deltaY
    } = event;
    offsetRef += deltaY;
    wheelValue = deltaY;
    if (originScroll(deltaY)) return;
    if (!isFF) {
      event.preventDefault();
    }
    nextFrame = wrapperRaf(() => {
      const patchMultiple = isMouseScroll ? 10 : 1;
      onWheelDelta(offsetRef * patchMultiple);
      offsetRef = 0;
    });
  }
  function onFireFoxScroll(event) {
    if (!inVirtual.value) return;
    isMouseScroll = event.detail === wheelValue;
  }
  return [onWheel, onFireFoxScroll];
}
const SMOOTH_PTG = 14 / 15;
function useMobileTouchMove(inVirtual, listRef, callback) {
  let touched = false;
  let touchY = 0;
  let element = null;
  let interval = null;
  const cleanUpEvents = () => {
    if (element) {
      element.removeEventListener("touchmove", onTouchMove);
      element.removeEventListener("touchend", onTouchEnd);
    }
  };
  const onTouchMove = (e) => {
    if (touched) {
      const currentY = Math.ceil(e.touches[0].pageY);
      let offsetY = touchY - currentY;
      touchY = currentY;
      if (callback(offsetY)) {
        e.preventDefault();
      }
      clearInterval(interval);
      interval = setInterval(() => {
        offsetY *= SMOOTH_PTG;
        if (!callback(offsetY, true) || Math.abs(offsetY) <= 0.1) {
          clearInterval(interval);
        }
      }, 16);
    }
  };
  const onTouchEnd = () => {
    touched = false;
    cleanUpEvents();
  };
  const onTouchStart = (e) => {
    cleanUpEvents();
    if (e.touches.length === 1 && !touched) {
      touched = true;
      touchY = Math.ceil(e.touches[0].pageY);
      element = e.target;
      element.addEventListener("touchmove", onTouchMove, {
        passive: false
      });
      element.addEventListener("touchend", onTouchEnd);
    }
  };
  const noop = () => {
  };
  onMounted(() => {
    document.addEventListener("touchmove", noop, {
      passive: false
    });
    watch(inVirtual, (val) => {
      listRef.value.removeEventListener("touchstart", onTouchStart);
      cleanUpEvents();
      clearInterval(interval);
      if (val) {
        listRef.value.addEventListener("touchstart", onTouchStart, {
          passive: false
        });
      }
    }, {
      immediate: true
    });
  });
  onBeforeUnmount(() => {
    document.removeEventListener("touchmove", noop);
  });
}
var __rest$2 = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
const EMPTY_DATA = [];
const ScrollStyle = {
  overflowY: "auto",
  overflowAnchor: "none"
};
function renderChildren(list, startIndex, endIndex, setNodeRef, renderFunc, _ref) {
  let {
    getKey: getKey2
  } = _ref;
  return list.slice(startIndex, endIndex + 1).map((item, index) => {
    const eleIndex = startIndex + index;
    const node = renderFunc(item, eleIndex, {
      // style: status === 'MEASURE_START' ? { visibility: 'hidden' } : {},
    });
    const key = getKey2(item);
    return createVNode(Item, {
      "key": key,
      "setRef": (ele) => setNodeRef(item, ele)
    }, {
      default: () => [node]
    });
  });
}
const List = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "List",
  inheritAttrs: false,
  props: {
    prefixCls: String,
    data: PropTypes.array,
    height: Number,
    itemHeight: Number,
    /** If not match virtual scroll condition, Set List still use height of container. */
    fullHeight: {
      type: Boolean,
      default: void 0
    },
    itemKey: {
      type: [String, Number, Function],
      required: true
    },
    component: {
      type: [String, Object]
    },
    /** Set `false` will always use real scroll instead of virtual one */
    virtual: {
      type: Boolean,
      default: void 0
    },
    children: Function,
    onScroll: Function,
    onMousedown: Function,
    onMouseenter: Function,
    onVisibleChange: Function
  },
  setup(props2, _ref2) {
    let {
      expose
    } = _ref2;
    const useVirtual = computed(() => {
      const {
        height,
        itemHeight,
        virtual
      } = props2;
      return !!(virtual !== false && height && itemHeight);
    });
    const inVirtual = computed(() => {
      const {
        height,
        itemHeight,
        data: data2
      } = props2;
      return useVirtual.value && data2 && itemHeight * data2.length > height;
    });
    const state = reactive({
      scrollTop: 0,
      scrollMoving: false
    });
    const data = computed(() => {
      return props2.data || EMPTY_DATA;
    });
    const mergedData = shallowRef([]);
    watch(data, () => {
      mergedData.value = toRaw(data.value).slice();
    }, {
      immediate: true
    });
    const itemKey = shallowRef((_item) => void 0);
    watch(() => props2.itemKey, (val) => {
      if (typeof val === "function") {
        itemKey.value = val;
      } else {
        itemKey.value = (item) => item === null || item === void 0 ? void 0 : item[val];
      }
    }, {
      immediate: true
    });
    const componentRef = shallowRef();
    const fillerInnerRef = shallowRef();
    const scrollBarRef = shallowRef();
    const getKey2 = (item) => {
      return itemKey.value(item);
    };
    const sharedConfig = {
      getKey: getKey2
    };
    function syncScrollTop(newTop) {
      let value;
      if (typeof newTop === "function") {
        value = newTop(state.scrollTop);
      } else {
        value = newTop;
      }
      const alignedTop = keepInRange(value);
      if (componentRef.value) {
        componentRef.value.scrollTop = alignedTop;
      }
      state.scrollTop = alignedTop;
    }
    const [setInstance, collectHeight, heights, updatedMark] = useHeights(mergedData, getKey2);
    const calRes = reactive({
      scrollHeight: void 0,
      start: 0,
      end: 0,
      offset: void 0
    });
    const offsetHeight = shallowRef(0);
    onMounted(() => {
      nextTick(() => {
        var _a;
        offsetHeight.value = ((_a = fillerInnerRef.value) === null || _a === void 0 ? void 0 : _a.offsetHeight) || 0;
      });
    });
    onUpdated(() => {
      nextTick(() => {
        var _a;
        offsetHeight.value = ((_a = fillerInnerRef.value) === null || _a === void 0 ? void 0 : _a.offsetHeight) || 0;
      });
    });
    watch([useVirtual, mergedData], () => {
      if (!useVirtual.value) {
        _extends(calRes, {
          scrollHeight: void 0,
          start: 0,
          end: mergedData.value.length - 1,
          offset: void 0
        });
      }
    }, {
      immediate: true
    });
    watch([useVirtual, mergedData, offsetHeight, inVirtual], () => {
      if (useVirtual.value && !inVirtual.value) {
        _extends(calRes, {
          scrollHeight: offsetHeight.value,
          start: 0,
          end: mergedData.value.length - 1,
          offset: void 0
        });
      }
      if (componentRef.value) {
        state.scrollTop = componentRef.value.scrollTop;
      }
    }, {
      immediate: true
    });
    watch([inVirtual, useVirtual, () => state.scrollTop, mergedData, updatedMark, () => props2.height, offsetHeight], () => {
      if (!useVirtual.value || !inVirtual.value) {
        return;
      }
      let itemTop = 0;
      let startIndex;
      let startOffset;
      let endIndex;
      const dataLen = mergedData.value.length;
      const data2 = mergedData.value;
      const scrollTop = state.scrollTop;
      const {
        itemHeight,
        height
      } = props2;
      const scrollTopHeight = scrollTop + height;
      for (let i = 0; i < dataLen; i += 1) {
        const item = data2[i];
        const key = getKey2(item);
        let cacheHeight = heights.get(key);
        if (cacheHeight === void 0) {
          cacheHeight = itemHeight;
        }
        const currentItemBottom = itemTop + cacheHeight;
        if (startIndex === void 0 && currentItemBottom >= scrollTop) {
          startIndex = i;
          startOffset = itemTop;
        }
        if (endIndex === void 0 && currentItemBottom > scrollTopHeight) {
          endIndex = i;
        }
        itemTop = currentItemBottom;
      }
      if (startIndex === void 0) {
        startIndex = 0;
        startOffset = 0;
        endIndex = Math.ceil(height / itemHeight);
      }
      if (endIndex === void 0) {
        endIndex = dataLen - 1;
      }
      endIndex = Math.min(endIndex + 1, dataLen);
      _extends(calRes, {
        scrollHeight: itemTop,
        start: startIndex,
        end: endIndex,
        offset: startOffset
      });
    }, {
      immediate: true
    });
    const maxScrollHeight = computed(() => calRes.scrollHeight - props2.height);
    function keepInRange(newScrollTop) {
      let newTop = newScrollTop;
      if (!Number.isNaN(maxScrollHeight.value)) {
        newTop = Math.min(newTop, maxScrollHeight.value);
      }
      newTop = Math.max(newTop, 0);
      return newTop;
    }
    const isScrollAtTop = computed(() => state.scrollTop <= 0);
    const isScrollAtBottom = computed(() => state.scrollTop >= maxScrollHeight.value);
    const originScroll = useOriginScroll(isScrollAtTop, isScrollAtBottom);
    function onScrollBar(newScrollTop) {
      const newTop = newScrollTop;
      syncScrollTop(newTop);
    }
    function onFallbackScroll(e) {
      var _a;
      const {
        scrollTop: newScrollTop
      } = e.currentTarget;
      if (newScrollTop !== state.scrollTop) {
        syncScrollTop(newScrollTop);
      }
      (_a = props2.onScroll) === null || _a === void 0 ? void 0 : _a.call(props2, e);
    }
    const [onRawWheel, onFireFoxScroll] = useFrameWheel(useVirtual, isScrollAtTop, isScrollAtBottom, (offsetY) => {
      syncScrollTop((top) => {
        const newTop = top + offsetY;
        return newTop;
      });
    });
    useMobileTouchMove(useVirtual, componentRef, (deltaY, smoothOffset) => {
      if (originScroll(deltaY, smoothOffset)) {
        return false;
      }
      onRawWheel({
        preventDefault() {
        },
        deltaY
      });
      return true;
    });
    function onMozMousePixelScroll(e) {
      if (useVirtual.value) {
        e.preventDefault();
      }
    }
    const removeEventListener = () => {
      if (componentRef.value) {
        componentRef.value.removeEventListener("wheel", onRawWheel, supportsPassive ? {
          passive: false
        } : false);
        componentRef.value.removeEventListener("DOMMouseScroll", onFireFoxScroll);
        componentRef.value.removeEventListener("MozMousePixelScroll", onMozMousePixelScroll);
      }
    };
    watchEffect(() => {
      nextTick(() => {
        if (componentRef.value) {
          removeEventListener();
          componentRef.value.addEventListener("wheel", onRawWheel, supportsPassive ? {
            passive: false
          } : false);
          componentRef.value.addEventListener("DOMMouseScroll", onFireFoxScroll);
          componentRef.value.addEventListener("MozMousePixelScroll", onMozMousePixelScroll);
        }
      });
    });
    onBeforeUnmount(() => {
      removeEventListener();
    });
    const scrollTo = useScrollTo(componentRef, mergedData, heights, props2, getKey2, collectHeight, syncScrollTop, () => {
      var _a;
      (_a = scrollBarRef.value) === null || _a === void 0 ? void 0 : _a.delayHidden();
    });
    expose({
      scrollTo
    });
    const componentStyle = computed(() => {
      let cs = null;
      if (props2.height) {
        cs = _extends({
          [props2.fullHeight ? "height" : "maxHeight"]: props2.height + "px"
        }, ScrollStyle);
        if (useVirtual.value) {
          cs.overflowY = "hidden";
          if (state.scrollMoving) {
            cs.pointerEvents = "none";
          }
        }
      }
      return cs;
    });
    watch([() => calRes.start, () => calRes.end, mergedData], () => {
      if (props2.onVisibleChange) {
        const renderList = mergedData.value.slice(calRes.start, calRes.end + 1);
        props2.onVisibleChange(renderList, mergedData.value);
      }
    }, {
      flush: "post"
    });
    const delayHideScrollBar = () => {
      var _a;
      (_a = scrollBarRef.value) === null || _a === void 0 ? void 0 : _a.delayHidden();
    };
    return {
      state,
      mergedData,
      componentStyle,
      onFallbackScroll,
      onScrollBar,
      componentRef,
      useVirtual,
      calRes,
      collectHeight,
      setInstance,
      sharedConfig,
      scrollBarRef,
      fillerInnerRef,
      delayHideScrollBar
    };
  },
  render() {
    const _a = _extends(_extends({}, this.$props), this.$attrs), {
      prefixCls = "rc-virtual-list",
      height,
      itemHeight,
      // eslint-disable-next-line no-unused-vars
      fullHeight,
      data,
      itemKey,
      virtual,
      component: Component = "div",
      onScroll,
      children = this.$slots.default,
      style,
      class: className
    } = _a, restProps = __rest$2(_a, ["prefixCls", "height", "itemHeight", "fullHeight", "data", "itemKey", "virtual", "component", "onScroll", "children", "style", "class"]);
    const mergedClassName = classNames(prefixCls, className);
    const {
      scrollTop
    } = this.state;
    const {
      scrollHeight,
      offset,
      start,
      end
    } = this.calRes;
    const {
      componentStyle,
      onFallbackScroll,
      onScrollBar,
      useVirtual,
      collectHeight,
      sharedConfig,
      setInstance,
      mergedData,
      delayHideScrollBar
    } = this;
    return createVNode("div", _objectSpread2({
      "style": _extends(_extends({}, style), {
        position: "relative"
      }),
      "class": mergedClassName
    }, restProps), [createVNode(Component, {
      "class": `${prefixCls}-holder`,
      "style": componentStyle,
      "ref": "componentRef",
      "onScroll": onFallbackScroll,
      "onMouseenter": delayHideScrollBar
    }, {
      default: () => [createVNode(Filter, {
        "prefixCls": prefixCls,
        "height": scrollHeight,
        "offset": offset,
        "onInnerResize": collectHeight,
        "ref": "fillerInnerRef"
      }, {
        default: () => renderChildren(mergedData, start, end, setInstance, children, sharedConfig)
      })]
    }), useVirtual && createVNode(ScrollBar, {
      "ref": "scrollBarRef",
      "prefixCls": prefixCls,
      "scrollTop": scrollTop,
      "height": height,
      "scrollHeight": scrollHeight,
      "count": mergedData.length,
      "onScroll": onScrollBar,
      "onStartMove": () => {
        this.state.scrollMoving = true;
      },
      "onStopMove": () => {
        this.state.scrollMoving = false;
      }
    }, null)]);
  }
});
function useMemo(getValue, condition, shouldUpdate) {
  const cacheRef = ref(getValue());
  watch(condition, (next, pre) => {
    if (shouldUpdate) {
      if (shouldUpdate(next, pre)) {
        cacheRef.value = getValue();
      }
    } else {
      cacheRef.value = getValue();
    }
  });
  return cacheRef;
}
function isPlatformMac() {
  return /(mac\sos|macintosh)/i.test(navigator.appVersion);
}
const SelectContextKey = Symbol("SelectContextKey");
function useProvideSelectProps(props2) {
  return provide(SelectContextKey, props2);
}
function useSelectProps() {
  return inject(SelectContextKey, {});
}
var __rest$1 = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
function isTitleType(content) {
  return typeof content === "string" || typeof content === "number";
}
const OptionList = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "OptionList",
  inheritAttrs: false,
  setup(_, _ref) {
    let {
      expose,
      slots
    } = _ref;
    const baseProps = useBaseProps();
    const props2 = useSelectProps();
    const itemPrefixCls = computed(() => `${baseProps.prefixCls}-item`);
    const memoFlattenOptions = useMemo(() => props2.flattenOptions, [() => baseProps.open, () => props2.flattenOptions], (next) => next[0]);
    const listRef = createRef();
    const onListMouseDown = (event) => {
      event.preventDefault();
    };
    const scrollIntoView = (args) => {
      if (listRef.current) {
        listRef.current.scrollTo(typeof args === "number" ? {
          index: args
        } : args);
      }
    };
    const getEnabledActiveIndex = function(index) {
      let offset = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : 1;
      const len = memoFlattenOptions.value.length;
      for (let i = 0; i < len; i += 1) {
        const current = (index + i * offset + len) % len;
        const {
          group,
          data
        } = memoFlattenOptions.value[current];
        if (!group && !data.disabled) {
          return current;
        }
      }
      return -1;
    };
    const state = reactive({
      activeIndex: getEnabledActiveIndex(0)
    });
    const setActive = function(index) {
      let fromKeyboard = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : false;
      state.activeIndex = index;
      const info = {
        source: fromKeyboard ? "keyboard" : "mouse"
      };
      const flattenItem = memoFlattenOptions.value[index];
      if (!flattenItem) {
        props2.onActiveValue(null, -1, info);
        return;
      }
      props2.onActiveValue(flattenItem.value, index, info);
    };
    watch([() => memoFlattenOptions.value.length, () => baseProps.searchValue], () => {
      setActive(props2.defaultActiveFirstOption !== false ? getEnabledActiveIndex(0) : -1);
    }, {
      immediate: true
    });
    const isSelected = (value) => props2.rawValues.has(value) && baseProps.mode !== "combobox";
    watch([() => baseProps.open, () => baseProps.searchValue], () => {
      if (!baseProps.multiple && baseProps.open && props2.rawValues.size === 1) {
        const value = Array.from(props2.rawValues)[0];
        const index = toRaw(memoFlattenOptions.value).findIndex((_ref2) => {
          let {
            data
          } = _ref2;
          return data[props2.fieldNames.value] === value;
        });
        if (index !== -1) {
          setActive(index);
          nextTick(() => {
            scrollIntoView(index);
          });
        }
      }
      if (baseProps.open) {
        nextTick(() => {
          var _a;
          (_a = listRef.current) === null || _a === void 0 ? void 0 : _a.scrollTo(void 0);
        });
      }
    }, {
      immediate: true,
      flush: "post"
    });
    const onSelectValue = (value) => {
      if (value !== void 0) {
        props2.onSelect(value, {
          selected: !props2.rawValues.has(value)
        });
      }
      if (!baseProps.multiple) {
        baseProps.toggleOpen(false);
      }
    };
    const getLabel = (item) => typeof item.label === "function" ? item.label() : item.label;
    function renderItem(index) {
      const item = memoFlattenOptions.value[index];
      if (!item) return null;
      const itemData = item.data || {};
      const {
        value
      } = itemData;
      const {
        group
      } = item;
      const attrs = pickAttrs(itemData, true);
      const mergedLabel = getLabel(item);
      return item ? createVNode("div", _objectSpread2(_objectSpread2({
        "aria-label": typeof mergedLabel === "string" && !group ? mergedLabel : null
      }, attrs), {}, {
        "key": index,
        "role": group ? "presentation" : "option",
        "id": `${baseProps.id}_list_${index}`,
        "aria-selected": isSelected(value)
      }), [value]) : null;
    }
    const onKeydown = (event) => {
      const {
        which,
        ctrlKey
      } = event;
      switch (which) {
        case KeyCode.N:
        case KeyCode.P:
        case KeyCode.UP:
        case KeyCode.DOWN: {
          let offset = 0;
          if (which === KeyCode.UP) {
            offset = -1;
          } else if (which === KeyCode.DOWN) {
            offset = 1;
          } else if (isPlatformMac() && ctrlKey) {
            if (which === KeyCode.N) {
              offset = 1;
            } else if (which === KeyCode.P) {
              offset = -1;
            }
          }
          if (offset !== 0) {
            const nextActiveIndex = getEnabledActiveIndex(state.activeIndex + offset, offset);
            scrollIntoView(nextActiveIndex);
            setActive(nextActiveIndex, true);
          }
          break;
        }
        case KeyCode.ENTER: {
          const item = memoFlattenOptions.value[state.activeIndex];
          if (item && !item.data.disabled) {
            onSelectValue(item.value);
          } else {
            onSelectValue(void 0);
          }
          if (baseProps.open) {
            event.preventDefault();
          }
          break;
        }
        case KeyCode.ESC: {
          baseProps.toggleOpen(false);
          if (baseProps.open) {
            event.stopPropagation();
          }
        }
      }
    };
    const onKeyup = () => {
    };
    const scrollTo = (index) => {
      scrollIntoView(index);
    };
    expose({
      onKeydown,
      onKeyup,
      scrollTo
    });
    return () => {
      const {
        id,
        notFoundContent,
        onPopupScroll
      } = baseProps;
      const {
        menuItemSelectedIcon,
        fieldNames,
        virtual,
        listHeight,
        listItemHeight
      } = props2;
      const renderOption = slots.option;
      const {
        activeIndex
      } = state;
      const omitFieldNameList = Object.keys(fieldNames).map((key) => fieldNames[key]);
      if (memoFlattenOptions.value.length === 0) {
        return createVNode("div", {
          "role": "listbox",
          "id": `${id}_list`,
          "class": `${itemPrefixCls.value}-empty`,
          "onMousedown": onListMouseDown
        }, [notFoundContent]);
      }
      return createVNode(Fragment, null, [createVNode("div", {
        "role": "listbox",
        "id": `${id}_list`,
        "style": {
          height: 0,
          width: 0,
          overflow: "hidden"
        }
      }, [renderItem(activeIndex - 1), renderItem(activeIndex), renderItem(activeIndex + 1)]), createVNode(List, {
        "itemKey": "key",
        "ref": listRef,
        "data": memoFlattenOptions.value,
        "height": listHeight,
        "itemHeight": listItemHeight,
        "fullHeight": false,
        "onMousedown": onListMouseDown,
        "onScroll": onPopupScroll,
        "virtual": virtual
      }, {
        default: (item, itemIndex) => {
          var _a;
          const {
            group,
            groupOption,
            data,
            value
          } = item;
          const {
            key
          } = data;
          const label = typeof item.label === "function" ? item.label() : item.label;
          if (group) {
            const groupTitle = (_a = data.title) !== null && _a !== void 0 ? _a : isTitleType(label) && label;
            return createVNode("div", {
              "class": classNames(itemPrefixCls.value, `${itemPrefixCls.value}-group`),
              "title": groupTitle
            }, [renderOption ? renderOption(data) : label !== void 0 ? label : key]);
          }
          const {
            disabled,
            title,
            children,
            style,
            class: cls,
            className
          } = data, otherProps = __rest$1(data, ["disabled", "title", "children", "style", "class", "className"]);
          const passedProps = omit(otherProps, omitFieldNameList);
          const selected = isSelected(value);
          const optionPrefixCls = `${itemPrefixCls.value}-option`;
          const optionClassName = classNames(itemPrefixCls.value, optionPrefixCls, cls, className, {
            [`${optionPrefixCls}-grouped`]: groupOption,
            [`${optionPrefixCls}-active`]: activeIndex === itemIndex && !disabled,
            [`${optionPrefixCls}-disabled`]: disabled,
            [`${optionPrefixCls}-selected`]: selected
          });
          const mergedLabel = getLabel(item);
          const iconVisible = !menuItemSelectedIcon || typeof menuItemSelectedIcon === "function" || selected;
          const content = typeof mergedLabel === "number" ? mergedLabel : mergedLabel || value;
          let optionTitle = isTitleType(content) ? content.toString() : void 0;
          if (title !== void 0) {
            optionTitle = title;
          }
          return createVNode("div", _objectSpread2(_objectSpread2({}, passedProps), {}, {
            "aria-selected": selected,
            "class": optionClassName,
            "title": optionTitle,
            "onMousemove": (e) => {
              if (otherProps.onMousemove) {
                otherProps.onMousemove(e);
              }
              if (activeIndex === itemIndex || disabled) {
                return;
              }
              setActive(itemIndex);
            },
            "onClick": (e) => {
              if (!disabled) {
                onSelectValue(value);
              }
              if (otherProps.onClick) {
                otherProps.onClick(e);
              }
            },
            "style": style
          }), [createVNode("div", {
            "class": `${optionPrefixCls}-content`
          }, [renderOption ? renderOption(data) : content]), isValidElement(menuItemSelectedIcon) || selected, iconVisible && createVNode(TransBtn, {
            "class": `${itemPrefixCls.value}-option-state`,
            "customizeIcon": menuItemSelectedIcon,
            "customizeIconProps": {
              isSelected: selected
            }
          }, {
            default: () => [selected ? "✓" : null]
          })]);
        }
      })]);
    };
  }
});
var __rest = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
function convertNodeToOption(node) {
  const _a = node, {
    key,
    children
  } = _a, _b = _a.props, {
    value,
    disabled
  } = _b, restProps = __rest(_b, ["value", "disabled"]);
  const child = children === null || children === void 0 ? void 0 : children.default;
  return _extends({
    key,
    value: value !== void 0 ? value : key,
    children: child,
    disabled: disabled || disabled === ""
  }, restProps);
}
function convertChildrenToData(nodes) {
  let optionOnly = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : false;
  const dd = flattenChildren(nodes).map((node, index) => {
    var _a;
    if (!isValidElement(node) || !node.type) {
      return null;
    }
    const {
      type: {
        isSelectOptGroup
      },
      key,
      children,
      props: props2
    } = node;
    if (optionOnly || !isSelectOptGroup) {
      return convertNodeToOption(node);
    }
    const child = children && children.default ? children.default() : void 0;
    const label = (props2 === null || props2 === void 0 ? void 0 : props2.label) || ((_a = children.label) === null || _a === void 0 ? void 0 : _a.call(children)) || key;
    return _extends(_extends({
      key: `__RC_SELECT_GRP__${key === null ? index : String(key)}__`
    }, props2), {
      label,
      options: convertChildrenToData(child || [])
    });
  }).filter((data) => data);
  return dd;
}
function useOptions(options, children, fieldNames) {
  const mergedOptions = shallowRef();
  const valueOptions = shallowRef();
  const labelOptions = shallowRef();
  const tempMergedOptions = shallowRef([]);
  watch([options, children], () => {
    if (options.value) {
      tempMergedOptions.value = toRaw(options.value).slice();
    } else {
      tempMergedOptions.value = convertChildrenToData(children.value);
    }
  }, {
    immediate: true,
    deep: true
  });
  watchEffect(() => {
    const newOptions = tempMergedOptions.value;
    const newValueOptions = /* @__PURE__ */ new Map();
    const newLabelOptions = /* @__PURE__ */ new Map();
    const fieldNamesValue = fieldNames.value;
    function dig(optionList) {
      let isChildren = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : false;
      for (let i = 0; i < optionList.length; i += 1) {
        const option = optionList[i];
        if (!option[fieldNamesValue.options] || isChildren) {
          newValueOptions.set(option[fieldNamesValue.value], option);
          newLabelOptions.set(option[fieldNamesValue.label], option);
        } else {
          dig(option[fieldNamesValue.options], true);
        }
      }
    }
    dig(newOptions);
    mergedOptions.value = newOptions;
    valueOptions.value = newValueOptions;
    labelOptions.value = newLabelOptions;
  });
  return {
    options: mergedOptions,
    valueOptions,
    labelOptions
  };
}
let uuid = 0;
const isBrowserClient = process.env.NODE_ENV !== "test" && canUseDom();
function getUUID() {
  let retId;
  if (isBrowserClient) {
    retId = uuid;
    uuid += 1;
  } else {
    retId = "TEST_OR_SSR";
  }
  return retId;
}
function useId() {
  let id = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : ref("");
  const innerId = `rc_select_${getUUID()}`;
  return id.value || innerId;
}
function toArray(value) {
  if (Array.isArray(value)) {
    return value;
  }
  return value !== void 0 ? [value] : [];
}
const isClient = typeof window !== "undefined" && window.document && window.document.documentElement;
process.env.NODE_ENV !== "test" && isClient;
function warningProps(props2) {
  const {
    mode,
    options,
    children,
    backfill,
    allowClear,
    placeholder,
    getInputElement,
    showSearch,
    onSearch,
    defaultOpen,
    autofocus,
    labelInValue,
    value,
    inputValue,
    optionLabelProp
  } = props2;
  const multiple = isMultiple(mode);
  const mergedShowSearch = showSearch !== void 0 ? showSearch : multiple || mode === "combobox";
  const mergedOptions = options || convertChildrenToData(children);
  warningOnce(mode !== "tags" || mergedOptions.every((opt) => !opt.disabled), "Please avoid setting option to disabled in tags mode since user can always type text as tag.");
  warningOnce(mode !== "combobox" || !optionLabelProp, "`combobox` mode not support `optionLabelProp`. Please set `value` on Option directly.");
  warningOnce(mode === "combobox" || !backfill, "`backfill` only works with `combobox` mode.");
  warningOnce(mode === "combobox" || !getInputElement, "`getInputElement` only work with `combobox` mode.");
  noteOnce(mode !== "combobox" || !getInputElement || !allowClear || !placeholder, "Customize `getInputElement` should customize clear and placeholder logic instead of configuring `allowClear` and `placeholder`.");
  if (onSearch && !mergedShowSearch && mode !== "combobox" && mode !== "tags") {
    warningOnce(false, "`onSearch` should work with `showSearch` instead of use alone.");
  }
  noteOnce(!defaultOpen || autofocus, "`defaultOpen` makes Select open without focus which means it will not close by click outside. You can set `autofocus` if needed.");
  if (value !== void 0 && value !== null) {
    const values = toArray(value);
    warningOnce(!labelInValue || values.every((val) => typeof val === "object" && ("key" in val || "value" in val)), "`value` should in shape of `{ value: string | number, label?: any }` when you set `labelInValue` to `true`");
    warningOnce(!multiple || Array.isArray(value), "`value` should be array when `mode` is `multiple` or `tags`");
  }
  if (children) {
    let invalidateChildType = null;
    children.some((node) => {
      var _a;
      if (!isValidElement(node) || !node.type) {
        return false;
      }
      const {
        type
      } = node;
      if (type.isSelectOption) {
        return false;
      }
      if (type.isSelectOptGroup) {
        const childs = ((_a = node.children) === null || _a === void 0 ? void 0 : _a.default()) || [];
        const allChildrenValid = childs.every((subNode) => {
          if (!isValidElement(subNode) || !node.type || subNode.type.isSelectOption) {
            return true;
          }
          invalidateChildType = subNode.type;
          return false;
        });
        if (allChildrenValid) {
          return false;
        }
        return true;
      }
      invalidateChildType = type;
      return true;
    });
    if (invalidateChildType) {
      warningOnce(false, `\`children\` should be \`Select.Option\` or \`Select.OptGroup\` instead of \`${invalidateChildType.displayName || invalidateChildType.name || invalidateChildType}\`.`);
    }
    warningOnce(inputValue === void 0, "`inputValue` is deprecated, please use `searchValue` instead.");
  }
}
function includes(test, search) {
  return toArray(test).join("").toUpperCase().includes(search);
}
const useFilterOptions = (options, fieldNames, searchValue, filterOption, optionFilterProp) => computed(() => {
  const searchValueVal = searchValue.value;
  const optionFilterPropValue = optionFilterProp === null || optionFilterProp === void 0 ? void 0 : optionFilterProp.value;
  const filterOptionValue = filterOption === null || filterOption === void 0 ? void 0 : filterOption.value;
  if (!searchValueVal || filterOptionValue === false) {
    return options.value;
  }
  const {
    options: fieldOptions,
    label: fieldLabel,
    value: fieldValue
  } = fieldNames.value;
  const filteredOptions = [];
  const customizeFilter = typeof filterOptionValue === "function";
  const upperSearch = searchValueVal.toUpperCase();
  const filterFunc = customizeFilter ? filterOptionValue : (_, option) => {
    if (optionFilterPropValue) {
      return includes(option[optionFilterPropValue], upperSearch);
    }
    if (option[fieldOptions]) {
      return includes(option[fieldLabel !== "children" ? fieldLabel : "label"], upperSearch);
    }
    return includes(option[fieldValue], upperSearch);
  };
  const wrapOption = customizeFilter ? (opt) => injectPropsWithOption(opt) : (opt) => opt;
  options.value.forEach((item) => {
    if (item[fieldOptions]) {
      const matchGroup = filterFunc(searchValueVal, wrapOption(item));
      if (matchGroup) {
        filteredOptions.push(item);
      } else {
        const subOptions = item[fieldOptions].filter((subItem) => filterFunc(searchValueVal, wrapOption(subItem)));
        if (subOptions.length) {
          filteredOptions.push(_extends(_extends({}, item), {
            [fieldOptions]: subOptions
          }));
        }
      }
      return;
    }
    if (filterFunc(searchValueVal, wrapOption(item))) {
      filteredOptions.push(item);
    }
  });
  return filteredOptions;
});
const useCache = (labeledValues, valueOptions) => {
  const cacheRef = shallowRef({
    values: /* @__PURE__ */ new Map(),
    options: /* @__PURE__ */ new Map()
  });
  const filledLabeledValues = computed(() => {
    const {
      values: prevValueCache,
      options: prevOptionCache
    } = cacheRef.value;
    const patchedValues = labeledValues.value.map((item) => {
      var _a;
      if (item.label === void 0) {
        return _extends(_extends({}, item), {
          label: (_a = prevValueCache.get(item.value)) === null || _a === void 0 ? void 0 : _a.label
        });
      }
      return item;
    });
    const valueCache = /* @__PURE__ */ new Map();
    const optionCache = /* @__PURE__ */ new Map();
    patchedValues.forEach((item) => {
      valueCache.set(item.value, item);
      optionCache.set(item.value, valueOptions.value.get(item.value) || prevOptionCache.get(item.value));
    });
    cacheRef.value.values = valueCache;
    cacheRef.value.options = optionCache;
    return patchedValues;
  });
  const getOption = (val) => valueOptions.value.get(val) || cacheRef.value.options.get(val);
  return [filledLabeledValues, getOption];
};
function useMergedState(defaultStateValue, option) {
  const {
    defaultValue,
    value = ref()
  } = option || {};
  let initValue = typeof defaultStateValue === "function" ? defaultStateValue() : defaultStateValue;
  if (value.value !== void 0) {
    initValue = unref(value);
  }
  if (defaultValue !== void 0) {
    initValue = typeof defaultValue === "function" ? defaultValue() : defaultValue;
  }
  const innerValue = ref(initValue);
  const mergedValue = ref(initValue);
  watchEffect(() => {
    let val = value.value !== void 0 ? value.value : innerValue.value;
    if (option.postState) {
      val = option.postState(val);
    }
    mergedValue.value = val;
  });
  function triggerChange(newValue) {
    const preVal = mergedValue.value;
    innerValue.value = newValue;
    if (toRaw(mergedValue.value) !== newValue && option.onChange) {
      option.onChange(newValue, preVal);
    }
  }
  watch(value, () => {
    innerValue.value = value.value;
  });
  return [mergedValue, triggerChange];
}
const OMIT_DOM_PROPS = ["inputValue"];
function selectProps$1() {
  return _extends(_extends({}, baseSelectPropsWithoutPrivate()), {
    prefixCls: String,
    id: String,
    backfill: {
      type: Boolean,
      default: void 0
    },
    // >>> Field Names
    fieldNames: Object,
    // >>> Search
    /** @deprecated Use `searchValue` instead */
    inputValue: String,
    searchValue: String,
    onSearch: Function,
    autoClearSearchValue: {
      type: Boolean,
      default: void 0
    },
    // >>> Select
    onSelect: Function,
    onDeselect: Function,
    // >>> Options
    /**
     * In Select, `false` means do nothing.
     * In TreeSelect, `false` will highlight match item.
     * It's by design.
     */
    filterOption: {
      type: [Boolean, Function],
      default: void 0
    },
    filterSort: Function,
    optionFilterProp: String,
    optionLabelProp: String,
    options: Array,
    defaultActiveFirstOption: {
      type: Boolean,
      default: void 0
    },
    virtual: {
      type: Boolean,
      default: void 0
    },
    listHeight: Number,
    listItemHeight: Number,
    // >>> Icon
    menuItemSelectedIcon: PropTypes.any,
    mode: String,
    labelInValue: {
      type: Boolean,
      default: void 0
    },
    value: PropTypes.any,
    defaultValue: PropTypes.any,
    onChange: Function,
    children: Array
  });
}
function isRawValue(value) {
  return !value || typeof value !== "object";
}
const Select$1 = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "VcSelect",
  inheritAttrs: false,
  props: initDefaultProps(selectProps$1(), {
    prefixCls: "vc-select",
    autoClearSearchValue: true,
    listHeight: 200,
    listItemHeight: 20,
    dropdownMatchSelectWidth: true
  }),
  setup(props2, _ref) {
    let {
      expose,
      attrs,
      slots
    } = _ref;
    const mergedId = useId(toRef(props2, "id"));
    const multiple = computed(() => isMultiple(props2.mode));
    const childrenAsData = computed(() => !!(!props2.options && props2.children));
    const mergedFilterOption = computed(() => {
      if (props2.filterOption === void 0 && props2.mode === "combobox") {
        return false;
      }
      return props2.filterOption;
    });
    const mergedFieldNames = computed(() => fillFieldNames(props2.fieldNames, childrenAsData.value));
    const [mergedSearchValue, setSearchValue] = useMergedState("", {
      value: computed(() => props2.searchValue !== void 0 ? props2.searchValue : props2.inputValue),
      postState: (search) => search || ""
    });
    const parsedOptions = useOptions(toRef(props2, "options"), toRef(props2, "children"), mergedFieldNames);
    const {
      valueOptions,
      labelOptions,
      options: mergedOptions
    } = parsedOptions;
    const convert2LabelValues = (draftValues) => {
      const valueList = toArray(draftValues);
      return valueList.map((val) => {
        var _a, _b;
        let rawValue;
        let rawLabel;
        let rawKey;
        let rawDisabled;
        if (isRawValue(val)) {
          rawValue = val;
        } else {
          rawKey = val.key;
          rawLabel = val.label;
          rawValue = (_a = val.value) !== null && _a !== void 0 ? _a : rawKey;
        }
        const option = valueOptions.value.get(rawValue);
        if (option) {
          if (rawLabel === void 0) rawLabel = option === null || option === void 0 ? void 0 : option[props2.optionLabelProp || mergedFieldNames.value.label];
          if (rawKey === void 0) rawKey = (_b = option === null || option === void 0 ? void 0 : option.key) !== null && _b !== void 0 ? _b : rawValue;
          rawDisabled = option === null || option === void 0 ? void 0 : option.disabled;
        }
        return {
          label: rawLabel,
          value: rawValue,
          key: rawKey,
          disabled: rawDisabled,
          option
        };
      });
    };
    const [internalValue, setInternalValue] = useMergedState(props2.defaultValue, {
      value: toRef(props2, "value")
    });
    const rawLabeledValues = computed(() => {
      var _a;
      const values = convert2LabelValues(internalValue.value);
      if (props2.mode === "combobox" && !((_a = values[0]) === null || _a === void 0 ? void 0 : _a.value)) {
        return [];
      }
      return values;
    });
    const [mergedValues, getMixedOption] = useCache(rawLabeledValues, valueOptions);
    const displayValues = computed(() => {
      if (!props2.mode && mergedValues.value.length === 1) {
        const firstValue = mergedValues.value[0];
        if (firstValue.value === null && (firstValue.label === null || firstValue.label === void 0)) {
          return [];
        }
      }
      return mergedValues.value.map((item) => {
        var _a;
        return _extends(_extends({}, item), {
          label: (_a = typeof item.label === "function" ? item.label() : item.label) !== null && _a !== void 0 ? _a : item.value
        });
      });
    });
    const rawValues = computed(() => new Set(mergedValues.value.map((val) => val.value)));
    watchEffect(() => {
      var _a;
      if (props2.mode === "combobox") {
        const strValue = (_a = mergedValues.value[0]) === null || _a === void 0 ? void 0 : _a.value;
        if (strValue !== void 0 && strValue !== null) {
          setSearchValue(String(strValue));
        }
      }
    }, {
      flush: "post"
    });
    const createTagOption = (val, label) => {
      const mergedLabel = label !== null && label !== void 0 ? label : val;
      return {
        [mergedFieldNames.value.value]: val,
        [mergedFieldNames.value.label]: mergedLabel
      };
    };
    const filledTagOptions = shallowRef();
    watchEffect(() => {
      if (props2.mode !== "tags") {
        filledTagOptions.value = mergedOptions.value;
        return;
      }
      const cloneOptions = mergedOptions.value.slice();
      const existOptions = (val) => valueOptions.value.has(val);
      [...mergedValues.value].sort((a, b) => a.value < b.value ? -1 : 1).forEach((item) => {
        const val = item.value;
        if (!existOptions(val)) {
          cloneOptions.push(createTagOption(val, item.label));
        }
      });
      filledTagOptions.value = cloneOptions;
    });
    const filteredOptions = useFilterOptions(filledTagOptions, mergedFieldNames, mergedSearchValue, mergedFilterOption, toRef(props2, "optionFilterProp"));
    const filledSearchOptions = computed(() => {
      if (props2.mode !== "tags" || !mergedSearchValue.value || filteredOptions.value.some((item) => item[props2.optionFilterProp || "value"] === mergedSearchValue.value)) {
        return filteredOptions.value;
      }
      return [createTagOption(mergedSearchValue.value), ...filteredOptions.value];
    });
    const orderedFilteredOptions = computed(() => {
      if (!props2.filterSort) {
        return filledSearchOptions.value;
      }
      return [...filledSearchOptions.value].sort((a, b) => props2.filterSort(a, b));
    });
    const displayOptions = computed(() => flattenOptions(orderedFilteredOptions.value, {
      fieldNames: mergedFieldNames.value,
      childrenAsData: childrenAsData.value
    }));
    const triggerChange = (values) => {
      const labeledValues = convert2LabelValues(values);
      setInternalValue(labeledValues);
      if (props2.onChange && // Trigger event only when value changed
      (labeledValues.length !== mergedValues.value.length || labeledValues.some((newVal, index) => {
        var _a;
        return ((_a = mergedValues.value[index]) === null || _a === void 0 ? void 0 : _a.value) !== (newVal === null || newVal === void 0 ? void 0 : newVal.value);
      }))) {
        const returnValues = props2.labelInValue ? labeledValues.map((v) => {
          return _extends(_extends({}, v), {
            originLabel: v.label,
            label: typeof v.label === "function" ? v.label() : v.label
          });
        }) : labeledValues.map((v) => v.value);
        const returnOptions = labeledValues.map((v) => injectPropsWithOption(getMixedOption(v.value)));
        props2.onChange(
          // Value
          multiple.value ? returnValues : returnValues[0],
          // Option
          multiple.value ? returnOptions : returnOptions[0]
        );
      }
    };
    const [activeValue, setActiveValue] = useState(null);
    const [accessibilityIndex, setAccessibilityIndex] = useState(0);
    const mergedDefaultActiveFirstOption = computed(() => props2.defaultActiveFirstOption !== void 0 ? props2.defaultActiveFirstOption : props2.mode !== "combobox");
    const onActiveValue = function(active, index) {
      let {
        source = "keyboard"
      } = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : {};
      setAccessibilityIndex(index);
      if (props2.backfill && props2.mode === "combobox" && active !== null && source === "keyboard") {
        setActiveValue(String(active));
      }
    };
    const triggerSelect = (val, selected) => {
      const getSelectEnt = () => {
        var _a;
        const option = getMixedOption(val);
        const originLabel = option === null || option === void 0 ? void 0 : option[mergedFieldNames.value.label];
        return [props2.labelInValue ? {
          label: typeof originLabel === "function" ? originLabel() : originLabel,
          originLabel,
          value: val,
          key: (_a = option === null || option === void 0 ? void 0 : option.key) !== null && _a !== void 0 ? _a : val
        } : val, injectPropsWithOption(option)];
      };
      if (selected && props2.onSelect) {
        const [wrappedValue, option] = getSelectEnt();
        props2.onSelect(wrappedValue, option);
      } else if (!selected && props2.onDeselect) {
        const [wrappedValue, option] = getSelectEnt();
        props2.onDeselect(wrappedValue, option);
      }
    };
    const onInternalSelect = (val, info) => {
      let cloneValues;
      const mergedSelect = multiple.value ? info.selected : true;
      if (mergedSelect) {
        cloneValues = multiple.value ? [...mergedValues.value, val] : [val];
      } else {
        cloneValues = mergedValues.value.filter((v) => v.value !== val);
      }
      triggerChange(cloneValues);
      triggerSelect(val, mergedSelect);
      if (props2.mode === "combobox") {
        setActiveValue("");
      } else if (!multiple.value || props2.autoClearSearchValue) {
        setSearchValue("");
        setActiveValue("");
      }
    };
    const onDisplayValuesChange = (nextValues, info) => {
      triggerChange(nextValues);
      if (info.type === "remove" || info.type === "clear") {
        info.values.forEach((item) => {
          triggerSelect(item.value, false);
        });
      }
    };
    const onInternalSearch = (searchText, info) => {
      var _a;
      setSearchValue(searchText);
      setActiveValue(null);
      if (info.source === "submit") {
        const formatted = (searchText || "").trim();
        if (formatted) {
          const newRawValues = Array.from(/* @__PURE__ */ new Set([...rawValues.value, formatted]));
          triggerChange(newRawValues);
          triggerSelect(formatted, true);
          setSearchValue("");
        }
        return;
      }
      if (info.source !== "blur") {
        if (props2.mode === "combobox") {
          triggerChange(searchText);
        }
        (_a = props2.onSearch) === null || _a === void 0 ? void 0 : _a.call(props2, searchText);
      }
    };
    const onInternalSearchSplit = (words) => {
      let patchValues = words;
      if (props2.mode !== "tags") {
        patchValues = words.map((word) => {
          const opt = labelOptions.value.get(word);
          return opt === null || opt === void 0 ? void 0 : opt.value;
        }).filter((val) => val !== void 0);
      }
      const newRawValues = Array.from(/* @__PURE__ */ new Set([...rawValues.value, ...patchValues]));
      triggerChange(newRawValues);
      newRawValues.forEach((newRawValue) => {
        triggerSelect(newRawValue, true);
      });
    };
    const realVirtual = computed(() => props2.virtual !== false && props2.dropdownMatchSelectWidth !== false);
    useProvideSelectProps(toReactive(_extends(_extends({}, parsedOptions), {
      flattenOptions: displayOptions,
      onActiveValue,
      defaultActiveFirstOption: mergedDefaultActiveFirstOption,
      onSelect: onInternalSelect,
      menuItemSelectedIcon: toRef(props2, "menuItemSelectedIcon"),
      rawValues,
      fieldNames: mergedFieldNames,
      virtual: realVirtual,
      listHeight: toRef(props2, "listHeight"),
      listItemHeight: toRef(props2, "listItemHeight"),
      childrenAsData
    })));
    if (process.env.NODE_ENV !== "production") {
      watchEffect(() => {
        warningProps(props2);
      }, {
        flush: "post"
      });
    }
    const selectRef = ref();
    expose({
      focus() {
        var _a;
        (_a = selectRef.value) === null || _a === void 0 ? void 0 : _a.focus();
      },
      blur() {
        var _a;
        (_a = selectRef.value) === null || _a === void 0 ? void 0 : _a.blur();
      },
      scrollTo(arg) {
        var _a;
        (_a = selectRef.value) === null || _a === void 0 ? void 0 : _a.scrollTo(arg);
      }
    });
    const pickProps = computed(() => {
      return omit(props2, [
        "id",
        "mode",
        "prefixCls",
        "backfill",
        "fieldNames",
        // Search
        "inputValue",
        "searchValue",
        "onSearch",
        "autoClearSearchValue",
        // Select
        "onSelect",
        "onDeselect",
        "dropdownMatchSelectWidth",
        // Options
        "filterOption",
        "filterSort",
        "optionFilterProp",
        "optionLabelProp",
        "options",
        "children",
        "defaultActiveFirstOption",
        "menuItemSelectedIcon",
        "virtual",
        "listHeight",
        "listItemHeight",
        // Value
        "value",
        "defaultValue",
        "labelInValue",
        "onChange"
      ]);
    });
    return () => {
      return createVNode(BaseSelect, _objectSpread2(_objectSpread2(_objectSpread2({}, pickProps.value), attrs), {}, {
        "id": mergedId,
        "prefixCls": props2.prefixCls,
        "ref": selectRef,
        "omitDomProps": OMIT_DOM_PROPS,
        "mode": props2.mode,
        "displayValues": displayValues.value,
        "onDisplayValuesChange": onDisplayValuesChange,
        "searchValue": mergedSearchValue.value,
        "onSearch": onInternalSearch,
        "onSearchSplit": onInternalSearchSplit,
        "dropdownMatchSelectWidth": props2.dropdownMatchSelectWidth,
        "OptionList": OptionList,
        "emptyOptions": !displayOptions.value.length,
        "activeValue": activeValue.value,
        "activeDescendantId": `${mergedId}_list_${accessibilityIndex.value}`
      }), slots);
    };
  }
});
const Option = () => null;
Option.isSelectOption = true;
Option.displayName = "ASelectOption";
const OptGroup = () => null;
OptGroup.isSelectOptGroup = true;
OptGroup.displayName = "ASelectOptGroup";
var DownOutlined$1 = { "icon": { "tag": "svg", "attrs": { "viewBox": "64 64 896 896", "focusable": "false" }, "children": [{ "tag": "path", "attrs": { "d": "M884 256h-75c-5.1 0-9.9 2.5-12.9 6.6L512 654.2 227.9 262.6c-3-4.1-7.8-6.6-12.9-6.6h-75c-6.5 0-10.3 7.4-6.5 12.7l352.6 486.1c12.8 17.6 39 17.6 51.7 0l352.6-486.1c3.9-5.3.1-12.7-6.4-12.7z" } }] }, "name": "down", "theme": "outlined" };
function _objectSpread$1(target) {
  for (var i = 1; i < arguments.length; i++) {
    var source = arguments[i] != null ? Object(arguments[i]) : {};
    var ownKeys = Object.keys(source);
    if (typeof Object.getOwnPropertySymbols === "function") {
      ownKeys = ownKeys.concat(Object.getOwnPropertySymbols(source).filter(function(sym) {
        return Object.getOwnPropertyDescriptor(source, sym).enumerable;
      }));
    }
    ownKeys.forEach(function(key) {
      _defineProperty$1(target, key, source[key]);
    });
  }
  return target;
}
function _defineProperty$1(obj, key, value) {
  if (key in obj) {
    Object.defineProperty(obj, key, { value, enumerable: true, configurable: true, writable: true });
  } else {
    obj[key] = value;
  }
  return obj;
}
var DownOutlined = function DownOutlined2(props2, context) {
  var p = _objectSpread$1({}, props2, context.attrs);
  return createVNode(Icon, _objectSpread$1({}, p, {
    "icon": DownOutlined$1
  }), null);
};
DownOutlined.displayName = "DownOutlined";
DownOutlined.inheritAttrs = false;
var CheckOutlined$1 = { "icon": { "tag": "svg", "attrs": { "viewBox": "64 64 896 896", "focusable": "false" }, "children": [{ "tag": "path", "attrs": { "d": "M912 190h-69.9c-9.8 0-19.1 4.5-25.1 12.2L404.7 724.5 207 474a32 32 0 00-25.1-12.2H112c-6.7 0-10.4 7.7-6.3 12.9l273.9 347c12.8 16.2 37.4 16.2 50.3 0l488.4-618.9c4.1-5.1.4-12.8-6.3-12.8z" } }] }, "name": "check", "theme": "outlined" };
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
var CheckOutlined = function CheckOutlined2(props2, context) {
  var p = _objectSpread({}, props2, context.attrs);
  return createVNode(Icon, _objectSpread({}, p, {
    "icon": CheckOutlined$1
  }), null);
};
CheckOutlined.displayName = "CheckOutlined";
CheckOutlined.inheritAttrs = false;
function getIcons(props2) {
  let slots = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
  const {
    loading,
    multiple,
    prefixCls,
    hasFeedback,
    feedbackIcon,
    showArrow
  } = props2;
  const suffixIcon = props2.suffixIcon || slots.suffixIcon && slots.suffixIcon();
  const clearIcon = props2.clearIcon || slots.clearIcon && slots.clearIcon();
  const menuItemSelectedIcon = props2.menuItemSelectedIcon || slots.menuItemSelectedIcon && slots.menuItemSelectedIcon();
  const removeIcon = props2.removeIcon || slots.removeIcon && slots.removeIcon();
  const mergedClearIcon = clearIcon !== null && clearIcon !== void 0 ? clearIcon : createVNode(CloseCircleFilled, null, null);
  const getSuffixIconNode = (arrowIcon) => createVNode(Fragment, null, [showArrow !== false && arrowIcon, hasFeedback && feedbackIcon]);
  let mergedSuffixIcon = null;
  if (suffixIcon !== void 0) {
    mergedSuffixIcon = getSuffixIconNode(suffixIcon);
  } else if (loading) {
    mergedSuffixIcon = getSuffixIconNode(createVNode(LoadingOutlined, {
      "spin": true
    }, null));
  } else {
    const iconCls = `${prefixCls}-suffix`;
    mergedSuffixIcon = (_ref) => {
      let {
        open: open2,
        showSearch
      } = _ref;
      if (open2 && showSearch) {
        return getSuffixIconNode(createVNode(SearchOutlined, {
          "class": iconCls
        }, null));
      }
      return getSuffixIconNode(createVNode(DownOutlined, {
        "class": iconCls
      }, null));
    };
  }
  let mergedItemIcon = null;
  if (menuItemSelectedIcon !== void 0) {
    mergedItemIcon = menuItemSelectedIcon;
  } else if (multiple) {
    mergedItemIcon = createVNode(CheckOutlined, null, null);
  } else {
    mergedItemIcon = null;
  }
  let mergedRemoveIcon = null;
  if (removeIcon !== void 0) {
    mergedRemoveIcon = removeIcon;
  } else {
    mergedRemoveIcon = createVNode(CloseOutlined, null, null);
  }
  return {
    clearIcon: mergedClearIcon,
    suffixIcon: mergedSuffixIcon,
    itemIcon: mergedItemIcon,
    removeIcon: mergedRemoveIcon
  };
}
const genItemStyle = (token) => {
  const {
    controlPaddingHorizontal
  } = token;
  return {
    position: "relative",
    display: "block",
    minHeight: token.controlHeight,
    padding: `${(token.controlHeight - token.fontSize * token.lineHeight) / 2}px ${controlPaddingHorizontal}px`,
    color: token.colorText,
    fontWeight: "normal",
    fontSize: token.fontSize,
    lineHeight: token.lineHeight,
    boxSizing: "border-box"
  };
};
const genSingleStyle$1 = (token) => {
  const {
    antCls,
    componentCls
  } = token;
  const selectItemCls = `${componentCls}-item`;
  return [
    {
      [`${componentCls}-dropdown`]: _extends(_extends({}, resetComponent(token)), {
        position: "absolute",
        top: -9999,
        zIndex: token.zIndexPopup,
        boxSizing: "border-box",
        padding: token.paddingXXS,
        overflow: "hidden",
        fontSize: token.fontSize,
        // Fix select render lag of long text in chrome
        // https://github.com/ant-design/ant-design/issues/11456
        // https://github.com/ant-design/ant-design/issues/11843
        fontVariant: "initial",
        backgroundColor: token.colorBgElevated,
        borderRadius: token.borderRadiusLG,
        outline: "none",
        boxShadow: token.boxShadowSecondary,
        [`
            &${antCls}-slide-up-enter${antCls}-slide-up-enter-active${componentCls}-dropdown-placement-bottomLeft,
            &${antCls}-slide-up-appear${antCls}-slide-up-appear-active${componentCls}-dropdown-placement-bottomLeft
          `]: {
          animationName: slideUpIn
        },
        [`
            &${antCls}-slide-up-enter${antCls}-slide-up-enter-active${componentCls}-dropdown-placement-topLeft,
            &${antCls}-slide-up-appear${antCls}-slide-up-appear-active${componentCls}-dropdown-placement-topLeft
          `]: {
          animationName: slideDownIn
        },
        [`&${antCls}-slide-up-leave${antCls}-slide-up-leave-active${componentCls}-dropdown-placement-bottomLeft`]: {
          animationName: slideUpOut
        },
        [`&${antCls}-slide-up-leave${antCls}-slide-up-leave-active${componentCls}-dropdown-placement-topLeft`]: {
          animationName: slideDownOut
        },
        "&-hidden": {
          display: "none"
        },
        "&-empty": {
          color: token.colorTextDisabled
        },
        // ========================= Options =========================
        [`${selectItemCls}-empty`]: _extends(_extends({}, genItemStyle(token)), {
          color: token.colorTextDisabled
        }),
        [`${selectItemCls}`]: _extends(_extends({}, genItemStyle(token)), {
          cursor: "pointer",
          transition: `background ${token.motionDurationSlow} ease`,
          borderRadius: token.borderRadiusSM,
          // =========== Group ============
          "&-group": {
            color: token.colorTextDescription,
            fontSize: token.fontSizeSM,
            cursor: "default"
          },
          // =========== Option ===========
          "&-option": {
            display: "flex",
            "&-content": _extends({
              flex: "auto"
            }, textEllipsis),
            "&-state": {
              flex: "none"
            },
            [`&-active:not(${selectItemCls}-option-disabled)`]: {
              backgroundColor: token.controlItemBgHover
            },
            [`&-selected:not(${selectItemCls}-option-disabled)`]: {
              color: token.colorText,
              fontWeight: token.fontWeightStrong,
              backgroundColor: token.controlItemBgActive,
              [`${selectItemCls}-option-state`]: {
                color: token.colorPrimary
              }
            },
            "&-disabled": {
              [`&${selectItemCls}-option-selected`]: {
                backgroundColor: token.colorBgContainerDisabled
              },
              color: token.colorTextDisabled,
              cursor: "not-allowed"
            },
            "&-grouped": {
              paddingInlineStart: token.controlPaddingHorizontal * 2
            }
          }
        }),
        // =========================== RTL ===========================
        "&-rtl": {
          direction: "rtl"
        }
      })
    },
    // Follow code may reuse in other components
    initSlideMotion(token, "slide-up"),
    initSlideMotion(token, "slide-down"),
    initMoveMotion(token, "move-up"),
    initMoveMotion(token, "move-down")
  ];
};
const FIXED_ITEM_MARGIN = 2;
function getSelectItemStyle(_ref) {
  let {
    controlHeightSM,
    controlHeight,
    lineWidth: borderWidth
  } = _ref;
  const selectItemDist = (controlHeight - controlHeightSM) / 2 - borderWidth;
  const selectItemMargin = Math.ceil(selectItemDist / 2);
  return [selectItemDist, selectItemMargin];
}
function genSizeStyle$1(token, suffix) {
  const {
    componentCls,
    iconCls
  } = token;
  const selectOverflowPrefixCls = `${componentCls}-selection-overflow`;
  const selectItemHeight = token.controlHeightSM;
  const [selectItemDist] = getSelectItemStyle(token);
  const suffixCls = suffix ? `${componentCls}-${suffix}` : "";
  return {
    [`${componentCls}-multiple${suffixCls}`]: {
      fontSize: token.fontSize,
      /**
       * Do not merge `height` & `line-height` under style with `selection` & `search`, since chrome
       * may update to redesign with its align logic.
       */
      // =========================== Overflow ===========================
      [selectOverflowPrefixCls]: {
        position: "relative",
        display: "flex",
        flex: "auto",
        flexWrap: "wrap",
        maxWidth: "100%",
        "&-item": {
          flex: "none",
          alignSelf: "center",
          maxWidth: "100%",
          display: "inline-flex"
        }
      },
      // ========================= Selector =========================
      [`${componentCls}-selector`]: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        // Multiple is little different that horizontal is follow the vertical
        padding: `${selectItemDist - FIXED_ITEM_MARGIN}px ${FIXED_ITEM_MARGIN * 2}px`,
        borderRadius: token.borderRadius,
        [`${componentCls}-show-search&`]: {
          cursor: "text"
        },
        [`${componentCls}-disabled&`]: {
          background: token.colorBgContainerDisabled,
          cursor: "not-allowed"
        },
        "&:after": {
          display: "inline-block",
          width: 0,
          margin: `${FIXED_ITEM_MARGIN}px 0`,
          lineHeight: `${selectItemHeight}px`,
          content: '"\\a0"'
        }
      },
      [`
        &${componentCls}-show-arrow ${componentCls}-selector,
        &${componentCls}-allow-clear ${componentCls}-selector
      `]: {
        paddingInlineEnd: token.fontSizeIcon + token.controlPaddingHorizontal
      },
      // ======================== Selections ========================
      [`${componentCls}-selection-item`]: {
        position: "relative",
        display: "flex",
        flex: "none",
        boxSizing: "border-box",
        maxWidth: "100%",
        height: selectItemHeight,
        marginTop: FIXED_ITEM_MARGIN,
        marginBottom: FIXED_ITEM_MARGIN,
        lineHeight: `${selectItemHeight - token.lineWidth * 2}px`,
        background: token.colorFillSecondary,
        border: `${token.lineWidth}px solid ${token.colorSplit}`,
        borderRadius: token.borderRadiusSM,
        cursor: "default",
        transition: `font-size ${token.motionDurationSlow}, line-height ${token.motionDurationSlow}, height ${token.motionDurationSlow}`,
        userSelect: "none",
        marginInlineEnd: FIXED_ITEM_MARGIN * 2,
        paddingInlineStart: token.paddingXS,
        paddingInlineEnd: token.paddingXS / 2,
        [`${componentCls}-disabled&`]: {
          color: token.colorTextDisabled,
          borderColor: token.colorBorder,
          cursor: "not-allowed"
        },
        // It's ok not to do this, but 24px makes bottom narrow in view should adjust
        "&-content": {
          display: "inline-block",
          marginInlineEnd: token.paddingXS / 2,
          overflow: "hidden",
          whiteSpace: "pre",
          textOverflow: "ellipsis"
        },
        "&-remove": _extends(_extends({}, resetIcon()), {
          display: "inline-block",
          color: token.colorIcon,
          fontWeight: "bold",
          fontSize: 10,
          lineHeight: "inherit",
          cursor: "pointer",
          [`> ${iconCls}`]: {
            verticalAlign: "-0.2em"
          },
          "&:hover": {
            color: token.colorIconHover
          }
        })
      },
      // ========================== Input ==========================
      [`${selectOverflowPrefixCls}-item + ${selectOverflowPrefixCls}-item`]: {
        [`${componentCls}-selection-search`]: {
          marginInlineStart: 0
        }
      },
      [`${componentCls}-selection-search`]: {
        display: "inline-flex",
        position: "relative",
        maxWidth: "100%",
        marginInlineStart: token.inputPaddingHorizontalBase - selectItemDist,
        [`
          &-input,
          &-mirror
        `]: {
          height: selectItemHeight,
          fontFamily: token.fontFamily,
          lineHeight: `${selectItemHeight}px`,
          transition: `all ${token.motionDurationSlow}`
        },
        "&-input": {
          width: "100%",
          minWidth: 4.1
          // fix search cursor missing
        },
        "&-mirror": {
          position: "absolute",
          top: 0,
          insetInlineStart: 0,
          insetInlineEnd: "auto",
          zIndex: 999,
          whiteSpace: "pre",
          visibility: "hidden"
        }
      },
      // ======================= Placeholder =======================
      [`${componentCls}-selection-placeholder `]: {
        position: "absolute",
        top: "50%",
        insetInlineStart: token.inputPaddingHorizontalBase,
        insetInlineEnd: token.inputPaddingHorizontalBase,
        transform: "translateY(-50%)",
        transition: `all ${token.motionDurationSlow}`
      }
    }
  };
}
function genMultipleStyle(token) {
  const {
    componentCls
  } = token;
  const smallToken = merge(token, {
    controlHeight: token.controlHeightSM,
    controlHeightSM: token.controlHeightXS,
    borderRadius: token.borderRadiusSM,
    borderRadiusSM: token.borderRadiusXS
  });
  const [, smSelectItemMargin] = getSelectItemStyle(token);
  return [
    genSizeStyle$1(token),
    // ======================== Small ========================
    // Shared
    genSizeStyle$1(smallToken, "sm"),
    // Padding
    {
      [`${componentCls}-multiple${componentCls}-sm`]: {
        [`${componentCls}-selection-placeholder`]: {
          insetInlineStart: token.controlPaddingHorizontalSM - token.lineWidth,
          insetInlineEnd: "auto"
        },
        // https://github.com/ant-design/ant-design/issues/29559
        [`${componentCls}-selection-search`]: {
          marginInlineStart: smSelectItemMargin
        }
      }
    },
    // ======================== Large ========================
    // Shared
    genSizeStyle$1(merge(token, {
      fontSize: token.fontSizeLG,
      controlHeight: token.controlHeightLG,
      controlHeightSM: token.controlHeight,
      borderRadius: token.borderRadiusLG,
      borderRadiusSM: token.borderRadius
    }), "lg")
  ];
}
function genSizeStyle(token, suffix) {
  const {
    componentCls,
    inputPaddingHorizontalBase,
    borderRadius
  } = token;
  const selectHeightWithoutBorder = token.controlHeight - token.lineWidth * 2;
  const selectionItemPadding = Math.ceil(token.fontSize * 1.25);
  const suffixCls = suffix ? `${componentCls}-${suffix}` : "";
  return {
    [`${componentCls}-single${suffixCls}`]: {
      fontSize: token.fontSize,
      // ========================= Selector =========================
      [`${componentCls}-selector`]: _extends(_extends({}, resetComponent(token)), {
        display: "flex",
        borderRadius,
        [`${componentCls}-selection-search`]: {
          position: "absolute",
          top: 0,
          insetInlineStart: inputPaddingHorizontalBase,
          insetInlineEnd: inputPaddingHorizontalBase,
          bottom: 0,
          "&-input": {
            width: "100%"
          }
        },
        [`
          ${componentCls}-selection-item,
          ${componentCls}-selection-placeholder
        `]: {
          padding: 0,
          lineHeight: `${selectHeightWithoutBorder}px`,
          transition: `all ${token.motionDurationSlow}`,
          // Firefox inline-block position calculation is not same as Chrome & Safari. Patch this:
          "@supports (-moz-appearance: meterbar)": {
            lineHeight: `${selectHeightWithoutBorder}px`
          }
        },
        [`${componentCls}-selection-item`]: {
          position: "relative",
          userSelect: "none"
        },
        [`${componentCls}-selection-placeholder`]: {
          transition: "none",
          pointerEvents: "none"
        },
        // For common baseline align
        [[
          "&:after",
          /* For '' value baseline align */
          `${componentCls}-selection-item:after`,
          /* For undefined value baseline align */
          `${componentCls}-selection-placeholder:after`
        ].join(",")]: {
          display: "inline-block",
          width: 0,
          visibility: "hidden",
          content: '"\\a0"'
        }
      }),
      [`
        &${componentCls}-show-arrow ${componentCls}-selection-item,
        &${componentCls}-show-arrow ${componentCls}-selection-placeholder
      `]: {
        paddingInlineEnd: selectionItemPadding
      },
      // Opacity selection if open
      [`&${componentCls}-open ${componentCls}-selection-item`]: {
        color: token.colorTextPlaceholder
      },
      // ========================== Input ==========================
      // We only change the style of non-customize input which is only support by `combobox` mode.
      // Not customize
      [`&:not(${componentCls}-customize-input)`]: {
        [`${componentCls}-selector`]: {
          width: "100%",
          height: token.controlHeight,
          padding: `0 ${inputPaddingHorizontalBase}px`,
          [`${componentCls}-selection-search-input`]: {
            height: selectHeightWithoutBorder
          },
          "&:after": {
            lineHeight: `${selectHeightWithoutBorder}px`
          }
        }
      },
      [`&${componentCls}-customize-input`]: {
        [`${componentCls}-selector`]: {
          "&:after": {
            display: "none"
          },
          [`${componentCls}-selection-search`]: {
            position: "static",
            width: "100%"
          },
          [`${componentCls}-selection-placeholder`]: {
            position: "absolute",
            insetInlineStart: 0,
            insetInlineEnd: 0,
            padding: `0 ${inputPaddingHorizontalBase}px`,
            "&:after": {
              display: "none"
            }
          }
        }
      }
    }
  };
}
function genSingleStyle(token) {
  const {
    componentCls
  } = token;
  const inputPaddingHorizontalSM = token.controlPaddingHorizontalSM - token.lineWidth;
  return [
    genSizeStyle(token),
    // ======================== Small ========================
    // Shared
    genSizeStyle(merge(token, {
      controlHeight: token.controlHeightSM,
      borderRadius: token.borderRadiusSM
    }), "sm"),
    // padding
    {
      [`${componentCls}-single${componentCls}-sm`]: {
        [`&:not(${componentCls}-customize-input)`]: {
          [`${componentCls}-selection-search`]: {
            insetInlineStart: inputPaddingHorizontalSM,
            insetInlineEnd: inputPaddingHorizontalSM
          },
          [`${componentCls}-selector`]: {
            padding: `0 ${inputPaddingHorizontalSM}px`
          },
          // With arrow should provides `padding-right` to show the arrow
          [`&${componentCls}-show-arrow ${componentCls}-selection-search`]: {
            insetInlineEnd: inputPaddingHorizontalSM + token.fontSize * 1.5
          },
          [`
            &${componentCls}-show-arrow ${componentCls}-selection-item,
            &${componentCls}-show-arrow ${componentCls}-selection-placeholder
          `]: {
            paddingInlineEnd: token.fontSize * 1.5
          }
        }
      }
    },
    // ======================== Large ========================
    // Shared
    genSizeStyle(merge(token, {
      controlHeight: token.controlHeightLG,
      fontSize: token.fontSizeLG,
      borderRadius: token.borderRadiusLG
    }), "lg")
  ];
}
const genSelectorStyle = (token) => {
  const {
    componentCls
  } = token;
  return {
    position: "relative",
    backgroundColor: token.colorBgContainer,
    border: `${token.lineWidth}px ${token.lineType} ${token.colorBorder}`,
    transition: `all ${token.motionDurationMid} ${token.motionEaseInOut}`,
    input: {
      cursor: "pointer"
    },
    [`${componentCls}-show-search&`]: {
      cursor: "text",
      input: {
        cursor: "auto",
        color: "inherit"
      }
    },
    [`${componentCls}-disabled&`]: {
      color: token.colorTextDisabled,
      background: token.colorBgContainerDisabled,
      cursor: "not-allowed",
      [`${componentCls}-multiple&`]: {
        background: token.colorBgContainerDisabled
      },
      input: {
        cursor: "not-allowed"
      }
    }
  };
};
const genStatusStyle = function(rootSelectCls, token) {
  let overwriteDefaultBorder = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : false;
  const {
    componentCls,
    borderHoverColor,
    outlineColor,
    antCls
  } = token;
  const overwriteStyle = overwriteDefaultBorder ? {
    [`${componentCls}-selector`]: {
      borderColor: borderHoverColor
    }
  } : {};
  return {
    [rootSelectCls]: {
      [`&:not(${componentCls}-disabled):not(${componentCls}-customize-input):not(${antCls}-pagination-size-changer)`]: _extends(_extends({}, overwriteStyle), {
        [`${componentCls}-focused& ${componentCls}-selector`]: {
          borderColor: borderHoverColor,
          boxShadow: `0 0 0 ${token.controlOutlineWidth}px ${outlineColor}`,
          borderInlineEndWidth: `${token.controlLineWidth}px !important`,
          outline: 0
        },
        [`&:hover ${componentCls}-selector`]: {
          borderColor: borderHoverColor,
          borderInlineEndWidth: `${token.controlLineWidth}px !important`
        }
      })
    }
  };
};
const getSearchInputWithoutBorderStyle = (token) => {
  const {
    componentCls
  } = token;
  return {
    [`${componentCls}-selection-search-input`]: {
      margin: 0,
      padding: 0,
      background: "transparent",
      border: "none",
      outline: "none",
      appearance: "none",
      "&::-webkit-search-cancel-button": {
        display: "none",
        "-webkit-appearance": "none"
      }
    }
  };
};
const genBaseStyle = (token) => {
  const {
    componentCls,
    inputPaddingHorizontalBase,
    iconCls
  } = token;
  return {
    [componentCls]: _extends(_extends({}, resetComponent(token)), {
      position: "relative",
      display: "inline-block",
      cursor: "pointer",
      [`&:not(${componentCls}-customize-input) ${componentCls}-selector`]: _extends(_extends({}, genSelectorStyle(token)), getSearchInputWithoutBorderStyle(token)),
      // [`&:not(&-disabled):hover ${selectCls}-selector`]: {
      //   ...genHoverStyle(token),
      // },
      // ======================== Selection ========================
      [`${componentCls}-selection-item`]: _extends({
        flex: 1,
        fontWeight: "normal"
      }, textEllipsis),
      // ======================= Placeholder =======================
      [`${componentCls}-selection-placeholder`]: _extends(_extends({}, textEllipsis), {
        flex: 1,
        color: token.colorTextPlaceholder,
        pointerEvents: "none"
      }),
      // ========================== Arrow ==========================
      [`${componentCls}-arrow`]: _extends(_extends({}, resetIcon()), {
        position: "absolute",
        top: "50%",
        insetInlineStart: "auto",
        insetInlineEnd: inputPaddingHorizontalBase,
        height: token.fontSizeIcon,
        marginTop: -token.fontSizeIcon / 2,
        color: token.colorTextQuaternary,
        fontSize: token.fontSizeIcon,
        lineHeight: 1,
        textAlign: "center",
        pointerEvents: "none",
        display: "flex",
        alignItems: "center",
        [iconCls]: {
          verticalAlign: "top",
          transition: `transform ${token.motionDurationSlow}`,
          "> svg": {
            verticalAlign: "top"
          },
          [`&:not(${componentCls}-suffix)`]: {
            pointerEvents: "auto"
          }
        },
        [`${componentCls}-disabled &`]: {
          cursor: "not-allowed"
        },
        "> *:not(:last-child)": {
          marginInlineEnd: 8
          // FIXME: magic
        }
      }),
      // ========================== Clear ==========================
      [`${componentCls}-clear`]: {
        position: "absolute",
        top: "50%",
        insetInlineStart: "auto",
        insetInlineEnd: inputPaddingHorizontalBase,
        zIndex: 1,
        display: "inline-block",
        width: token.fontSizeIcon,
        height: token.fontSizeIcon,
        marginTop: -token.fontSizeIcon / 2,
        color: token.colorTextQuaternary,
        fontSize: token.fontSizeIcon,
        fontStyle: "normal",
        lineHeight: 1,
        textAlign: "center",
        textTransform: "none",
        background: token.colorBgContainer,
        cursor: "pointer",
        opacity: 0,
        transition: `color ${token.motionDurationMid} ease, opacity ${token.motionDurationSlow} ease`,
        textRendering: "auto",
        "&:before": {
          display: "block"
        },
        "&:hover": {
          color: token.colorTextTertiary
        }
      },
      "&:hover": {
        [`${componentCls}-clear`]: {
          opacity: 1
        }
      }
    }),
    // ========================= Feedback ==========================
    [`${componentCls}-has-feedback`]: {
      [`${componentCls}-clear`]: {
        insetInlineEnd: inputPaddingHorizontalBase + token.fontSize + token.paddingXXS
      }
    }
  };
};
const genSelectStyle = (token) => {
  const {
    componentCls
  } = token;
  return [
    {
      [componentCls]: {
        // ==================== BorderLess ====================
        [`&-borderless ${componentCls}-selector`]: {
          backgroundColor: `transparent !important`,
          borderColor: `transparent !important`,
          boxShadow: `none !important`
        },
        // ==================== In Form ====================
        [`&${componentCls}-in-form-item`]: {
          width: "100%"
        }
      }
    },
    // =====================================================
    // ==                       LTR                       ==
    // =====================================================
    // Base
    genBaseStyle(token),
    // Single
    genSingleStyle(token),
    // Multiple
    genMultipleStyle(token),
    // Dropdown
    genSingleStyle$1(token),
    // =====================================================
    // ==                       RTL                       ==
    // =====================================================
    {
      [`${componentCls}-rtl`]: {
        direction: "rtl"
      }
    },
    // =====================================================
    // ==                     Status                      ==
    // =====================================================
    genStatusStyle(componentCls, merge(token, {
      borderHoverColor: token.colorPrimaryHover,
      outlineColor: token.controlOutline
    })),
    genStatusStyle(`${componentCls}-status-error`, merge(token, {
      borderHoverColor: token.colorErrorHover,
      outlineColor: token.colorErrorOutline
    }), true),
    genStatusStyle(`${componentCls}-status-warning`, merge(token, {
      borderHoverColor: token.colorWarningHover,
      outlineColor: token.colorWarningOutline
    }), true),
    // =====================================================
    // ==             Space Compact                       ==
    // =====================================================
    genCompactItemStyle(token, {
      borderElCls: `${componentCls}-selector`,
      focusElCls: `${componentCls}-focused`
    })
  ];
};
const useStyle = genComponentStyleHook("Select", (token, _ref) => {
  let {
    rootPrefixCls
  } = _ref;
  const selectToken = merge(token, {
    rootPrefixCls,
    inputPaddingHorizontalBase: token.paddingSM - 1
  });
  return [genSelectStyle(selectToken)];
}, (token) => ({
  zIndexPopup: token.zIndexPopupBase + 50
}));
const selectProps = () => _extends(_extends({}, omit(selectProps$1(), ["inputIcon", "mode", "getInputElement", "getRawInputElement", "backfill"])), {
  value: someType([Array, Object, String, Number]),
  defaultValue: someType([Array, Object, String, Number]),
  notFoundContent: PropTypes.any,
  suffixIcon: PropTypes.any,
  itemIcon: PropTypes.any,
  size: stringType(),
  mode: stringType(),
  bordered: booleanType(true),
  transitionName: String,
  choiceTransitionName: stringType(""),
  popupClassName: String,
  /** @deprecated Please use `popupClassName` instead */
  dropdownClassName: String,
  placement: stringType(),
  status: stringType(),
  "onUpdate:value": functionType()
});
const SECRET_COMBOBOX_MODE_DO_NOT_USE = "SECRET_COMBOBOX_MODE_DO_NOT_USE";
const Select = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "ASelect",
  Option,
  OptGroup,
  inheritAttrs: false,
  props: initDefaultProps(selectProps(), {
    listHeight: 256,
    listItemHeight: 24
  }),
  SECRET_COMBOBOX_MODE_DO_NOT_USE,
  slots: Object,
  setup(props2, _ref) {
    let {
      attrs,
      emit,
      slots,
      expose
    } = _ref;
    const selectRef = ref();
    const formItemContext = useInjectFormItemContext();
    const formItemInputContext = FormItemInputContext.useInject();
    const mergedStatus = computed(() => getMergedStatus(formItemInputContext.status, props2.status));
    const focus = () => {
      var _a;
      (_a = selectRef.value) === null || _a === void 0 ? void 0 : _a.focus();
    };
    const blur = () => {
      var _a;
      (_a = selectRef.value) === null || _a === void 0 ? void 0 : _a.blur();
    };
    const scrollTo = (arg) => {
      var _a;
      (_a = selectRef.value) === null || _a === void 0 ? void 0 : _a.scrollTo(arg);
    };
    const mode = computed(() => {
      const {
        mode: mode2
      } = props2;
      if (mode2 === "combobox") {
        return void 0;
      }
      if (mode2 === SECRET_COMBOBOX_MODE_DO_NOT_USE) {
        return "combobox";
      }
      return mode2;
    });
    if (process.env.NODE_ENV !== "production") {
      devWarning(!props2.dropdownClassName, "Select", "`dropdownClassName` is deprecated. Please use `popupClassName` instead.");
    }
    const {
      prefixCls,
      direction,
      renderEmpty,
      size: contextSize,
      getPrefixCls,
      getPopupContainer,
      disabled,
      select
    } = useConfigInject("select", props2);
    const {
      compactSize,
      compactItemClassnames
    } = useCompactItemContext(prefixCls, direction);
    const mergedSize = computed(() => compactSize.value || contextSize.value);
    const contextDisabled = useInjectDisabled();
    const mergedDisabled = computed(() => {
      var _a;
      return (_a = disabled.value) !== null && _a !== void 0 ? _a : contextDisabled.value;
    });
    const [wrapSSR, hashId] = useStyle(prefixCls);
    const rootPrefixCls = computed(() => getPrefixCls());
    const placement = computed(() => {
      if (props2.placement !== void 0) {
        return props2.placement;
      }
      return direction.value === "rtl" ? "bottomRight" : "bottomLeft";
    });
    const transitionName = computed(() => getTransitionName(rootPrefixCls.value, getTransitionDirection(placement.value), props2.transitionName));
    const mergedClassName = computed(() => classNames({
      [`${prefixCls.value}-lg`]: mergedSize.value === "large",
      [`${prefixCls.value}-sm`]: mergedSize.value === "small",
      [`${prefixCls.value}-rtl`]: direction.value === "rtl",
      [`${prefixCls.value}-borderless`]: !props2.bordered,
      [`${prefixCls.value}-in-form-item`]: formItemInputContext.isFormItemInput
    }, getStatusClassNames(prefixCls.value, mergedStatus.value, formItemInputContext.hasFeedback), compactItemClassnames.value, hashId.value));
    const triggerChange = function() {
      for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
        args[_key] = arguments[_key];
      }
      emit("update:value", args[0]);
      emit("change", ...args);
      formItemContext.onFieldChange();
    };
    const handleBlur = (e) => {
      emit("blur", e);
      formItemContext.onFieldBlur();
    };
    expose({
      blur,
      focus,
      scrollTo
    });
    const isMultiple2 = computed(() => mode.value === "multiple" || mode.value === "tags");
    const mergedShowArrow = computed(() => props2.showArrow !== void 0 ? props2.showArrow : props2.loading || !(isMultiple2.value || mode.value === "combobox"));
    return () => {
      var _a, _b, _c, _d;
      const {
        notFoundContent,
        listHeight = 256,
        listItemHeight = 24,
        popupClassName,
        dropdownClassName,
        virtual,
        dropdownMatchSelectWidth,
        id = formItemContext.id.value,
        placeholder = (_a = slots.placeholder) === null || _a === void 0 ? void 0 : _a.call(slots),
        showArrow
      } = props2;
      const {
        hasFeedback,
        feedbackIcon
      } = formItemInputContext;
      let mergedNotFound;
      if (notFoundContent !== void 0) {
        mergedNotFound = notFoundContent;
      } else if (slots.notFoundContent) {
        mergedNotFound = slots.notFoundContent();
      } else if (mode.value === "combobox") {
        mergedNotFound = null;
      } else {
        mergedNotFound = (renderEmpty === null || renderEmpty === void 0 ? void 0 : renderEmpty("Select")) || createVNode(DefaultRenderEmpty, {
          "componentName": "Select"
        }, null);
      }
      const {
        suffixIcon,
        itemIcon,
        removeIcon,
        clearIcon
      } = getIcons(_extends(_extends({}, props2), {
        multiple: isMultiple2.value,
        prefixCls: prefixCls.value,
        hasFeedback,
        feedbackIcon,
        showArrow: mergedShowArrow.value
      }), slots);
      const selectProps2 = omit(props2, ["prefixCls", "suffixIcon", "itemIcon", "removeIcon", "clearIcon", "size", "bordered", "status"]);
      const rcSelectRtlDropdownClassName = classNames(popupClassName || dropdownClassName, {
        [`${prefixCls.value}-dropdown-${direction.value}`]: direction.value === "rtl"
      }, hashId.value);
      return wrapSSR(createVNode(Select$1, _objectSpread2(_objectSpread2(_objectSpread2({
        "ref": selectRef,
        "virtual": virtual,
        "dropdownMatchSelectWidth": dropdownMatchSelectWidth
      }, selectProps2), attrs), {}, {
        "showSearch": (_b = props2.showSearch) !== null && _b !== void 0 ? _b : (_c = select === null || select === void 0 ? void 0 : select.value) === null || _c === void 0 ? void 0 : _c.showSearch,
        "placeholder": placeholder,
        "listHeight": listHeight,
        "listItemHeight": listItemHeight,
        "mode": mode.value,
        "prefixCls": prefixCls.value,
        "direction": direction.value,
        "inputIcon": suffixIcon,
        "menuItemSelectedIcon": itemIcon,
        "removeIcon": removeIcon,
        "clearIcon": clearIcon,
        "notFoundContent": mergedNotFound,
        "class": [mergedClassName.value, attrs.class],
        "getPopupContainer": getPopupContainer === null || getPopupContainer === void 0 ? void 0 : getPopupContainer.value,
        "dropdownClassName": rcSelectRtlDropdownClassName,
        "onChange": triggerChange,
        "onBlur": handleBlur,
        "id": id,
        "dropdownRender": selectProps2.dropdownRender || slots.dropdownRender,
        "transitionName": transitionName.value,
        "children": (_d = slots.default) === null || _d === void 0 ? void 0 : _d.call(slots),
        "tagRender": props2.tagRender || slots.tagRender,
        "optionLabelRender": slots.optionLabel,
        "maxTagPlaceholder": props2.maxTagPlaceholder || slots.maxTagPlaceholder,
        "showArrow": hasFeedback || showArrow,
        "disabled": mergedDisabled.value
      }), {
        option: slots.option
      }));
    };
  }
});
Select.install = function(app) {
  app.component(Select.name, Select);
  app.component(Select.Option.displayName, Select.Option);
  app.component(Select.OptGroup.displayName, Select.OptGroup);
  return app;
};
Select.Option;
Select.OptGroup;
export {
  Select as default,
  selectProps
};
//# sourceMappingURL=index-BZ7E-XaN.js.map
