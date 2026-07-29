import { u as useNamespace, w as withInstall } from "./error-DiL_p-4A.js";
import { b as dialogContentEmits, c as dialogContentProps, e as dialogInjectionKey, d as dialogEmits, a as dialogProps, u as useDialog, E as ElOverlay, f as useSameTarget } from "./use-dialog-C88AEgTr.js";
import { D } from "./use-dialog-C88AEgTr.js";
import { u as useDeprecated } from "./index-ChyTK8JP.js";
import { F as FOCUS_TRAP_INJECTION_KEY, f as focus_trap_default$1 } from "./index-Blbk90Jx.js";
import { E as ElIcon, C as CloseComponents } from "./index-D5MsDfVU.js";
import { a as addUnit } from "./style-_T5b_00u.js";
import { ref, onMounted, watchEffect, onBeforeUnmount, defineComponent, inject, computed, openBlock, createElementBlock, normalizeStyle, unref, normalizeClass, createElementVNode, renderSlot, toDisplayString, createVNode, withCtx, createBlock, resolveDynamicComponent, createCommentVNode, useSlots, provide, Teleport, Transition, mergeProps, withDirectives, createSlots, vShow } from "vue";
import { k as clamp } from "./index-D9cY2WMP.js";
import { u as useLocale } from "./index-Dd4aCaME.js";
const composeRefs = (...refs) => {
  return (el) => {
    refs.forEach((ref2) => {
      ref2.value = el;
    });
  };
};
const useDraggable = (targetRef, dragRef, draggable, overflow) => {
  const transform = {
    offsetX: 0,
    offsetY: 0
  };
  const isDragging = ref(false);
  const adjustPosition = (moveX, moveY) => {
    if (targetRef.value) {
      const { offsetX, offsetY } = transform;
      const targetRect = targetRef.value.getBoundingClientRect();
      const targetLeft = Math.max(targetRect.left, 0);
      const targetTop = Math.max(targetRect.top, 0);
      const targetWidth = targetRect.width;
      const targetHeight = targetRect.height;
      const clientWidth = document.documentElement.clientWidth;
      const clientHeight = document.documentElement.clientHeight;
      const minLeft = -targetLeft + offsetX;
      const minTop = -targetTop + offsetY;
      const maxLeft = clientWidth - targetLeft - targetWidth + offsetX;
      const maxTop = clientHeight - targetTop - (targetHeight < clientHeight ? targetHeight : 0) + offsetY;
      if (!(overflow == null ? void 0 : overflow.value)) {
        moveX = clamp(moveX, minLeft, maxLeft);
        moveY = clamp(moveY, minTop, maxTop);
      }
      transform.offsetX = moveX;
      transform.offsetY = moveY;
      targetRef.value.style.transform = `translate(${addUnit(moveX)}, ${addUnit(moveY)})`;
    }
  };
  const onMousedown = (e) => {
    const downX = e.clientX;
    const downY = e.clientY;
    const { offsetX, offsetY } = transform;
    const onMousemove = (e2) => {
      if (!isDragging.value) isDragging.value = true;
      adjustPosition(offsetX + e2.clientX - downX, offsetY + e2.clientY - downY);
    };
    const onMouseup = () => {
      isDragging.value = false;
      document.removeEventListener("mousemove", onMousemove);
      document.removeEventListener("mouseup", onMouseup);
    };
    document.addEventListener("mousemove", onMousemove);
    document.addEventListener("mouseup", onMouseup);
  };
  const onDraggable = () => {
    if (dragRef.value && targetRef.value) {
      dragRef.value.addEventListener("mousedown", onMousedown);
      window.addEventListener("resize", updatePosition);
    }
  };
  const offDraggable = () => {
    if (dragRef.value && targetRef.value) {
      dragRef.value.removeEventListener("mousedown", onMousedown);
      window.removeEventListener("resize", updatePosition);
    }
  };
  const resetPosition = () => {
    transform.offsetX = 0;
    transform.offsetY = 0;
    if (targetRef.value) targetRef.value.style.transform = "";
  };
  const updatePosition = () => {
    const { offsetX, offsetY } = transform;
    adjustPosition(offsetX, offsetY);
  };
  onMounted(() => {
    watchEffect(() => {
      if (draggable.value) onDraggable();
      else offDraggable();
    });
  });
  onBeforeUnmount(() => {
    offDraggable();
  });
  return {
    isDragging,
    resetPosition,
    updatePosition
  };
};
const _hoisted_1$1 = ["aria-level"];
const _hoisted_2 = ["aria-label"];
const _hoisted_3 = ["id"];
var dialog_content_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: "ElDialogContent",
  __name: "dialog-content",
  props: dialogContentProps,
  emits: dialogContentEmits,
  setup(__props, { expose: __expose }) {
    const { t } = useLocale();
    const { Close } = CloseComponents;
    const props = __props;
    const { dialogRef, headerRef, bodyId, ns, style } = inject(dialogInjectionKey);
    const { focusTrapRef } = inject(FOCUS_TRAP_INJECTION_KEY);
    const composedDialogRef = composeRefs(focusTrapRef, dialogRef);
    const draggable = computed(() => !!props.draggable);
    const { resetPosition, updatePosition, isDragging } = useDraggable(dialogRef, headerRef, draggable, computed(() => !!props.overflow));
    const dialogKls = computed(() => [
      ns.b(),
      ns.is("fullscreen", props.fullscreen),
      ns.is("draggable", draggable.value),
      ns.is("dragging", isDragging.value),
      ns.is("align-center", !!props.alignCenter),
      { [ns.m("center")]: props.center }
    ]);
    __expose({
      resetPosition,
      updatePosition
    });
    return (_ctx, _cache) => {
      return openBlock(), createElementBlock("div", {
        ref: unref(composedDialogRef),
        class: normalizeClass(dialogKls.value),
        style: normalizeStyle(unref(style)),
        tabindex: "-1"
      }, [
        createElementVNode("header", {
          ref_key: "headerRef",
          ref: headerRef,
          class: normalizeClass([
            unref(ns).e("header"),
            __props.headerClass,
            { "show-close": __props.showClose }
          ])
        }, [renderSlot(_ctx.$slots, "header", {}, () => [createElementVNode("span", {
          role: "heading",
          "aria-level": __props.ariaLevel,
          class: normalizeClass(unref(ns).e("title"))
        }, toDisplayString(__props.title), 11, _hoisted_1$1)]), __props.showClose ? (openBlock(), createElementBlock("button", {
          key: 0,
          "aria-label": unref(t)("el.dialog.close"),
          class: normalizeClass(unref(ns).e("headerbtn")),
          type: "button",
          onClick: _cache[0] || (_cache[0] = ($event) => _ctx.$emit("close"))
        }, [createVNode(unref(ElIcon), { class: normalizeClass(unref(ns).e("close")) }, {
          default: withCtx(() => [(openBlock(), createBlock(resolveDynamicComponent(__props.closeIcon || unref(Close))))]),
          _: 1
        }, 8, ["class"])], 10, _hoisted_2)) : createCommentVNode("v-if", true)], 2),
        createElementVNode("div", {
          id: unref(bodyId),
          class: normalizeClass([unref(ns).e("body"), __props.bodyClass])
        }, [renderSlot(_ctx.$slots, "default")], 10, _hoisted_3),
        _ctx.$slots.footer ? (openBlock(), createElementBlock("footer", {
          key: 0,
          class: normalizeClass([unref(ns).e("footer"), __props.footerClass])
        }, [renderSlot(_ctx.$slots, "footer")], 2)) : createCommentVNode("v-if", true)
      ], 6);
    };
  }
});
var dialog_content_default = dialog_content_vue_vue_type_script_setup_true_lang_default;
const _hoisted_1 = [
  "aria-label",
  "aria-labelledby",
  "aria-describedby"
];
var dialog_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: "ElDialog",
  inheritAttrs: false,
  __name: "dialog",
  props: dialogProps,
  emits: dialogEmits,
  setup(__props, { expose: __expose }) {
    const props = __props;
    const slots = useSlots();
    useDeprecated({
      scope: "el-dialog",
      from: "the title slot",
      replacement: "the header slot",
      version: "3.0.0",
      ref: "https://element-plus.org/en-US/component/dialog.html#slots"
    }, computed(() => !!slots.title));
    const ns = useNamespace("dialog");
    const dialogRef = ref();
    const headerRef = ref();
    const dialogContentRef = ref();
    const { visible, titleId, bodyId, style, overlayDialogStyle, rendered, transitionConfig, zIndex, _draggable, _alignCenter, _overflow, penetrable, handleClose, onModalClick, onOpenAutoFocus, onCloseAutoFocus, onCloseRequested, onFocusoutPrevented, bringToFront, closing } = useDialog(props, dialogRef);
    provide(dialogInjectionKey, {
      dialogRef,
      headerRef,
      bodyId,
      ns,
      rendered,
      style
    });
    const overlayEvent = useSameTarget(onModalClick);
    const resetPosition = () => {
      var _a;
      (_a = dialogContentRef.value) == null ? void 0 : _a.resetPosition();
    };
    __expose({
      /** @description whether the dialog is visible */
      visible,
      dialogContentRef,
      resetPosition,
      handleClose
    });
    return (_ctx, _cache) => {
      return openBlock(), createBlock(Teleport, {
        to: __props.appendTo,
        disabled: __props.appendTo !== "body" ? false : !__props.appendToBody
      }, [createVNode(Transition, mergeProps(unref(transitionConfig), { persisted: "" }), {
        default: withCtx(() => [withDirectives(createVNode(unref(ElOverlay), {
          "custom-mask-event": "",
          mask: __props.modal,
          "overlay-class": [
            __props.modalClass ?? "",
            `${unref(ns).namespace.value}-modal-dialog`,
            unref(ns).is("penetrable", unref(penetrable))
          ],
          "z-index": unref(zIndex)
        }, {
          default: withCtx(() => [createElementVNode("div", {
            role: "dialog",
            "aria-modal": "true",
            "aria-label": __props.title || void 0,
            "aria-labelledby": !__props.title ? unref(titleId) : void 0,
            "aria-describedby": unref(bodyId),
            class: normalizeClass([`${unref(ns).namespace.value}-overlay-dialog`, unref(ns).is("closing", unref(closing))]),
            style: normalizeStyle(unref(overlayDialogStyle)),
            onClick: _cache[0] || (_cache[0] = (...args) => unref(overlayEvent).onClick && unref(overlayEvent).onClick(...args)),
            onMousedown: _cache[1] || (_cache[1] = (...args) => unref(overlayEvent).onMousedown && unref(overlayEvent).onMousedown(...args)),
            onMouseup: _cache[2] || (_cache[2] = (...args) => unref(overlayEvent).onMouseup && unref(overlayEvent).onMouseup(...args))
          }, [createVNode(unref(focus_trap_default$1), {
            loop: "",
            trapped: unref(visible),
            "focus-start-el": "container",
            onFocusAfterTrapped: unref(onOpenAutoFocus),
            onFocusAfterReleased: unref(onCloseAutoFocus),
            onFocusoutPrevented: unref(onFocusoutPrevented),
            onReleaseRequested: unref(onCloseRequested)
          }, {
            default: withCtx(() => [unref(rendered) ? (openBlock(), createBlock(dialog_content_default, mergeProps({
              key: 0,
              ref_key: "dialogContentRef",
              ref: dialogContentRef
            }, _ctx.$attrs, {
              center: __props.center,
              "align-center": unref(_alignCenter),
              "close-icon": __props.closeIcon,
              draggable: unref(_draggable),
              overflow: unref(_overflow),
              fullscreen: __props.fullscreen,
              "header-class": __props.headerClass,
              "body-class": __props.bodyClass,
              "footer-class": __props.footerClass,
              "show-close": __props.showClose,
              title: __props.title,
              "aria-level": __props.headerAriaLevel,
              onClose: unref(handleClose),
              onMousedown: unref(bringToFront)
            }), createSlots({
              header: withCtx(() => [!_ctx.$slots.title ? renderSlot(_ctx.$slots, "header", {
                key: 0,
                close: unref(handleClose),
                titleId: unref(titleId),
                titleClass: unref(ns).e("title")
              }) : renderSlot(_ctx.$slots, "title", { key: 1 })]),
              default: withCtx(() => [renderSlot(_ctx.$slots, "default")]),
              _: 2
            }, [_ctx.$slots.footer ? {
              name: "footer",
              fn: withCtx(() => [renderSlot(_ctx.$slots, "footer")]),
              key: "0"
            } : void 0]), 1040, [
              "center",
              "align-center",
              "close-icon",
              "draggable",
              "overflow",
              "fullscreen",
              "header-class",
              "body-class",
              "footer-class",
              "show-close",
              "title",
              "aria-level",
              "onClose",
              "onMousedown"
            ])) : createCommentVNode("v-if", true)]),
            _: 3
          }, 8, [
            "trapped",
            "onFocusAfterTrapped",
            "onFocusAfterReleased",
            "onFocusoutPrevented",
            "onReleaseRequested"
          ])], 46, _hoisted_1)]),
          _: 3
        }, 8, [
          "mask",
          "overlay-class",
          "z-index"
        ]), [[vShow, unref(visible)]])]),
        _: 3
      }, 16)], 8, ["to", "disabled"]);
    };
  }
});
var dialog_default = dialog_vue_vue_type_script_setup_true_lang_default;
const ElDialog = withInstall(dialog_default);
export {
  D as DEFAULT_DIALOG_TRANSITION,
  ElDialog,
  ElDialog as default,
  dialogEmits,
  dialogInjectionKey,
  dialogProps,
  useDialog
};
//# sourceMappingURL=index-CnX1dSty.js.map
