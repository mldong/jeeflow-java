import { s as set, j as get } from "./index-D9cY2WMP.js";
const keysOf = (arr) => Object.keys(arr);
const getProp = (obj, path, defaultValue) => {
  return {
    get value() {
      return get(obj, path, defaultValue);
    },
    set value(val) {
      set(obj, path, val);
    }
  };
};
export {
  getProp as g,
  keysOf as k
};
//# sourceMappingURL=objects-DdSh0aPF.js.map
