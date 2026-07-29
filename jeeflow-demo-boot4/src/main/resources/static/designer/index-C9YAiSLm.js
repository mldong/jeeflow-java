import { b as buildProps, c as clamp, u as useNamespace, w as withInstall } from "./error-DiL_p-4A.js";
import { d as dialogEmits, a as dialogProps, u as useDialog, E as ElOverlay } from "./use-dialog-C88AEgTr.js";
import { u as useDeprecated } from "./index-ChyTK8JP.js";
import { u as useLocale } from "./index-Dd4aCaME.js";
import { E as ElIcon, c as close_default } from "./index-D5MsDfVU.js";
import { f as focus_trap_default$1 } from "./index-Blbk90Jx.js";
import { a as addUnit } from "./style-_T5b_00u.js";
import { u as useEventListener, a as useWindowSize } from "./index-BoUuUOkb.js";
import { computed, ref, watch, onBeforeUnmount, defineComponent, useSlots, openBlock, createBlock, Teleport, createVNode, Transition, unref, withCtx, withDirectives, createElementVNode, mergeProps, withModifiers, normalizeClass, createElementBlock, renderSlot, toDisplayString, createCommentVNode, normalizeStyle, vShow } from "vue";
const drawerProps = buildProps({
  ...dialogProps,
  direction: {
    type: String,
    default: "rtl",
    values: [
      "ltr",
      "rtl",
      "ttb",
      "btt"
    ]
  },
  resizable: Boolean,
  size: {
    type: [String, Number],
    default: "30%"
  },
  withHeader: {
    type: Boolean,
    default: true
  },
  modalFade: {
    type: Boolean,
    default: true
  },
  headerAriaLevel: {
    type: String,
    default: "2"
  }
});
const drawerEmits = {
  ...dialogEmits,
  "resize-start": (evt, size) => evt instanceof MouseEvent && typeof size === "number",
  resize: (evt, size) => evt instanceof MouseEvent && typeof size === "number",
  "resize-end": (evt, size) => evt instanceof MouseEvent && typeof size === "number"
};
function useResizable(props, target, emit) {
  const { width, height } = useWindowSize();
  const isHorizontal = computed(() => ["ltr", "rtl"].includes(props.direction));
  const sign = computed(() => ["ltr", "ttb"].includes(props.direction) ? 1 : -1);
  const windowSize = computed(() => isHorizontal.value ? width.value : height.value);
  const getSize = computed(() => {
    return clamp(startSize.value + sign.value * offset.value, 4, windowSize.value);
  });
  const startSize = ref(0);
  const offset = ref(0);
  const isResizing = ref(false);
  const hasStartedDragging = ref(false);
  let startPos = [];
  let cleanups = [];
  const getActualSize = () => {
    var _a;
    const drawerEl = (_a = target.value) == null ? void 0 : _a.closest('[aria-modal="true"]');
    if (drawerEl) return isHorizontal.value ? drawerEl.offsetWidth : drawerEl.offsetHeight;
    return 100;
  };
  watch(() => [props.size, props.resizable], () => {
    hasStartedDragging.value = false;
    startSize.value = 0;
    offset.value = 0;
    onMouseUp();
  });
  const onMousedown = (e) => {
    if (!props.resizable) return;
    if (!hasStartedDragging.value) {
      startSize.value = getActualSize();
      hasStartedDragging.value = true;
    }
    startPos = [e.pageX, e.pageY];
    isResizing.value = true;
    emit("resize-start", e, startSize.value);
    cleanups.push(useEventListener(window, "mouseup", onMouseUp), useEventListener(window, "mousemove", onMouseMove));
  };
  const onMouseMove = (e) => {
    const { pageX, pageY } = e;
    const offsetX = pageX - startPos[0];
    const offsetY = pageY - startPos[1];
    offset.value = isHorizontal.value ? offsetX : offsetY;
    emit("resize", e, getSize.value);
  };
  const onMouseUp = (e) => {
    if (!isResizing.value) return;
    startPos = [];
    startSize.value = getSize.value;
    offset.value = 0;
    isResizing.value = false;
    cleanups.forEach((cleanup2) => cleanup2 == null ? void 0 : cleanup2());
    cleanups = [];
    if (e) emit("resize-end", e, startSize.value);
  };
  const cleanup = useEventListener(target, "mousedown", onMousedown);
  onBeforeUnmount(() => {
    cleanup();
    onMouseUp();
  });
  return {
    size: computed(() => {
      return hasStartedDragging.value ? `${getSize.value}px` : addUnit(props.size);
    }),
    isResizing,
    isHorizontal
  };
}
const _hoisted_1 = [
  "aria-label",
  "aria-labelledby",
  "aria-describedby"
];
const _hoisted_2 = ["id", "aria-level"];
const _hoisted_3 = ["aria-label"];
const _hoisted_4 = ["id"];
var drawer_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: "ElDrawer",
  inheritAttrs: false,
  __name: "drawer",
  props: drawerProps,
  emits: drawerEmits,
  setup(__props, { expose: __expose, emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const slots = useSlots();
    useDeprecated({
      scope: "el-drawer",
      from: "the title slot",
      replacement: "the header slot",
      version: "3.0.0",
      ref: "https://element-plus.org/en-US/component/drawer.html#slots"
    }, computed(() => !!slots.title));
    const drawerRef = ref();
    const focusStartRef = ref();
    const draggerRef = ref();
    const ns = useNamespace("drawer");
    const { t } = useLocale();
    const { afterEnter, afterLeave, beforeLeave, visible, rendered, titleId, bodyId, zIndex, onModalClick, onOpenAutoFocus, onCloseAutoFocus, onFocusoutPrevented, onCloseRequested, handleClose } = useDialog(props, drawerRef);
    const { isHorizontal, size, isResizing } = useResizable(props, draggerRef, emit);
    const penetrable = computed(() => props.modalPenetrable && !props.modal);
    __expose({ handleClose });
    return (_ctx, _cache) => {
      return openBlock(), createBlock(Teleport, {
        to: __props.appendTo,
        disabled: __props.appendTo !== "body" ? false : !__props.appendToBody
      }, [createVNode(Transition, {
        name: unref(ns).b("fade"),
        onAfterEnter: unref(afterEnter),
        onAfterLeave: unref(afterLeave),
        onBeforeLeave: unref(beforeLeave),
        persisted: ""
      }, {
        default: withCtx(() => [withDirectives(createVNode(unref(ElOverlay), {
          mask: __props.modal,
          "overlay-class": [
            unref(ns).is("drawer"),
            __props.modalClass ?? "",
            `${unref(ns).namespace.value}-modal-drawer`,
            unref(ns).is("penetrable", penetrable.value)
          ],
          "z-index": unref(zIndex),
          onClick: unref(onModalClick)
        }, {
          default: withCtx(() => [createVNode(unref(focus_trap_default$1), {
            loop: "",
            trapped: unref(visible),
            "focus-trap-el": drawerRef.value,
            "focus-start-el": focusStartRef.value,
            onFocusAfterTrapped: unref(onOpenAutoFocus),
            onFocusAfterReleased: unref(onCloseAutoFocus),
            onFocusoutPrevented: unref(onFocusoutPrevented),
            onReleaseRequested: unref(onCloseRequested)
          }, {
            default: withCtx(() => [createElementVNode("div", mergeProps({
              ref_key: "drawerRef",
              ref: drawerRef,
              "aria-modal": "true",
              "aria-label": __props.title || void 0,
              "aria-labelledby": !__props.title ? unref(titleId) : void 0,
              "aria-describedby": unref(bodyId)
            }, _ctx.$attrs, {
              class: [
                unref(ns).b(),
                __props.direction,
                unref(visible) && "open",
                unref(ns).is("dragging", unref(isResizing))
              ],
              style: { [unref(isHorizontal) ? "width" : "height"]: unref(size) },
              role: "dialog",
              onClick: _cache[1] || (_cache[1] = withModifiers(() => {
              }, ["stop"]))
            }), [
              createElementVNode("span", {
                ref_key: "focusStartRef",
                ref: focusStartRef,
                class: normalizeClass(unref(ns).e("sr-focus")),
                tabindex: "-1"
              }, null, 2),
              __props.withHeader ? (openBlock(), createElementBlock("header", {
                key: 0,
                class: normalizeClass([unref(ns).e("header"), __props.headerClass])
              }, [!_ctx.$slots.title ? renderSlot(_ctx.$slots, "header", {
                key: 0,
                close: unref(handleClose),
                titleId: unref(titleId),
                titleClass: unref(ns).e("title")
              }, () => [createElementVNode("span", {
                id: unref(titleId),
                role: "heading",
                "aria-level": __props.headerAriaLevel,
                class: normalizeClass(unref(ns).e("title"))
              }, toDisplayString(__props.title), 11, _hoisted_2)]) : renderSlot(_ctx.$slots, "title", { key: 1 }, () => [createCommentVNode(" DEPRECATED SLOT ")]), __props.showClose ? (openBlock(), createElementBlock("button", {
                key: 2,
                "aria-label": unref(t)("el.drawer.close"),
                class: normalizeClass(unref(ns).e("close-btn")),
                type: "button",
                onClick: _cache[0] || (_cache[0] = (...args) => unref(handleClose) && unref(handleClose)(...args))
              }, [createVNode(unref(ElIcon), { class: normalizeClass(unref(ns).e("close")) }, {
                default: withCtx(() => [createVNode(unref(close_default))]),
                _: 1
              }, 8, ["class"])], 10, _hoisted_3)) : createCommentVNode("v-if", true)], 2)) : createCommentVNode("v-if", true),
              unref(rendered) ? (openBlock(), createElementBlock("div", {
                key: 1,
                id: unref(bodyId),
                class: normalizeClass([unref(ns).e("body"), __props.bodyClass])
              }, [renderSlot(_ctx.$slots, "default")], 10, _hoisted_4)) : createCommentVNode("v-if", true),
              _ctx.$slots.footer ? (openBlock(), createElementBlock("div", {
                key: 2,
                class: normalizeClass([unref(ns).e("footer"), __props.footerClass])
              }, [renderSlot(_ctx.$slots, "footer")], 2)) : createCommentVNode("v-if", true),
              __props.resizable ? (openBlock(), createElementBlock("div", {
                key: 3,
                ref_key: "draggerRef",
                ref: draggerRef,
                style: normalizeStyle({ zIndex: unref(zIndex) }),
                class: normalizeClass(unref(ns).e("dragger"))
              }, null, 6)) : createCommentVNode("v-if", true)
            ], 16, _hoisted_1)]),
            _: 3
          }, 8, [
            "trapped",
            "focus-trap-el",
            "focus-start-el",
            "onFocusAfterTrapped",
            "onFocusAfterReleased",
            "onFocusoutPrevented",
            "onReleaseRequested"
          ])]),
          _: 3
        }, 8, [
          "mask",
          "overlay-class",
          "z-index",
          "onClick"
        ]), [[vShow, unref(visible)]])]),
        _: 3
      }, 8, [
        "name",
        "onAfterEnter",
        "onAfterLeave",
        "onBeforeLeave"
      ])], 8, ["to", "disabled"]);
    };
  }
});
var drawer_default = drawer_vue_vue_type_script_setup_true_lang_default;
const ElDrawer = withInstall(drawer_default);
export {
  ElDrawer,
  ElDrawer as default,
  drawerEmits,
  drawerProps
};
//# sourceMappingURL=index-C9YAiSLm.js.map
