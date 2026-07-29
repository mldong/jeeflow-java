import { g as debugWarn } from "./error-DiL_p-4A.js";
import { watch, unref } from "vue";
const useDeprecated = ({ from, replacement, scope, version, ref, type = "API" }, condition) => {
  watch(() => unref(condition), (val) => {
    if (val) debugWarn(scope, `[${type}] ${from} is about to be deprecated in version ${version}, please use ${replacement} instead.
For more detail, please visit: ${ref}
`);
  }, { immediate: true });
};
export {
  useDeprecated as u
};
//# sourceMappingURL=index-ChyTK8JP.js.map
