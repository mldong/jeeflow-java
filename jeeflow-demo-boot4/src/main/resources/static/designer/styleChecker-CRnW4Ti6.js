import { E as canUseDom } from "./index-IfV1sCLI.js";
const canUseDocElement = () => canUseDom() && window.document.documentElement;
let flexGapSupported;
const detectFlexGapSupported = () => {
  if (!canUseDocElement()) {
    return false;
  }
  if (flexGapSupported !== void 0) {
    return flexGapSupported;
  }
  const flex = document.createElement("div");
  flex.style.display = "flex";
  flex.style.flexDirection = "column";
  flex.style.rowGap = "1px";
  flex.appendChild(document.createElement("div"));
  flex.appendChild(document.createElement("div"));
  document.body.appendChild(flex);
  flexGapSupported = flex.scrollHeight === 1;
  document.body.removeChild(flex);
  return flexGapSupported;
};
export {
  canUseDocElement as c,
  detectFlexGapSupported as d
};
//# sourceMappingURL=styleChecker-CRnW4Ti6.js.map
