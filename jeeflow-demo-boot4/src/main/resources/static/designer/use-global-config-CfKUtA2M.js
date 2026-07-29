import { k as keysOf } from "./objects-DdSh0aPF.js";
import { u as useNamespace, g as debugWarn, A as namespaceContextKey } from "./error-DiL_p-4A.js";
import { u as useLocale, l as localeContextKey, e as emptyValuesContextKey } from "./index-Dd4aCaME.js";
import { u as useZIndex, d as defaultInitialZIndex, z as zIndexContextKey } from "./index-BHCMzsem.js";
import { S as SIZE_INJECTION_KEY } from "./style-_T5b_00u.js";
import { getCurrentInstance, inject, computed, ref, unref, provide } from "vue";
import { h as isNil } from "./index-D9cY2WMP.js";
const configProviderContextKey = Symbol();
const globalConfig = ref();
function useGlobalConfig(key, defaultValue = void 0) {
  const config = getCurrentInstance() ? inject(configProviderContextKey, globalConfig) : globalConfig;
  if (key) return computed(() => {
    var _a;
    return ((_a = config.value) == null ? void 0 : _a[key]) ?? defaultValue;
  });
  else return config;
}
function useGlobalComponentSettings(block, sizeFallback) {
  const config = useGlobalConfig();
  const ns = useNamespace(block, computed(() => {
    var _a;
    return ((_a = config.value) == null ? void 0 : _a.namespace) || "el";
  }));
  const locale = useLocale(computed(() => {
    var _a;
    return (_a = config.value) == null ? void 0 : _a.locale;
  }));
  const zIndex = useZIndex(computed(() => {
    var _a;
    const zIndex2 = (_a = config.value) == null ? void 0 : _a.zIndex;
    return isNil(zIndex2) || Number.isNaN(zIndex2) ? defaultInitialZIndex : zIndex2;
  }));
  const size = computed(() => {
    var _a;
    return unref(sizeFallback) || ((_a = config.value) == null ? void 0 : _a.size) || "";
  });
  provideGlobalConfig(computed(() => unref(config) || {}));
  return {
    ns,
    locale,
    zIndex,
    size
  };
}
const provideGlobalConfig = (config, app, global = false) => {
  const inSetup = !!getCurrentInstance();
  const oldConfig = inSetup ? useGlobalConfig() : void 0;
  const provideFn = inSetup ? provide : void 0;
  if (!provideFn) {
    debugWarn("provideGlobalConfig", "provideGlobalConfig() can only be used inside setup().");
    return;
  }
  const context = computed(() => {
    const cfg = unref(config);
    if (!(oldConfig == null ? void 0 : oldConfig.value)) return cfg;
    return mergeConfig(oldConfig.value, cfg);
  });
  provideFn(configProviderContextKey, context);
  provideFn(localeContextKey, computed(() => context.value.locale));
  provideFn(namespaceContextKey, computed(() => context.value.namespace));
  provideFn(zIndexContextKey, computed(() => context.value.zIndex));
  provideFn(SIZE_INJECTION_KEY, { size: computed(() => context.value.size || "") });
  provideFn(emptyValuesContextKey, computed(() => ({
    emptyValues: context.value.emptyValues,
    valueOnClear: context.value.valueOnClear
  })));
  if (global || !globalConfig.value) globalConfig.value = context.value;
  return context;
};
const mergeConfig = (a, b) => {
  const keys = [.../* @__PURE__ */ new Set([...keysOf(a), ...keysOf(b)])];
  const obj = {};
  for (const key of keys) obj[key] = b[key] !== void 0 ? b[key] : a[key];
  return obj;
};
export {
  useGlobalComponentSettings as a,
  provideGlobalConfig as p,
  useGlobalConfig as u
};
//# sourceMappingURL=use-global-config-CfKUtA2M.js.map
