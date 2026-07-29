import { _ as _extends, P as PropTypes, o as objectType, f as functionType, a as arrayType, c as classNames, b as _objectSpread2, g as genComponentStyleHook, m as merge, w as withInstall, u as useConfigInject, d as getPropsSlot } from "./index-IfV1sCLI.js";
import { defineComponent, shallowRef, onMounted, nextTick, watch, onUnmounted, createVNode, Transition, withDirectives, vShow, ref, computed, inject, provide } from "vue";
import { K as KeyCode } from "./KeyCode-CnYuZtHO.js";
import { o as omit } from "./omit-CQMssqPV.js";
import { i as initDefaultProps } from "./raf-DXHxwK-q.js";
import { P as Portal } from "./PortalWrapper-FITAQJ0K.js";
import { C as CloseOutlined } from "./CloseOutlined-nOZPpOLa.js";
import { d as devWarning } from "./createContext-E4gSbaZA.js";
import { N as NoCompactStyle } from "./Compact-rSArJkpr.js";
import { g as getTransitionProps, a as getTransitionName } from "./transition-AJOM0T1w.js";
const props = () => ({
  prefixCls: String,
  width: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  height: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  style: {
    type: Object,
    default: void 0
  },
  class: String,
  rootClassName: String,
  rootStyle: objectType(),
  placement: {
    type: String
  },
  wrapperClassName: String,
  level: {
    type: [String, Array]
  },
  levelMove: {
    type: [Number, Function, Array]
  },
  duration: String,
  ease: String,
  showMask: {
    type: Boolean,
    default: void 0
  },
  maskClosable: {
    type: Boolean,
    default: void 0
  },
  maskStyle: {
    type: Object,
    default: void 0
  },
  afterVisibleChange: Function,
  keyboard: {
    type: Boolean,
    default: void 0
  },
  contentWrapperStyle: arrayType(),
  autofocus: {
    type: Boolean,
    default: void 0
  },
  open: {
    type: Boolean,
    default: void 0
  },
  // Motion
  motion: functionType(),
  maskMotion: objectType()
});
const drawerProps$1 = () => _extends(_extends({}, props()), {
  forceRender: {
    type: Boolean,
    default: void 0
  },
  getContainer: PropTypes.oneOfType([PropTypes.string, PropTypes.func, PropTypes.object, PropTypes.looseBool])
});
const drawerChildProps = () => _extends(_extends({}, props()), {
  getContainer: Function,
  getOpenCount: Function,
  scrollLocker: PropTypes.any,
  inline: Boolean
});
function dataToArray(vars) {
  if (Array.isArray(vars)) {
    return vars;
  }
  return [vars];
}
const transitionEndObject = {
  transition: "transitionend",
  WebkitTransition: "webkitTransitionEnd",
  MozTransition: "transitionend",
  OTransition: "oTransitionEnd otransitionend"
};
Object.keys(transitionEndObject).filter((key) => {
  if (typeof document === "undefined") {
    return false;
  }
  const html = document.getElementsByTagName("html")[0];
  return key in (html ? html.style : {});
})[0];
const windowIsUndefined = !(typeof window !== "undefined" && window.document && window.document.createElement);
var __rest$2 = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
const DrawerChild = defineComponent({
  compatConfig: {
    MODE: 3
  },
  inheritAttrs: false,
  props: drawerChildProps(),
  emits: ["close", "handleClick", "change"],
  setup(props2, _ref) {
    let {
      emit,
      slots
    } = _ref;
    const contentWrapper = shallowRef();
    const dom = shallowRef();
    const maskDom = shallowRef();
    const handlerDom = shallowRef();
    const contentDom = shallowRef();
    let levelDom = [];
    `drawer_id_${Number((Date.now() + Math.random()).toString().replace(".", Math.round(Math.random() * 9).toString())).toString(16)}`;
    onMounted(() => {
      nextTick(() => {
        var _a;
        const {
          open,
          getContainer,
          showMask,
          autofocus
        } = props2;
        const container = getContainer === null || getContainer === void 0 ? void 0 : getContainer();
        getLevelDom(props2);
        if (open) {
          if (container && container.parentNode === document.body) ;
          nextTick(() => {
            if (autofocus) {
              domFocus();
            }
          });
          if (showMask) {
            (_a = props2.scrollLocker) === null || _a === void 0 ? void 0 : _a.lock();
          }
        }
      });
    });
    watch(() => props2.level, () => {
      getLevelDom(props2);
    }, {
      flush: "post"
    });
    watch(() => props2.open, () => {
      const {
        open,
        getContainer,
        scrollLocker,
        showMask,
        autofocus
      } = props2;
      const container = getContainer === null || getContainer === void 0 ? void 0 : getContainer();
      if (container && container.parentNode === document.body) ;
      if (open) {
        if (autofocus) {
          domFocus();
        }
        if (showMask) {
          scrollLocker === null || scrollLocker === void 0 ? void 0 : scrollLocker.lock();
        }
      } else {
        scrollLocker === null || scrollLocker === void 0 ? void 0 : scrollLocker.unLock();
      }
    }, {
      flush: "post"
    });
    onUnmounted(() => {
      var _a;
      const {
        open
      } = props2;
      if (open) {
        document.body.style.touchAction = "";
      }
      (_a = props2.scrollLocker) === null || _a === void 0 ? void 0 : _a.unLock();
    });
    watch(() => props2.placement, (val) => {
      if (val) {
        contentDom.value = null;
      }
    });
    const domFocus = () => {
      var _a, _b;
      (_b = (_a = dom.value) === null || _a === void 0 ? void 0 : _a.focus) === null || _b === void 0 ? void 0 : _b.call(_a);
    };
    const onClose = (e) => {
      emit("close", e);
    };
    const onKeyDown = (e) => {
      if (e.keyCode === KeyCode.ESC) {
        e.stopPropagation();
        onClose(e);
      }
    };
    const onAfterVisibleChange = () => {
      const {
        open,
        afterVisibleChange
      } = props2;
      if (afterVisibleChange) {
        afterVisibleChange(!!open);
      }
    };
    const getLevelDom = (_ref2) => {
      let {
        level,
        getContainer
      } = _ref2;
      if (windowIsUndefined) {
        return;
      }
      const container = getContainer === null || getContainer === void 0 ? void 0 : getContainer();
      const parent = container ? container.parentNode : null;
      levelDom = [];
      if (level === "all") {
        const children = parent ? Array.prototype.slice.call(parent.children) : [];
        children.forEach((child) => {
          if (child.nodeName !== "SCRIPT" && child.nodeName !== "STYLE" && child.nodeName !== "LINK" && child !== container) {
            levelDom.push(child);
          }
        });
      } else if (level) {
        dataToArray(level).forEach((key) => {
          document.querySelectorAll(key).forEach((item) => {
            levelDom.push(item);
          });
        });
      }
    };
    const onHandleClick = (e) => {
      emit("handleClick", e);
    };
    const canOpen = shallowRef(false);
    watch(dom, () => {
      nextTick(() => {
        canOpen.value = true;
      });
    });
    return () => {
      var _a, _b;
      const {
        width,
        height,
        open: $open,
        prefixCls,
        placement,
        level,
        levelMove,
        ease,
        duration,
        getContainer,
        onChange,
        afterVisibleChange,
        showMask,
        maskClosable,
        maskStyle,
        keyboard,
        getOpenCount,
        scrollLocker,
        contentWrapperStyle,
        style,
        class: className,
        rootClassName,
        rootStyle,
        maskMotion,
        motion,
        inline
      } = props2, otherProps = __rest$2(props2, ["width", "height", "open", "prefixCls", "placement", "level", "levelMove", "ease", "duration", "getContainer", "onChange", "afterVisibleChange", "showMask", "maskClosable", "maskStyle", "keyboard", "getOpenCount", "scrollLocker", "contentWrapperStyle", "style", "class", "rootClassName", "rootStyle", "maskMotion", "motion", "inline"]);
      const open = $open && canOpen.value;
      const wrapperClassName = classNames(prefixCls, {
        [`${prefixCls}-${placement}`]: true,
        [`${prefixCls}-open`]: open,
        [`${prefixCls}-inline`]: inline,
        "no-mask": !showMask,
        [rootClassName]: true
      });
      const motionProps = typeof motion === "function" ? motion(placement) : motion;
      return createVNode("div", _objectSpread2(_objectSpread2({}, omit(otherProps, ["autofocus"])), {}, {
        "tabindex": -1,
        "class": wrapperClassName,
        "style": rootStyle,
        "ref": dom,
        "onKeydown": open && keyboard ? onKeyDown : void 0
      }), [createVNode(Transition, maskMotion, {
        default: () => [showMask && withDirectives(createVNode("div", {
          "class": `${prefixCls}-mask`,
          "onClick": maskClosable ? onClose : void 0,
          "style": maskStyle,
          "ref": maskDom
        }, null), [[vShow, open]])]
      }), createVNode(Transition, _objectSpread2(_objectSpread2({}, motionProps), {}, {
        "onAfterEnter": onAfterVisibleChange,
        "onAfterLeave": onAfterVisibleChange
      }), {
        default: () => [withDirectives(createVNode("div", {
          "class": `${prefixCls}-content-wrapper`,
          "style": [contentWrapperStyle],
          "ref": contentWrapper
        }, [createVNode("div", {
          "class": [`${prefixCls}-content`, className],
          "style": style,
          "ref": contentDom
        }, [(_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots)]), slots.handler ? createVNode("div", {
          "onClick": onHandleClick,
          "ref": handlerDom
        }, [(_b = slots.handler) === null || _b === void 0 ? void 0 : _b.call(slots)]) : null]), [[vShow, open]])]
      })]);
    };
  }
});
var __rest$1 = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
const DrawerWrapper = defineComponent({
  compatConfig: {
    MODE: 3
  },
  inheritAttrs: false,
  props: initDefaultProps(drawerProps$1(), {
    prefixCls: "drawer",
    placement: "left",
    getContainer: "body",
    level: "all",
    duration: ".3s",
    ease: "cubic-bezier(0.78, 0.14, 0.15, 0.86)",
    afterVisibleChange: () => {
    },
    showMask: true,
    maskClosable: true,
    maskStyle: {},
    wrapperClassName: "",
    keyboard: true,
    forceRender: false,
    autofocus: true
  }),
  emits: ["handleClick", "close"],
  setup(props2, _ref) {
    let {
      emit,
      slots
    } = _ref;
    const dom = ref(null);
    const onHandleClick = (e) => {
      emit("handleClick", e);
    };
    const onClose = (e) => {
      emit("close", e);
    };
    return () => {
      const {
        getContainer,
        wrapperClassName,
        rootClassName,
        rootStyle,
        forceRender
      } = props2, otherProps = __rest$1(props2, ["getContainer", "wrapperClassName", "rootClassName", "rootStyle", "forceRender"]);
      let portal = null;
      if (!getContainer) {
        return createVNode(DrawerChild, _objectSpread2(_objectSpread2({}, otherProps), {}, {
          "rootClassName": rootClassName,
          "rootStyle": rootStyle,
          "open": props2.open,
          "onClose": onClose,
          "onHandleClick": onHandleClick,
          "inline": true
        }), slots);
      }
      const $forceRender = !!slots.handler || forceRender;
      if ($forceRender || props2.open || dom.value) {
        portal = createVNode(Portal, {
          "autoLock": true,
          "visible": props2.open,
          "forceRender": $forceRender,
          "getContainer": getContainer,
          "wrapperClassName": wrapperClassName
        }, {
          default: (_a) => {
            var {
              visible,
              afterClose
            } = _a, rest = __rest$1(_a, ["visible", "afterClose"]);
            return createVNode(DrawerChild, _objectSpread2(_objectSpread2(_objectSpread2({
              "ref": dom
            }, otherProps), rest), {}, {
              "rootClassName": rootClassName,
              "rootStyle": rootStyle,
              "open": visible !== void 0 ? visible : props2.open,
              "afterVisibleChange": afterClose !== void 0 ? afterClose : props2.afterVisibleChange,
              "onClose": onClose,
              "onHandleClick": onHandleClick
            }), slots);
          }
        });
      }
      return portal;
    };
  }
});
const genMotionStyle = (token) => {
  const {
    componentCls,
    motionDurationSlow
  } = token;
  const sharedPanelMotion = {
    "&-enter, &-appear, &-leave": {
      "&-start": {
        transition: "none"
      },
      "&-active": {
        transition: `all ${motionDurationSlow}`
      }
    }
  };
  return {
    [componentCls]: {
      // ======================== Mask ========================
      [`${componentCls}-mask-motion`]: {
        "&-enter, &-appear, &-leave": {
          "&-active": {
            transition: `all ${motionDurationSlow}`
          }
        },
        "&-enter, &-appear": {
          opacity: 0,
          "&-active": {
            opacity: 1
          }
        },
        "&-leave": {
          opacity: 1,
          "&-active": {
            opacity: 0
          }
        }
      },
      // ======================= Panel ========================
      [`${componentCls}-panel-motion`]: {
        // Left
        "&-left": [sharedPanelMotion, {
          "&-enter, &-appear": {
            "&-start": {
              transform: "translateX(-100%) !important"
            },
            "&-active": {
              transform: "translateX(0)"
            }
          },
          "&-leave": {
            transform: "translateX(0)",
            "&-active": {
              transform: "translateX(-100%)"
            }
          }
        }],
        // Right
        "&-right": [sharedPanelMotion, {
          "&-enter, &-appear": {
            "&-start": {
              transform: "translateX(100%) !important"
            },
            "&-active": {
              transform: "translateX(0)"
            }
          },
          "&-leave": {
            transform: "translateX(0)",
            "&-active": {
              transform: "translateX(100%)"
            }
          }
        }],
        // Top
        "&-top": [sharedPanelMotion, {
          "&-enter, &-appear": {
            "&-start": {
              transform: "translateY(-100%) !important"
            },
            "&-active": {
              transform: "translateY(0)"
            }
          },
          "&-leave": {
            transform: "translateY(0)",
            "&-active": {
              transform: "translateY(-100%)"
            }
          }
        }],
        // Bottom
        "&-bottom": [sharedPanelMotion, {
          "&-enter, &-appear": {
            "&-start": {
              transform: "translateY(100%) !important"
            },
            "&-active": {
              transform: "translateY(0)"
            }
          },
          "&-leave": {
            transform: "translateY(0)",
            "&-active": {
              transform: "translateY(100%)"
            }
          }
        }]
      }
    }
  };
};
const genDrawerStyle = (token) => {
  const {
    componentCls,
    zIndexPopup,
    colorBgMask,
    colorBgElevated,
    motionDurationSlow,
    motionDurationMid,
    padding,
    paddingLG,
    fontSizeLG,
    lineHeightLG,
    lineWidth,
    lineType,
    colorSplit,
    marginSM,
    colorIcon,
    colorIconHover,
    colorText,
    fontWeightStrong,
    drawerFooterPaddingVertical,
    drawerFooterPaddingHorizontal
  } = token;
  const wrapperCls = `${componentCls}-content-wrapper`;
  return {
    [componentCls]: {
      position: "fixed",
      inset: 0,
      zIndex: zIndexPopup,
      pointerEvents: "none",
      "&-pure": {
        position: "relative",
        background: colorBgElevated,
        [`&${componentCls}-left`]: {
          boxShadow: token.boxShadowDrawerLeft
        },
        [`&${componentCls}-right`]: {
          boxShadow: token.boxShadowDrawerRight
        },
        [`&${componentCls}-top`]: {
          boxShadow: token.boxShadowDrawerUp
        },
        [`&${componentCls}-bottom`]: {
          boxShadow: token.boxShadowDrawerDown
        }
      },
      "&-inline": {
        position: "absolute"
      },
      // ====================== Mask ======================
      [`${componentCls}-mask`]: {
        position: "absolute",
        inset: 0,
        zIndex: zIndexPopup,
        background: colorBgMask,
        pointerEvents: "auto"
      },
      // ==================== Content =====================
      [wrapperCls]: {
        position: "absolute",
        zIndex: zIndexPopup,
        transition: `all ${motionDurationSlow}`,
        "&-hidden": {
          display: "none"
        }
      },
      // Placement
      [`&-left > ${wrapperCls}`]: {
        top: 0,
        bottom: 0,
        left: {
          _skip_check_: true,
          value: 0
        },
        boxShadow: token.boxShadowDrawerLeft
      },
      [`&-right > ${wrapperCls}`]: {
        top: 0,
        right: {
          _skip_check_: true,
          value: 0
        },
        bottom: 0,
        boxShadow: token.boxShadowDrawerRight
      },
      [`&-top > ${wrapperCls}`]: {
        top: 0,
        insetInline: 0,
        boxShadow: token.boxShadowDrawerUp
      },
      [`&-bottom > ${wrapperCls}`]: {
        bottom: 0,
        insetInline: 0,
        boxShadow: token.boxShadowDrawerDown
      },
      [`${componentCls}-content`]: {
        width: "100%",
        height: "100%",
        overflow: "auto",
        background: colorBgElevated,
        pointerEvents: "auto"
      },
      // ===================== Panel ======================
      [`${componentCls}-wrapper-body`]: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%"
      },
      // Header
      [`${componentCls}-header`]: {
        display: "flex",
        flex: 0,
        alignItems: "center",
        padding: `${padding}px ${paddingLG}px`,
        fontSize: fontSizeLG,
        lineHeight: lineHeightLG,
        borderBottom: `${lineWidth}px ${lineType} ${colorSplit}`,
        "&-title": {
          display: "flex",
          flex: 1,
          alignItems: "center",
          minWidth: 0,
          minHeight: 0
        }
      },
      [`${componentCls}-extra`]: {
        flex: "none"
      },
      [`${componentCls}-close`]: {
        display: "inline-block",
        marginInlineEnd: marginSM,
        color: colorIcon,
        fontWeight: fontWeightStrong,
        fontSize: fontSizeLG,
        fontStyle: "normal",
        lineHeight: 1,
        textAlign: "center",
        textTransform: "none",
        textDecoration: "none",
        background: "transparent",
        border: 0,
        outline: 0,
        cursor: "pointer",
        transition: `color ${motionDurationMid}`,
        textRendering: "auto",
        "&:focus, &:hover": {
          color: colorIconHover,
          textDecoration: "none"
        }
      },
      [`${componentCls}-title`]: {
        flex: 1,
        margin: 0,
        color: colorText,
        fontWeight: token.fontWeightStrong,
        fontSize: fontSizeLG,
        lineHeight: lineHeightLG
      },
      // Body
      [`${componentCls}-body`]: {
        flex: 1,
        minWidth: 0,
        minHeight: 0,
        padding: paddingLG,
        overflow: "auto"
      },
      // Footer
      [`${componentCls}-footer`]: {
        flexShrink: 0,
        padding: `${drawerFooterPaddingVertical}px ${drawerFooterPaddingHorizontal}px`,
        borderTop: `${lineWidth}px ${lineType} ${colorSplit}`
      },
      // ====================== RTL =======================
      "&-rtl": {
        direction: "rtl"
      }
    }
  };
};
const useStyle = genComponentStyleHook("Drawer", (token) => {
  const drawerToken = merge(token, {
    drawerFooterPaddingVertical: token.paddingXS,
    drawerFooterPaddingHorizontal: token.padding
  });
  return [genDrawerStyle(drawerToken), genMotionStyle(drawerToken)];
}, (token) => ({
  zIndexPopup: token.zIndexPopupBase
}));
const isNumeric = (value) => {
  return !isNaN(parseFloat(value)) && isFinite(value);
};
var __rest = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
const PlacementTypes = ["top", "right", "bottom", "left"];
const defaultPushState = {
  distance: 180
};
const drawerProps = () => ({
  autofocus: {
    type: Boolean,
    default: void 0
  },
  closable: {
    type: Boolean,
    default: void 0
  },
  closeIcon: PropTypes.any,
  destroyOnClose: {
    type: Boolean,
    default: void 0
  },
  forceRender: {
    type: Boolean,
    default: void 0
  },
  getContainer: {
    type: [String, Function, Boolean, Object],
    default: void 0
  },
  maskClosable: {
    type: Boolean,
    default: void 0
  },
  mask: {
    type: Boolean,
    default: void 0
  },
  maskStyle: objectType(),
  rootClassName: String,
  rootStyle: objectType(),
  size: {
    type: String
  },
  drawerStyle: objectType(),
  headerStyle: objectType(),
  bodyStyle: objectType(),
  contentWrapperStyle: {
    type: Object,
    default: void 0
  },
  title: PropTypes.any,
  /** @deprecated Please use `open` instead */
  visible: {
    type: Boolean,
    default: void 0
  },
  open: {
    type: Boolean,
    default: void 0
  },
  width: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  height: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  zIndex: Number,
  prefixCls: String,
  push: PropTypes.oneOfType([PropTypes.looseBool, {
    type: Object
  }]),
  placement: PropTypes.oneOf(PlacementTypes),
  keyboard: {
    type: Boolean,
    default: void 0
  },
  extra: PropTypes.any,
  footer: PropTypes.any,
  footerStyle: objectType(),
  level: PropTypes.any,
  levelMove: {
    type: [Number, Array, Function]
  },
  handle: PropTypes.any,
  /** @deprecated Use `@afterVisibleChange` instead */
  afterVisibleChange: Function,
  /** @deprecated Please use `@afterOpenChange` instead */
  onAfterVisibleChange: Function,
  onAfterOpenChange: Function,
  /** @deprecated Please use `onUpdate:open` instead */
  "onUpdate:visible": Function,
  "onUpdate:open": Function,
  onClose: Function
});
const Drawer = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "ADrawer",
  inheritAttrs: false,
  props: initDefaultProps(drawerProps(), {
    closable: true,
    placement: "right",
    maskClosable: true,
    mask: true,
    level: null,
    keyboard: true,
    push: defaultPushState
  }),
  slots: Object,
  // emits: ['update:visible', 'close', 'afterVisibleChange'],
  setup(props2, _ref) {
    let {
      emit,
      slots,
      attrs
    } = _ref;
    const sPush = shallowRef(false);
    const destroyClose = shallowRef(false);
    const vcDrawer = shallowRef(null);
    const load = shallowRef(false);
    const visible = shallowRef(false);
    const mergedOpen = computed(() => {
      var _a;
      return (_a = props2.open) !== null && _a !== void 0 ? _a : props2.visible;
    });
    watch(mergedOpen, () => {
      if (mergedOpen.value) {
        load.value = true;
      } else {
        visible.value = false;
      }
    }, {
      immediate: true
    });
    watch([mergedOpen, load], () => {
      if (mergedOpen.value && load.value) {
        visible.value = true;
      }
    }, {
      immediate: true
    });
    const parentDrawerOpts = inject("parentDrawerOpts", null);
    const {
      prefixCls,
      getPopupContainer,
      direction
    } = useConfigInject("drawer", props2);
    const [wrapSSR, hashId] = useStyle(prefixCls);
    const getContainer = computed(() => (
      // 有可能为 false，所以不能直接判断
      props2.getContainer === void 0 && (getPopupContainer === null || getPopupContainer === void 0 ? void 0 : getPopupContainer.value) ? () => getPopupContainer.value(document.body) : props2.getContainer
    ));
    devWarning(!props2.afterVisibleChange, "Drawer", "`afterVisibleChange` prop is deprecated, please use `@afterVisibleChange` event instead");
    if (process.env.NODE_ENV !== "production") {
      [["visible", "open"], ["onUpdate:visible", "onUpdate:open"], ["onAfterVisibleChange", "onAfterOpenChange"]].forEach((_ref2) => {
        let [deprecatedName, newName] = _ref2;
        devWarning(!props2[deprecatedName], "Drawer", `\`${deprecatedName}\` is deprecated, please use \`${newName}\` instead.`);
      });
    }
    const setPush = () => {
      sPush.value = true;
    };
    const setPull = () => {
      sPush.value = false;
      nextTick(() => {
        domFocus();
      });
    };
    provide("parentDrawerOpts", {
      setPush,
      setPull
    });
    onMounted(() => {
      if (mergedOpen.value && parentDrawerOpts) {
        parentDrawerOpts.setPush();
      }
    });
    onUnmounted(() => {
      if (parentDrawerOpts) {
        parentDrawerOpts.setPull();
      }
    });
    watch(visible, () => {
      if (parentDrawerOpts) {
        if (visible.value) {
          parentDrawerOpts.setPush();
        } else {
          parentDrawerOpts.setPull();
        }
      }
    }, {
      flush: "post"
    });
    const domFocus = () => {
      var _a, _b;
      (_b = (_a = vcDrawer.value) === null || _a === void 0 ? void 0 : _a.domFocus) === null || _b === void 0 ? void 0 : _b.call(_a);
    };
    const close = (e) => {
      emit("update:visible", false);
      emit("update:open", false);
      emit("close", e);
    };
    const afterVisibleChange = (open) => {
      var _a;
      if (!open) {
        if (destroyClose.value === false) {
          destroyClose.value = true;
        }
        if (props2.destroyOnClose) {
          load.value = false;
        }
      }
      (_a = props2.afterVisibleChange) === null || _a === void 0 ? void 0 : _a.call(props2, open);
      emit("afterVisibleChange", open);
      emit("afterOpenChange", open);
    };
    const pushTransform = computed(() => {
      const {
        push,
        placement
      } = props2;
      let distance;
      if (typeof push === "boolean") {
        distance = push ? defaultPushState.distance : 0;
      } else {
        distance = push.distance;
      }
      distance = parseFloat(String(distance || 0));
      if (placement === "left" || placement === "right") {
        return `translateX(${placement === "left" ? distance : -distance}px)`;
      }
      if (placement === "top" || placement === "bottom") {
        return `translateY(${placement === "top" ? distance : -distance}px)`;
      }
      return null;
    });
    const mergedWidth = computed(() => {
      var _a;
      return (_a = props2.width) !== null && _a !== void 0 ? _a : props2.size === "large" ? 736 : 378;
    });
    const mergedHeight = computed(() => {
      var _a;
      return (_a = props2.height) !== null && _a !== void 0 ? _a : props2.size === "large" ? 736 : 378;
    });
    const offsetStyle = computed(() => {
      const {
        mask,
        placement
      } = props2;
      if (!visible.value && !mask) {
        return {};
      }
      const val = {};
      if (placement === "left" || placement === "right") {
        val.width = isNumeric(mergedWidth.value) ? `${mergedWidth.value}px` : mergedWidth.value;
      } else {
        val.height = isNumeric(mergedHeight.value) ? `${mergedHeight.value}px` : mergedHeight.value;
      }
      return val;
    });
    const wrapperStyle = computed(() => {
      const {
        zIndex,
        contentWrapperStyle
      } = props2;
      const val = offsetStyle.value;
      return [{
        zIndex,
        transform: sPush.value ? pushTransform.value : void 0
      }, _extends({}, contentWrapperStyle), val];
    });
    const renderHeader = (prefixCls2) => {
      const {
        closable,
        headerStyle
      } = props2;
      const extra = getPropsSlot(slots, props2, "extra");
      const title = getPropsSlot(slots, props2, "title");
      if (!title && !closable) {
        return null;
      }
      return createVNode("div", {
        "class": classNames(`${prefixCls2}-header`, {
          [`${prefixCls2}-header-close-only`]: closable && !title && !extra
        }),
        "style": headerStyle
      }, [createVNode("div", {
        "class": `${prefixCls2}-header-title`
      }, [renderCloseIcon(prefixCls2), title && createVNode("div", {
        "class": `${prefixCls2}-title`
      }, [title])]), extra && createVNode("div", {
        "class": `${prefixCls2}-extra`
      }, [extra])]);
    };
    const renderCloseIcon = (prefixCls2) => {
      var _a;
      const {
        closable
      } = props2;
      const $closeIcon = slots.closeIcon ? (_a = slots.closeIcon) === null || _a === void 0 ? void 0 : _a.call(slots) : props2.closeIcon;
      return closable && createVNode("button", {
        "key": "closer",
        "onClick": close,
        "aria-label": "Close",
        "class": `${prefixCls2}-close`
      }, [$closeIcon === void 0 ? createVNode(CloseOutlined, null, null) : $closeIcon]);
    };
    const renderBody = (prefixCls2) => {
      var _a;
      if (destroyClose.value && !props2.forceRender && !load.value) {
        return null;
      }
      const {
        bodyStyle,
        drawerStyle
      } = props2;
      return createVNode("div", {
        "class": `${prefixCls2}-wrapper-body`,
        "style": drawerStyle
      }, [renderHeader(prefixCls2), createVNode("div", {
        "key": "body",
        "class": `${prefixCls2}-body`,
        "style": bodyStyle
      }, [(_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots)]), renderFooter(prefixCls2)]);
    };
    const renderFooter = (prefixCls2) => {
      const footer = getPropsSlot(slots, props2, "footer");
      if (!footer) {
        return null;
      }
      const footerClassName = `${prefixCls2}-footer`;
      return createVNode("div", {
        "class": footerClassName,
        "style": props2.footerStyle
      }, [footer]);
    };
    const drawerClassName = computed(() => classNames({
      "no-mask": !props2.mask,
      [`${prefixCls.value}-rtl`]: direction.value === "rtl"
    }, props2.rootClassName, hashId.value));
    const maskMotion = computed(() => {
      return getTransitionProps(getTransitionName(prefixCls.value, "mask-motion"));
    });
    const panelMotion = (motionPlacement) => {
      return getTransitionProps(getTransitionName(prefixCls.value, `panel-motion-${motionPlacement}`));
    };
    return () => {
      const {
        width,
        height,
        placement,
        mask,
        forceRender
      } = props2, rest = __rest(props2, ["width", "height", "placement", "mask", "forceRender"]);
      const vcDrawerProps = _extends(_extends(_extends({}, attrs), omit(rest, ["size", "closeIcon", "closable", "destroyOnClose", "drawerStyle", "headerStyle", "bodyStyle", "title", "push", "onAfterVisibleChange", "onClose", "onUpdate:visible", "onUpdate:open", "visible"])), {
        forceRender,
        onClose: close,
        afterVisibleChange,
        handler: false,
        prefixCls: prefixCls.value,
        open: visible.value,
        showMask: mask,
        placement,
        ref: vcDrawer
      });
      return wrapSSR(createVNode(NoCompactStyle, null, {
        default: () => [createVNode(DrawerWrapper, _objectSpread2(_objectSpread2({}, vcDrawerProps), {}, {
          "maskMotion": maskMotion.value,
          "motion": panelMotion,
          "width": mergedWidth.value,
          "height": mergedHeight.value,
          "getContainer": getContainer.value,
          "rootClassName": drawerClassName.value,
          "rootStyle": props2.rootStyle,
          "contentWrapperStyle": wrapperStyle.value
        }), {
          handler: props2.handle ? () => props2.handle : slots.handle,
          default: () => renderBody(prefixCls.value)
        })]
      }));
    };
  }
});
const index = withInstall(Drawer);
export {
  index as default,
  drawerProps
};
//# sourceMappingURL=index-tv39AuCc.js.map
