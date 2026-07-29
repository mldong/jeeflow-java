import { b as buildProps } from "./error-DiL_p-4A.js";
import { p as pick } from "./index-D9cY2WMP.js";
const ariaProps = buildProps({
  /**
  * @description native `aria-label` attribute
  */
  ariaLabel: String,
  /**
  * @description native `aria-orientation` attribute
  */
  ariaOrientation: {
    type: String,
    values: [
      "horizontal",
      "vertical",
      "undefined"
    ]
  },
  /**
  * @description native `aria-controls` attribute
  */
  ariaControls: String
});
const useAriaProps = (arias) => {
  return pick(ariaProps, arias);
};
export {
  useAriaProps as u
};
//# sourceMappingURL=index-C-31KauU.js.map
