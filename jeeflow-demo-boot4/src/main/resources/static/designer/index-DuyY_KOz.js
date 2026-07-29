import { j as isClient, g as debugWarn, G as useGetDerivedNamespace, I as computedEager } from "./error-DiL_p-4A.js";
import { getCurrentInstance, inject, unref } from "vue";
const defaultIdInjection = {
  prefix: Math.floor(Math.random() * 1e4),
  current: 0
};
const ID_INJECTION_KEY = Symbol("elIdInjection");
const useIdInjection = () => {
  return getCurrentInstance() ? inject(ID_INJECTION_KEY, defaultIdInjection) : defaultIdInjection;
};
const useId = (deterministicId) => {
  const idInjection = useIdInjection();
  if (!isClient && idInjection === defaultIdInjection) debugWarn("IdInjection", `Looks like you are using server rendering, you must provide a id provider to ensure the hydration process to be succeed
usage: app.provide(ID_INJECTION_KEY, {
  prefix: number,
  current: number,
})`);
  const namespace = useGetDerivedNamespace();
  return computedEager(() => unref(deterministicId) || `${namespace.value}-id-${idInjection.prefix}-${idInjection.current++}`);
};
export {
  useIdInjection as a,
  useId as u
};
//# sourceMappingURL=index-DuyY_KOz.js.map
