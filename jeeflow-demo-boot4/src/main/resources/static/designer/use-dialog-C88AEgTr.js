import { U as UPDATE_MODEL_EVENT } from "./event-BZTOGHfp.js";
import { b as buildProps, f as isBoolean, d as definePropType, N as NOOP, u as useNamespace, t as throwError, l as isObject, g as debugWarn, i as isArray, h as isFunction, x as useTimeoutFn, j as isClient } from "./error-DiL_p-4A.js";
import { i as iconPropType } from "./index-D5MsDfVU.js";
import { defineComponent, createVNode, renderSlot, h, isRef, computed, watch, onScopeDispose, getCurrentInstance, ref, nextTick, onMounted } from "vue";
import { h as hasClass, d as addClass, g as getStyle, r as removeClass, a as addUnit } from "./style-_T5b_00u.js";
import { g as getScrollBarWidth } from "./scroll-BL4Qux_Z.js";
import { u as useId } from "./index-DuyY_KOz.js";
import { u as useZIndex } from "./index-BHCMzsem.js";
import { u as useGlobalConfig } from "./use-global-config-CfKUtA2M.js";
const dialogContentProps = buildProps({
  /**
  * @description whether to align the header and footer in center
  */
  center: Boolean,
  /**
  * @description whether to align the dialog both horizontally and vertically
  */
  alignCenter: {
    type: Boolean,
    default: void 0
  },
  /**
  * @description custom close icon, default is Close
  */
  closeIcon: { type: iconPropType },
  /**
  * @description enable dragging feature for Dialog
  */
  draggable: {
    type: Boolean,
    default: void 0
  },
  /**
  * @description draggable Dialog can overflow the viewport
  */
  overflow: {
    type: Boolean,
    default: void 0
  },
  /**
  * @description whether the Dialog takes up full screen
  */
  fullscreen: Boolean,
  /**
  * @description custom class names for header wrapper
  */
  headerClass: String,
  /**
  * @description custom class names for body wrapper
  */
  bodyClass: String,
  /**
  * @description custom class names for footer wrapper
  */
  footerClass: String,
  /**
  * @description whether to show a close button
  */
  showClose: {
    type: Boolean,
    default: true
  },
  /**
  * @description title of Dialog. Can also be passed with a named slot (see the following table)
  */
  title: {
    type: String,
    default: ""
  },
  /**
  * @description header's aria-level attribute
  */
  ariaLevel: {
    type: String,
    default: "2"
  }
});
const dialogContentEmits = { close: () => true };
const dialogProps = buildProps({
  ...dialogContentProps,
  /**
  * @description whether to append Dialog itself to body. A nested Dialog should have this attribute set to `true`
  */
  appendToBody: Boolean,
  /**
  * @description which element the Dialog appends to
  */
  appendTo: {
    type: definePropType([String, Object]),
    default: "body"
  },
  /**
  * @description callback before Dialog closes, and it will prevent Dialog from closing, use done to close the dialog
  */
  beforeClose: { type: definePropType(Function) },
  /**
  * @description destroy elements in Dialog when closed
  */
  destroyOnClose: Boolean,
  /**
  * @description whether the Dialog can be closed by clicking the mask
  */
  closeOnClickModal: {
    type: Boolean,
    default: true
  },
  /**
  * @description whether the Dialog can be closed by pressing ESC
  */
  closeOnPressEscape: {
    type: Boolean,
    default: true
  },
  /**
  * @description whether scroll of body is disabled while Dialog is displayed
  */
  lockScroll: {
    type: Boolean,
    default: true
  },
  /**
  * @description whether a mask is displayed
  */
  modal: {
    type: Boolean,
    default: true
  },
  /**
  * @description whether the mask is penetrable
  */
  modalPenetrable: Boolean,
  /**
  * @description the Time(milliseconds) before open
  */
  openDelay: {
    type: Number,
    default: 0
  },
  /**
  * @description the Time(milliseconds) before close
  */
  closeDelay: {
    type: Number,
    default: 0
  },
  /**
  * @description value for `margin-top` of Dialog CSS, default is 15vh
  */
  top: { type: String },
  /**
  * @description visibility of Dialog
  */
  modelValue: Boolean,
  /**
  * @description custom class names for mask
  */
  modalClass: String,
  /**
  * @description custom class names for header wrapper
  */
  headerClass: String,
  /**
  * @description custom class names for body wrapper
  */
  bodyClass: String,
  /**
  * @description custom class names for footer wrapper
  */
  footerClass: String,
  /**
  * @description width of Dialog, default is 50%
  */
  width: { type: [String, Number] },
  /**
  * @description same as z-index in native CSS, z-order of dialog
  */
  zIndex: { type: Number },
  trapFocus: Boolean,
  /**
  * @description header's aria-level attribute
  */
  headerAriaLevel: {
    type: String,
    default: "2"
  },
  /**
  * @description custom transition configuration for dialog animation, it can be a string (transition name) or an object with Vue transition props
  */
  transition: {
    type: definePropType([String, Object]),
    default: void 0
  }
});
const dialogEmits = {
  open: () => true,
  opened: () => true,
  close: () => true,
  closed: () => true,
  [UPDATE_MODEL_EVENT]: (value) => isBoolean(value),
  openAutoFocus: () => true,
  closeAutoFocus: () => true
};
const useSameTarget = (handleClick) => {
  if (!handleClick) return {
    onClick: NOOP,
    onMousedown: NOOP,
    onMouseup: NOOP
  };
  let mousedownTarget = false;
  let mouseupTarget = false;
  const onClick = (e) => {
    if (mousedownTarget && mouseupTarget) handleClick(e);
    mousedownTarget = mouseupTarget = false;
  };
  const onMousedown = (e) => {
    mousedownTarget = e.target === e.currentTarget;
  };
  const onMouseup = (e) => {
    mouseupTarget = e.target === e.currentTarget;
  };
  return {
    onClick,
    onMousedown,
    onMouseup
  };
};
const overlayProps = buildProps({
  mask: {
    type: Boolean,
    default: true
  },
  customMaskEvent: Boolean,
  overlayClass: { type: definePropType([
    String,
    Array,
    Object
  ]) },
  zIndex: { type: definePropType([String, Number]) }
});
const overlayEmits = { click: (evt) => evt instanceof MouseEvent };
const BLOCK = "overlay";
var overlay_default = defineComponent({
  name: "ElOverlay",
  props: overlayProps,
  emits: overlayEmits,
  setup(props, { slots, emit }) {
    const ns = useNamespace(BLOCK);
    const onMaskClick = (e) => {
      emit("click", e);
    };
    const { onClick, onMousedown, onMouseup } = useSameTarget(props.customMaskEvent ? void 0 : onMaskClick);
    return () => {
      return props.mask ? createVNode("div", {
        class: [ns.b(), props.overlayClass],
        style: { zIndex: props.zIndex },
        onClick,
        onMousedown,
        onMouseup
      }, [renderSlot(slots, "default")], 14, [
        "onClick",
        "onMouseup",
        "onMousedown"
      ]) : h("div", {
        class: props.overlayClass,
        style: {
          zIndex: props.zIndex,
          position: "fixed",
          top: "0px",
          right: "0px",
          bottom: "0px",
          left: "0px"
        }
      }, [renderSlot(slots, "default")]);
    };
  }
});
const ElOverlay = overlay_default;
const useLockscreen = (trigger, options = {}) => {
  if (!isRef(trigger)) throwError("[useLockscreen]", "You need to pass a ref param to this function");
  const ns = options.ns || useNamespace("popup");
  const hiddenCls = computed(() => ns.bm("parent", "hidden"));
  let scrollBarWidth = 0;
  let withoutHiddenClass = false;
  let bodyWidth = "0";
  let cleaned = false;
  const cleanup = () => {
    if (cleaned) return;
    cleaned = true;
    setTimeout(() => {
      if (typeof document === "undefined") return;
      if (withoutHiddenClass && document) {
        document.body.style.width = bodyWidth;
        removeClass(document.body, hiddenCls.value);
      }
    }, 200);
  };
  watch(trigger, (val) => {
    if (!val) {
      cleanup();
      return;
    }
    cleaned = false;
    withoutHiddenClass = !hasClass(document.body, hiddenCls.value);
    if (withoutHiddenClass) {
      bodyWidth = document.body.style.width;
      addClass(document.body, hiddenCls.value);
    }
    scrollBarWidth = getScrollBarWidth(ns.namespace.value);
    const bodyHasOverflow = document.documentElement.clientHeight < document.body.scrollHeight;
    const bodyOverflowY = getStyle(document.body, "overflowY");
    if (scrollBarWidth > 0 && (bodyHasOverflow || bodyOverflowY === "scroll") && withoutHiddenClass) document.body.style.width = `calc(100% - ${scrollBarWidth}px)`;
  });
  onScopeDispose(() => cleanup());
};
const dialogInjectionKey = Symbol("dialogInjectionKey");
const DEFAULT_DIALOG_TRANSITION = "dialog-fade";
const COMPONENT_NAME = "ElDialog";
const useDialog = (props, targetRef) => {
  const emit = getCurrentInstance().emit;
  const { nextZIndex } = useZIndex();
  let lastPosition = "";
  const titleId = useId();
  const bodyId = useId();
  const visible = ref(false);
  const closed = ref(false);
  const rendered = ref(false);
  const zIndex = ref(props.zIndex ?? nextZIndex());
  const closing = ref(false);
  let openTimer = void 0;
  let closeTimer = void 0;
  const config = useGlobalConfig();
  const namespace = computed(() => {
    var _a;
    return ((_a = config.value) == null ? void 0 : _a.namespace) ?? "el";
  });
  const globalConfig = computed(() => {
    var _a;
    return (_a = config.value) == null ? void 0 : _a.dialog;
  });
  const style = computed(() => {
    const style2 = {};
    const varPrefix = `--${namespace.value}-dialog`;
    if (!props.fullscreen) {
      if (props.top) style2[`${varPrefix}-margin-top`] = props.top;
      const width = addUnit(props.width);
      if (width) style2[`${varPrefix}-width`] = width;
    }
    return style2;
  });
  const _draggable = computed(() => {
    var _a;
    return (props.draggable ?? ((_a = globalConfig.value) == null ? void 0 : _a.draggable) ?? false) && !props.fullscreen;
  });
  const _alignCenter = computed(() => {
    var _a;
    return props.alignCenter ?? ((_a = globalConfig.value) == null ? void 0 : _a.alignCenter) ?? false;
  });
  const _overflow = computed(() => {
    var _a;
    return props.overflow ?? ((_a = globalConfig.value) == null ? void 0 : _a.overflow) ?? false;
  });
  const penetrable = computed(() => props.modalPenetrable && !props.modal && !props.fullscreen);
  const overlayDialogStyle = computed(() => {
    if (_alignCenter.value) return { display: "flex" };
    return {};
  });
  const transitionConfig = computed(() => {
    var _a;
    const transition = props.transition ?? ((_a = globalConfig.value) == null ? void 0 : _a.transition) ?? "dialog-fade";
    const baseConfig = {
      name: transition,
      onAfterEnter: afterEnter,
      onBeforeLeave: beforeLeave,
      onAfterLeave: afterLeave
    };
    if (isObject(transition)) {
      const config2 = { ...transition };
      const _mergeHook = (userHook, defaultHook) => {
        return (el) => {
          if (isArray(userHook)) userHook.forEach((fn) => {
            if (isFunction(fn)) fn(el);
          });
          else if (isFunction(userHook)) userHook(el);
          defaultHook();
        };
      };
      config2.onAfterEnter = _mergeHook(config2.onAfterEnter, afterEnter);
      config2.onBeforeLeave = _mergeHook(config2.onBeforeLeave, beforeLeave);
      config2.onAfterLeave = _mergeHook(config2.onAfterLeave, afterLeave);
      if (!config2.name) {
        config2.name = DEFAULT_DIALOG_TRANSITION;
        debugWarn(COMPONENT_NAME, `transition.name is missing when using object syntax, fallback to '${DEFAULT_DIALOG_TRANSITION}'`);
      }
      return config2;
    }
    return baseConfig;
  });
  function afterEnter() {
    emit("opened");
  }
  function afterLeave() {
    emit("closed");
    emit(UPDATE_MODEL_EVENT, false);
    if (props.destroyOnClose) rendered.value = false;
    closing.value = false;
  }
  function beforeLeave() {
    closing.value = true;
    emit("close");
  }
  function open() {
    closeTimer == null ? void 0 : closeTimer();
    openTimer == null ? void 0 : openTimer();
    if (props.openDelay && props.openDelay > 0) ({ stop: openTimer } = useTimeoutFn(() => doOpen(), props.openDelay));
    else doOpen();
  }
  function close() {
    openTimer == null ? void 0 : openTimer();
    closeTimer == null ? void 0 : closeTimer();
    if (props.closeDelay && props.closeDelay > 0) ({ stop: closeTimer } = useTimeoutFn(() => doClose(), props.closeDelay));
    else doClose();
  }
  function handleClose() {
    function hide(shouldCancel) {
      if (shouldCancel) return;
      closed.value = true;
      visible.value = false;
    }
    if (props.beforeClose) props.beforeClose(hide);
    else close();
  }
  function onModalClick() {
    if (props.closeOnClickModal) handleClose();
  }
  function doOpen() {
    if (!isClient) return;
    visible.value = true;
  }
  function doClose() {
    visible.value = false;
  }
  function onOpenAutoFocus() {
    emit("openAutoFocus");
  }
  function onCloseAutoFocus() {
    emit("closeAutoFocus");
  }
  function onFocusoutPrevented(event) {
    var _a;
    if (((_a = event.detail) == null ? void 0 : _a.focusReason) === "pointer") event.preventDefault();
  }
  if (props.lockScroll) useLockscreen(visible);
  function onCloseRequested() {
    if (props.closeOnPressEscape) handleClose();
  }
  function bringToFront() {
    if (!visible.value || !penetrable.value || props.zIndex !== void 0) return;
    zIndex.value = nextZIndex();
  }
  watch(() => props.zIndex, () => {
    zIndex.value = props.zIndex ?? nextZIndex();
  });
  watch(() => props.modelValue, (val) => {
    if (val) {
      closed.value = false;
      closing.value = false;
      open();
      rendered.value = true;
      zIndex.value = props.zIndex ?? nextZIndex();
      nextTick(() => {
        emit("open");
        if (targetRef.value) {
          targetRef.value.parentElement.scrollTop = 0;
          targetRef.value.parentElement.scrollLeft = 0;
          targetRef.value.scrollTop = 0;
        }
      });
    } else if (visible.value) close();
  });
  watch(() => props.fullscreen, (val) => {
    if (!targetRef.value) return;
    if (val) {
      lastPosition = targetRef.value.style.transform;
      targetRef.value.style.transform = "";
    } else targetRef.value.style.transform = lastPosition;
  });
  onMounted(() => {
    if (props.modelValue) {
      visible.value = true;
      rendered.value = true;
      open();
    }
  });
  return {
    afterEnter,
    afterLeave,
    beforeLeave,
    handleClose,
    onModalClick,
    close,
    doClose,
    onOpenAutoFocus,
    onCloseAutoFocus,
    onCloseRequested,
    onFocusoutPrevented,
    bringToFront,
    titleId,
    bodyId,
    closed,
    style,
    overlayDialogStyle,
    rendered,
    visible,
    zIndex,
    transitionConfig,
    _draggable,
    _alignCenter,
    _overflow,
    closing,
    penetrable
  };
};
export {
  DEFAULT_DIALOG_TRANSITION as D,
  ElOverlay as E,
  dialogProps as a,
  dialogContentEmits as b,
  dialogContentProps as c,
  dialogEmits as d,
  dialogInjectionKey as e,
  useSameTarget as f,
  useDialog as u
};
//# sourceMappingURL=use-dialog-C88AEgTr.js.map
