import { _ as _extends, u as useConfigInject, e as useProviderSize, h as useProviderDisabled, i as useInjectGlobalForm, c as classNames, b as _objectSpread2, P as PropTypes, f as functionType, j as booleanType, s as stringType, k as someType, l as anyType, o as objectType, t as tuple, n as warning } from "./index-IfV1sCLI.js";
import { ref, unref, reactive, shallowRef, watch, toRaw, nextTick, defineComponent, computed, createVNode } from "vue";
import { v as validateRules, d as defaultValidateMessages, F as FormItem, u as useStyle, a as useProvideForm, c as cloneByNamePathList, t as toArray$1, g as getNamePath, b as containsNamePath } from "./FormItem-CuXiQtyR.js";
import { f } from "./FormItem-CuXiQtyR.js";
import { c as cloneDeep, d as debounce, o as omit, i as isEqual, a as intersection } from "./index-D9cY2WMP.js";
import { i as initDefaultProps } from "./raf-DXHxwK-q.js";
import { u as useInjectFormItemContext, F as FormItemRest } from "./FormItemContext-CwA3WYnI.js";
function allPromiseFinish(promiseList) {
  let hasError = false;
  let count = promiseList.length;
  const results = [];
  if (!promiseList.length) {
    return Promise.resolve([]);
  }
  return new Promise((resolve, reject) => {
    promiseList.forEach((promise, index) => {
      promise.catch((e2) => {
        hasError = true;
        return e2;
      }).then((result) => {
        count -= 1;
        results[index] = result;
        if (count > 0) {
          return;
        }
        if (hasError) {
          reject(results);
        }
        resolve(results);
      });
    });
  });
}
function t(t2) {
  return "object" == typeof t2 && null != t2 && 1 === t2.nodeType;
}
function e(t2, e2) {
  return (!e2 || "hidden" !== t2) && "visible" !== t2 && "clip" !== t2;
}
function n(t2, n2) {
  if (t2.clientHeight < t2.scrollHeight || t2.clientWidth < t2.scrollWidth) {
    var r2 = getComputedStyle(t2, null);
    return e(r2.overflowY, n2) || e(r2.overflowX, n2) || function(t3) {
      var e2 = function(t4) {
        if (!t4.ownerDocument || !t4.ownerDocument.defaultView) return null;
        try {
          return t4.ownerDocument.defaultView.frameElement;
        } catch (t5) {
          return null;
        }
      }(t3);
      return !!e2 && (e2.clientHeight < t3.scrollHeight || e2.clientWidth < t3.scrollWidth);
    }(t2);
  }
  return false;
}
function r(t2, e2, n2, r2, i2, o, l, d) {
  return o < t2 && l > e2 || o > t2 && l < e2 ? 0 : o <= t2 && d <= n2 || l >= e2 && d >= n2 ? o - t2 - r2 : l > e2 && d < n2 || o < t2 && d > n2 ? l - e2 + i2 : 0;
}
var i = function(e2, i2) {
  var o = window, l = i2.scrollMode, d = i2.block, f2 = i2.inline, h = i2.boundary, u = i2.skipOverflowHiddenElements, s = "function" == typeof h ? h : function(t2) {
    return t2 !== h;
  };
  if (!t(e2)) throw new TypeError("Invalid target");
  for (var a, c, g = document.scrollingElement || document.documentElement, p = [], m = e2; t(m) && s(m); ) {
    if ((m = null == (c = (a = m).parentElement) ? a.getRootNode().host || null : c) === g) {
      p.push(m);
      break;
    }
    null != m && m === document.body && n(m) && !n(document.documentElement) || null != m && n(m, u) && p.push(m);
  }
  for (var w = o.visualViewport ? o.visualViewport.width : innerWidth, v = o.visualViewport ? o.visualViewport.height : innerHeight, W = window.scrollX || pageXOffset, H = window.scrollY || pageYOffset, b = e2.getBoundingClientRect(), y = b.height, E = b.width, M = b.top, V = b.right, x = b.bottom, I = b.left, C = "start" === d || "nearest" === d ? M : "end" === d ? x : M + y / 2, R = "center" === f2 ? I + E / 2 : "end" === f2 ? V : I, T = [], k = 0; k < p.length; k++) {
    var B = p[k], D = B.getBoundingClientRect(), O = D.height, X = D.width, Y = D.top, L = D.right, S = D.bottom, j = D.left;
    if ("if-needed" === l && M >= 0 && I >= 0 && x <= v && V <= w && M >= Y && x <= S && I >= j && V <= L) return T;
    var N = getComputedStyle(B), q = parseInt(N.borderLeftWidth, 10), z = parseInt(N.borderTopWidth, 10), A = parseInt(N.borderRightWidth, 10), F = parseInt(N.borderBottomWidth, 10), G = 0, J = 0, K = "offsetWidth" in B ? B.offsetWidth - B.clientWidth - q - A : 0, P = "offsetHeight" in B ? B.offsetHeight - B.clientHeight - z - F : 0, Q = "offsetWidth" in B ? 0 === B.offsetWidth ? 0 : X / B.offsetWidth : 0, U = "offsetHeight" in B ? 0 === B.offsetHeight ? 0 : O / B.offsetHeight : 0;
    if (g === B) G = "start" === d ? C : "end" === d ? C - v : "nearest" === d ? r(H, H + v, v, z, F, H + C, H + C + y, y) : C - v / 2, J = "start" === f2 ? R : "center" === f2 ? R - w / 2 : "end" === f2 ? R - w : r(W, W + w, w, q, A, W + R, W + R + E, E), G = Math.max(0, G + H), J = Math.max(0, J + W);
    else {
      G = "start" === d ? C - Y - z : "end" === d ? C - S + F + P : "nearest" === d ? r(Y, S, O, z, F + P, C, C + y, y) : C - (Y + O / 2) + P / 2, J = "start" === f2 ? R - j - q : "center" === f2 ? R - (j + X / 2) + K / 2 : "end" === f2 ? R - L + A + K : r(j, L, X, q, A + K, R, R + E, E);
      var Z = B.scrollLeft, $ = B.scrollTop;
      C += $ - (G = Math.max(0, Math.min($ + G / U, B.scrollHeight - O / U + P))), R += Z - (J = Math.max(0, Math.min(Z + J / Q, B.scrollWidth - X / Q + K)));
    }
    T.push({ el: B, top: G, left: J });
  }
  return T;
};
function isOptionsObject(options) {
  return options === Object(options) && Object.keys(options).length !== 0;
}
function defaultBehavior(actions, behavior) {
  if (behavior === void 0) {
    behavior = "auto";
  }
  var canSmoothScroll = "scrollBehavior" in document.body.style;
  actions.forEach(function(_ref) {
    var el = _ref.el, top = _ref.top, left = _ref.left;
    if (el.scroll && canSmoothScroll) {
      el.scroll({
        top,
        left,
        behavior
      });
    } else {
      el.scrollTop = top;
      el.scrollLeft = left;
    }
  });
}
function getOptions(options) {
  if (options === false) {
    return {
      block: "end",
      inline: "nearest"
    };
  }
  if (isOptionsObject(options)) {
    return options;
  }
  return {
    block: "start",
    inline: "nearest"
  };
}
function scrollIntoView(target, options) {
  var isTargetAttached = target.isConnected || target.ownerDocument.documentElement.contains(target);
  if (isOptionsObject(options) && typeof options.behavior === "function") {
    return options.behavior(isTargetAttached ? i(target, options) : []);
  }
  if (!isTargetAttached) {
    return;
  }
  var computeOptions = getOptions(options);
  return defaultBehavior(i(target, computeOptions), computeOptions.behavior);
}
function isRequired(rules) {
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
  return isRequired2;
}
function toArray(value) {
  if (value === void 0 || value === null) {
    return [];
  }
  return Array.isArray(value) ? value : [value];
}
function getPropByPath(obj, path, strict) {
  let tempObj = obj;
  path = path.replace(/\[(\w+)\]/g, ".$1");
  path = path.replace(/^\./, "");
  const keyArr = path.split(".");
  let i2 = 0;
  for (let len = keyArr.length; i2 < len - 1; ++i2) {
    if (!tempObj && !strict) break;
    const key = keyArr[i2];
    if (key in tempObj) {
      tempObj = tempObj[key];
    } else {
      if (strict) {
        throw new Error("please transfer a valid name path to validate!");
      }
      break;
    }
  }
  return {
    o: tempObj,
    k: keyArr[i2],
    v: tempObj ? tempObj[keyArr[i2]] : null,
    isValid: tempObj && keyArr[i2] in tempObj
  };
}
function useForm(modelRef) {
  let rulesRef = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : ref({});
  let options = arguments.length > 2 ? arguments[2] : void 0;
  const initialModel = cloneDeep(unref(modelRef));
  const validateInfos = reactive({});
  const rulesKeys = shallowRef([]);
  const resetFields = (newValues) => {
    _extends(unref(modelRef), _extends(_extends({}, cloneDeep(initialModel)), newValues));
    nextTick(() => {
      Object.keys(validateInfos).forEach((key) => {
        validateInfos[key] = {
          autoLink: false,
          required: isRequired(unref(rulesRef)[key])
        };
      });
    });
  };
  const filterRules = function() {
    let rules = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : [];
    let trigger = arguments.length > 1 ? arguments[1] : void 0;
    if (!trigger.length) {
      return rules;
    } else {
      return rules.filter((rule) => {
        const triggerList = toArray(rule.trigger || "change");
        return intersection(triggerList, trigger).length;
      });
    }
  };
  let lastValidatePromise = null;
  const validateFields = function(names) {
    let option = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
    let strict = arguments.length > 2 ? arguments[2] : void 0;
    const promiseList = [];
    const values = {};
    for (let i2 = 0; i2 < names.length; i2++) {
      const name = names[i2];
      const prop = getPropByPath(unref(modelRef), name, strict);
      if (!prop.isValid) continue;
      values[name] = prop.v;
      const rules = filterRules(unref(rulesRef)[name], toArray(option && option.trigger));
      if (rules.length) {
        promiseList.push(validateField(name, prop.v, rules, option || {}).then(() => ({
          name,
          errors: [],
          warnings: []
        })).catch((ruleErrors) => {
          const mergedErrors = [];
          const mergedWarnings = [];
          ruleErrors.forEach((_ref) => {
            let {
              rule: {
                warningOnly
              },
              errors
            } = _ref;
            if (warningOnly) {
              mergedWarnings.push(...errors);
            } else {
              mergedErrors.push(...errors);
            }
          });
          if (mergedErrors.length) {
            return Promise.reject({
              name,
              errors: mergedErrors,
              warnings: mergedWarnings
            });
          }
          return {
            name,
            errors: mergedErrors,
            warnings: mergedWarnings
          };
        }));
      }
    }
    const summaryPromise = allPromiseFinish(promiseList);
    lastValidatePromise = summaryPromise;
    const returnPromise = summaryPromise.then(() => {
      if (lastValidatePromise === summaryPromise) {
        return Promise.resolve(values);
      }
      return Promise.reject([]);
    }).catch((results) => {
      const errorList = results.filter((result) => result && result.errors.length);
      return errorList.length ? Promise.reject({
        values,
        errorFields: errorList,
        outOfDate: lastValidatePromise !== summaryPromise
      }) : Promise.resolve(values);
    });
    returnPromise.catch((e2) => e2);
    return returnPromise;
  };
  const validateField = function(name, value, rules) {
    let option = arguments.length > 3 && arguments[3] !== void 0 ? arguments[3] : {};
    const promise = validateRules([name], value, rules, _extends({
      validateMessages: defaultValidateMessages
    }, option), !!option.validateFirst);
    if (!validateInfos[name]) {
      return promise.catch((e2) => e2);
    }
    validateInfos[name].validateStatus = "validating";
    promise.catch((e2) => e2).then(function() {
      let results = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : [];
      var _a;
      if (validateInfos[name].validateStatus === "validating") {
        const res = results.filter((result) => result && result.errors.length);
        validateInfos[name].validateStatus = res.length ? "error" : "success";
        validateInfos[name].help = res.length ? res.map((r2) => r2.errors) : null;
        (_a = options === null || options === void 0 ? void 0 : options.onValidate) === null || _a === void 0 ? void 0 : _a.call(options, name, !res.length, res.length ? toRaw(validateInfos[name].help[0]) : null);
      }
    });
    return promise;
  };
  const validate = (names, option) => {
    let keys = [];
    let strict = true;
    if (!names) {
      strict = false;
      keys = rulesKeys.value;
    } else if (Array.isArray(names)) {
      keys = names;
    } else {
      keys = [names];
    }
    const promises = validateFields(keys, option || {}, strict);
    promises.catch((e2) => e2);
    return promises;
  };
  const clearValidate = (names) => {
    let keys = [];
    if (!names) {
      keys = rulesKeys.value;
    } else if (Array.isArray(names)) {
      keys = names;
    } else {
      keys = [names];
    }
    keys.forEach((key) => {
      validateInfos[key] && _extends(validateInfos[key], {
        validateStatus: "",
        help: null
      });
    });
  };
  const mergeValidateInfo = (items) => {
    const info = {
      autoLink: false
    };
    const help = [];
    const infos = Array.isArray(items) ? items : [items];
    for (let i2 = 0; i2 < infos.length; i2++) {
      const arg = infos[i2];
      if ((arg === null || arg === void 0 ? void 0 : arg.validateStatus) === "error") {
        info.validateStatus = "error";
        arg.help && help.push(arg.help);
      }
      info.required = info.required || (arg === null || arg === void 0 ? void 0 : arg.required);
    }
    info.help = help;
    return info;
  };
  let oldModel = initialModel;
  let isFirstTime = true;
  const modelFn = (model) => {
    const names = [];
    rulesKeys.value.forEach((key) => {
      const prop = getPropByPath(model, key, false);
      const oldProp = getPropByPath(oldModel, key, false);
      const isFirstValidation = isFirstTime && (options === null || options === void 0 ? void 0 : options.immediate) && prop.isValid;
      if (isFirstValidation || !isEqual(prop.v, oldProp.v)) {
        names.push(key);
      }
    });
    validate(names, {
      trigger: "change"
    });
    isFirstTime = false;
    oldModel = cloneDeep(toRaw(model));
  };
  const debounceOptions = options === null || options === void 0 ? void 0 : options.debounce;
  let first = true;
  watch(rulesRef, () => {
    rulesKeys.value = rulesRef ? Object.keys(unref(rulesRef)) : [];
    if (!first && options && options.validateOnRuleChange) {
      validate();
    }
    first = false;
  }, {
    deep: true,
    immediate: true
  });
  watch(rulesKeys, () => {
    const newValidateInfos = {};
    rulesKeys.value.forEach((key) => {
      newValidateInfos[key] = _extends({}, validateInfos[key], {
        autoLink: false,
        required: isRequired(unref(rulesRef)[key])
      });
      delete validateInfos[key];
    });
    for (const key in validateInfos) {
      if (Object.prototype.hasOwnProperty.call(validateInfos, key)) {
        delete validateInfos[key];
      }
    }
    _extends(validateInfos, newValidateInfos);
  }, {
    immediate: true
  });
  watch(modelRef, debounceOptions && debounceOptions.wait ? debounce(modelFn, debounceOptions.wait, omit(debounceOptions, ["wait"])) : modelFn, {
    immediate: options && !!options.immediate,
    deep: true
  });
  return {
    modelRef,
    rulesRef,
    initialModel,
    validateInfos,
    resetFields,
    validate,
    validateField,
    mergeValidateInfo,
    clearValidate
  };
}
const formProps = () => ({
  layout: PropTypes.oneOf(tuple("horizontal", "inline", "vertical")),
  labelCol: objectType(),
  wrapperCol: objectType(),
  colon: booleanType(),
  labelAlign: stringType(),
  labelWrap: booleanType(),
  prefixCls: String,
  requiredMark: someType([String, Boolean]),
  /** @deprecated Will warning in future branch. Pls use `requiredMark` instead. */
  hideRequiredMark: booleanType(),
  model: PropTypes.object,
  rules: objectType(),
  validateMessages: objectType(),
  validateOnRuleChange: booleanType(),
  // 提交失败自动滚动到第一个错误字段
  scrollToFirstError: anyType(),
  onSubmit: functionType(),
  name: String,
  validateTrigger: someType([String, Array]),
  size: stringType(),
  disabled: booleanType(),
  onValuesChange: functionType(),
  onFieldsChange: functionType(),
  onFinish: functionType(),
  onFinishFailed: functionType(),
  onValidate: functionType()
});
function isEqualName(name1, name2) {
  return isEqual(toArray$1(name1), toArray$1(name2));
}
const Form = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "AForm",
  inheritAttrs: false,
  props: initDefaultProps(formProps(), {
    layout: "horizontal",
    hideRequiredMark: false,
    colon: true
  }),
  Item: FormItem,
  useForm,
  // emits: ['finishFailed', 'submit', 'finish', 'validate'],
  setup(props, _ref) {
    let {
      emit,
      slots,
      expose,
      attrs
    } = _ref;
    const {
      prefixCls,
      direction,
      form: contextForm,
      size,
      disabled
    } = useConfigInject("form", props);
    const requiredMark = computed(() => props.requiredMark === "" || props.requiredMark);
    const mergedRequiredMark = computed(() => {
      var _a;
      if (requiredMark.value !== void 0) {
        return requiredMark.value;
      }
      if (contextForm && ((_a = contextForm.value) === null || _a === void 0 ? void 0 : _a.requiredMark) !== void 0) {
        return contextForm.value.requiredMark;
      }
      if (props.hideRequiredMark) {
        return false;
      }
      return true;
    });
    useProviderSize(size);
    useProviderDisabled(disabled);
    const mergedColon = computed(() => {
      var _a, _b;
      return (_a = props.colon) !== null && _a !== void 0 ? _a : (_b = contextForm.value) === null || _b === void 0 ? void 0 : _b.colon;
    });
    const {
      validateMessages: globalValidateMessages
    } = useInjectGlobalForm();
    const validateMessages = computed(() => {
      return _extends(_extends(_extends({}, defaultValidateMessages), globalValidateMessages.value), props.validateMessages);
    });
    const [wrapSSR, hashId] = useStyle(prefixCls);
    const formClassName = computed(() => classNames(prefixCls.value, {
      [`${prefixCls.value}-${props.layout}`]: true,
      [`${prefixCls.value}-hide-required-mark`]: mergedRequiredMark.value === false,
      [`${prefixCls.value}-rtl`]: direction.value === "rtl",
      [`${prefixCls.value}-${size.value}`]: size.value
    }, hashId.value));
    const lastValidatePromise = ref();
    const fields = {};
    const addField = (eventKey, field) => {
      fields[eventKey] = field;
    };
    const removeField = (eventKey) => {
      delete fields[eventKey];
    };
    const getFieldsByNameList = (nameList) => {
      const provideNameList = !!nameList;
      const namePathList = provideNameList ? toArray$1(nameList).map(getNamePath) : [];
      if (!provideNameList) {
        return Object.values(fields);
      } else {
        return Object.values(fields).filter((field) => namePathList.findIndex((namePath) => isEqualName(namePath, field.fieldName.value)) > -1);
      }
    };
    const resetFields = (name) => {
      if (!props.model) {
        warning(false, "Form", "model is required for resetFields to work.");
        return;
      }
      getFieldsByNameList(name).forEach((field) => {
        field.resetField();
      });
    };
    const clearValidate = (name) => {
      getFieldsByNameList(name).forEach((field) => {
        field.clearValidate();
      });
    };
    const handleFinishFailed = (errorInfo) => {
      const {
        scrollToFirstError
      } = props;
      emit("finishFailed", errorInfo);
      if (scrollToFirstError && errorInfo.errorFields.length) {
        let scrollToFieldOptions = {};
        if (typeof scrollToFirstError === "object") {
          scrollToFieldOptions = scrollToFirstError;
        }
        scrollToField(errorInfo.errorFields[0].name, scrollToFieldOptions);
      }
    };
    const validate = function() {
      return validateField(...arguments);
    };
    const scrollToField = function(name) {
      let options = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
      const fields2 = getFieldsByNameList(name ? [name] : void 0);
      if (fields2.length) {
        const fieldId = fields2[0].fieldId.value;
        const node = fieldId ? document.getElementById(fieldId) : null;
        if (node) {
          scrollIntoView(node, _extends({
            scrollMode: "if-needed",
            block: "nearest"
          }, options));
        }
      }
    };
    const getFieldsValue = function() {
      let nameList = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : true;
      if (nameList === true) {
        const allNameList = [];
        Object.values(fields).forEach((_ref2) => {
          let {
            namePath
          } = _ref2;
          allNameList.push(namePath.value);
        });
        return cloneByNamePathList(props.model, allNameList);
      } else {
        return cloneByNamePathList(props.model, nameList);
      }
    };
    const validateFields = (nameList, options) => {
      warning(!(nameList instanceof Function), "Form", "validateFields/validateField/validate not support callback, please use promise instead");
      if (!props.model) {
        warning(false, "Form", "model is required for validateFields to work.");
        return Promise.reject("Form `model` is required for validateFields to work.");
      }
      const provideNameList = !!nameList;
      const namePathList = provideNameList ? toArray$1(nameList).map(getNamePath) : [];
      const promiseList = [];
      Object.values(fields).forEach((field) => {
        var _a;
        if (!provideNameList) {
          namePathList.push(field.namePath.value);
        }
        if (!((_a = field.rules) === null || _a === void 0 ? void 0 : _a.value.length)) {
          return;
        }
        const fieldNamePath = field.namePath.value;
        if (!provideNameList || containsNamePath(namePathList, fieldNamePath)) {
          const promise = field.validateRules(_extends({
            validateMessages: validateMessages.value
          }, options));
          promiseList.push(promise.then(() => ({
            name: fieldNamePath,
            errors: [],
            warnings: []
          })).catch((ruleErrors) => {
            const mergedErrors = [];
            const mergedWarnings = [];
            ruleErrors.forEach((_ref3) => {
              let {
                rule: {
                  warningOnly
                },
                errors
              } = _ref3;
              if (warningOnly) {
                mergedWarnings.push(...errors);
              } else {
                mergedErrors.push(...errors);
              }
            });
            if (mergedErrors.length) {
              return Promise.reject({
                name: fieldNamePath,
                errors: mergedErrors,
                warnings: mergedWarnings
              });
            }
            return {
              name: fieldNamePath,
              errors: mergedErrors,
              warnings: mergedWarnings
            };
          }));
        }
      });
      const summaryPromise = allPromiseFinish(promiseList);
      lastValidatePromise.value = summaryPromise;
      const returnPromise = summaryPromise.then(() => {
        if (lastValidatePromise.value === summaryPromise) {
          return Promise.resolve(getFieldsValue(namePathList));
        }
        return Promise.reject([]);
      }).catch((results) => {
        const errorList = results.filter((result) => result && result.errors.length);
        return Promise.reject({
          values: getFieldsValue(namePathList),
          errorFields: errorList,
          outOfDate: lastValidatePromise.value !== summaryPromise
        });
      });
      returnPromise.catch((e2) => e2);
      return returnPromise;
    };
    const validateField = function() {
      return validateFields(...arguments);
    };
    const handleSubmit = (e2) => {
      e2.preventDefault();
      e2.stopPropagation();
      emit("submit", e2);
      if (props.model) {
        const res = validateFields();
        res.then((values) => {
          emit("finish", values);
        }).catch((errors) => {
          handleFinishFailed(errors);
        });
      }
    };
    expose({
      resetFields,
      clearValidate,
      validateFields,
      getFieldsValue,
      validate,
      scrollToField
    });
    useProvideForm({
      model: computed(() => props.model),
      name: computed(() => props.name),
      labelAlign: computed(() => props.labelAlign),
      labelCol: computed(() => props.labelCol),
      labelWrap: computed(() => props.labelWrap),
      wrapperCol: computed(() => props.wrapperCol),
      vertical: computed(() => props.layout === "vertical"),
      colon: mergedColon,
      requiredMark: mergedRequiredMark,
      validateTrigger: computed(() => props.validateTrigger),
      rules: computed(() => props.rules),
      addField,
      removeField,
      onValidate: (name, status, errors) => {
        emit("validate", name, status, errors);
      },
      validateMessages
    });
    watch(() => props.rules, () => {
      if (props.validateOnRuleChange) {
        validateFields();
      }
    });
    return () => {
      var _a;
      return wrapSSR(createVNode("form", _objectSpread2(_objectSpread2({}, attrs), {}, {
        "onSubmit": handleSubmit,
        "class": [formClassName.value, attrs.class]
      }), [(_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots)]));
    };
  }
});
Form.useInjectFormItemContext = useInjectFormItemContext;
Form.ItemRest = FormItemRest;
Form.install = function(app) {
  app.component(Form.name, Form);
  app.component(Form.Item.name, Form.Item);
  app.component(FormItemRest.name, FormItemRest);
  return app;
};
export {
  FormItem,
  FormItemRest,
  Form as default,
  f as formItemProps,
  formProps,
  useForm,
  useInjectFormItemContext
};
//# sourceMappingURL=index-Bf1Vr6b_.js.map
