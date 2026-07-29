import { p as useToken, _ as _extends, g as genComponentStyleHook, m as merge, u as useConfigInject, c as classNames, b as _objectSpread2, k as someType, q as warning, r as isValidElement, v as useLocaleReceiver, x as localeValues, y as resetComponent, z as filterEmpty, P as PropTypes, t as tuple } from "./index-IfV1sCLI.js";
import { computed, shallowRef, onMounted, provide, inject, defineComponent, ref, onBeforeUnmount, createVNode, cloneVNode, Fragment, nextTick, watch, Transition, withDirectives, TransitionGroup, vShow, watchEffect, reactive, toRaw } from "vue";
import { L as LoadingOutlined } from "./LoadingOutlined-ZmO9GVlJ.js";
import { C as CloseCircleFilled } from "./CloseCircleFilled-CzkqoH7Y.js";
import { E as ExclamationCircleFilled, C as CheckCircleFilled } from "./ExclamationCircleFilled-B1vxzrB_.js";
import { c as cloneDeep, f as find } from "./index-D9cY2WMP.js";
import { d as detectFlexGapSupported } from "./styleChecker-CRnW4Ti6.js";
import { S as Schema } from "./index-B_zQ2KpM.js";
import Tooltip from "./index-COTSUqJq.js";
import { I as Icon } from "./AntdIcon-DuXnwIrR.js";
import { b as getTransitionGroupProps, g as getTransitionProps } from "./transition-AJOM0T1w.js";
import { z as zoomIn } from "./zoom-CQljQfTH.js";
import { a as useProvideFormItemContext, b as FormItemInputContext } from "./FormItemContext-CwA3WYnI.js";
const responsiveArray = ["xxxl", "xxl", "xl", "lg", "md", "sm", "xs"];
const getResponsiveMap = (token) => ({
  xs: `(max-width: ${token.screenXSMax}px)`,
  sm: `(min-width: ${token.screenSM}px)`,
  md: `(min-width: ${token.screenMD}px)`,
  lg: `(min-width: ${token.screenLG}px)`,
  xl: `(min-width: ${token.screenXL}px)`,
  xxl: `(min-width: ${token.screenXXL}px)`,
  xxxl: `{min-width: ${token.screenXXXL}px}`
});
function useResponsiveObserver() {
  const [, token] = useToken();
  return computed(() => {
    const responsiveMap = getResponsiveMap(token.value);
    const subscribers = /* @__PURE__ */ new Map();
    let subUid = -1;
    let screens = {};
    return {
      matchHandlers: {},
      dispatch(pointMap) {
        screens = pointMap;
        subscribers.forEach((func) => func(screens));
        return subscribers.size >= 1;
      },
      subscribe(func) {
        if (!subscribers.size) this.register();
        subUid += 1;
        subscribers.set(subUid, func);
        func(screens);
        return subUid;
      },
      unsubscribe(paramToken) {
        subscribers.delete(paramToken);
        if (!subscribers.size) this.unregister();
      },
      unregister() {
        Object.keys(responsiveMap).forEach((screen) => {
          const matchMediaQuery = responsiveMap[screen];
          const handler = this.matchHandlers[matchMediaQuery];
          handler === null || handler === void 0 ? void 0 : handler.mql.removeListener(handler === null || handler === void 0 ? void 0 : handler.listener);
        });
        subscribers.clear();
      },
      register() {
        Object.keys(responsiveMap).forEach((screen) => {
          const matchMediaQuery = responsiveMap[screen];
          const listener = (_ref) => {
            let {
              matches
            } = _ref;
            this.dispatch(_extends(_extends({}, screens), {
              [screen]: matches
            }));
          };
          const mql = window.matchMedia(matchMediaQuery);
          mql.addListener(listener);
          this.matchHandlers[matchMediaQuery] = {
            mql,
            listener
          };
          listener(mql);
        });
      },
      responsiveMap
    };
  });
}
const useFlexGapSupport = () => {
  const flexible = shallowRef(false);
  onMounted(() => {
    flexible.value = detectFlexGapSupported();
  });
  return flexible;
};
const RowContextKey = Symbol("rowContextKey");
const useProvideRow = (state) => {
  provide(RowContextKey, state);
};
const useInjectRow = () => {
  return inject(RowContextKey, {
    gutter: computed(() => void 0),
    wrap: computed(() => void 0),
    supportFlexGap: computed(() => void 0)
  });
};
const genGridRowStyle = (token) => {
  const {
    componentCls
  } = token;
  return {
    // Grid system
    [componentCls]: {
      display: "flex",
      flexFlow: "row wrap",
      minWidth: 0,
      "&::before, &::after": {
        display: "flex"
      },
      "&-no-wrap": {
        flexWrap: "nowrap"
      },
      // The origin of the X-axis
      "&-start": {
        justifyContent: "flex-start"
      },
      // The center of the X-axis
      "&-center": {
        justifyContent: "center"
      },
      // The opposite of the X-axis
      "&-end": {
        justifyContent: "flex-end"
      },
      "&-space-between": {
        justifyContent: "space-between"
      },
      "&-space-around ": {
        justifyContent: "space-around"
      },
      "&-space-evenly ": {
        justifyContent: "space-evenly"
      },
      // Align at the top
      "&-top": {
        alignItems: "flex-start"
      },
      // Align at the center
      "&-middle": {
        alignItems: "center"
      },
      "&-bottom": {
        alignItems: "flex-end"
      }
    }
  };
};
const genGridColStyle = (token) => {
  const {
    componentCls
  } = token;
  return {
    // Grid system
    [componentCls]: {
      position: "relative",
      maxWidth: "100%",
      // Prevent columns from collapsing when empty
      minHeight: 1
    }
  };
};
const genLoopGridColumnsStyle = (token, sizeCls) => {
  const {
    componentCls,
    gridColumns
  } = token;
  const gridColumnsStyle = {};
  for (let i = gridColumns; i >= 0; i--) {
    if (i === 0) {
      gridColumnsStyle[`${componentCls}${sizeCls}-${i}`] = {
        display: "none"
      };
      gridColumnsStyle[`${componentCls}-push-${i}`] = {
        insetInlineStart: "auto"
      };
      gridColumnsStyle[`${componentCls}-pull-${i}`] = {
        insetInlineEnd: "auto"
      };
      gridColumnsStyle[`${componentCls}${sizeCls}-push-${i}`] = {
        insetInlineStart: "auto"
      };
      gridColumnsStyle[`${componentCls}${sizeCls}-pull-${i}`] = {
        insetInlineEnd: "auto"
      };
      gridColumnsStyle[`${componentCls}${sizeCls}-offset-${i}`] = {
        marginInlineEnd: 0
      };
      gridColumnsStyle[`${componentCls}${sizeCls}-order-${i}`] = {
        order: 0
      };
    } else {
      gridColumnsStyle[`${componentCls}${sizeCls}-${i}`] = {
        display: "block",
        flex: `0 0 ${i / gridColumns * 100}%`,
        maxWidth: `${i / gridColumns * 100}%`
      };
      gridColumnsStyle[`${componentCls}${sizeCls}-push-${i}`] = {
        insetInlineStart: `${i / gridColumns * 100}%`
      };
      gridColumnsStyle[`${componentCls}${sizeCls}-pull-${i}`] = {
        insetInlineEnd: `${i / gridColumns * 100}%`
      };
      gridColumnsStyle[`${componentCls}${sizeCls}-offset-${i}`] = {
        marginInlineStart: `${i / gridColumns * 100}%`
      };
      gridColumnsStyle[`${componentCls}${sizeCls}-order-${i}`] = {
        order: i
      };
    }
  }
  return gridColumnsStyle;
};
const genGridStyle = (token, sizeCls) => genLoopGridColumnsStyle(token, sizeCls);
const genGridMediaStyle = (token, screenSize, sizeCls) => ({
  [`@media (min-width: ${screenSize}px)`]: _extends({}, genGridStyle(token, sizeCls))
});
const useRowStyle = genComponentStyleHook("Grid", (token) => [genGridRowStyle(token)]);
const useColStyle = genComponentStyleHook("Grid", (token) => {
  const gridToken = merge(token, {
    gridColumns: 24
    // Row is divided into 24 parts in Grid
  });
  const gridMediaSizesMap = {
    "-sm": gridToken.screenSMMin,
    "-md": gridToken.screenMDMin,
    "-lg": gridToken.screenLGMin,
    "-xl": gridToken.screenXLMin,
    "-xxl": gridToken.screenXXLMin
  };
  return [genGridColStyle(gridToken), genGridStyle(gridToken, ""), genGridStyle(gridToken, "-xs"), Object.keys(gridMediaSizesMap).map((key) => genGridMediaStyle(gridToken, gridMediaSizesMap[key], key)).reduce((pre, cur) => _extends(_extends({}, pre), cur), {})];
});
const rowProps = () => ({
  align: someType([String, Object]),
  justify: someType([String, Object]),
  prefixCls: String,
  gutter: someType([Number, Array, Object], 0),
  wrap: {
    type: Boolean,
    default: void 0
  }
});
const ARow = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "ARow",
  inheritAttrs: false,
  props: rowProps(),
  setup(props, _ref) {
    let {
      slots,
      attrs
    } = _ref;
    const {
      prefixCls,
      direction
    } = useConfigInject("row", props);
    const [wrapSSR, hashId] = useRowStyle(prefixCls);
    let token;
    const responsiveObserve = useResponsiveObserver();
    const screens = ref({
      xs: true,
      sm: true,
      md: true,
      lg: true,
      xl: true,
      xxl: true
    });
    const curScreens = ref({
      xs: false,
      sm: false,
      md: false,
      lg: false,
      xl: false,
      xxl: false
    });
    const mergePropsByScreen = (oriProp) => {
      return computed(() => {
        if (typeof props[oriProp] === "string") {
          return props[oriProp];
        }
        if (typeof props[oriProp] !== "object") {
          return "";
        }
        for (let i = 0; i < responsiveArray.length; i++) {
          const breakpoint = responsiveArray[i];
          if (!curScreens.value[breakpoint]) continue;
          const curVal = props[oriProp][breakpoint];
          if (curVal !== void 0) {
            return curVal;
          }
        }
        return "";
      });
    };
    const mergeAlign = mergePropsByScreen("align");
    const mergeJustify = mergePropsByScreen("justify");
    const supportFlexGap = useFlexGapSupport();
    onMounted(() => {
      token = responsiveObserve.value.subscribe((screen) => {
        curScreens.value = screen;
        const currentGutter = props.gutter || 0;
        if (!Array.isArray(currentGutter) && typeof currentGutter === "object" || Array.isArray(currentGutter) && (typeof currentGutter[0] === "object" || typeof currentGutter[1] === "object")) {
          screens.value = screen;
        }
      });
    });
    onBeforeUnmount(() => {
      responsiveObserve.value.unsubscribe(token);
    });
    const gutter = computed(() => {
      const results = [void 0, void 0];
      const {
        gutter: gutter2 = 0
      } = props;
      const normalizedGutter = Array.isArray(gutter2) ? gutter2 : [gutter2, void 0];
      normalizedGutter.forEach((g, index) => {
        if (typeof g === "object") {
          for (let i = 0; i < responsiveArray.length; i++) {
            const breakpoint = responsiveArray[i];
            if (screens.value[breakpoint] && g[breakpoint] !== void 0) {
              results[index] = g[breakpoint];
              break;
            }
          }
        } else {
          results[index] = g;
        }
      });
      return results;
    });
    useProvideRow({
      gutter,
      supportFlexGap,
      wrap: computed(() => props.wrap)
    });
    const classes = computed(() => classNames(prefixCls.value, {
      [`${prefixCls.value}-no-wrap`]: props.wrap === false,
      [`${prefixCls.value}-${mergeJustify.value}`]: mergeJustify.value,
      [`${prefixCls.value}-${mergeAlign.value}`]: mergeAlign.value,
      [`${prefixCls.value}-rtl`]: direction.value === "rtl"
    }, attrs.class, hashId.value));
    const rowStyle = computed(() => {
      const gt = gutter.value;
      const style = {};
      const horizontalGutter = gt[0] != null && gt[0] > 0 ? `${gt[0] / -2}px` : void 0;
      const verticalGutter = gt[1] != null && gt[1] > 0 ? `${gt[1] / -2}px` : void 0;
      if (horizontalGutter) {
        style.marginLeft = horizontalGutter;
        style.marginRight = horizontalGutter;
      }
      if (supportFlexGap.value) {
        style.rowGap = `${gt[1]}px`;
      } else if (verticalGutter) {
        style.marginTop = verticalGutter;
        style.marginBottom = verticalGutter;
      }
      return style;
    });
    return () => {
      var _a;
      return wrapSSR(createVNode("div", _objectSpread2(_objectSpread2({}, attrs), {}, {
        "class": classes.value,
        "style": _extends(_extends({}, rowStyle.value), attrs.style)
      }), [(_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots)]));
    };
  }
});
function toArray(value) {
  if (value === void 0 || value === null) {
    return [];
  }
  return Array.isArray(value) ? value : [value];
}
function get(entity, path) {
  let current = entity;
  for (let i = 0; i < path.length; i += 1) {
    if (current === null || current === void 0) {
      return void 0;
    }
    current = current[path[i]];
  }
  return current;
}
function internalSet(entity, paths, value, removeIfUndefined) {
  if (!paths.length) {
    return value;
  }
  const [path, ...restPath] = paths;
  let clone;
  if (!entity && typeof path === "number") {
    clone = [];
  } else if (Array.isArray(entity)) {
    clone = [...entity];
  } else {
    clone = _extends({}, entity);
  }
  if (removeIfUndefined && value === void 0 && restPath.length === 1) {
    delete clone[path][restPath[0]];
  } else {
    clone[path] = internalSet(clone[path], restPath, value, removeIfUndefined);
  }
  return clone;
}
function set(entity, paths, value) {
  let removeIfUndefined = arguments.length > 3 && arguments[3] !== void 0 ? arguments[3] : false;
  if (paths.length && removeIfUndefined && value === void 0 && !get(entity, paths.slice(0, -1))) {
    return entity;
  }
  return internalSet(entity, paths, value, removeIfUndefined);
}
function getNamePath(path) {
  return toArray(path);
}
function getValue(store, namePath) {
  const value = get(store, namePath);
  return value;
}
function setValue(store, namePath, value) {
  let removeIfUndefined = arguments.length > 3 && arguments[3] !== void 0 ? arguments[3] : false;
  const newStore = set(store, namePath, value, removeIfUndefined);
  return newStore;
}
function containsNamePath(namePathList, namePath) {
  return namePathList && namePathList.some((path) => matchNamePath(path, namePath));
}
function isObject(obj) {
  return typeof obj === "object" && obj !== null && Object.getPrototypeOf(obj) === Object.prototype;
}
function internalSetValues(store, values) {
  const newStore = Array.isArray(store) ? [...store] : _extends({}, store);
  if (!values) {
    return newStore;
  }
  Object.keys(values).forEach((key) => {
    const prevValue = newStore[key];
    const value = values[key];
    const recursive = isObject(prevValue) && isObject(value);
    newStore[key] = recursive ? internalSetValues(prevValue, value || {}) : value;
  });
  return newStore;
}
function setValues(store) {
  for (var _len = arguments.length, restValues = new Array(_len > 1 ? _len - 1 : 0), _key = 1; _key < _len; _key++) {
    restValues[_key - 1] = arguments[_key];
  }
  return restValues.reduce((current, newStore) => internalSetValues(current, newStore), store);
}
function cloneByNamePathList(store, namePathList) {
  let newStore = {};
  namePathList.forEach((namePath) => {
    const value = getValue(store, namePath);
    newStore = setValue(newStore, namePath, value);
  });
  return newStore;
}
function matchNamePath(namePath, changedNamePath) {
  if (!namePath || !changedNamePath || namePath.length !== changedNamePath.length) {
    return false;
  }
  return namePath.every((nameUnit, i) => changedNamePath[i] === nameUnit);
}
const typeTemplate = "'${name}' is not a valid ${type}";
const defaultValidateMessages = {
  default: "Validation error on field '${name}'",
  required: "'${name}' is required",
  enum: "'${name}' must be one of [${enum}]",
  whitespace: "'${name}' cannot be empty",
  date: {
    format: "'${name}' is invalid for format date",
    parse: "'${name}' could not be parsed as date",
    invalid: "'${name}' is invalid date"
  },
  types: {
    string: typeTemplate,
    method: typeTemplate,
    array: typeTemplate,
    object: typeTemplate,
    number: typeTemplate,
    date: typeTemplate,
    boolean: typeTemplate,
    integer: typeTemplate,
    float: typeTemplate,
    regexp: typeTemplate,
    email: typeTemplate,
    url: typeTemplate,
    hex: typeTemplate
  },
  string: {
    len: "'${name}' must be exactly ${len} characters",
    min: "'${name}' must be at least ${min} characters",
    max: "'${name}' cannot be longer than ${max} characters",
    range: "'${name}' must be between ${min} and ${max} characters"
  },
  number: {
    len: "'${name}' must equal ${len}",
    min: "'${name}' cannot be less than ${min}",
    max: "'${name}' cannot be greater than ${max}",
    range: "'${name}' must be between ${min} and ${max}"
  },
  array: {
    len: "'${name}' must be exactly ${len} in length",
    min: "'${name}' cannot be less than ${min} in length",
    max: "'${name}' cannot be greater than ${max} in length",
    range: "'${name}' must be between ${min} and ${max} in length"
  },
  pattern: {
    mismatch: "'${name}' does not match pattern ${pattern}"
  }
};
var __awaiter = function(thisArg, _arguments, P, generator) {
  function adopt(value) {
    return value instanceof P ? value : new P(function(resolve) {
      resolve(value);
    });
  }
  return new (P || (P = Promise))(function(resolve, reject) {
    function fulfilled(value) {
      try {
        step(generator.next(value));
      } catch (e) {
        reject(e);
      }
    }
    function rejected(value) {
      try {
        step(generator["throw"](value));
      } catch (e) {
        reject(e);
      }
    }
    function step(result) {
      result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected);
    }
    step((generator = generator.apply(thisArg, _arguments || [])).next());
  });
};
const AsyncValidator = Schema;
function replaceMessage(template, kv) {
  return template.replace(/\$\{\w+\}/g, (str) => {
    const key = str.slice(2, -1);
    return kv[key];
  });
}
function validateRule(name, value, rule, options, messageVariables) {
  return __awaiter(this, void 0, void 0, function* () {
    const cloneRule = _extends({}, rule);
    delete cloneRule.ruleIndex;
    delete cloneRule.trigger;
    let subRuleField = null;
    if (cloneRule && cloneRule.type === "array" && cloneRule.defaultField) {
      subRuleField = cloneRule.defaultField;
      delete cloneRule.defaultField;
    }
    const validator = new AsyncValidator({
      [name]: [cloneRule]
    });
    const messages = setValues({}, defaultValidateMessages, options.validateMessages);
    validator.messages(messages);
    let result = [];
    try {
      yield Promise.resolve(validator.validate({
        [name]: value
      }, _extends({}, options)));
    } catch (errObj) {
      if (errObj.errors) {
        result = errObj.errors.map((_ref, index) => {
          let {
            message
          } = _ref;
          return (
            // Wrap VueNode with `key`
            isValidElement(message) ? cloneVNode(message, {
              key: `error_${index}`
            }) : message
          );
        });
      } else {
        console.error(errObj);
        result = [messages.default()];
      }
    }
    if (!result.length && subRuleField) {
      const subResults = yield Promise.all(value.map((subValue, i) => validateRule(`${name}.${i}`, subValue, subRuleField, options, messageVariables)));
      return subResults.reduce((prev, errors) => [...prev, ...errors], []);
    }
    const kv = _extends(_extends(_extends({}, rule), {
      name,
      enum: (rule.enum || []).join(", ")
    }), messageVariables);
    const fillVariableResult = result.map((error) => {
      if (typeof error === "string") {
        return replaceMessage(error, kv);
      }
      return error;
    });
    return fillVariableResult;
  });
}
function validateRules(namePath, value, rules, options, validateFirst, messageVariables) {
  const name = namePath.join(".");
  const filledRules = rules.map((currentRule, ruleIndex) => {
    const originValidatorFunc = currentRule.validator;
    const cloneRule = _extends(_extends({}, currentRule), {
      ruleIndex
    });
    if (originValidatorFunc) {
      cloneRule.validator = (rule, val, callback) => {
        let hasPromise = false;
        const wrappedCallback = function() {
          for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
            args[_key] = arguments[_key];
          }
          Promise.resolve().then(() => {
            warning(!hasPromise, "Your validator function has already return a promise. `callback` will be ignored.");
            if (!hasPromise) {
              callback(...args);
            }
          });
        };
        const promise = originValidatorFunc(rule, val, wrappedCallback);
        hasPromise = promise && typeof promise.then === "function" && typeof promise.catch === "function";
        warning(hasPromise, "`callback` is deprecated. Please return a promise instead.");
        if (hasPromise) {
          promise.then(() => {
            callback();
          }).catch((err) => {
            callback(err || " ");
          });
        }
      };
    }
    return cloneRule;
  }).sort((_ref2, _ref3) => {
    let {
      warningOnly: w1,
      ruleIndex: i1
    } = _ref2;
    let {
      warningOnly: w2,
      ruleIndex: i2
    } = _ref3;
    if (!!w1 === !!w2) {
      return i1 - i2;
    }
    if (w1) {
      return 1;
    }
    return -1;
  });
  let summaryPromise;
  if (validateFirst === true) {
    summaryPromise = new Promise((resolve, reject) => __awaiter(this, void 0, void 0, function* () {
      for (let i = 0; i < filledRules.length; i += 1) {
        const rule = filledRules[i];
        const errors = yield validateRule(name, value, rule, options, messageVariables);
        if (errors.length) {
          reject([{
            errors,
            rule
          }]);
          return;
        }
      }
      resolve([]);
    }));
  } else {
    const rulePromises = filledRules.map((rule) => validateRule(name, value, rule, options, messageVariables).then((errors) => ({
      errors,
      rule
    })));
    summaryPromise = (validateFirst ? finishOnFirstFailed(rulePromises) : finishOnAllFailed(rulePromises)).then((errors) => {
      return Promise.reject(errors);
    });
  }
  summaryPromise.catch((e) => e);
  return summaryPromise;
}
function finishOnAllFailed(rulePromises) {
  return __awaiter(this, void 0, void 0, function* () {
    return Promise.all(rulePromises).then((errorsList) => {
      const errors = [].concat(...errorsList);
      return errors;
    });
  });
}
function finishOnFirstFailed(rulePromises) {
  return __awaiter(this, void 0, void 0, function* () {
    let count = 0;
    return new Promise((resolve) => {
      rulePromises.forEach((promise) => {
        promise.then((ruleError) => {
          if (ruleError.errors.length) {
            resolve([ruleError]);
          }
          count += 1;
          if (count === rulePromises.length) {
            resolve([]);
          }
        });
      });
    });
  });
}
const FormContextKey = Symbol("formContextKey");
const useProvideForm = (state) => {
  provide(FormContextKey, state);
};
const useInjectForm = () => {
  return inject(FormContextKey, {
    name: computed(() => void 0),
    labelAlign: computed(() => "right"),
    vertical: computed(() => false),
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    addField: (_eventKey, _field) => {
    },
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    removeField: (_eventKey) => {
    },
    model: computed(() => void 0),
    rules: computed(() => void 0),
    colon: computed(() => void 0),
    labelWrap: computed(() => void 0),
    labelCol: computed(() => void 0),
    requiredMark: computed(() => false),
    validateTrigger: computed(() => void 0),
    onValidate: () => {
    },
    validateMessages: computed(() => defaultValidateMessages)
  });
};
const FormItemPrefixContextKey = Symbol("formItemPrefixContextKey");
const useProvideFormItemPrefix = (state) => {
  provide(FormItemPrefixContextKey, state);
};
const useInjectFormItemPrefix = () => {
  return inject(FormItemPrefixContextKey, {
    prefixCls: computed(() => "")
  });
};
function parseFlex(flex) {
  if (typeof flex === "number") {
    return `${flex} ${flex} auto`;
  }
  if (/^\d+(\.\d+)?(px|em|rem|%)$/.test(flex)) {
    return `0 0 ${flex}`;
  }
  return flex;
}
const colProps = () => ({
  span: [String, Number],
  order: [String, Number],
  offset: [String, Number],
  push: [String, Number],
  pull: [String, Number],
  xs: {
    type: [String, Number, Object],
    default: void 0
  },
  sm: {
    type: [String, Number, Object],
    default: void 0
  },
  md: {
    type: [String, Number, Object],
    default: void 0
  },
  lg: {
    type: [String, Number, Object],
    default: void 0
  },
  xl: {
    type: [String, Number, Object],
    default: void 0
  },
  xxl: {
    type: [String, Number, Object],
    default: void 0
  },
  prefixCls: String,
  flex: [String, Number]
});
const sizes = ["xs", "sm", "md", "lg", "xl", "xxl"];
const Col = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "ACol",
  inheritAttrs: false,
  props: colProps(),
  setup(props, _ref) {
    let {
      slots,
      attrs
    } = _ref;
    const {
      gutter,
      supportFlexGap,
      wrap
    } = useInjectRow();
    const {
      prefixCls,
      direction
    } = useConfigInject("col", props);
    const [wrapSSR, hashId] = useColStyle(prefixCls);
    const classes = computed(() => {
      const {
        span,
        order,
        offset,
        push,
        pull
      } = props;
      const pre = prefixCls.value;
      let sizeClassObj = {};
      sizes.forEach((size) => {
        let sizeProps = {};
        const propSize = props[size];
        if (typeof propSize === "number") {
          sizeProps.span = propSize;
        } else if (typeof propSize === "object") {
          sizeProps = propSize || {};
        }
        sizeClassObj = _extends(_extends({}, sizeClassObj), {
          [`${pre}-${size}-${sizeProps.span}`]: sizeProps.span !== void 0,
          [`${pre}-${size}-order-${sizeProps.order}`]: sizeProps.order || sizeProps.order === 0,
          [`${pre}-${size}-offset-${sizeProps.offset}`]: sizeProps.offset || sizeProps.offset === 0,
          [`${pre}-${size}-push-${sizeProps.push}`]: sizeProps.push || sizeProps.push === 0,
          [`${pre}-${size}-pull-${sizeProps.pull}`]: sizeProps.pull || sizeProps.pull === 0,
          [`${pre}-rtl`]: direction.value === "rtl"
        });
      });
      return classNames(pre, {
        [`${pre}-${span}`]: span !== void 0,
        [`${pre}-order-${order}`]: order,
        [`${pre}-offset-${offset}`]: offset,
        [`${pre}-push-${push}`]: push,
        [`${pre}-pull-${pull}`]: pull
      }, sizeClassObj, attrs.class, hashId.value);
    });
    const mergedStyle = computed(() => {
      const {
        flex
      } = props;
      const gutterVal = gutter.value;
      const style = {};
      if (gutterVal && gutterVal[0] > 0) {
        const horizontalGutter = `${gutterVal[0] / 2}px`;
        style.paddingLeft = horizontalGutter;
        style.paddingRight = horizontalGutter;
      }
      if (gutterVal && gutterVal[1] > 0 && !supportFlexGap.value) {
        const verticalGutter = `${gutterVal[1] / 2}px`;
        style.paddingTop = verticalGutter;
        style.paddingBottom = verticalGutter;
      }
      if (flex) {
        style.flex = parseFlex(flex);
        if (wrap.value === false && !style.minWidth) {
          style.minWidth = 0;
        }
      }
      return style;
    });
    return () => {
      var _a;
      return wrapSSR(createVNode("div", _objectSpread2(_objectSpread2({}, attrs), {}, {
        "class": classes.value,
        "style": [mergedStyle.value, attrs.style]
      }), [(_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots)]));
    };
  }
});
const genCollapseMotion = (token) => ({
  [token.componentCls]: {
    // For common/openAnimation
    [`${token.antCls}-motion-collapse-legacy`]: {
      overflow: "hidden",
      "&-active": {
        transition: `height ${token.motionDurationMid} ${token.motionEaseInOut},
        opacity ${token.motionDurationMid} ${token.motionEaseInOut} !important`
      }
    },
    [`${token.antCls}-motion-collapse`]: {
      overflow: "hidden",
      transition: `height ${token.motionDurationMid} ${token.motionEaseInOut},
        opacity ${token.motionDurationMid} ${token.motionEaseInOut} !important`
    }
  }
});
var QuestionCircleOutlined$1 = { "icon": { "tag": "svg", "attrs": { "viewBox": "64 64 896 896", "focusable": "false" }, "children": [{ "tag": "path", "attrs": { "d": "M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z" } }, { "tag": "path", "attrs": { "d": "M623.6 316.7C593.6 290.4 554 276 512 276s-81.6 14.5-111.6 40.7C369.2 344 352 380.7 352 420v7.6c0 4.4 3.6 8 8 8h48c4.4 0 8-3.6 8-8V420c0-44.1 43.1-80 96-80s96 35.9 96 80c0 31.1-22 59.6-56.1 72.7-21.2 8.1-39.2 22.3-52.1 40.9-13.1 19-19.9 41.8-19.9 64.9V620c0 4.4 3.6 8 8 8h48c4.4 0 8-3.6 8-8v-22.7a48.3 48.3 0 0130.9-44.8c59-22.7 97.1-74.7 97.1-132.5.1-39.3-17.1-76-48.3-103.3zM472 732a40 40 0 1080 0 40 40 0 10-80 0z" } }] }, "name": "question-circle", "theme": "outlined" };
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
var QuestionCircleOutlined = function QuestionCircleOutlined2(props, context) {
  var p = _objectSpread({}, props, context.attrs);
  return createVNode(Icon, _objectSpread({}, p, {
    "icon": QuestionCircleOutlined$1
  }), null);
};
QuestionCircleOutlined.displayName = "QuestionCircleOutlined";
QuestionCircleOutlined.inheritAttrs = false;
const FormItemLabel = (props, _ref) => {
  let {
    slots,
    emit,
    attrs
  } = _ref;
  var _a, _b, _c, _d, _e;
  const {
    prefixCls,
    htmlFor,
    labelCol,
    labelAlign,
    colon,
    required,
    requiredMark
  } = _extends(_extends({}, props), attrs);
  const [formLocale] = useLocaleReceiver("Form");
  const label = (_a = props.label) !== null && _a !== void 0 ? _a : (_b = slots.label) === null || _b === void 0 ? void 0 : _b.call(slots);
  if (!label) return null;
  const {
    vertical,
    labelAlign: contextLabelAlign,
    labelCol: contextLabelCol,
    labelWrap,
    colon: contextColon
  } = useInjectForm();
  const mergedLabelCol = labelCol || (contextLabelCol === null || contextLabelCol === void 0 ? void 0 : contextLabelCol.value) || {};
  const mergedLabelAlign = labelAlign || (contextLabelAlign === null || contextLabelAlign === void 0 ? void 0 : contextLabelAlign.value);
  const labelClsBasic = `${prefixCls}-item-label`;
  const labelColClassName = classNames(labelClsBasic, mergedLabelAlign === "left" && `${labelClsBasic}-left`, mergedLabelCol.class, {
    [`${labelClsBasic}-wrap`]: !!labelWrap.value
  });
  let labelChildren = label;
  const computedColon = colon === true || (contextColon === null || contextColon === void 0 ? void 0 : contextColon.value) !== false && colon !== false;
  const haveColon = computedColon && !vertical.value;
  if (haveColon && typeof label === "string" && label.trim() !== "") {
    labelChildren = label.replace(/[:|：]\s*$/, "");
  }
  if (props.tooltip || slots.tooltip) {
    const tooltipNode = createVNode("span", {
      "class": `${prefixCls}-item-tooltip`
    }, [createVNode(Tooltip, {
      "title": props.tooltip
    }, {
      default: () => [createVNode(QuestionCircleOutlined, null, null)]
    })]);
    labelChildren = createVNode(Fragment, null, [labelChildren, slots.tooltip ? (_c = slots.tooltip) === null || _c === void 0 ? void 0 : _c.call(slots, {
      class: `${prefixCls}-item-tooltip`
    }) : tooltipNode]);
  }
  if (requiredMark === "optional" && !required) {
    labelChildren = createVNode(Fragment, null, [labelChildren, createVNode("span", {
      "class": `${prefixCls}-item-optional`
    }, [((_d = formLocale.value) === null || _d === void 0 ? void 0 : _d.optional) || ((_e = localeValues.Form) === null || _e === void 0 ? void 0 : _e.optional)])]);
  }
  const labelClassName = classNames({
    [`${prefixCls}-item-required`]: required,
    [`${prefixCls}-item-required-mark-optional`]: requiredMark === "optional",
    [`${prefixCls}-item-no-colon`]: !computedColon
  });
  return createVNode(Col, _objectSpread2(_objectSpread2({}, mergedLabelCol), {}, {
    "class": labelColClassName
  }), {
    default: () => [createVNode("label", {
      "for": htmlFor,
      "class": labelClassName,
      "title": typeof label === "string" ? label : "",
      "onClick": (e) => emit("click", e)
    }, [labelChildren])]
  });
};
FormItemLabel.displayName = "FormItemLabel";
FormItemLabel.inheritAttrs = false;
function hasClass(node, className) {
  if (node.classList) {
    return node.classList.contains(className);
  }
  const originClass = node.className;
  return ` ${originClass} `.indexOf(` ${className} `) > -1;
}
function addClass(node, className) {
  if (node.classList) {
    node.classList.add(className);
  } else {
    if (!hasClass(node, className)) {
      node.className = `${node.className} ${className}`;
    }
  }
}
function removeClass(node, className) {
  if (node.classList) {
    node.classList.remove(className);
  } else {
    if (hasClass(node, className)) {
      const originClass = node.className;
      node.className = ` ${originClass} `.replace(` ${className} `, " ");
    }
  }
}
const collapseMotion = function() {
  let name = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : "ant-motion-collapse";
  let appear = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : true;
  return {
    name,
    appear,
    css: true,
    onBeforeEnter: (node) => {
      node.style.height = "0px";
      node.style.opacity = "0";
      addClass(node, name);
    },
    onEnter: (node) => {
      nextTick(() => {
        node.style.height = `${node.scrollHeight}px`;
        node.style.opacity = "1";
      });
    },
    onAfterEnter: (node) => {
      if (node) {
        removeClass(node, name);
        node.style.height = null;
        node.style.opacity = null;
      }
    },
    onBeforeLeave: (node) => {
      addClass(node, name);
      node.style.height = `${node.offsetHeight}px`;
      node.style.opacity = null;
    },
    onLeave: (node) => {
      setTimeout(() => {
        node.style.height = "0px";
        node.style.opacity = "0";
      });
    },
    onAfterLeave: (node) => {
      if (node) {
        removeClass(node, name);
        if (node.style) {
          node.style.height = null;
          node.style.opacity = null;
        }
      }
    }
  };
};
const genFormValidateMotionStyle = (token) => {
  const {
    componentCls
  } = token;
  const helpCls = `${componentCls}-show-help`;
  const helpItemCls = `${componentCls}-show-help-item`;
  return {
    [helpCls]: {
      // Explain holder
      transition: `opacity ${token.motionDurationSlow} ${token.motionEaseInOut}`,
      "&-appear, &-enter": {
        opacity: 0,
        "&-active": {
          opacity: 1
        }
      },
      "&-leave": {
        opacity: 1,
        "&-active": {
          opacity: 0
        }
      },
      // Explain
      [helpItemCls]: {
        overflow: "hidden",
        transition: `height ${token.motionDurationSlow} ${token.motionEaseInOut},
                     opacity ${token.motionDurationSlow} ${token.motionEaseInOut},
                     transform ${token.motionDurationSlow} ${token.motionEaseInOut} !important`,
        [`&${helpItemCls}-appear, &${helpItemCls}-enter`]: {
          transform: `translateY(-5px)`,
          opacity: 0,
          [`&-active`]: {
            transform: "translateY(0)",
            opacity: 1
          }
        },
        [`&${helpItemCls}-leave-active`]: {
          transform: `translateY(-5px)`
        }
      }
    }
  };
};
const resetForm = (token) => ({
  legend: {
    display: "block",
    width: "100%",
    marginBottom: token.marginLG,
    padding: 0,
    color: token.colorTextDescription,
    fontSize: token.fontSizeLG,
    lineHeight: "inherit",
    border: 0,
    borderBottom: `${token.lineWidth}px ${token.lineType} ${token.colorBorder}`
  },
  label: {
    fontSize: token.fontSize
  },
  'input[type="search"]': {
    boxSizing: "border-box"
  },
  // Position radios and checkboxes better
  'input[type="radio"], input[type="checkbox"]': {
    lineHeight: "normal"
  },
  'input[type="file"]': {
    display: "block"
  },
  // Make range inputs behave like textual form controls
  'input[type="range"]': {
    display: "block",
    width: "100%"
  },
  // Make multiple select elements height not fixed
  "select[multiple], select[size]": {
    height: "auto"
  },
  // Focus for file, radio, and checkbox
  [`input[type='file']:focus,
  input[type='radio']:focus,
  input[type='checkbox']:focus`]: {
    outline: 0,
    boxShadow: `0 0 0 ${token.controlOutlineWidth}px ${token.controlOutline}`
  },
  // Adjust output element
  output: {
    display: "block",
    paddingTop: 15,
    color: token.colorText,
    fontSize: token.fontSize,
    lineHeight: token.lineHeight
  }
});
const genFormSize = (token, height) => {
  const {
    formItemCls
  } = token;
  return {
    [formItemCls]: {
      [`${formItemCls}-label > label`]: {
        height
      },
      [`${formItemCls}-control-input`]: {
        minHeight: height
      }
    }
  };
};
const genFormStyle = (token) => {
  const {
    componentCls
  } = token;
  return {
    [token.componentCls]: _extends(_extends(_extends({}, resetComponent(token)), resetForm(token)), {
      [`${componentCls}-text`]: {
        display: "inline-block",
        paddingInlineEnd: token.paddingSM
      },
      // ================================================================
      // =                             Size                             =
      // ================================================================
      "&-small": _extends({}, genFormSize(token, token.controlHeightSM)),
      "&-large": _extends({}, genFormSize(token, token.controlHeightLG))
    })
  };
};
const genFormItemStyle = (token) => {
  const {
    formItemCls,
    iconCls,
    componentCls,
    rootPrefixCls
  } = token;
  return {
    [formItemCls]: _extends(_extends({}, resetComponent(token)), {
      marginBottom: token.marginLG,
      verticalAlign: "top",
      "&-with-help": {
        transition: "none"
      },
      [`&-hidden,
        &-hidden.${rootPrefixCls}-row`]: {
        // https://github.com/ant-design/ant-design/issues/26141
        display: "none"
      },
      "&-has-warning": {
        [`${formItemCls}-split`]: {
          color: token.colorError
        }
      },
      "&-has-error": {
        [`${formItemCls}-split`]: {
          color: token.colorWarning
        }
      },
      // ==============================================================
      // =                            Label                           =
      // ==============================================================
      [`${formItemCls}-label`]: {
        display: "inline-block",
        flexGrow: 0,
        overflow: "hidden",
        whiteSpace: "nowrap",
        textAlign: "end",
        verticalAlign: "middle",
        "&-left": {
          textAlign: "start"
        },
        "&-wrap": {
          overflow: "unset",
          lineHeight: `${token.lineHeight} - 0.25em`,
          whiteSpace: "unset"
        },
        "> label": {
          position: "relative",
          display: "inline-flex",
          alignItems: "center",
          maxWidth: "100%",
          height: token.controlHeight,
          color: token.colorTextHeading,
          fontSize: token.fontSize,
          [`> ${iconCls}`]: {
            fontSize: token.fontSize,
            verticalAlign: "top"
          },
          // Required mark
          [`&${formItemCls}-required:not(${formItemCls}-required-mark-optional)::before`]: {
            display: "inline-block",
            marginInlineEnd: token.marginXXS,
            color: token.colorError,
            fontSize: token.fontSize,
            fontFamily: "SimSun, sans-serif",
            lineHeight: 1,
            content: '"*"',
            [`${componentCls}-hide-required-mark &`]: {
              display: "none"
            }
          },
          // Optional mark
          [`${formItemCls}-optional`]: {
            display: "inline-block",
            marginInlineStart: token.marginXXS,
            color: token.colorTextDescription,
            [`${componentCls}-hide-required-mark &`]: {
              display: "none"
            }
          },
          // Optional mark
          [`${formItemCls}-tooltip`]: {
            color: token.colorTextDescription,
            cursor: "help",
            writingMode: "horizontal-tb",
            marginInlineStart: token.marginXXS
          },
          "&::after": {
            content: '":"',
            position: "relative",
            marginBlock: 0,
            marginInlineStart: token.marginXXS / 2,
            marginInlineEnd: token.marginXS
          },
          [`&${formItemCls}-no-colon::after`]: {
            content: '" "'
          }
        }
      },
      // ==============================================================
      // =                            Input                           =
      // ==============================================================
      [`${formItemCls}-control`]: {
        display: "flex",
        flexDirection: "column",
        flexGrow: 1,
        [`&:first-child:not([class^="'${rootPrefixCls}-col-'"]):not([class*="' ${rootPrefixCls}-col-'"])`]: {
          width: "100%"
        },
        "&-input": {
          position: "relative",
          display: "flex",
          alignItems: "center",
          minHeight: token.controlHeight,
          "&-content": {
            flex: "auto",
            maxWidth: "100%"
          }
        }
      },
      // ==============================================================
      // =                           Explain                          =
      // ==============================================================
      [formItemCls]: {
        "&-explain, &-extra": {
          clear: "both",
          color: token.colorTextDescription,
          fontSize: token.fontSize,
          lineHeight: token.lineHeight
        },
        "&-explain-connected": {
          width: "100%"
        },
        "&-extra": {
          minHeight: token.controlHeightSM,
          transition: `color ${token.motionDurationMid} ${token.motionEaseOut}`
          // sync input color transition
        },
        "&-explain": {
          "&-error": {
            color: token.colorError
          },
          "&-warning": {
            color: token.colorWarning
          }
        }
      },
      [`&-with-help ${formItemCls}-explain`]: {
        height: "auto",
        opacity: 1
      },
      // ==============================================================
      // =                        Feedback Icon                       =
      // ==============================================================
      [`${formItemCls}-feedback-icon`]: {
        fontSize: token.fontSize,
        textAlign: "center",
        visibility: "visible",
        animationName: zoomIn,
        animationDuration: token.motionDurationMid,
        animationTimingFunction: token.motionEaseOutBack,
        pointerEvents: "none",
        "&-success": {
          color: token.colorSuccess
        },
        "&-error": {
          color: token.colorError
        },
        "&-warning": {
          color: token.colorWarning
        },
        "&-validating": {
          color: token.colorPrimary
        }
      }
    })
  };
};
const genHorizontalStyle = (token) => {
  const {
    componentCls,
    formItemCls,
    rootPrefixCls
  } = token;
  return {
    [`${componentCls}-horizontal`]: {
      [`${formItemCls}-label`]: {
        flexGrow: 0
      },
      [`${formItemCls}-control`]: {
        flex: "1 1 0",
        // https://github.com/ant-design/ant-design/issues/32777
        // https://github.com/ant-design/ant-design/issues/33773
        minWidth: 0
      },
      // https://github.com/ant-design/ant-design/issues/32980
      [`${formItemCls}-label.${rootPrefixCls}-col-24 + ${formItemCls}-control`]: {
        minWidth: "unset"
      }
    }
  };
};
const genInlineStyle = (token) => {
  const {
    componentCls,
    formItemCls
  } = token;
  return {
    [`${componentCls}-inline`]: {
      display: "flex",
      flexWrap: "wrap",
      [formItemCls]: {
        flex: "none",
        flexWrap: "nowrap",
        marginInlineEnd: token.margin,
        marginBottom: 0,
        "&-with-help": {
          marginBottom: token.marginLG
        },
        [`> ${formItemCls}-label,
        > ${formItemCls}-control`]: {
          display: "inline-block",
          verticalAlign: "top"
        },
        [`> ${formItemCls}-label`]: {
          flex: "none"
        },
        [`${componentCls}-text`]: {
          display: "inline-block"
        },
        [`${formItemCls}-has-feedback`]: {
          display: "inline-block"
        }
      }
    }
  };
};
const makeVerticalLayoutLabel = (token) => ({
  margin: 0,
  padding: `0 0 ${token.paddingXS}px`,
  whiteSpace: "initial",
  textAlign: "start",
  "> label": {
    margin: 0,
    "&::after": {
      display: "none"
    }
  }
});
const makeVerticalLayout = (token) => {
  const {
    componentCls,
    formItemCls
  } = token;
  return {
    [`${formItemCls} ${formItemCls}-label`]: makeVerticalLayoutLabel(token),
    [componentCls]: {
      [formItemCls]: {
        flexWrap: "wrap",
        [`${formItemCls}-label,
          ${formItemCls}-control`]: {
          flex: "0 0 100%",
          maxWidth: "100%"
        }
      }
    }
  };
};
const genVerticalStyle = (token) => {
  const {
    componentCls,
    formItemCls,
    rootPrefixCls
  } = token;
  return {
    [`${componentCls}-vertical`]: {
      [formItemCls]: {
        "&-row": {
          flexDirection: "column"
        },
        "&-label > label": {
          height: "auto"
        },
        [`${componentCls}-item-control`]: {
          width: "100%"
        }
      }
    },
    [`${componentCls}-vertical ${formItemCls}-label,
      .${rootPrefixCls}-col-24${formItemCls}-label,
      .${rootPrefixCls}-col-xl-24${formItemCls}-label`]: makeVerticalLayoutLabel(token),
    [`@media (max-width: ${token.screenXSMax}px)`]: [makeVerticalLayout(token), {
      [componentCls]: {
        [`.${rootPrefixCls}-col-xs-24${formItemCls}-label`]: makeVerticalLayoutLabel(token)
      }
    }],
    [`@media (max-width: ${token.screenSMMax}px)`]: {
      [componentCls]: {
        [`.${rootPrefixCls}-col-sm-24${formItemCls}-label`]: makeVerticalLayoutLabel(token)
      }
    },
    [`@media (max-width: ${token.screenMDMax}px)`]: {
      [componentCls]: {
        [`.${rootPrefixCls}-col-md-24${formItemCls}-label`]: makeVerticalLayoutLabel(token)
      }
    },
    [`@media (max-width: ${token.screenLGMax}px)`]: {
      [componentCls]: {
        [`.${rootPrefixCls}-col-lg-24${formItemCls}-label`]: makeVerticalLayoutLabel(token)
      }
    }
  };
};
const useStyle = genComponentStyleHook("Form", (token, _ref) => {
  let {
    rootPrefixCls
  } = _ref;
  const formToken = merge(token, {
    formItemCls: `${token.componentCls}-item`,
    rootPrefixCls
  });
  return [genFormStyle(formToken), genFormItemStyle(formToken), genFormValidateMotionStyle(formToken), genHorizontalStyle(formToken), genInlineStyle(formToken), genVerticalStyle(formToken), genCollapseMotion(formToken), zoomIn];
});
const ErrorList = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "ErrorList",
  inheritAttrs: false,
  props: ["errors", "help", "onErrorVisibleChanged", "helpStatus", "warnings"],
  setup(props, _ref) {
    let {
      attrs
    } = _ref;
    const {
      prefixCls,
      status
    } = useInjectFormItemPrefix();
    const baseClassName = computed(() => `${prefixCls.value}-item-explain`);
    const visible = computed(() => !!(props.errors && props.errors.length));
    const innerStatus = ref(status.value);
    const [, hashId] = useStyle(prefixCls);
    watch([visible, status], () => {
      if (visible.value) {
        innerStatus.value = status.value;
      }
    });
    return () => {
      var _a, _b;
      const colMItem = collapseMotion(`${prefixCls.value}-show-help-item`);
      const transitionGroupProps = getTransitionGroupProps(`${prefixCls.value}-show-help-item`, colMItem);
      transitionGroupProps.role = "alert";
      transitionGroupProps.class = [hashId.value, baseClassName.value, attrs.class, `${prefixCls.value}-show-help`];
      return createVNode(Transition, _objectSpread2(_objectSpread2({}, getTransitionProps(`${prefixCls.value}-show-help`)), {}, {
        "onAfterEnter": () => props.onErrorVisibleChanged(true),
        "onAfterLeave": () => props.onErrorVisibleChanged(false)
      }), {
        default: () => [withDirectives(createVNode(TransitionGroup, _objectSpread2(_objectSpread2({}, transitionGroupProps), {}, {
          "tag": "div"
        }), {
          default: () => [(_b = props.errors) === null || _b === void 0 ? void 0 : _b.map((error, index) => createVNode("div", {
            "key": index,
            "class": innerStatus.value ? `${baseClassName.value}-${innerStatus.value}` : ""
          }, [error]))]
        }), [[vShow, !!((_a = props.errors) === null || _a === void 0 ? void 0 : _a.length)]])]
      });
    };
  }
});
const FormItemInput = defineComponent({
  compatConfig: {
    MODE: 3
  },
  slots: Object,
  inheritAttrs: false,
  props: ["prefixCls", "errors", "hasFeedback", "onDomErrorVisibleChange", "wrapperCol", "help", "extra", "status", "marginBottom", "onErrorVisibleChanged"],
  setup(props, _ref) {
    let {
      slots
    } = _ref;
    const formContext = useInjectForm();
    const {
      wrapperCol: contextWrapperCol
    } = formContext;
    const subFormContext = _extends({}, formContext);
    delete subFormContext.labelCol;
    delete subFormContext.wrapperCol;
    useProvideForm(subFormContext);
    useProvideFormItemPrefix({
      prefixCls: computed(() => props.prefixCls),
      status: computed(() => props.status)
    });
    return () => {
      var _a, _b, _c;
      const {
        prefixCls,
        wrapperCol,
        marginBottom,
        onErrorVisibleChanged,
        help = (_a = slots.help) === null || _a === void 0 ? void 0 : _a.call(slots),
        errors = filterEmpty((_b = slots.errors) === null || _b === void 0 ? void 0 : _b.call(slots)),
        // hasFeedback,
        // status,
        extra = (_c = slots.extra) === null || _c === void 0 ? void 0 : _c.call(slots)
      } = props;
      const baseClassName = `${prefixCls}-item`;
      const mergedWrapperCol = wrapperCol || (contextWrapperCol === null || contextWrapperCol === void 0 ? void 0 : contextWrapperCol.value) || {};
      const className = classNames(`${baseClassName}-control`, mergedWrapperCol.class);
      return createVNode(Col, _objectSpread2(_objectSpread2({}, mergedWrapperCol), {}, {
        "class": className
      }), {
        default: () => {
          var _a2;
          return createVNode(Fragment, null, [createVNode("div", {
            "class": `${baseClassName}-control-input`
          }, [createVNode("div", {
            "class": `${baseClassName}-control-input-content`
          }, [(_a2 = slots.default) === null || _a2 === void 0 ? void 0 : _a2.call(slots)])]), marginBottom !== null || errors.length ? createVNode("div", {
            "style": {
              display: "flex",
              flexWrap: "nowrap"
            }
          }, [createVNode(ErrorList, {
            "errors": errors,
            "help": help,
            "class": `${baseClassName}-explain-connected`,
            "onErrorVisibleChanged": onErrorVisibleChanged
          }, null), !!marginBottom && createVNode("div", {
            "style": {
              width: 0,
              height: `${marginBottom}px`
            }
          }, null)]) : null, extra ? createVNode("div", {
            "class": `${baseClassName}-extra`
          }, [extra]) : null]);
        }
      });
    };
  }
});
function useDebounce(value) {
  const cacheValue = shallowRef(value.value.slice());
  let timeout = null;
  watchEffect(() => {
    clearTimeout(timeout);
    timeout = setTimeout(() => {
      cacheValue.value = value.value;
    }, value.value.length ? 0 : 10);
  });
  return cacheValue;
}
tuple("success", "warning", "error", "validating", "");
const iconMap = {
  success: CheckCircleFilled,
  warning: ExclamationCircleFilled,
  error: CloseCircleFilled,
  validating: LoadingOutlined
};
function getPropByPath(obj, namePathList, strict) {
  let tempObj = obj;
  const keyArr = namePathList;
  let i = 0;
  try {
    for (let len = keyArr.length; i < len - 1; ++i) {
      if (!tempObj && !strict) break;
      const key = keyArr[i];
      if (key in tempObj) {
        tempObj = tempObj[key];
      } else {
        if (strict) {
          throw Error("please transfer a valid name path to form item!");
        }
        break;
      }
    }
    if (strict && !tempObj) {
      throw Error("please transfer a valid name path to form item!");
    }
  } catch (error) {
    console.error("please transfer a valid name path to form item!");
  }
  return {
    o: tempObj,
    k: keyArr[i],
    v: tempObj ? tempObj[keyArr[i]] : void 0
  };
}
const formItemProps = () => ({
  htmlFor: String,
  prefixCls: String,
  label: PropTypes.any,
  help: PropTypes.any,
  extra: PropTypes.any,
  labelCol: {
    type: Object
  },
  wrapperCol: {
    type: Object
  },
  hasFeedback: {
    type: Boolean,
    default: false
  },
  colon: {
    type: Boolean,
    default: void 0
  },
  labelAlign: String,
  prop: {
    type: [String, Number, Array]
  },
  name: {
    type: [String, Number, Array]
  },
  rules: [Array, Object],
  autoLink: {
    type: Boolean,
    default: true
  },
  required: {
    type: Boolean,
    default: void 0
  },
  validateFirst: {
    type: Boolean,
    default: void 0
  },
  validateStatus: PropTypes.oneOf(tuple("", "success", "warning", "error", "validating")),
  validateTrigger: {
    type: [String, Array]
  },
  messageVariables: {
    type: Object
  },
  hidden: Boolean,
  noStyle: Boolean,
  tooltip: String
});
let indexGuid = 0;
const defaultItemNamePrefixCls = "form_item";
const FormItem = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "AFormItem",
  inheritAttrs: false,
  __ANT_NEW_FORM_ITEM: true,
  props: formItemProps(),
  slots: Object,
  setup(props, _ref) {
    let {
      slots,
      attrs,
      expose
    } = _ref;
    warning(props.prop === void 0, `\`prop\` is deprecated. Please use \`name\` instead.`);
    const eventKey = `form-item-${++indexGuid}`;
    const {
      prefixCls
    } = useConfigInject("form", props);
    const [wrapSSR, hashId] = useStyle(prefixCls);
    const itemRef = shallowRef();
    const formContext = useInjectForm();
    const fieldName = computed(() => props.name || props.prop);
    const errors = shallowRef([]);
    const validateDisabled = shallowRef(false);
    const inputRef = shallowRef();
    const namePath = computed(() => {
      const val = fieldName.value;
      return getNamePath(val);
    });
    const fieldId = computed(() => {
      if (!namePath.value.length) {
        return void 0;
      } else {
        const formName = formContext.name.value;
        const mergedId = namePath.value.join("_");
        return formName ? `${formName}_${mergedId}` : `${defaultItemNamePrefixCls}_${mergedId}`;
      }
    });
    const getNewFieldValue = () => {
      const model = formContext.model.value;
      if (!model || !fieldName.value) {
        return;
      } else {
        return getPropByPath(model, namePath.value, true).v;
      }
    };
    const fieldValue = computed(() => getNewFieldValue());
    const initialValue = shallowRef(cloneDeep(fieldValue.value));
    const mergedValidateTrigger = computed(() => {
      let validateTrigger = props.validateTrigger !== void 0 ? props.validateTrigger : formContext.validateTrigger.value;
      validateTrigger = validateTrigger === void 0 ? "change" : validateTrigger;
      return toArray(validateTrigger);
    });
    const rulesRef = computed(() => {
      let formRules = formContext.rules.value;
      const selfRules = props.rules;
      const requiredRule = props.required !== void 0 ? {
        required: !!props.required,
        trigger: mergedValidateTrigger.value
      } : [];
      const prop = getPropByPath(formRules, namePath.value);
      formRules = formRules ? prop.o[prop.k] || prop.v : [];
      const rules = [].concat(selfRules || formRules || []);
      if (find(rules, (rule) => rule.required)) {
        return rules;
      } else {
        return rules.concat(requiredRule);
      }
    });
    const isRequired = computed(() => {
      const rules = rulesRef.value;
      let isRequired2 = false;
      if (rules && rules.length) {
        rules.every((rule) => {
          if (rule.required) {
            isRequired2 = true;
            return false;
          }
          return true;
        });
      }
      return isRequired2 || props.required;
    });
    const validateState = shallowRef();
    watchEffect(() => {
      validateState.value = props.validateStatus;
    });
    const messageVariables = computed(() => {
      let variables = {};
      if (typeof props.label === "string") {
        variables.label = props.label;
      } else if (props.name) {
        variables.label = String(props.name);
      }
      if (props.messageVariables) {
        variables = _extends(_extends({}, variables), props.messageVariables);
      }
      return variables;
    });
    const validateRules$1 = (options) => {
      if (namePath.value.length === 0) {
        return;
      }
      const {
        validateFirst = false
      } = props;
      const {
        triggerName
      } = options || {};
      let filteredRules = rulesRef.value;
      if (triggerName) {
        filteredRules = filteredRules.filter((rule) => {
          const {
            trigger
          } = rule;
          if (!trigger && !mergedValidateTrigger.value.length) {
            return true;
          }
          const triggerList = toArray(trigger || mergedValidateTrigger.value);
          return triggerList.includes(triggerName);
        });
      }
      if (!filteredRules.length) {
        return Promise.resolve();
      }
      const promise = validateRules(namePath.value, fieldValue.value, filteredRules, _extends({
        validateMessages: formContext.validateMessages.value
      }, options), validateFirst, messageVariables.value);
      validateState.value = "validating";
      errors.value = [];
      promise.catch((e) => e).then(function() {
        let results = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : [];
        if (validateState.value === "validating") {
          const res = results.filter((result) => result && result.errors.length);
          validateState.value = res.length ? "error" : "success";
          errors.value = res.map((r) => r.errors);
          formContext.onValidate(fieldName.value, !errors.value.length, errors.value.length ? toRaw(errors.value[0]) : null);
        }
      });
      return promise;
    };
    const onFieldBlur = () => {
      validateRules$1({
        triggerName: "blur"
      });
    };
    const onFieldChange = () => {
      if (validateDisabled.value) {
        validateDisabled.value = false;
        return;
      }
      validateRules$1({
        triggerName: "change"
      });
    };
    const clearValidate = () => {
      validateState.value = props.validateStatus;
      validateDisabled.value = false;
      errors.value = [];
    };
    const resetField = () => {
      var _a;
      validateState.value = props.validateStatus;
      validateDisabled.value = true;
      errors.value = [];
      const model = formContext.model.value || {};
      const value = fieldValue.value;
      const prop = getPropByPath(model, namePath.value, true);
      if (Array.isArray(value)) {
        prop.o[prop.k] = [].concat((_a = initialValue.value) !== null && _a !== void 0 ? _a : []);
      } else {
        prop.o[prop.k] = initialValue.value;
      }
      nextTick(() => {
        validateDisabled.value = false;
      });
    };
    const htmlFor = computed(() => {
      return props.htmlFor === void 0 ? fieldId.value : props.htmlFor;
    });
    const onLabelClick = () => {
      const id = htmlFor.value;
      if (!id || !inputRef.value) {
        return;
      }
      const control = inputRef.value.$el.querySelector(`[id="${id}"]`);
      if (control && control.focus) {
        control.focus();
      }
    };
    expose({
      onFieldBlur,
      onFieldChange,
      clearValidate,
      resetField
    });
    useProvideFormItemContext({
      id: fieldId,
      onFieldBlur: () => {
        if (props.autoLink) {
          onFieldBlur();
        }
      },
      onFieldChange: () => {
        if (props.autoLink) {
          onFieldChange();
        }
      },
      clearValidate
    }, computed(() => {
      return !!(props.autoLink && formContext.model.value && fieldName.value);
    }));
    let registered = false;
    watch(fieldName, (val) => {
      if (val) {
        if (!registered) {
          registered = true;
          formContext.addField(eventKey, {
            fieldValue,
            fieldId,
            fieldName,
            resetField,
            clearValidate,
            namePath,
            validateRules: validateRules$1,
            rules: rulesRef
          });
        }
      } else {
        registered = false;
        formContext.removeField(eventKey);
      }
    }, {
      immediate: true
    });
    onBeforeUnmount(() => {
      formContext.removeField(eventKey);
    });
    const debounceErrors = useDebounce(errors);
    const mergedValidateStatus = computed(() => {
      if (props.validateStatus !== void 0) {
        return props.validateStatus;
      } else if (debounceErrors.value.length) {
        return "error";
      }
      return validateState.value;
    });
    const itemClassName = computed(() => ({
      [`${prefixCls.value}-item`]: true,
      [hashId.value]: true,
      // Status
      [`${prefixCls.value}-item-has-feedback`]: mergedValidateStatus.value && props.hasFeedback,
      [`${prefixCls.value}-item-has-success`]: mergedValidateStatus.value === "success",
      [`${prefixCls.value}-item-has-warning`]: mergedValidateStatus.value === "warning",
      [`${prefixCls.value}-item-has-error`]: mergedValidateStatus.value === "error",
      [`${prefixCls.value}-item-is-validating`]: mergedValidateStatus.value === "validating",
      [`${prefixCls.value}-item-hidden`]: props.hidden
    }));
    const formItemInputContext = reactive({});
    FormItemInputContext.useProvide(formItemInputContext);
    watchEffect(() => {
      let feedbackIcon;
      if (props.hasFeedback) {
        const IconNode = mergedValidateStatus.value && iconMap[mergedValidateStatus.value];
        feedbackIcon = IconNode ? createVNode("span", {
          "class": classNames(`${prefixCls.value}-item-feedback-icon`, `${prefixCls.value}-item-feedback-icon-${mergedValidateStatus.value}`)
        }, [createVNode(IconNode, null, null)]) : null;
      }
      _extends(formItemInputContext, {
        status: mergedValidateStatus.value,
        hasFeedback: props.hasFeedback,
        feedbackIcon,
        isFormItemInput: true
      });
    });
    const marginBottom = shallowRef(null);
    const showMarginOffset = shallowRef(false);
    const updateMarginBottom = () => {
      if (itemRef.value) {
        const itemStyle = getComputedStyle(itemRef.value);
        marginBottom.value = parseInt(itemStyle.marginBottom, 10);
      }
    };
    onMounted(() => {
      watch(showMarginOffset, () => {
        if (showMarginOffset.value) {
          updateMarginBottom();
        }
      }, {
        flush: "post",
        immediate: true
      });
    });
    const onErrorVisibleChanged = (nextVisible) => {
      if (!nextVisible) {
        marginBottom.value = null;
      }
    };
    return () => {
      var _a, _b;
      if (props.noStyle) return (_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots);
      const help = (_b = props.help) !== null && _b !== void 0 ? _b : slots.help ? filterEmpty(slots.help()) : null;
      const withHelp = !!(help !== void 0 && help !== null && Array.isArray(help) && help.length || debounceErrors.value.length);
      showMarginOffset.value = withHelp;
      return wrapSSR(createVNode("div", {
        "class": [itemClassName.value, withHelp ? `${prefixCls.value}-item-with-help` : "", attrs.class],
        "ref": itemRef
      }, [createVNode(ARow, _objectSpread2(_objectSpread2({}, attrs), {}, {
        "class": `${prefixCls.value}-item-row`,
        "key": "row"
      }), {
        default: () => {
          var _a2, _b2;
          return createVNode(Fragment, null, [createVNode(FormItemLabel, _objectSpread2(_objectSpread2({}, props), {}, {
            "htmlFor": htmlFor.value,
            "required": isRequired.value,
            "requiredMark": formContext.requiredMark.value,
            "prefixCls": prefixCls.value,
            "onClick": onLabelClick,
            "label": props.label
          }), {
            label: slots.label,
            tooltip: slots.tooltip
          }), createVNode(FormItemInput, _objectSpread2(_objectSpread2({}, props), {}, {
            "errors": help !== void 0 && help !== null ? toArray(help) : debounceErrors.value,
            "marginBottom": marginBottom.value,
            "prefixCls": prefixCls.value,
            "status": mergedValidateStatus.value,
            "ref": inputRef,
            "help": help,
            "extra": (_a2 = props.extra) !== null && _a2 !== void 0 ? _a2 : (_b2 = slots.extra) === null || _b2 === void 0 ? void 0 : _b2.call(slots),
            "onErrorVisibleChanged": onErrorVisibleChanged
          }), {
            default: slots.default
          })]);
        }
      }), !!marginBottom.value && createVNode("div", {
        "class": `${prefixCls.value}-margin-offset`,
        "style": {
          marginBottom: `-${marginBottom.value}px`
        }
      }, null)]));
    };
  }
});
const FormItem$1 = /* @__PURE__ */ Object.freeze(/* @__PURE__ */ Object.defineProperty({
  __proto__: null,
  default: FormItem,
  formItemProps
}, Symbol.toStringTag, { value: "Module" }));
export {
  FormItem as F,
  useProvideForm as a,
  containsNamePath as b,
  cloneByNamePathList as c,
  defaultValidateMessages as d,
  FormItem$1 as e,
  formItemProps as f,
  getNamePath as g,
  toArray as t,
  useStyle as u,
  validateRules as v
};
//# sourceMappingURL=FormItem-CuXiQtyR.js.map
