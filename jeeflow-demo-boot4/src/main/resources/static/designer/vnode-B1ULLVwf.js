import { z as filterEmpty, _ as _extends, n as warning } from "./index-IfV1sCLI.js";
import { cloneVNode, render } from "vue";
function cloneElement(vnode) {
  let nodeProps = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
  let override = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : true;
  let mergeRef = arguments.length > 3 && arguments[3] !== void 0 ? arguments[3] : false;
  let ele = vnode;
  if (Array.isArray(vnode)) {
    ele = filterEmpty(vnode)[0];
  }
  if (!ele) {
    return null;
  }
  const node = cloneVNode(ele, nodeProps, mergeRef);
  node.props = override ? _extends(_extends({}, node.props), nodeProps) : node.props;
  warning(typeof node.props.class !== "object", "class must be string");
  return node;
}
function triggerVNodeUpdate(vm, attrs, dom) {
  render(cloneVNode(vm, _extends({}, attrs)), dom);
}
export {
  cloneElement as c,
  triggerVNodeUpdate as t
};
//# sourceMappingURL=vnode-B1ULLVwf.js.map
