import { _ as _extends } from "./index-IfV1sCLI.js";
function omit(obj, fields) {
  const shallowCopy = _extends({}, obj);
  for (let i = 0; i < fields.length; i += 1) {
    const key = fields[i];
    delete shallowCopy[key];
  }
  return shallowCopy;
}
export {
  omit as o
};
//# sourceMappingURL=omit-CQMssqPV.js.map
