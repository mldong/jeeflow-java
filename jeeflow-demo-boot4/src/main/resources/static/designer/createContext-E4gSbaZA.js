import { F as warningOnce, _ as _extends } from "./index-IfV1sCLI.js";
import { inject, reactive, provide, watchEffect } from "vue";
const devWarning = (valid, component, message) => {
  warningOnce(valid, `[ant-design-vue: ${component}] ${message}`);
};
function createContext(defaultValue) {
  const contextKey = Symbol("contextKey");
  const useProvide = (props, newProps) => {
    const mergedProps = reactive({});
    provide(contextKey, mergedProps);
    watchEffect(() => {
      _extends(mergedProps, props, newProps || {});
    });
    return mergedProps;
  };
  const useInject = () => {
    return inject(contextKey, defaultValue) || {};
  };
  return {
    useProvide,
    useInject
  };
}
export {
  createContext as c,
  devWarning as d
};
//# sourceMappingURL=createContext-E4gSbaZA.js.map
