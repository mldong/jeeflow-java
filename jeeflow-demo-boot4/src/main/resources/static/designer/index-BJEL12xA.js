import { P as PropTypes, b as _objectSpread2, _ as _extends, c as classNames, L as contains, g as genComponentStyleHook, m as merge, y as resetComponent, M as genFocusStyle, B as clearFix, v as useLocaleReceiver, u as useConfigInject, n as warning, o as objectType, K as findDOMNode, N as useConfigContextInject, x as localeValues } from "./index-IfV1sCLI.js";
import { defineComponent, ref, computed, createVNode, Transition, withDirectives, vShow, nextTick, shallowRef, watch, onBeforeUnmount, watchEffect, Fragment, onMounted, render, unref, isRef } from "vue";
import { K as KeyCode } from "./KeyCode-CnYuZtHO.js";
import { o as omit } from "./omit-CQMssqPV.js";
import { p as pickAttrs } from "./pickAttrs-BpQ0uBJu.js";
import { i as initDefaultProps } from "./raf-DXHxwK-q.js";
import { g as getTransitionProps, u as useProvidePortal, a as getTransitionName } from "./transition-AJOM0T1w.js";
import { P as Portal } from "./PortalWrapper-FITAQJ0K.js";
import { i as initMotion, a as addEventListenerWrap } from "./motion-Oa66PlhN.js";
import { C as CloseOutlined } from "./CloseOutlined-nOZPpOLa.js";
import { B as Button, c as convertLegacyProps } from "./index-CWx-H6CJ.js";
import { c as canUseDocElement } from "./styleChecker-CRnW4Ti6.js";
import { K as Keyframe } from "./motionUtil-C3pKw7LX.js";
import { i as initZoomMotion } from "./zoom-CQljQfTH.js";
import { E as ExclamationCircleFilled, C as CheckCircleFilled } from "./ExclamationCircleFilled-B1vxzrB_.js";
import { C as CloseCircleFilled } from "./CloseCircleFilled-CzkqoH7Y.js";
import { I as InfoCircleFilled, g as globalConfigForApi, C as ConfigProvider, a as getConfirmLocale } from "./index-Dt2YWyFZ.js";
import { t as triggerVNodeUpdate } from "./vnode-B1ULLVwf.js";
const fadeIn = new Keyframe("antFadeIn", {
  "0%": {
    opacity: 0
  },
  "100%": {
    opacity: 1
  }
});
const fadeOut = new Keyframe("antFadeOut", {
  "0%": {
    opacity: 1
  },
  "100%": {
    opacity: 0
  }
});
const initFadeMotion = function(token) {
  let sameLevel = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : false;
  const {
    antCls
  } = token;
  const motionCls = `${antCls}-fade`;
  const sameLevelPrefix = sameLevel ? "&" : "";
  return [initMotion(motionCls, fadeIn, fadeOut, token.motionDurationMid, sameLevel), {
    [`
        ${sameLevelPrefix}${motionCls}-enter,
        ${sameLevelPrefix}${motionCls}-appear
      `]: {
      opacity: 0,
      animationTimingFunction: "linear"
    },
    [`${sameLevelPrefix}${motionCls}-leave`]: {
      animationTimingFunction: "linear"
    }
  }];
};
function dialogPropTypes() {
  return {
    keyboard: {
      type: Boolean,
      default: void 0
    },
    mask: {
      type: Boolean,
      default: void 0
    },
    afterClose: Function,
    closable: {
      type: Boolean,
      default: void 0
    },
    maskClosable: {
      type: Boolean,
      default: void 0
    },
    visible: {
      type: Boolean,
      default: void 0
    },
    destroyOnClose: {
      type: Boolean,
      default: void 0
    },
    mousePosition: PropTypes.shape({
      x: Number,
      y: Number
    }).loose,
    title: PropTypes.any,
    footer: PropTypes.any,
    transitionName: String,
    maskTransitionName: String,
    animation: PropTypes.any,
    maskAnimation: PropTypes.any,
    wrapStyle: {
      type: Object,
      default: void 0
    },
    bodyStyle: {
      type: Object,
      default: void 0
    },
    maskStyle: {
      type: Object,
      default: void 0
    },
    prefixCls: String,
    wrapClassName: String,
    rootClassName: String,
    width: [String, Number],
    height: [String, Number],
    zIndex: Number,
    bodyProps: PropTypes.any,
    maskProps: PropTypes.any,
    wrapProps: PropTypes.any,
    getContainer: PropTypes.any,
    dialogStyle: {
      type: Object,
      default: void 0
    },
    dialogClass: String,
    closeIcon: PropTypes.any,
    forceRender: {
      type: Boolean,
      default: void 0
    },
    getOpenCount: Function,
    // https://github.com/ant-design/ant-design/issues/19771
    // https://github.com/react-component/dialog/issues/95
    focusTriggerAfterClose: {
      type: Boolean,
      default: void 0
    },
    onClose: Function,
    modalRender: Function
  };
}
function getMotionName(prefixCls, transitionName, animationName) {
  let motionName = transitionName;
  if (!motionName && animationName) {
    motionName = `${prefixCls}-${animationName}`;
  }
  return motionName;
}
let uuid$1 = -1;
function getUUID() {
  uuid$1 += 1;
  return uuid$1;
}
function getScroll(w, top) {
  let ret = w[`page${top ? "Y" : "X"}Offset`];
  const method = `scroll${top ? "Top" : "Left"}`;
  if (typeof ret !== "number") {
    const d = w.document;
    ret = d.documentElement[method];
    if (typeof ret !== "number") {
      ret = d.body[method];
    }
  }
  return ret;
}
function offset(el) {
  const rect = el.getBoundingClientRect();
  const pos = {
    left: rect.left,
    top: rect.top
  };
  const doc = el.ownerDocument;
  const w = doc.defaultView || doc.parentWindow;
  pos.left += getScroll(w);
  pos.top += getScroll(w, true);
  return pos;
}
const sentinelStyle = {
  width: 0,
  height: 0,
  overflow: "hidden",
  outline: "none"
};
const entityStyle = {
  outline: "none"
};
const Content = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "DialogContent",
  inheritAttrs: false,
  props: _extends(_extends({}, dialogPropTypes()), {
    motionName: String,
    ariaId: String,
    onVisibleChanged: Function,
    onMousedown: Function,
    onMouseup: Function
  }),
  setup(props, _ref) {
    let {
      expose,
      slots,
      attrs
    } = _ref;
    const sentinelStartRef = ref();
    const sentinelEndRef = ref();
    const dialogRef = ref();
    expose({
      focus: () => {
        var _a;
        (_a = sentinelStartRef.value) === null || _a === void 0 ? void 0 : _a.focus({
          preventScroll: true
        });
      },
      changeActive: (next) => {
        const {
          activeElement
        } = document;
        if (next && activeElement === sentinelEndRef.value) {
          sentinelStartRef.value.focus({
            preventScroll: true
          });
        } else if (!next && activeElement === sentinelStartRef.value) {
          sentinelEndRef.value.focus({
            preventScroll: true
          });
        }
      }
    });
    const transformOrigin = ref();
    const contentStyleRef = computed(() => {
      const {
        width,
        height
      } = props;
      const contentStyle = {};
      if (width !== void 0) {
        contentStyle.width = typeof width === "number" ? `${width}px` : width;
      }
      if (height !== void 0) {
        contentStyle.height = typeof height === "number" ? `${height}px` : height;
      }
      if (transformOrigin.value) {
        contentStyle.transformOrigin = transformOrigin.value;
      }
      return contentStyle;
    });
    const onPrepare = () => {
      nextTick(() => {
        if (dialogRef.value) {
          const elementOffset = offset(dialogRef.value);
          transformOrigin.value = props.mousePosition ? `${props.mousePosition.x - elementOffset.left}px ${props.mousePosition.y - elementOffset.top}px` : "";
        }
      });
    };
    const onVisibleChanged = (visible) => {
      props.onVisibleChanged(visible);
    };
    return () => {
      var _a, _b, _c, _d;
      const {
        prefixCls,
        footer = (_a = slots.footer) === null || _a === void 0 ? void 0 : _a.call(slots),
        title = (_b = slots.title) === null || _b === void 0 ? void 0 : _b.call(slots),
        ariaId,
        closable,
        closeIcon = (_c = slots.closeIcon) === null || _c === void 0 ? void 0 : _c.call(slots),
        onClose,
        bodyStyle,
        bodyProps,
        onMousedown,
        onMouseup,
        visible,
        modalRender = slots.modalRender,
        destroyOnClose,
        motionName
      } = props;
      let footerNode;
      if (footer) {
        footerNode = createVNode("div", {
          "class": `${prefixCls}-footer`
        }, [footer]);
      }
      let headerNode;
      if (title) {
        headerNode = createVNode("div", {
          "class": `${prefixCls}-header`
        }, [createVNode("div", {
          "class": `${prefixCls}-title`,
          "id": ariaId
        }, [title])]);
      }
      let closer;
      if (closable) {
        closer = createVNode("button", {
          "type": "button",
          "onClick": onClose,
          "aria-label": "Close",
          "class": `${prefixCls}-close`
        }, [closeIcon || createVNode("span", {
          "class": `${prefixCls}-close-x`
        }, null)]);
      }
      const content = createVNode("div", {
        "class": `${prefixCls}-content`
      }, [closer, headerNode, createVNode("div", _objectSpread2({
        "class": `${prefixCls}-body`,
        "style": bodyStyle
      }, bodyProps), [(_d = slots.default) === null || _d === void 0 ? void 0 : _d.call(slots)]), footerNode]);
      const transitionProps = getTransitionProps(motionName);
      return createVNode(Transition, _objectSpread2(_objectSpread2({}, transitionProps), {}, {
        "onBeforeEnter": onPrepare,
        "onAfterEnter": () => onVisibleChanged(true),
        "onAfterLeave": () => onVisibleChanged(false)
      }), {
        default: () => [visible || !destroyOnClose ? withDirectives(createVNode("div", _objectSpread2(_objectSpread2({}, attrs), {}, {
          "ref": dialogRef,
          "key": "dialog-element",
          "role": "document",
          "style": [contentStyleRef.value, attrs.style],
          "class": [prefixCls, attrs.class],
          "onMousedown": onMousedown,
          "onMouseup": onMouseup
        }), [createVNode("div", {
          "tabindex": 0,
          "ref": sentinelStartRef,
          "style": entityStyle
        }, [modalRender ? modalRender({
          originVNode: content
        }) : content]), createVNode("div", {
          "tabindex": 0,
          "ref": sentinelEndRef,
          "style": sentinelStyle
        }, null)]), [[vShow, visible]]) : null]
      });
    };
  }
});
const Mask = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "DialogMask",
  props: {
    prefixCls: String,
    visible: Boolean,
    motionName: String,
    maskProps: Object
  },
  setup(props, _ref) {
    return () => {
      const {
        prefixCls,
        visible,
        maskProps,
        motionName
      } = props;
      const transitionProps = getTransitionProps(motionName);
      return createVNode(Transition, transitionProps, {
        default: () => [withDirectives(createVNode("div", _objectSpread2({
          "class": `${prefixCls}-mask`
        }, maskProps), null), [[vShow, visible]])]
      });
    };
  }
});
const Dialog = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "VcDialog",
  inheritAttrs: false,
  props: initDefaultProps(_extends(_extends({}, dialogPropTypes()), {
    getOpenCount: Function,
    scrollLocker: Object
  }), {
    mask: true,
    visible: false,
    keyboard: true,
    closable: true,
    maskClosable: true,
    destroyOnClose: false,
    prefixCls: "rc-dialog",
    getOpenCount: () => null,
    focusTriggerAfterClose: true
  }),
  setup(props, _ref) {
    let {
      attrs,
      slots
    } = _ref;
    const lastOutSideActiveElementRef = shallowRef();
    const wrapperRef = shallowRef();
    const contentRef = shallowRef();
    const animatedVisible = shallowRef(props.visible);
    const ariaIdRef = shallowRef(`vcDialogTitle${getUUID()}`);
    const onDialogVisibleChanged = (newVisible) => {
      var _a, _b;
      if (newVisible) {
        if (!contains(wrapperRef.value, document.activeElement)) {
          lastOutSideActiveElementRef.value = document.activeElement;
          (_a = contentRef.value) === null || _a === void 0 ? void 0 : _a.focus();
        }
      } else {
        const preAnimatedVisible = animatedVisible.value;
        animatedVisible.value = false;
        if (props.mask && lastOutSideActiveElementRef.value && props.focusTriggerAfterClose) {
          try {
            lastOutSideActiveElementRef.value.focus({
              preventScroll: true
            });
          } catch (e) {
          }
          lastOutSideActiveElementRef.value = null;
        }
        if (preAnimatedVisible) {
          (_b = props.afterClose) === null || _b === void 0 ? void 0 : _b.call(props);
        }
      }
    };
    const onInternalClose = (e) => {
      var _a;
      (_a = props.onClose) === null || _a === void 0 ? void 0 : _a.call(props, e);
    };
    const contentClickRef = shallowRef(false);
    const contentTimeoutRef = shallowRef();
    const onContentMouseDown = () => {
      clearTimeout(contentTimeoutRef.value);
      contentClickRef.value = true;
    };
    const onContentMouseUp = () => {
      contentTimeoutRef.value = setTimeout(() => {
        contentClickRef.value = false;
      });
    };
    const onWrapperClick = (e) => {
      if (!props.maskClosable) return null;
      if (contentClickRef.value) {
        contentClickRef.value = false;
      } else if (wrapperRef.value === e.target) {
        onInternalClose(e);
      }
    };
    const onWrapperKeyDown = (e) => {
      if (props.keyboard && e.keyCode === KeyCode.ESC) {
        e.stopPropagation();
        onInternalClose(e);
        return;
      }
      if (props.visible) {
        if (e.keyCode === KeyCode.TAB) {
          contentRef.value.changeActive(!e.shiftKey);
        }
      }
    };
    watch(() => props.visible, () => {
      if (props.visible) {
        animatedVisible.value = true;
      }
    }, {
      flush: "post"
    });
    onBeforeUnmount(() => {
      var _a;
      clearTimeout(contentTimeoutRef.value);
      (_a = props.scrollLocker) === null || _a === void 0 ? void 0 : _a.unLock();
    });
    watchEffect(() => {
      var _a, _b;
      (_a = props.scrollLocker) === null || _a === void 0 ? void 0 : _a.unLock();
      if (animatedVisible.value) {
        (_b = props.scrollLocker) === null || _b === void 0 ? void 0 : _b.lock();
      }
    });
    return () => {
      const {
        prefixCls,
        mask,
        visible,
        maskTransitionName,
        maskAnimation,
        zIndex,
        wrapClassName,
        rootClassName,
        wrapStyle,
        closable,
        maskProps,
        maskStyle,
        transitionName,
        animation,
        wrapProps,
        title = slots.title
      } = props;
      const {
        style,
        class: className
      } = attrs;
      return createVNode("div", _objectSpread2({
        "class": [`${prefixCls}-root`, rootClassName]
      }, pickAttrs(props, {
        data: true
      })), [createVNode(Mask, {
        "prefixCls": prefixCls,
        "visible": mask && visible,
        "motionName": getMotionName(prefixCls, maskTransitionName, maskAnimation),
        "style": _extends({
          zIndex
        }, maskStyle),
        "maskProps": maskProps
      }, null), createVNode("div", _objectSpread2({
        "tabIndex": -1,
        "onKeydown": onWrapperKeyDown,
        "class": classNames(`${prefixCls}-wrap`, wrapClassName),
        "ref": wrapperRef,
        "onClick": onWrapperClick,
        "role": "dialog",
        "aria-labelledby": title ? ariaIdRef.value : null,
        "style": _extends(_extends({
          zIndex
        }, wrapStyle), {
          display: !animatedVisible.value ? "none" : null
        })
      }, wrapProps), [createVNode(Content, _objectSpread2(_objectSpread2({}, omit(props, ["scrollLocker"])), {}, {
        "style": style,
        "class": className,
        "onMousedown": onContentMouseDown,
        "onMouseup": onContentMouseUp,
        "ref": contentRef,
        "closable": closable,
        "ariaId": ariaIdRef.value,
        "prefixCls": prefixCls,
        "visible": visible,
        "onClose": onInternalClose,
        "onVisibleChanged": onDialogVisibleChanged,
        "motionName": getMotionName(prefixCls, transitionName, animation)
      }), slots)])]);
    };
  }
});
const IDialogPropTypes = dialogPropTypes();
const DialogWrap = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "DialogWrap",
  inheritAttrs: false,
  props: initDefaultProps(IDialogPropTypes, {
    visible: false
  }),
  setup(props, _ref) {
    let {
      attrs,
      slots
    } = _ref;
    const animatedVisible = ref(props.visible);
    useProvidePortal({}, {
      inTriggerContext: false
    });
    watch(() => props.visible, () => {
      if (props.visible) {
        animatedVisible.value = true;
      }
    }, {
      flush: "post"
    });
    return () => {
      const {
        visible,
        getContainer,
        forceRender,
        destroyOnClose = false,
        afterClose
      } = props;
      let dialogProps = _extends(_extends(_extends({}, props), attrs), {
        ref: "_component",
        key: "dialog"
      });
      if (getContainer === false) {
        return createVNode(Dialog, _objectSpread2(_objectSpread2({}, dialogProps), {}, {
          "getOpenCount": () => 2
        }), slots);
      }
      if (!forceRender && destroyOnClose && !animatedVisible.value) {
        return null;
      }
      return createVNode(Portal, {
        "autoLock": true,
        "visible": visible,
        "forceRender": forceRender,
        "getContainer": getContainer
      }, {
        default: (childProps) => {
          dialogProps = _extends(_extends(_extends({}, dialogProps), childProps), {
            afterClose: () => {
              afterClose === null || afterClose === void 0 ? void 0 : afterClose();
              animatedVisible.value = false;
            }
          });
          return createVNode(Dialog, dialogProps, slots);
        }
      });
    };
  }
});
function box(position) {
  return {
    position,
    top: 0,
    insetInlineEnd: 0,
    bottom: 0,
    insetInlineStart: 0
  };
}
const genModalMaskStyle = (token) => {
  const {
    componentCls
  } = token;
  return [{
    [`${componentCls}-root`]: {
      [`${componentCls}${token.antCls}-zoom-enter, ${componentCls}${token.antCls}-zoom-appear`]: {
        // reset scale avoid mousePosition bug
        transform: "none",
        opacity: 0,
        animationDuration: token.motionDurationSlow,
        // https://github.com/ant-design/ant-design/issues/11777
        userSelect: "none"
      },
      [`${componentCls}${token.antCls}-zoom-leave ${componentCls}-content`]: {
        pointerEvents: "none"
      },
      [`${componentCls}-mask`]: _extends(_extends({}, box("fixed")), {
        zIndex: token.zIndexPopupBase,
        height: "100%",
        backgroundColor: token.colorBgMask,
        [`${componentCls}-hidden`]: {
          display: "none"
        }
      }),
      [`${componentCls}-wrap`]: _extends(_extends({}, box("fixed")), {
        overflow: "auto",
        outline: 0,
        WebkitOverflowScrolling: "touch"
      })
    }
  }, {
    [`${componentCls}-root`]: initFadeMotion(token)
  }];
};
const genModalStyle = (token) => {
  const {
    componentCls
  } = token;
  return [
    // ======================== Root =========================
    {
      [`${componentCls}-root`]: {
        [`${componentCls}-wrap`]: {
          zIndex: token.zIndexPopupBase,
          position: "fixed",
          inset: 0,
          overflow: "auto",
          outline: 0,
          WebkitOverflowScrolling: "touch"
        },
        [`${componentCls}-wrap-rtl`]: {
          direction: "rtl"
        },
        [`${componentCls}-centered`]: {
          textAlign: "center",
          "&::before": {
            display: "inline-block",
            width: 0,
            height: "100%",
            verticalAlign: "middle",
            content: '""'
          },
          [componentCls]: {
            top: 0,
            display: "inline-block",
            paddingBottom: 0,
            textAlign: "start",
            verticalAlign: "middle"
          }
        },
        [`@media (max-width: ${token.screenSMMax})`]: {
          [componentCls]: {
            maxWidth: "calc(100vw - 16px)",
            margin: `${token.marginXS} auto`
          },
          [`${componentCls}-centered`]: {
            [componentCls]: {
              flex: 1
            }
          }
        }
      }
    },
    // ======================== Modal ========================
    {
      [componentCls]: _extends(_extends({}, resetComponent(token)), {
        pointerEvents: "none",
        position: "relative",
        top: 100,
        width: "auto",
        maxWidth: `calc(100vw - ${token.margin * 2}px)`,
        margin: "0 auto",
        paddingBottom: token.paddingLG,
        [`${componentCls}-title`]: {
          margin: 0,
          color: token.modalHeadingColor,
          fontWeight: token.fontWeightStrong,
          fontSize: token.modalHeaderTitleFontSize,
          lineHeight: token.modalHeaderTitleLineHeight,
          wordWrap: "break-word"
        },
        [`${componentCls}-content`]: {
          position: "relative",
          backgroundColor: token.modalContentBg,
          backgroundClip: "padding-box",
          border: 0,
          borderRadius: token.borderRadiusLG,
          boxShadow: token.boxShadowSecondary,
          pointerEvents: "auto",
          padding: `${token.paddingMD}px ${token.paddingContentHorizontalLG}px`
        },
        [`${componentCls}-close`]: _extends({
          position: "absolute",
          top: (token.modalHeaderCloseSize - token.modalCloseBtnSize) / 2,
          insetInlineEnd: (token.modalHeaderCloseSize - token.modalCloseBtnSize) / 2,
          zIndex: token.zIndexPopupBase + 10,
          padding: 0,
          color: token.modalCloseColor,
          fontWeight: token.fontWeightStrong,
          lineHeight: 1,
          textDecoration: "none",
          background: "transparent",
          borderRadius: token.borderRadiusSM,
          width: token.modalConfirmIconSize,
          height: token.modalConfirmIconSize,
          border: 0,
          outline: 0,
          cursor: "pointer",
          transition: `color ${token.motionDurationMid}, background-color ${token.motionDurationMid}`,
          "&-x": {
            display: "block",
            fontSize: token.fontSizeLG,
            fontStyle: "normal",
            lineHeight: `${token.modalCloseBtnSize}px`,
            textAlign: "center",
            textTransform: "none",
            textRendering: "auto"
          },
          "&:hover": {
            color: token.modalIconHoverColor,
            backgroundColor: token.wireframe ? "transparent" : token.colorFillContent,
            textDecoration: "none"
          },
          "&:active": {
            backgroundColor: token.wireframe ? "transparent" : token.colorFillContentHover
          }
        }, genFocusStyle(token)),
        [`${componentCls}-header`]: {
          color: token.colorText,
          background: token.modalHeaderBg,
          borderRadius: `${token.borderRadiusLG}px ${token.borderRadiusLG}px 0 0`,
          marginBottom: token.marginXS
        },
        [`${componentCls}-body`]: {
          fontSize: token.fontSize,
          lineHeight: token.lineHeight,
          wordWrap: "break-word"
        },
        [`${componentCls}-footer`]: {
          textAlign: "end",
          background: token.modalFooterBg,
          marginTop: token.marginSM,
          [`${token.antCls}-btn + ${token.antCls}-btn:not(${token.antCls}-dropdown-trigger)`]: {
            marginBottom: 0,
            marginInlineStart: token.marginXS
          }
        },
        [`${componentCls}-open`]: {
          overflow: "hidden"
        }
      })
    },
    // ======================== Pure =========================
    {
      [`${componentCls}-pure-panel`]: {
        top: "auto",
        padding: 0,
        display: "flex",
        flexDirection: "column",
        [`${componentCls}-content,
          ${componentCls}-body,
          ${componentCls}-confirm-body-wrapper`]: {
          display: "flex",
          flexDirection: "column",
          flex: "auto"
        },
        [`${componentCls}-confirm-body`]: {
          marginBottom: "auto"
        }
      }
    }
  ];
};
const genModalConfirmStyle = (token) => {
  const {
    componentCls
  } = token;
  const confirmComponentCls = `${componentCls}-confirm`;
  return {
    [confirmComponentCls]: {
      "&-rtl": {
        direction: "rtl"
      },
      [`${token.antCls}-modal-header`]: {
        display: "none"
      },
      [`${confirmComponentCls}-body-wrapper`]: _extends({}, clearFix()),
      [`${confirmComponentCls}-body`]: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        [`${confirmComponentCls}-title`]: {
          flex: "0 0 100%",
          display: "block",
          // create BFC to avoid
          // https://user-images.githubusercontent.com/507615/37702510-ba844e06-2d2d-11e8-9b67-8e19be57f445.png
          overflow: "hidden",
          color: token.colorTextHeading,
          fontWeight: token.fontWeightStrong,
          fontSize: token.modalHeaderTitleFontSize,
          lineHeight: token.modalHeaderTitleLineHeight,
          [`+ ${confirmComponentCls}-content`]: {
            marginBlockStart: token.marginXS,
            flexBasis: "100%",
            maxWidth: `calc(100% - ${token.modalConfirmIconSize + token.marginSM}px)`
          }
        },
        [`${confirmComponentCls}-content`]: {
          color: token.colorText,
          fontSize: token.fontSize
        },
        [`> ${token.iconCls}`]: {
          flex: "none",
          marginInlineEnd: token.marginSM,
          fontSize: token.modalConfirmIconSize,
          [`+ ${confirmComponentCls}-title`]: {
            flex: 1
          },
          // `content` after `icon` should set marginLeft
          [`+ ${confirmComponentCls}-title + ${confirmComponentCls}-content`]: {
            marginInlineStart: token.modalConfirmIconSize + token.marginSM
          }
        }
      },
      [`${confirmComponentCls}-btns`]: {
        textAlign: "end",
        marginTop: token.marginSM,
        [`${token.antCls}-btn + ${token.antCls}-btn`]: {
          marginBottom: 0,
          marginInlineStart: token.marginXS
        }
      }
    },
    [`${confirmComponentCls}-error ${confirmComponentCls}-body > ${token.iconCls}`]: {
      color: token.colorError
    },
    [`${confirmComponentCls}-warning ${confirmComponentCls}-body > ${token.iconCls},
        ${confirmComponentCls}-confirm ${confirmComponentCls}-body > ${token.iconCls}`]: {
      color: token.colorWarning
    },
    [`${confirmComponentCls}-info ${confirmComponentCls}-body > ${token.iconCls}`]: {
      color: token.colorInfo
    },
    [`${confirmComponentCls}-success ${confirmComponentCls}-body > ${token.iconCls}`]: {
      color: token.colorSuccess
    },
    // https://github.com/ant-design/ant-design/issues/37329
    [`${componentCls}-zoom-leave ${componentCls}-btns`]: {
      pointerEvents: "none"
    }
  };
};
const genRTLStyle = (token) => {
  const {
    componentCls
  } = token;
  return {
    [`${componentCls}-root`]: {
      [`${componentCls}-wrap-rtl`]: {
        direction: "rtl",
        [`${componentCls}-confirm-body`]: {
          direction: "rtl"
        }
      }
    }
  };
};
const genWireframeStyle = (token) => {
  const {
    componentCls,
    antCls
  } = token;
  const confirmComponentCls = `${componentCls}-confirm`;
  return {
    [componentCls]: {
      [`${componentCls}-content`]: {
        padding: 0
      },
      [`${componentCls}-header`]: {
        padding: token.modalHeaderPadding,
        borderBottom: `${token.modalHeaderBorderWidth}px ${token.modalHeaderBorderStyle} ${token.modalHeaderBorderColorSplit}`,
        marginBottom: 0
      },
      [`${componentCls}-body`]: {
        padding: token.modalBodyPadding
      },
      [`${componentCls}-footer`]: {
        padding: `${token.modalFooterPaddingVertical}px ${token.modalFooterPaddingHorizontal}px`,
        borderTop: `${token.modalFooterBorderWidth}px ${token.modalFooterBorderStyle} ${token.modalFooterBorderColorSplit}`,
        borderRadius: `0 0 ${token.borderRadiusLG}px ${token.borderRadiusLG}px`,
        marginTop: 0
      }
    },
    [confirmComponentCls]: {
      [`${antCls}-modal-body`]: {
        padding: `${token.padding * 2}px ${token.padding * 2}px ${token.paddingLG}px`
      },
      [`${confirmComponentCls}-body`]: {
        [`> ${token.iconCls}`]: {
          marginInlineEnd: token.margin,
          // `content` after `icon` should set marginLeft
          [`+ ${confirmComponentCls}-title + ${confirmComponentCls}-content`]: {
            marginInlineStart: token.modalConfirmIconSize + token.margin
          }
        }
      },
      [`${confirmComponentCls}-btns`]: {
        marginTop: token.marginLG
      }
    }
  };
};
const useStyle = genComponentStyleHook("Modal", (token) => {
  const headerPaddingVertical = token.padding;
  const headerFontSize = token.fontSizeHeading5;
  const headerLineHeight = token.lineHeightHeading5;
  const modalToken = merge(token, {
    modalBodyPadding: token.paddingLG,
    modalHeaderBg: token.colorBgElevated,
    modalHeaderPadding: `${headerPaddingVertical}px ${token.paddingLG}px`,
    modalHeaderBorderWidth: token.lineWidth,
    modalHeaderBorderStyle: token.lineType,
    modalHeaderTitleLineHeight: headerLineHeight,
    modalHeaderTitleFontSize: headerFontSize,
    modalHeaderBorderColorSplit: token.colorSplit,
    modalHeaderCloseSize: headerLineHeight * headerFontSize + headerPaddingVertical * 2,
    modalContentBg: token.colorBgElevated,
    modalHeadingColor: token.colorTextHeading,
    modalCloseColor: token.colorTextDescription,
    modalFooterBg: "transparent",
    modalFooterBorderColorSplit: token.colorSplit,
    modalFooterBorderStyle: token.lineType,
    modalFooterPaddingVertical: token.paddingXS,
    modalFooterPaddingHorizontal: token.padding,
    modalFooterBorderWidth: token.lineWidth,
    modalConfirmTitleFontSize: token.fontSizeLG,
    modalIconHoverColor: token.colorIconHover,
    modalConfirmIconSize: token.fontSize * token.lineHeight,
    modalCloseBtnSize: token.controlHeightLG * 0.55
  });
  return [genModalStyle(modalToken), genModalConfirmStyle(modalToken), genRTLStyle(modalToken), genModalMaskStyle(modalToken), token.wireframe && genWireframeStyle(modalToken), initZoomMotion(modalToken, "zoom")];
});
var __rest = function(s, e) {
  var t = {};
  for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
};
let mousePosition;
const getClickPosition = (e) => {
  mousePosition = {
    x: e.pageX,
    y: e.pageY
  };
  setTimeout(() => mousePosition = null, 100);
};
if (canUseDocElement()) {
  addEventListenerWrap(document.documentElement, "click", getClickPosition, true);
}
const modalProps = () => ({
  prefixCls: String,
  /** @deprecated Please use `open` instead. */
  visible: {
    type: Boolean,
    default: void 0
  },
  open: {
    type: Boolean,
    default: void 0
  },
  confirmLoading: {
    type: Boolean,
    default: void 0
  },
  title: PropTypes.any,
  closable: {
    type: Boolean,
    default: void 0
  },
  closeIcon: PropTypes.any,
  onOk: Function,
  onCancel: Function,
  "onUpdate:visible": Function,
  "onUpdate:open": Function,
  onChange: Function,
  afterClose: Function,
  centered: {
    type: Boolean,
    default: void 0
  },
  width: [String, Number],
  footer: PropTypes.any,
  okText: PropTypes.any,
  okType: String,
  cancelText: PropTypes.any,
  icon: PropTypes.any,
  maskClosable: {
    type: Boolean,
    default: void 0
  },
  forceRender: {
    type: Boolean,
    default: void 0
  },
  okButtonProps: objectType(),
  cancelButtonProps: objectType(),
  destroyOnClose: {
    type: Boolean,
    default: void 0
  },
  wrapClassName: String,
  maskTransitionName: String,
  transitionName: String,
  getContainer: {
    type: [String, Function, Boolean, Object],
    default: void 0
  },
  zIndex: Number,
  bodyStyle: objectType(),
  maskStyle: objectType(),
  mask: {
    type: Boolean,
    default: void 0
  },
  keyboard: {
    type: Boolean,
    default: void 0
  },
  wrapProps: Object,
  focusTriggerAfterClose: {
    type: Boolean,
    default: void 0
  },
  modalRender: Function,
  mousePosition: objectType()
});
const Modal = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "AModal",
  inheritAttrs: false,
  props: initDefaultProps(modalProps(), {
    width: 520,
    confirmLoading: false,
    okType: "primary"
  }),
  setup(props, _ref) {
    let {
      emit,
      slots,
      attrs
    } = _ref;
    const [locale] = useLocaleReceiver("Modal");
    const {
      prefixCls,
      rootPrefixCls,
      direction,
      getPopupContainer
    } = useConfigInject("modal", props);
    const [wrapSSR, hashId] = useStyle(prefixCls);
    warning(props.visible === void 0, "Modal", `\`visible\` will be removed in next major version, please use \`open\` instead.`);
    const handleCancel = (e) => {
      emit("update:visible", false);
      emit("update:open", false);
      emit("cancel", e);
      emit("change", false);
    };
    const handleOk = (e) => {
      emit("ok", e);
    };
    const renderFooter = () => {
      var _a, _b;
      const {
        okText = (_a = slots.okText) === null || _a === void 0 ? void 0 : _a.call(slots),
        okType,
        cancelText = (_b = slots.cancelText) === null || _b === void 0 ? void 0 : _b.call(slots),
        confirmLoading
      } = props;
      return createVNode(Fragment, null, [createVNode(Button, _objectSpread2({
        "onClick": handleCancel
      }, props.cancelButtonProps), {
        default: () => [cancelText || locale.value.cancelText]
      }), createVNode(Button, _objectSpread2(_objectSpread2({}, convertLegacyProps(okType)), {}, {
        "loading": confirmLoading,
        "onClick": handleOk
      }, props.okButtonProps), {
        default: () => [okText || locale.value.okText]
      })]);
    };
    return () => {
      var _a, _b;
      const {
        prefixCls: customizePrefixCls,
        visible,
        open,
        wrapClassName,
        centered,
        getContainer,
        closeIcon = (_a = slots.closeIcon) === null || _a === void 0 ? void 0 : _a.call(slots),
        focusTriggerAfterClose = true
      } = props, restProps = __rest(props, ["prefixCls", "visible", "open", "wrapClassName", "centered", "getContainer", "closeIcon", "focusTriggerAfterClose"]);
      const wrapClassNameExtended = classNames(wrapClassName, {
        [`${prefixCls.value}-centered`]: !!centered,
        [`${prefixCls.value}-wrap-rtl`]: direction.value === "rtl"
      });
      return wrapSSR(createVNode(DialogWrap, _objectSpread2(_objectSpread2(_objectSpread2({}, restProps), attrs), {}, {
        "rootClassName": hashId.value,
        "class": classNames(hashId.value, attrs.class),
        "getContainer": getContainer || (getPopupContainer === null || getPopupContainer === void 0 ? void 0 : getPopupContainer.value),
        "prefixCls": prefixCls.value,
        "wrapClassName": wrapClassNameExtended,
        "visible": open !== null && open !== void 0 ? open : visible,
        "onClose": handleCancel,
        "focusTriggerAfterClose": focusTriggerAfterClose,
        "transitionName": getTransitionName(rootPrefixCls.value, "zoom", props.transitionName),
        "maskTransitionName": getTransitionName(rootPrefixCls.value, "fade", props.maskTransitionName),
        "mousePosition": (_b = restProps.mousePosition) !== null && _b !== void 0 ? _b : mousePosition
      }), _extends(_extends({}, slots), {
        footer: slots.footer || renderFooter,
        closeIcon: () => {
          return createVNode("span", {
            "class": `${prefixCls.value}-close-x`
          }, [closeIcon || createVNode(CloseOutlined, {
            "class": `${prefixCls.value}-close-icon`
          }, null)]);
        }
      })));
    };
  }
});
const useDestroyed = () => {
  const destroyed = shallowRef(false);
  onBeforeUnmount(() => {
    destroyed.value = true;
  });
  return destroyed;
};
const actionButtonProps = {
  type: {
    type: String
  },
  actionFn: Function,
  close: Function,
  autofocus: Boolean,
  prefixCls: String,
  buttonProps: objectType(),
  emitEvent: Boolean,
  quitOnNullishReturnValue: Boolean
};
function isThenable(thing) {
  return !!(thing && thing.then);
}
const ActionButton = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "ActionButton",
  props: actionButtonProps,
  setup(props, _ref) {
    let {
      slots
    } = _ref;
    const clickedRef = shallowRef(false);
    const buttonRef = shallowRef();
    const loading = shallowRef(false);
    let timeoutId;
    const isDestroyed = useDestroyed();
    onMounted(() => {
      if (props.autofocus) {
        timeoutId = setTimeout(() => {
          var _a, _b;
          return (_b = (_a = findDOMNode(buttonRef.value)) === null || _a === void 0 ? void 0 : _a.focus) === null || _b === void 0 ? void 0 : _b.call(_a);
        });
      }
    });
    onBeforeUnmount(() => {
      clearTimeout(timeoutId);
    });
    const onInternalClose = function() {
      var _a;
      for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
        args[_key] = arguments[_key];
      }
      (_a = props.close) === null || _a === void 0 ? void 0 : _a.call(props, ...args);
    };
    const handlePromiseOnOk = (returnValueOfOnOk) => {
      if (!isThenable(returnValueOfOnOk)) {
        return;
      }
      loading.value = true;
      returnValueOfOnOk.then(function() {
        if (!isDestroyed.value) {
          loading.value = false;
        }
        onInternalClose(...arguments);
        clickedRef.value = false;
      }, (e) => {
        if (!isDestroyed.value) {
          loading.value = false;
        }
        clickedRef.value = false;
        return Promise.reject(e);
      });
    };
    const onClick = (e) => {
      const {
        actionFn
      } = props;
      if (clickedRef.value) {
        return;
      }
      clickedRef.value = true;
      if (!actionFn) {
        onInternalClose();
        return;
      }
      let returnValueOfOnOk;
      if (props.emitEvent) {
        returnValueOfOnOk = actionFn(e);
        if (props.quitOnNullishReturnValue && !isThenable(returnValueOfOnOk)) {
          clickedRef.value = false;
          onInternalClose(e);
          return;
        }
      } else if (actionFn.length) {
        returnValueOfOnOk = actionFn(props.close);
        clickedRef.value = false;
      } else {
        returnValueOfOnOk = actionFn();
        if (!returnValueOfOnOk) {
          onInternalClose();
          return;
        }
      }
      handlePromiseOnOk(returnValueOfOnOk);
    };
    return () => {
      const {
        type,
        prefixCls,
        buttonProps
      } = props;
      return createVNode(Button, _objectSpread2(_objectSpread2(_objectSpread2({}, convertLegacyProps(type)), {}, {
        "onClick": onClick,
        "loading": loading.value,
        "prefixCls": prefixCls
      }, buttonProps), {}, {
        "ref": buttonRef
      }), slots);
    };
  }
});
function renderSomeContent(someContent) {
  if (typeof someContent === "function") {
    return someContent();
  }
  return someContent;
}
const ConfirmDialog = defineComponent({
  name: "ConfirmDialog",
  inheritAttrs: false,
  props: ["icon", "onCancel", "onOk", "close", "closable", "zIndex", "afterClose", "visible", "open", "keyboard", "centered", "getContainer", "maskStyle", "okButtonProps", "cancelButtonProps", "okType", "prefixCls", "okCancel", "width", "mask", "maskClosable", "okText", "cancelText", "autoFocusButton", "transitionName", "maskTransitionName", "type", "title", "content", "direction", "rootPrefixCls", "bodyStyle", "closeIcon", "modalRender", "focusTriggerAfterClose", "wrapClassName", "confirmPrefixCls", "footer"],
  setup(props, _ref) {
    let {
      attrs
    } = _ref;
    const [locale] = useLocaleReceiver("Modal");
    if (process.env.NODE_ENV !== "production") {
      warning(props.visible === void 0, "Modal", `\`visible\` is deprecated, please use \`open\` instead.`);
    }
    return () => {
      const {
        icon,
        onCancel,
        onOk,
        close,
        okText,
        closable = false,
        zIndex,
        afterClose,
        keyboard,
        centered,
        getContainer,
        maskStyle,
        okButtonProps,
        cancelButtonProps,
        okCancel,
        width = 416,
        mask = true,
        maskClosable = false,
        type,
        open,
        title,
        content,
        direction,
        closeIcon,
        modalRender,
        focusTriggerAfterClose,
        rootPrefixCls,
        bodyStyle,
        wrapClassName,
        footer
      } = props;
      let mergedIcon = icon;
      if (!icon && icon !== null) {
        switch (type) {
          case "info":
            mergedIcon = createVNode(InfoCircleFilled, null, null);
            break;
          case "success":
            mergedIcon = createVNode(CheckCircleFilled, null, null);
            break;
          case "error":
            mergedIcon = createVNode(CloseCircleFilled, null, null);
            break;
          default:
            mergedIcon = createVNode(ExclamationCircleFilled, null, null);
        }
      }
      const okType = props.okType || "primary";
      const prefixCls = props.prefixCls || "ant-modal";
      const contentPrefixCls = `${prefixCls}-confirm`;
      const style = attrs.style || {};
      const mergedOkCancel = okCancel !== null && okCancel !== void 0 ? okCancel : type === "confirm";
      const autoFocusButton = props.autoFocusButton === null ? false : props.autoFocusButton || "ok";
      const confirmPrefixCls = `${prefixCls}-confirm`;
      const classString = classNames(confirmPrefixCls, `${confirmPrefixCls}-${props.type}`, {
        [`${confirmPrefixCls}-rtl`]: direction === "rtl"
      }, attrs.class);
      const mergedLocal = locale.value;
      const cancelButton = mergedOkCancel && createVNode(ActionButton, {
        "actionFn": onCancel,
        "close": close,
        "autofocus": autoFocusButton === "cancel",
        "buttonProps": cancelButtonProps,
        "prefixCls": `${rootPrefixCls}-btn`
      }, {
        default: () => [renderSomeContent(props.cancelText) || mergedLocal.cancelText]
      });
      return createVNode(Modal, {
        "prefixCls": prefixCls,
        "class": classString,
        "wrapClassName": classNames({
          [`${confirmPrefixCls}-centered`]: !!centered
        }, wrapClassName),
        "onCancel": (e) => close === null || close === void 0 ? void 0 : close({
          triggerCancel: true
        }, e),
        "open": open,
        "title": "",
        "footer": "",
        "transitionName": getTransitionName(rootPrefixCls, "zoom", props.transitionName),
        "maskTransitionName": getTransitionName(rootPrefixCls, "fade", props.maskTransitionName),
        "mask": mask,
        "maskClosable": maskClosable,
        "maskStyle": maskStyle,
        "style": style,
        "bodyStyle": bodyStyle,
        "width": width,
        "zIndex": zIndex,
        "afterClose": afterClose,
        "keyboard": keyboard,
        "centered": centered,
        "getContainer": getContainer,
        "closable": closable,
        "closeIcon": closeIcon,
        "modalRender": modalRender,
        "focusTriggerAfterClose": focusTriggerAfterClose
      }, {
        default: () => [createVNode("div", {
          "class": `${contentPrefixCls}-body-wrapper`
        }, [createVNode("div", {
          "class": `${contentPrefixCls}-body`
        }, [renderSomeContent(mergedIcon), title === void 0 ? null : createVNode("span", {
          "class": `${contentPrefixCls}-title`
        }, [renderSomeContent(title)]), createVNode("div", {
          "class": `${contentPrefixCls}-content`
        }, [renderSomeContent(content)])]), footer !== void 0 ? renderSomeContent(footer) : createVNode("div", {
          "class": `${contentPrefixCls}-btns`
        }, [cancelButton, createVNode(ActionButton, {
          "type": okType,
          "actionFn": onOk,
          "close": close,
          "autofocus": autoFocusButton === "ok",
          "buttonProps": okButtonProps,
          "prefixCls": `${rootPrefixCls}-btn`
        }, {
          default: () => [renderSomeContent(okText) || (mergedOkCancel ? mergedLocal.okText : mergedLocal.justOkText)]
        })])])]
      });
    };
  }
});
const destroyFns = [];
const confirm = (config) => {
  const container = document.createDocumentFragment();
  let currentConfig = _extends(_extends({}, omit(config, ["parentContext", "appContext"])), {
    close,
    open: true
  });
  let confirmDialogInstance = null;
  function destroy() {
    if (confirmDialogInstance) {
      render(null, container);
      confirmDialogInstance = null;
    }
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    const triggerCancel = args.some((param) => param && param.triggerCancel);
    if (config.onCancel && triggerCancel) {
      config.onCancel(() => {
      }, ...args.slice(1));
    }
    for (let i = 0; i < destroyFns.length; i++) {
      const fn = destroyFns[i];
      if (fn === close) {
        destroyFns.splice(i, 1);
        break;
      }
    }
  }
  function close() {
    for (var _len2 = arguments.length, args = new Array(_len2), _key2 = 0; _key2 < _len2; _key2++) {
      args[_key2] = arguments[_key2];
    }
    currentConfig = _extends(_extends({}, currentConfig), {
      open: false,
      afterClose: () => {
        if (typeof config.afterClose === "function") {
          config.afterClose();
        }
        destroy.apply(this, args);
      }
    });
    if (currentConfig.visible) {
      delete currentConfig.visible;
    }
    update(currentConfig);
  }
  function update(configUpdate) {
    if (typeof configUpdate === "function") {
      currentConfig = configUpdate(currentConfig);
    } else {
      currentConfig = _extends(_extends({}, currentConfig), configUpdate);
    }
    if (confirmDialogInstance) {
      triggerVNodeUpdate(confirmDialogInstance, currentConfig, container);
    }
  }
  const Wrapper = (p) => {
    const global = globalConfigForApi;
    const rootPrefixCls = global.prefixCls;
    const prefixCls = p.prefixCls || `${rootPrefixCls}-modal`;
    const iconPrefixCls = global.iconPrefixCls;
    const runtimeLocale = getConfirmLocale();
    return createVNode(ConfigProvider, _objectSpread2(_objectSpread2({}, global), {}, {
      "prefixCls": rootPrefixCls
    }), {
      default: () => [createVNode(ConfirmDialog, _objectSpread2(_objectSpread2({}, p), {}, {
        "rootPrefixCls": rootPrefixCls,
        "prefixCls": prefixCls,
        "iconPrefixCls": iconPrefixCls,
        "locale": runtimeLocale,
        "cancelText": p.cancelText || runtimeLocale.cancelText
      }), null)]
    });
  };
  function render$1(props) {
    const vm = createVNode(Wrapper, _extends({}, props));
    vm.appContext = config.parentContext || config.appContext || vm.appContext;
    render(vm, container);
    return vm;
  }
  confirmDialogInstance = render$1(currentConfig);
  destroyFns.push(close);
  return {
    destroy: close,
    update
  };
};
function withWarn(props) {
  return _extends(_extends({}, props), {
    type: "warning"
  });
}
function withInfo(props) {
  return _extends(_extends({}, props), {
    type: "info"
  });
}
function withSuccess(props) {
  return _extends(_extends({}, props), {
    type: "success"
  });
}
function withError(props) {
  return _extends(_extends({}, props), {
    type: "error"
  });
}
function withConfirm(props) {
  return _extends(_extends({}, props), {
    type: "confirm"
  });
}
const comfirmFuncProps = () => ({
  config: Object,
  afterClose: Function,
  destroyAction: Function,
  open: Boolean
});
const HookModal = defineComponent({
  name: "HookModal",
  inheritAttrs: false,
  props: initDefaultProps(comfirmFuncProps(), {
    config: {
      width: 520,
      okType: "primary"
    }
  }),
  setup(props, _ref) {
    let {
      expose
    } = _ref;
    var _a;
    const open = computed(() => props.open);
    const innerConfig = computed(() => props.config);
    const {
      direction,
      getPrefixCls
    } = useConfigContextInject();
    const prefixCls = getPrefixCls("modal");
    const rootPrefixCls = getPrefixCls();
    const afterClose = () => {
      var _a2, _b;
      props === null || props === void 0 ? void 0 : props.afterClose();
      (_b = (_a2 = innerConfig.value).afterClose) === null || _b === void 0 ? void 0 : _b.call(_a2);
    };
    const close = function() {
      props.destroyAction(...arguments);
    };
    expose({
      destroy: close
    });
    const mergedOkCancel = (_a = innerConfig.value.okCancel) !== null && _a !== void 0 ? _a : innerConfig.value.type === "confirm";
    const [contextLocale] = useLocaleReceiver("Modal", localeValues.Modal);
    return () => createVNode(ConfirmDialog, _objectSpread2(_objectSpread2({
      "prefixCls": prefixCls,
      "rootPrefixCls": rootPrefixCls
    }, innerConfig.value), {}, {
      "close": close,
      "open": open.value,
      "afterClose": afterClose,
      "okText": innerConfig.value.okText || (mergedOkCancel ? contextLocale === null || contextLocale === void 0 ? void 0 : contextLocale.value.okText : contextLocale === null || contextLocale === void 0 ? void 0 : contextLocale.value.justOkText),
      "direction": innerConfig.value.direction || direction.value,
      "cancelText": innerConfig.value.cancelText || (contextLocale === null || contextLocale === void 0 ? void 0 : contextLocale.value.cancelText)
    }), null);
  }
});
let uuid = 0;
const ElementsHolder = defineComponent({
  name: "ElementsHolder",
  inheritAttrs: false,
  setup(_, _ref) {
    let {
      expose
    } = _ref;
    const modals = shallowRef([]);
    const addModal = (modal) => {
      modals.value.push(modal);
      modals.value = modals.value.slice();
      return () => {
        modals.value = modals.value.filter((currentModal) => currentModal !== modal);
      };
    };
    expose({
      addModal
    });
    return () => {
      return modals.value.map((modal) => modal());
    };
  }
});
function useModal() {
  const holderRef = shallowRef(null);
  const actionQueue = shallowRef([]);
  watch(actionQueue, () => {
    if (actionQueue.value.length) {
      const cloneQueue = [...actionQueue.value];
      cloneQueue.forEach((action) => {
        action();
      });
      actionQueue.value = [];
    }
  }, {
    immediate: true
  });
  const getConfirmFunc = (withFunc) => function hookConfirm(config) {
    var _a;
    uuid += 1;
    const open = shallowRef(true);
    const modalRef = shallowRef(null);
    const configRef = shallowRef(unref(config));
    const updateConfig = shallowRef({});
    watch(() => config, (val) => {
      updateAction(_extends(_extends({}, isRef(val) ? val.value : val), updateConfig.value));
    });
    const destroyAction = function() {
      open.value = false;
      for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
        args[_key] = arguments[_key];
      }
      const triggerCancel = args.some((param) => param && param.triggerCancel);
      if (configRef.value.onCancel && triggerCancel) {
        configRef.value.onCancel(() => {
        }, ...args.slice(1));
      }
    };
    let closeFunc;
    const modal = () => createVNode(HookModal, {
      "key": `modal-${uuid}`,
      "config": withFunc(configRef.value),
      "ref": modalRef,
      "open": open.value,
      "destroyAction": destroyAction,
      "afterClose": () => {
        closeFunc === null || closeFunc === void 0 ? void 0 : closeFunc();
      }
    }, null);
    closeFunc = (_a = holderRef.value) === null || _a === void 0 ? void 0 : _a.addModal(modal);
    if (closeFunc) {
      destroyFns.push(closeFunc);
    }
    const updateAction = (newConfig) => {
      configRef.value = _extends(_extends({}, configRef.value), newConfig);
    };
    const destroy = () => {
      if (modalRef.value) {
        destroyAction();
      } else {
        actionQueue.value = [...actionQueue.value, destroyAction];
      }
    };
    const update = (newConfig) => {
      updateConfig.value = newConfig;
      if (modalRef.value) {
        updateAction(newConfig);
      } else {
        actionQueue.value = [...actionQueue.value, () => updateAction(newConfig)];
      }
    };
    return {
      destroy,
      update
    };
  };
  const fns = computed(() => ({
    info: getConfirmFunc(withInfo),
    success: getConfirmFunc(withSuccess),
    error: getConfirmFunc(withError),
    warning: getConfirmFunc(withWarn),
    confirm: getConfirmFunc(withConfirm)
  }));
  const holderKey = Symbol("modalHolderKey");
  return [fns.value, () => createVNode(ElementsHolder, {
    "key": holderKey,
    "ref": holderRef
  }, null)];
}
function modalWarn(props) {
  return confirm(withWarn(props));
}
Modal.useModal = useModal;
Modal.info = function infoFn(props) {
  return confirm(withInfo(props));
};
Modal.success = function successFn(props) {
  return confirm(withSuccess(props));
};
Modal.error = function errorFn(props) {
  return confirm(withError(props));
};
Modal.warning = modalWarn;
Modal.warn = modalWarn;
Modal.confirm = function confirmFn(props) {
  return confirm(withConfirm(props));
};
Modal.destroyAll = function destroyAllFn() {
  while (destroyFns.length) {
    const close = destroyFns.pop();
    if (close) {
      close();
    }
  }
};
Modal.install = function(app) {
  app.component(Modal.name, Modal);
  return app;
};
export {
  Modal as default
};
//# sourceMappingURL=index-BJEL12xA.js.map
