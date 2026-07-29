import { _ as _extends, P as PropTypes, d as getPropsSlot, o as objectType, c as classNames, g as genComponentStyleHook, m as merge, y as resetComponent, n as warning, u as useConfigInject, z as filterEmpty, r as isValidElement, a5 as isFragment, a6 as getStyle, w as withInstall } from "./index-IfV1sCLI.js";
import { defineComponent, createVNode, shallowRef, watchEffect, computed, ref, watch } from "vue";
import { T as Trigger } from "./Trigger-CpaAYR4S.js";
import { c as cloneElement } from "./vnode-B1ULLVwf.js";
import { w as wrapperRaf, i as initDefaultProps } from "./raf-DXHxwK-q.js";
import { i as initZoomMotion } from "./zoom-CQljQfTH.js";
import { a as getTransitionName } from "./transition-AJOM0T1w.js";
const PresetColors = ["blue", "purple", "cyan", "green", "magenta", "pink", "red", "orange", "yellow", "volcano", "geekblue", "lime", "gold"];
const roundedArrow = (width, innerRadius, outerRadius, bgColor, boxShadow) => {
  const unitWidth = width / 2;
  const ax = 0;
  const ay = unitWidth;
  const bx = outerRadius * 1 / Math.sqrt(2);
  const by = unitWidth - outerRadius * (1 - 1 / Math.sqrt(2));
  const cx = unitWidth - innerRadius * (1 / Math.sqrt(2));
  const cy = outerRadius * (Math.sqrt(2) - 1) + innerRadius * (1 / Math.sqrt(2));
  const dx = 2 * unitWidth - cx;
  const dy = cy;
  const ex = 2 * unitWidth - bx;
  const ey = by;
  const fx = 2 * unitWidth - ax;
  const fy = ay;
  const shadowWidth = unitWidth * Math.sqrt(2) + outerRadius * (Math.sqrt(2) - 2);
  const polygonOffset = outerRadius * (Math.sqrt(2) - 1);
  return {
    pointerEvents: "none",
    width,
    height: width,
    overflow: "hidden",
    "&::after": {
      content: '""',
      position: "absolute",
      width: shadowWidth,
      height: shadowWidth,
      bottom: 0,
      insetInline: 0,
      margin: "auto",
      borderRadius: {
        _skip_check_: true,
        value: `0 0 ${innerRadius}px 0`
      },
      transform: "translateY(50%) rotate(-135deg)",
      boxShadow,
      zIndex: 0,
      background: "transparent"
    },
    "&::before": {
      position: "absolute",
      bottom: 0,
      insetInlineStart: 0,
      width,
      height: width / 2,
      background: bgColor,
      clipPath: {
        _multi_value_: true,
        value: [`polygon(${polygonOffset}px 100%, 50% ${polygonOffset}px, ${2 * unitWidth - polygonOffset}px 100%, ${polygonOffset}px 100%)`, `path('M ${ax} ${ay} A ${outerRadius} ${outerRadius} 0 0 0 ${bx} ${by} L ${cx} ${cy} A ${innerRadius} ${innerRadius} 0 0 1 ${dx} ${dy} L ${ex} ${ey} A ${outerRadius} ${outerRadius} 0 0 0 ${fx} ${fy} Z')`]
      },
      content: '""'
    }
  };
};
function genPresetColor(token, genCss) {
  return PresetColors.reduce((prev, colorKey) => {
    const lightColor = token[`${colorKey}-1`];
    const lightBorderColor = token[`${colorKey}-3`];
    const darkColor = token[`${colorKey}-6`];
    const textColor = token[`${colorKey}-7`];
    return _extends(_extends({}, prev), genCss(colorKey, {
      lightColor,
      lightBorderColor,
      darkColor,
      textColor
    }));
  }, {});
}
const autoAdjustOverflow = {
  adjustX: 1,
  adjustY: 1
};
const targetOffset$1 = [0, 0];
const placements = {
  left: {
    points: ["cr", "cl"],
    overflow: autoAdjustOverflow,
    offset: [-4, 0],
    targetOffset: targetOffset$1
  },
  right: {
    points: ["cl", "cr"],
    overflow: autoAdjustOverflow,
    offset: [4, 0],
    targetOffset: targetOffset$1
  },
  top: {
    points: ["bc", "tc"],
    overflow: autoAdjustOverflow,
    offset: [0, -4],
    targetOffset: targetOffset$1
  },
  bottom: {
    points: ["tc", "bc"],
    overflow: autoAdjustOverflow,
    offset: [0, 4],
    targetOffset: targetOffset$1
  },
  topLeft: {
    points: ["bl", "tl"],
    overflow: autoAdjustOverflow,
    offset: [0, -4],
    targetOffset: targetOffset$1
  },
  leftTop: {
    points: ["tr", "tl"],
    overflow: autoAdjustOverflow,
    offset: [-4, 0],
    targetOffset: targetOffset$1
  },
  topRight: {
    points: ["br", "tr"],
    overflow: autoAdjustOverflow,
    offset: [0, -4],
    targetOffset: targetOffset$1
  },
  rightTop: {
    points: ["tl", "tr"],
    overflow: autoAdjustOverflow,
    offset: [4, 0],
    targetOffset: targetOffset$1
  },
  bottomRight: {
    points: ["tr", "br"],
    overflow: autoAdjustOverflow,
    offset: [0, 4],
    targetOffset: targetOffset$1
  },
  rightBottom: {
    points: ["bl", "br"],
    overflow: autoAdjustOverflow,
    offset: [4, 0],
    targetOffset: targetOffset$1
  },
  bottomLeft: {
    points: ["tl", "bl"],
    overflow: autoAdjustOverflow,
    offset: [0, 4],
    targetOffset: targetOffset$1
  },
  leftBottom: {
    points: ["br", "bl"],
    overflow: autoAdjustOverflow,
    offset: [-4, 0],
    targetOffset: targetOffset$1
  }
};
const tooltipContentProps = {
  prefixCls: String,
  id: String,
  overlayInnerStyle: PropTypes.any
};
const Content = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "TooltipContent",
  props: tooltipContentProps,
  setup(props, _ref) {
    let {
      slots
    } = _ref;
    return () => {
      var _a;
      return createVNode("div", {
        "class": `${props.prefixCls}-inner`,
        "id": props.id,
        "role": "tooltip",
        "style": props.overlayInnerStyle
      }, [(_a = slots.overlay) === null || _a === void 0 ? void 0 : _a.call(slots)]);
    };
  }
});
var __rest = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
function noop() {
}
const Tooltip$1 = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "Tooltip",
  inheritAttrs: false,
  props: {
    trigger: PropTypes.any.def(["hover"]),
    defaultVisible: {
      type: Boolean,
      default: void 0
    },
    visible: {
      type: Boolean,
      default: void 0
    },
    placement: PropTypes.string.def("right"),
    transitionName: String,
    animation: PropTypes.any,
    afterVisibleChange: PropTypes.func.def(() => {
    }),
    overlayStyle: {
      type: Object,
      default: void 0
    },
    overlayClassName: String,
    prefixCls: PropTypes.string.def("rc-tooltip"),
    mouseEnterDelay: PropTypes.number.def(0.1),
    mouseLeaveDelay: PropTypes.number.def(0.1),
    getPopupContainer: Function,
    destroyTooltipOnHide: {
      type: Boolean,
      default: false
    },
    align: PropTypes.object.def(() => ({})),
    arrowContent: PropTypes.any.def(null),
    tipId: String,
    builtinPlacements: PropTypes.object,
    overlayInnerStyle: {
      type: Object,
      default: void 0
    },
    popupVisible: {
      type: Boolean,
      default: void 0
    },
    onVisibleChange: Function,
    onPopupAlign: Function,
    arrow: {
      type: Boolean,
      default: true
    }
  },
  setup(props, _ref) {
    let {
      slots,
      attrs,
      expose
    } = _ref;
    const triggerDOM = shallowRef();
    const getPopupElement = () => {
      const {
        prefixCls,
        tipId,
        overlayInnerStyle
      } = props;
      return [!!props.arrow ? createVNode("div", {
        "class": `${prefixCls}-arrow`,
        "key": "arrow"
      }, [getPropsSlot(slots, props, "arrowContent")]) : null, createVNode(Content, {
        "key": "content",
        "prefixCls": prefixCls,
        "id": tipId,
        "overlayInnerStyle": overlayInnerStyle
      }, {
        overlay: slots.overlay
      })];
    };
    const getPopupDomNode = () => {
      return triggerDOM.value.getPopupDomNode();
    };
    expose({
      getPopupDomNode,
      triggerDOM,
      forcePopupAlign: () => {
        var _a;
        return (_a = triggerDOM.value) === null || _a === void 0 ? void 0 : _a.forcePopupAlign();
      }
    });
    const destroyTooltip = shallowRef(false);
    const autoDestroy = shallowRef(false);
    watchEffect(() => {
      const {
        destroyTooltipOnHide
      } = props;
      if (typeof destroyTooltipOnHide === "boolean") {
        destroyTooltip.value = destroyTooltipOnHide;
      } else if (destroyTooltipOnHide && typeof destroyTooltipOnHide === "object") {
        const {
          keepParent
        } = destroyTooltipOnHide;
        destroyTooltip.value = keepParent === true;
        autoDestroy.value = keepParent === false;
      }
    });
    return () => {
      const {
        overlayClassName,
        trigger,
        mouseEnterDelay,
        mouseLeaveDelay,
        overlayStyle,
        prefixCls,
        afterVisibleChange,
        transitionName,
        animation,
        placement,
        align,
        destroyTooltipOnHide,
        defaultVisible
      } = props, restProps = __rest(props, ["overlayClassName", "trigger", "mouseEnterDelay", "mouseLeaveDelay", "overlayStyle", "prefixCls", "afterVisibleChange", "transitionName", "animation", "placement", "align", "destroyTooltipOnHide", "defaultVisible"]);
      const extraProps = _extends({}, restProps);
      if (props.visible !== void 0) {
        extraProps.popupVisible = props.visible;
      }
      const triggerProps = _extends(_extends(_extends({
        popupClassName: overlayClassName,
        prefixCls,
        action: trigger,
        builtinPlacements: placements,
        popupPlacement: placement,
        popupAlign: align,
        afterPopupVisibleChange: afterVisibleChange,
        popupTransitionName: transitionName,
        popupAnimation: animation,
        defaultPopupVisible: defaultVisible,
        destroyPopupOnHide: destroyTooltip.value,
        autoDestroy: autoDestroy.value,
        mouseLeaveDelay,
        popupStyle: overlayStyle,
        mouseEnterDelay
      }, extraProps), attrs), {
        onPopupVisibleChange: props.onVisibleChange || noop,
        onPopupAlign: props.onPopupAlign || noop,
        ref: triggerDOM,
        arrow: !!props.arrow,
        popup: getPopupElement()
      });
      return createVNode(Trigger, triggerProps, {
        default: slots.default
      });
    };
  }
});
const abstractTooltipProps = () => ({
  trigger: [String, Array],
  open: {
    type: Boolean,
    default: void 0
  },
  /** @deprecated Please use `open` instead. */
  visible: {
    type: Boolean,
    default: void 0
  },
  placement: String,
  color: String,
  transitionName: String,
  overlayStyle: objectType(),
  overlayInnerStyle: objectType(),
  overlayClassName: String,
  openClassName: String,
  prefixCls: String,
  mouseEnterDelay: Number,
  mouseLeaveDelay: Number,
  getPopupContainer: Function,
  /**@deprecated Please use `arrow={{ pointAtCenter: true }}` instead. */
  arrowPointAtCenter: {
    type: Boolean,
    default: void 0
  },
  arrow: {
    type: [Boolean, Object],
    default: true
  },
  autoAdjustOverflow: {
    type: [Boolean, Object],
    default: void 0
  },
  destroyTooltipOnHide: {
    type: Boolean,
    default: void 0
  },
  align: objectType(),
  builtinPlacements: objectType(),
  children: Array,
  /** @deprecated Please use `onOpenChange` instead. */
  onVisibleChange: Function,
  /** @deprecated Please use `onUpdate:open` instead. */
  "onUpdate:visible": Function,
  onOpenChange: Function,
  "onUpdate:open": Function
});
const autoAdjustOverflowEnabled = {
  adjustX: 1,
  adjustY: 1
};
const autoAdjustOverflowDisabled = {
  adjustX: 0,
  adjustY: 0
};
const targetOffset = [0, 0];
function getOverflowOptions(autoAdjustOverflow2) {
  if (typeof autoAdjustOverflow2 === "boolean") {
    return autoAdjustOverflow2 ? autoAdjustOverflowEnabled : autoAdjustOverflowDisabled;
  }
  return _extends(_extends({}, autoAdjustOverflowDisabled), autoAdjustOverflow2);
}
function getPlacements(config) {
  const {
    arrowWidth = 4,
    horizontalArrowShift = 16,
    verticalArrowShift = 8,
    autoAdjustOverflow: autoAdjustOverflow2,
    arrowPointAtCenter
  } = config;
  const placementMap = {
    left: {
      points: ["cr", "cl"],
      offset: [-4, 0]
    },
    right: {
      points: ["cl", "cr"],
      offset: [4, 0]
    },
    top: {
      points: ["bc", "tc"],
      offset: [0, -4]
    },
    bottom: {
      points: ["tc", "bc"],
      offset: [0, 4]
    },
    topLeft: {
      points: ["bl", "tc"],
      offset: [-(horizontalArrowShift + arrowWidth), -4]
    },
    leftTop: {
      points: ["tr", "cl"],
      offset: [-4, -(verticalArrowShift + arrowWidth)]
    },
    topRight: {
      points: ["br", "tc"],
      offset: [horizontalArrowShift + arrowWidth, -4]
    },
    rightTop: {
      points: ["tl", "cr"],
      offset: [4, -(verticalArrowShift + arrowWidth)]
    },
    bottomRight: {
      points: ["tr", "bc"],
      offset: [horizontalArrowShift + arrowWidth, 4]
    },
    rightBottom: {
      points: ["bl", "cr"],
      offset: [4, verticalArrowShift + arrowWidth]
    },
    bottomLeft: {
      points: ["tl", "bc"],
      offset: [-(horizontalArrowShift + arrowWidth), 4]
    },
    leftBottom: {
      points: ["br", "cl"],
      offset: [-4, verticalArrowShift + arrowWidth]
    }
  };
  Object.keys(placementMap).forEach((key) => {
    placementMap[key] = arrowPointAtCenter ? _extends(_extends({}, placementMap[key]), {
      overflow: getOverflowOptions(autoAdjustOverflow2),
      targetOffset
    }) : _extends(_extends({}, placements[key]), {
      overflow: getOverflowOptions(autoAdjustOverflow2)
    });
    placementMap[key].ignoreShake = true;
  });
  return placementMap;
}
function firstNotUndefined() {
  let arr = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : [];
  for (let i = 0, len = arr.length; i < len; i++) {
    if (arr[i] !== void 0) {
      return arr[i];
    }
  }
  return void 0;
}
const inverseColors = PresetColors.map((color) => `${color}-inverse`);
function isPresetColor(color) {
  let includeInverse = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : true;
  if (includeInverse) {
    return [...inverseColors, ...PresetColors].includes(color);
  }
  return PresetColors.includes(color);
}
function parseColor(prefixCls, color) {
  const isInternalColor = isPresetColor(color);
  const className = classNames({
    [`${prefixCls}-${color}`]: color && isInternalColor
  });
  const overlayStyle = {};
  const arrowStyle = {};
  if (color && !isInternalColor) {
    overlayStyle.background = color;
    arrowStyle["--antd-arrow-background-color"] = color;
  }
  return {
    className,
    overlayStyle,
    arrowStyle
  };
}
function connectArrowCls(classList) {
  let showArrowCls = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : "";
  return classList.map((cls) => `${showArrowCls}${cls}`).join(",");
}
const MAX_VERTICAL_CONTENT_RADIUS = 8;
function getArrowOffset(options) {
  const maxVerticalContentRadius = MAX_VERTICAL_CONTENT_RADIUS;
  const {
    sizePopupArrow,
    contentRadius,
    borderRadiusOuter
  } = options;
  const arrowInnerOffset = sizePopupArrow / 2 - Math.ceil(borderRadiusOuter * (Math.sqrt(2) - 1));
  const dropdownArrowOffset = (contentRadius > 12 ? contentRadius + 2 : 12) - arrowInnerOffset;
  const dropdownArrowOffsetVertical = maxVerticalContentRadius - arrowInnerOffset;
  return {
    dropdownArrowOffset,
    dropdownArrowOffsetVertical
  };
}
function getArrowStyle(token, options) {
  const {
    componentCls,
    sizePopupArrow,
    marginXXS,
    borderRadiusXS,
    borderRadiusOuter,
    boxShadowPopoverArrow
  } = token;
  const {
    colorBg,
    showArrowCls,
    contentRadius = token.borderRadiusLG
  } = options;
  const {
    dropdownArrowOffsetVertical,
    dropdownArrowOffset
  } = getArrowOffset({
    sizePopupArrow,
    contentRadius,
    borderRadiusOuter
  });
  const dropdownArrowDistance = sizePopupArrow / 2 + marginXXS;
  return {
    [componentCls]: {
      // ============================ Basic ============================
      [`${componentCls}-arrow`]: [_extends(_extends({
        position: "absolute",
        zIndex: 1,
        display: "block"
      }, roundedArrow(sizePopupArrow, borderRadiusXS, borderRadiusOuter, colorBg, boxShadowPopoverArrow)), {
        "&:before": {
          background: colorBg
        }
      })],
      // ========================== Placement ==========================
      // Here handle the arrow position and rotate stuff
      // >>>>> Top
      [[`&-placement-top ${componentCls}-arrow`, `&-placement-topLeft ${componentCls}-arrow`, `&-placement-topRight ${componentCls}-arrow`].join(",")]: {
        bottom: 0,
        transform: "translateY(100%) rotate(180deg)"
      },
      [`&-placement-top ${componentCls}-arrow`]: {
        left: {
          _skip_check_: true,
          value: "50%"
        },
        transform: "translateX(-50%) translateY(100%) rotate(180deg)"
      },
      [`&-placement-topLeft ${componentCls}-arrow`]: {
        left: {
          _skip_check_: true,
          value: dropdownArrowOffset
        }
      },
      [`&-placement-topRight ${componentCls}-arrow`]: {
        right: {
          _skip_check_: true,
          value: dropdownArrowOffset
        }
      },
      // >>>>> Bottom
      [[`&-placement-bottom ${componentCls}-arrow`, `&-placement-bottomLeft ${componentCls}-arrow`, `&-placement-bottomRight ${componentCls}-arrow`].join(",")]: {
        top: 0,
        transform: `translateY(-100%)`
      },
      [`&-placement-bottom ${componentCls}-arrow`]: {
        left: {
          _skip_check_: true,
          value: "50%"
        },
        transform: `translateX(-50%) translateY(-100%)`
      },
      [`&-placement-bottomLeft ${componentCls}-arrow`]: {
        left: {
          _skip_check_: true,
          value: dropdownArrowOffset
        }
      },
      [`&-placement-bottomRight ${componentCls}-arrow`]: {
        right: {
          _skip_check_: true,
          value: dropdownArrowOffset
        }
      },
      // >>>>> Left
      [[`&-placement-left ${componentCls}-arrow`, `&-placement-leftTop ${componentCls}-arrow`, `&-placement-leftBottom ${componentCls}-arrow`].join(",")]: {
        right: {
          _skip_check_: true,
          value: 0
        },
        transform: "translateX(100%) rotate(90deg)"
      },
      [`&-placement-left ${componentCls}-arrow`]: {
        top: {
          _skip_check_: true,
          value: "50%"
        },
        transform: "translateY(-50%) translateX(100%) rotate(90deg)"
      },
      [`&-placement-leftTop ${componentCls}-arrow`]: {
        top: dropdownArrowOffsetVertical
      },
      [`&-placement-leftBottom ${componentCls}-arrow`]: {
        bottom: dropdownArrowOffsetVertical
      },
      // >>>>> Right
      [[`&-placement-right ${componentCls}-arrow`, `&-placement-rightTop ${componentCls}-arrow`, `&-placement-rightBottom ${componentCls}-arrow`].join(",")]: {
        left: {
          _skip_check_: true,
          value: 0
        },
        transform: "translateX(-100%) rotate(-90deg)"
      },
      [`&-placement-right ${componentCls}-arrow`]: {
        top: {
          _skip_check_: true,
          value: "50%"
        },
        transform: "translateY(-50%) translateX(-100%) rotate(-90deg)"
      },
      [`&-placement-rightTop ${componentCls}-arrow`]: {
        top: dropdownArrowOffsetVertical
      },
      [`&-placement-rightBottom ${componentCls}-arrow`]: {
        bottom: dropdownArrowOffsetVertical
      },
      // =========================== Offset ============================
      // Offset the popover to account for the dropdown arrow
      // >>>>> Top
      [connectArrowCls([`&-placement-topLeft`, `&-placement-top`, `&-placement-topRight`].map((cls) => cls += ":not(&-arrow-hidden)"), showArrowCls)]: {
        paddingBottom: dropdownArrowDistance
      },
      // >>>>> Bottom
      [connectArrowCls([`&-placement-bottomLeft`, `&-placement-bottom`, `&-placement-bottomRight`].map((cls) => cls += ":not(&-arrow-hidden)"), showArrowCls)]: {
        paddingTop: dropdownArrowDistance
      },
      // >>>>> Left
      [connectArrowCls([`&-placement-leftTop`, `&-placement-left`, `&-placement-leftBottom`].map((cls) => cls += ":not(&-arrow-hidden)"), showArrowCls)]: {
        paddingRight: {
          _skip_check_: true,
          value: dropdownArrowDistance
        }
      },
      // >>>>> Right
      [connectArrowCls([`&-placement-rightTop`, `&-placement-right`, `&-placement-rightBottom`].map((cls) => cls += ":not(&-arrow-hidden)"), showArrowCls)]: {
        paddingLeft: {
          _skip_check_: true,
          value: dropdownArrowDistance
        }
      }
    }
  };
}
const genTooltipStyle = (token) => {
  const {
    componentCls,
    // ant-tooltip
    tooltipMaxWidth,
    tooltipColor,
    tooltipBg,
    tooltipBorderRadius,
    zIndexPopup,
    controlHeight,
    boxShadowSecondary,
    paddingSM,
    paddingXS,
    tooltipRadiusOuter
  } = token;
  return [
    {
      [componentCls]: _extends(_extends(_extends(_extends({}, resetComponent(token)), {
        position: "absolute",
        zIndex: zIndexPopup,
        display: "block",
        "&": [{
          width: "max-content"
        }, {
          width: "intrinsic"
        }],
        maxWidth: tooltipMaxWidth,
        visibility: "visible",
        "&-hidden": {
          display: "none"
        },
        "--antd-arrow-background-color": tooltipBg,
        // Wrapper for the tooltip content
        [`${componentCls}-inner`]: {
          minWidth: controlHeight,
          minHeight: controlHeight,
          padding: `${paddingSM / 2}px ${paddingXS}px`,
          color: tooltipColor,
          textAlign: "start",
          textDecoration: "none",
          wordWrap: "break-word",
          backgroundColor: tooltipBg,
          borderRadius: tooltipBorderRadius,
          boxShadow: boxShadowSecondary
        },
        // Limit left and right placement radius
        [[`&-placement-left`, `&-placement-leftTop`, `&-placement-leftBottom`, `&-placement-right`, `&-placement-rightTop`, `&-placement-rightBottom`].join(",")]: {
          [`${componentCls}-inner`]: {
            borderRadius: Math.min(tooltipBorderRadius, MAX_VERTICAL_CONTENT_RADIUS)
          }
        },
        [`${componentCls}-content`]: {
          position: "relative"
        }
      }), genPresetColor(token, (colorKey, _ref) => {
        let {
          darkColor
        } = _ref;
        return {
          [`&${componentCls}-${colorKey}`]: {
            [`${componentCls}-inner`]: {
              backgroundColor: darkColor
            },
            [`${componentCls}-arrow`]: {
              "--antd-arrow-background-color": darkColor
            }
          }
        };
      })), {
        // RTL
        "&-rtl": {
          direction: "rtl"
        }
      })
    },
    // Arrow Style
    getArrowStyle(merge(token, {
      borderRadiusOuter: tooltipRadiusOuter
    }), {
      colorBg: "var(--antd-arrow-background-color)",
      showArrowCls: "",
      contentRadius: tooltipBorderRadius
    }),
    // Pure Render
    {
      [`${componentCls}-pure`]: {
        position: "relative",
        maxWidth: "none"
      }
    }
  ];
};
const useStyle = (prefixCls, injectStyle) => {
  const useOriginHook = genComponentStyleHook("Tooltip", (token) => {
    if ((injectStyle === null || injectStyle === void 0 ? void 0 : injectStyle.value) === false) {
      return [];
    }
    const {
      borderRadius,
      colorTextLightSolid,
      colorBgDefault,
      borderRadiusOuter
    } = token;
    const TooltipToken = merge(token, {
      // default variables
      tooltipMaxWidth: 250,
      tooltipColor: colorTextLightSolid,
      tooltipBorderRadius: borderRadius,
      tooltipBg: colorBgDefault,
      tooltipRadiusOuter: borderRadiusOuter > 4 ? 4 : borderRadiusOuter
    });
    return [genTooltipStyle(TooltipToken), initZoomMotion(token, "zoom-big-fast")];
  }, (_ref2) => {
    let {
      zIndexPopupBase,
      colorBgSpotlight
    } = _ref2;
    return {
      zIndexPopup: zIndexPopupBase + 70,
      colorBgDefault: colorBgSpotlight
    };
  });
  return useOriginHook(prefixCls);
};
const splitObject = (obj, keys) => {
  const picked = {};
  const omitted = _extends({}, obj);
  keys.forEach((key) => {
    if (obj && key in obj) {
      picked[key] = obj[key];
      delete omitted[key];
    }
  });
  return {
    picked,
    omitted
  };
};
const tooltipProps = () => _extends(_extends({}, abstractTooltipProps()), {
  title: PropTypes.any
});
const ToolTip = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "ATooltip",
  inheritAttrs: false,
  props: initDefaultProps(tooltipProps(), {
    trigger: "hover",
    align: {},
    placement: "top",
    mouseEnterDelay: 0.1,
    mouseLeaveDelay: 0.1,
    arrowPointAtCenter: false,
    autoAdjustOverflow: true
  }),
  slots: Object,
  // emits: ['update:visible', 'visibleChange'],
  setup(props, _ref) {
    let {
      slots,
      emit,
      attrs,
      expose
    } = _ref;
    if (process.env.NODE_ENV !== "production") {
      [["visible", "open"], ["onVisibleChange", "onOpenChange"]].forEach((_ref2) => {
        let [deprecatedName, newName] = _ref2;
        warning(props[deprecatedName] === void 0, "Tooltip", `\`${deprecatedName}\` is deprecated, please use \`${newName}\` instead.`);
      });
    }
    const {
      prefixCls,
      getPopupContainer,
      direction,
      rootPrefixCls
    } = useConfigInject("tooltip", props);
    const mergedOpen = computed(() => {
      var _a;
      return (_a = props.open) !== null && _a !== void 0 ? _a : props.visible;
    });
    const innerOpen = ref(firstNotUndefined([props.open, props.visible]));
    const tooltip = ref();
    let rafId;
    watch(mergedOpen, (val) => {
      wrapperRaf.cancel(rafId);
      rafId = wrapperRaf(() => {
        innerOpen.value = !!val;
      });
    });
    const isNoTitle = () => {
      var _a;
      const title = (_a = props.title) !== null && _a !== void 0 ? _a : slots.title;
      return !title && title !== 0;
    };
    const handleVisibleChange = (val) => {
      const noTitle = isNoTitle();
      if (mergedOpen.value === void 0) {
        innerOpen.value = noTitle ? false : val;
      }
      if (!noTitle) {
        emit("update:visible", val);
        emit("visibleChange", val);
        emit("update:open", val);
        emit("openChange", val);
      }
    };
    const getPopupDomNode = () => {
      return tooltip.value.getPopupDomNode();
    };
    expose({
      getPopupDomNode,
      open: innerOpen,
      forcePopupAlign: () => {
        var _a;
        return (_a = tooltip.value) === null || _a === void 0 ? void 0 : _a.forcePopupAlign();
      }
    });
    const tooltipPlacements = computed(() => {
      var _a;
      const {
        builtinPlacements,
        autoAdjustOverflow: autoAdjustOverflow2,
        arrow,
        arrowPointAtCenter
      } = props;
      let mergedArrowPointAtCenter = arrowPointAtCenter;
      if (typeof arrow === "object") {
        mergedArrowPointAtCenter = (_a = arrow.pointAtCenter) !== null && _a !== void 0 ? _a : arrowPointAtCenter;
      }
      return builtinPlacements || getPlacements({
        arrowPointAtCenter: mergedArrowPointAtCenter,
        autoAdjustOverflow: autoAdjustOverflow2
      });
    });
    const isTrueProps = (val) => {
      return val || val === "";
    };
    const getDisabledCompatibleChildren = (ele) => {
      const elementType = ele.type;
      if (typeof elementType === "object" && ele.props) {
        if ((elementType.__ANT_BUTTON === true || elementType === "button") && isTrueProps(ele.props.disabled) || elementType.__ANT_SWITCH === true && (isTrueProps(ele.props.disabled) || isTrueProps(ele.props.loading)) || elementType.__ANT_RADIO === true && isTrueProps(ele.props.disabled)) {
          const {
            picked,
            omitted
          } = splitObject(getStyle(ele), ["position", "left", "right", "top", "bottom", "float", "display", "zIndex"]);
          const spanStyle = _extends(_extends({
            display: "inline-block"
          }, picked), {
            cursor: "not-allowed",
            lineHeight: 1,
            width: ele.props && ele.props.block ? "100%" : void 0
          });
          const buttonStyle = _extends(_extends({}, omitted), {
            pointerEvents: "none"
          });
          const child = cloneElement(ele, {
            style: buttonStyle
          }, true);
          return createVNode("span", {
            "style": spanStyle,
            "class": `${prefixCls.value}-disabled-compatible-wrapper`
          }, [child]);
        }
      }
      return ele;
    };
    const getOverlay = () => {
      var _a, _b;
      return (_a = props.title) !== null && _a !== void 0 ? _a : (_b = slots.title) === null || _b === void 0 ? void 0 : _b.call(slots);
    };
    const onPopupAlign = (domNode, align) => {
      const placements2 = tooltipPlacements.value;
      const placement = Object.keys(placements2).find((key) => {
        var _a, _b;
        return placements2[key].points[0] === ((_a = align.points) === null || _a === void 0 ? void 0 : _a[0]) && placements2[key].points[1] === ((_b = align.points) === null || _b === void 0 ? void 0 : _b[1]);
      });
      if (placement) {
        const rect = domNode.getBoundingClientRect();
        const transformOrigin = {
          top: "50%",
          left: "50%"
        };
        if (placement.indexOf("top") >= 0 || placement.indexOf("Bottom") >= 0) {
          transformOrigin.top = `${rect.height - align.offset[1]}px`;
        } else if (placement.indexOf("Top") >= 0 || placement.indexOf("bottom") >= 0) {
          transformOrigin.top = `${-align.offset[1]}px`;
        }
        if (placement.indexOf("left") >= 0 || placement.indexOf("Right") >= 0) {
          transformOrigin.left = `${rect.width - align.offset[0]}px`;
        } else if (placement.indexOf("right") >= 0 || placement.indexOf("Left") >= 0) {
          transformOrigin.left = `${-align.offset[0]}px`;
        }
        domNode.style.transformOrigin = `${transformOrigin.left} ${transformOrigin.top}`;
      }
    };
    const colorInfo = computed(() => parseColor(prefixCls.value, props.color));
    const injectFromPopover = computed(() => attrs["data-popover-inject"]);
    const [wrapSSR, hashId] = useStyle(prefixCls, computed(() => !injectFromPopover.value));
    return () => {
      var _a, _b;
      const {
        openClassName,
        overlayClassName,
        overlayStyle,
        overlayInnerStyle
      } = props;
      let children = (_b = filterEmpty((_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots))) !== null && _b !== void 0 ? _b : null;
      children = children.length === 1 ? children[0] : children;
      let tempVisible = innerOpen.value;
      if (mergedOpen.value === void 0 && isNoTitle()) {
        tempVisible = false;
      }
      if (!children) {
        return null;
      }
      const child = getDisabledCompatibleChildren(isValidElement(children) && !isFragment(children) ? children : createVNode("span", null, [children]));
      const childCls = classNames({
        [openClassName || `${prefixCls.value}-open`]: true,
        [child.props && child.props.class]: child.props && child.props.class
      });
      const customOverlayClassName = classNames(overlayClassName, {
        [`${prefixCls.value}-rtl`]: direction.value === "rtl"
      }, colorInfo.value.className, hashId.value);
      const formattedOverlayInnerStyle = _extends(_extends({}, colorInfo.value.overlayStyle), overlayInnerStyle);
      const arrowContentStyle = colorInfo.value.arrowStyle;
      const vcTooltipProps = _extends(_extends(_extends({}, attrs), props), {
        prefixCls: prefixCls.value,
        arrow: !!props.arrow,
        getPopupContainer: getPopupContainer === null || getPopupContainer === void 0 ? void 0 : getPopupContainer.value,
        builtinPlacements: tooltipPlacements.value,
        visible: tempVisible,
        ref: tooltip,
        overlayClassName: customOverlayClassName,
        overlayStyle: _extends(_extends({}, arrowContentStyle), overlayStyle),
        overlayInnerStyle: formattedOverlayInnerStyle,
        onVisibleChange: handleVisibleChange,
        onPopupAlign,
        transitionName: getTransitionName(rootPrefixCls.value, "zoom-big-fast", props.transitionName)
      });
      return wrapSSR(createVNode(Tooltip$1, vcTooltipProps, {
        default: () => [innerOpen.value ? cloneElement(child, {
          class: childCls
        }) : child],
        arrowContent: () => createVNode("span", {
          "class": `${prefixCls.value}-arrow-content`
        }, null),
        overlay: getOverlay
      }));
    };
  }
});
const Tooltip = withInstall(ToolTip);
export {
  Tooltip as default,
  tooltipProps
};
//# sourceMappingURL=index-COTSUqJq.js.map
