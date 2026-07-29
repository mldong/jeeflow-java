import { g as getEventCode, E as EVENT_CODE } from "./event-Ka8Z03Z9.js";
import { H as focusElement, j as isClient, e as isString } from "./error-DiL_p-4A.js";
import { onMounted, onBeforeUnmount, ref, defineComponent, provide, watch, unref, nextTick, renderSlot } from "vue";
import { h as isNil } from "./index-D9cY2WMP.js";
const FOCUS_AFTER_TRAPPED = "focus-trap.focus-after-trapped";
const FOCUS_AFTER_RELEASED = "focus-trap.focus-after-released";
const FOCUSOUT_PREVENTED = "focus-trap.focusout-prevented";
const FOCUS_AFTER_TRAPPED_OPTS = {
  cancelable: true,
  bubbles: false
};
const FOCUSOUT_PREVENTED_OPTS = {
  cancelable: true,
  bubbles: false
};
const ON_TRAP_FOCUS_EVT = "focusAfterTrapped";
const ON_RELEASE_FOCUS_EVT = "focusAfterReleased";
const FOCUS_TRAP_INJECTION_KEY = Symbol("elFocusTrap");
const focusReason = ref();
const lastUserFocusTimestamp = ref(0);
const lastAutomatedFocusTimestamp = ref(0);
let focusReasonUserCount = 0;
const obtainAllFocusableElements = (element) => {
  const nodes = [];
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_ELEMENT, { acceptNode: (node) => {
    const isHiddenInput = node.tagName === "INPUT" && node.type === "hidden";
    if (node.disabled || node.hidden || isHiddenInput) return NodeFilter.FILTER_SKIP;
    return node.tabIndex >= 0 || node === document.activeElement ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP;
  } });
  while (walker.nextNode()) nodes.push(walker.currentNode);
  return nodes;
};
const getVisibleElement = (elements, container) => {
  for (const element of elements) if (!isHidden(element, container)) return element;
};
const isHidden = (element, container) => {
  if (getComputedStyle(element).visibility === "hidden") return true;
  while (element) {
    if (container && element === container) return false;
    if (getComputedStyle(element).display === "none") return true;
    element = element.parentElement;
  }
  return false;
};
const getEdges = (container) => {
  const focusable = obtainAllFocusableElements(container);
  return [getVisibleElement(focusable, container), getVisibleElement(focusable.reverse(), container)];
};
const isSelectable = (element) => {
  return element instanceof HTMLInputElement && "select" in element;
};
const tryFocus = (element, shouldSelect) => {
  if (element) {
    const prevFocusedElement = document.activeElement;
    focusElement(element, { preventScroll: true });
    lastAutomatedFocusTimestamp.value = window.performance.now();
    if (element !== prevFocusedElement && isSelectable(element) && shouldSelect) element.select();
  }
};
function removeFromStack(list, item) {
  const copy = [...list];
  const idx = list.indexOf(item);
  if (idx !== -1) copy.splice(idx, 1);
  return copy;
}
const createFocusableStack = () => {
  let stack = [];
  const push = (layer) => {
    const currentLayer = stack[0];
    if (currentLayer && layer !== currentLayer) currentLayer.pause();
    stack = removeFromStack(stack, layer);
    stack.unshift(layer);
  };
  const remove = (layer) => {
    var _a, _b;
    stack = removeFromStack(stack, layer);
    (_b = (_a = stack[0]) == null ? void 0 : _a.resume) == null ? void 0 : _b.call(_a);
  };
  return {
    push,
    remove
  };
};
const focusFirstDescendant = (elements, shouldSelect = false) => {
  const prevFocusedElement = document.activeElement;
  for (const element of elements) {
    tryFocus(element, shouldSelect);
    if (document.activeElement !== prevFocusedElement) return;
  }
};
const focusableStack = createFocusableStack();
const isFocusCausedByUserEvent = () => {
  return lastUserFocusTimestamp.value > lastAutomatedFocusTimestamp.value;
};
const notifyFocusReasonPointer = () => {
  focusReason.value = "pointer";
  lastUserFocusTimestamp.value = window.performance.now();
};
const notifyFocusReasonKeydown = () => {
  focusReason.value = "keyboard";
  lastUserFocusTimestamp.value = window.performance.now();
};
const useFocusReason = () => {
  onMounted(() => {
    if (focusReasonUserCount === 0) {
      document.addEventListener("mousedown", notifyFocusReasonPointer);
      document.addEventListener("touchstart", notifyFocusReasonPointer);
      document.addEventListener("keydown", notifyFocusReasonKeydown);
    }
    focusReasonUserCount++;
  });
  onBeforeUnmount(() => {
    focusReasonUserCount--;
    if (focusReasonUserCount <= 0) {
      document.removeEventListener("mousedown", notifyFocusReasonPointer);
      document.removeEventListener("touchstart", notifyFocusReasonPointer);
      document.removeEventListener("keydown", notifyFocusReasonKeydown);
    }
  });
  return {
    focusReason,
    lastUserFocusTimestamp,
    lastAutomatedFocusTimestamp
  };
};
const createFocusOutPreventedEvent = (detail) => {
  return new CustomEvent(FOCUSOUT_PREVENTED, {
    ...FOCUSOUT_PREVENTED_OPTS,
    detail
  });
};
let registeredEscapeHandlers = [];
const cachedHandler = (event) => {
  if (getEventCode(event) === EVENT_CODE.esc) registeredEscapeHandlers.forEach((registeredHandler) => registeredHandler(event));
};
const useEscapeKeydown = (handler) => {
  onMounted(() => {
    if (registeredEscapeHandlers.length === 0) document.addEventListener("keydown", cachedHandler);
    if (isClient) registeredEscapeHandlers.push(handler);
  });
  onBeforeUnmount(() => {
    registeredEscapeHandlers = registeredEscapeHandlers.filter((registeredHandler) => registeredHandler !== handler);
    if (registeredEscapeHandlers.length === 0) {
      if (isClient) document.removeEventListener("keydown", cachedHandler);
    }
  });
};
var focus_trap_vue_vue_type_script_lang_default = defineComponent({
  name: "ElFocusTrap",
  inheritAttrs: false,
  props: {
    loop: Boolean,
    trapped: Boolean,
    focusTrapEl: Object,
    focusStartEl: {
      type: [Object, String],
      default: "first"
    }
  },
  emits: [
    ON_TRAP_FOCUS_EVT,
    ON_RELEASE_FOCUS_EVT,
    "focusin",
    "focusout",
    "focusout-prevented",
    "release-requested"
  ],
  setup(props, { emit }) {
    const forwardRef = ref();
    let lastFocusBeforeTrapped;
    let lastFocusAfterTrapped;
    const { focusReason: focusReason2 } = useFocusReason();
    useEscapeKeydown((event) => {
      if (props.trapped && !focusLayer.paused) emit("release-requested", event);
    });
    const focusLayer = {
      paused: false,
      pause() {
        this.paused = true;
      },
      resume() {
        this.paused = false;
      }
    };
    const onKeydown = (e) => {
      if (!props.loop && !props.trapped) return;
      if (focusLayer.paused) return;
      const { altKey, ctrlKey, metaKey, currentTarget, shiftKey } = e;
      const { loop } = props;
      const isTabbing = getEventCode(e) === EVENT_CODE.tab && !altKey && !ctrlKey && !metaKey;
      const currentFocusingEl = document.activeElement;
      if (isTabbing && currentFocusingEl) {
        const container = currentTarget;
        const [first, last] = getEdges(container);
        if (!(first && last)) {
          if (currentFocusingEl === container) {
            const focusoutPreventedEvent = createFocusOutPreventedEvent({ focusReason: focusReason2.value });
            emit("focusout-prevented", focusoutPreventedEvent);
            if (!focusoutPreventedEvent.defaultPrevented) e.preventDefault();
          }
        } else if (!shiftKey && currentFocusingEl === last) {
          const focusoutPreventedEvent = createFocusOutPreventedEvent({ focusReason: focusReason2.value });
          emit("focusout-prevented", focusoutPreventedEvent);
          if (!focusoutPreventedEvent.defaultPrevented) {
            e.preventDefault();
            if (loop) tryFocus(first, true);
          }
        } else if (shiftKey && [first, container].includes(currentFocusingEl)) {
          const focusoutPreventedEvent = createFocusOutPreventedEvent({ focusReason: focusReason2.value });
          emit("focusout-prevented", focusoutPreventedEvent);
          if (!focusoutPreventedEvent.defaultPrevented) {
            e.preventDefault();
            if (loop) tryFocus(last, true);
          }
        }
      }
    };
    provide(FOCUS_TRAP_INJECTION_KEY, {
      focusTrapRef: forwardRef,
      onKeydown
    });
    watch(() => props.focusTrapEl, (focusTrapEl) => {
      if (focusTrapEl) forwardRef.value = focusTrapEl;
    }, { immediate: true });
    watch([forwardRef], ([forwardRef2], [oldForwardRef]) => {
      if (forwardRef2) {
        forwardRef2.addEventListener("keydown", onKeydown);
        forwardRef2.addEventListener("focusin", onFocusIn);
        forwardRef2.addEventListener("focusout", onFocusOut);
      }
      if (oldForwardRef) {
        oldForwardRef.removeEventListener("keydown", onKeydown);
        oldForwardRef.removeEventListener("focusin", onFocusIn);
        oldForwardRef.removeEventListener("focusout", onFocusOut);
      }
    });
    const trapOnFocus = (e) => {
      emit(ON_TRAP_FOCUS_EVT, e);
    };
    const releaseOnFocus = (e) => emit(ON_RELEASE_FOCUS_EVT, e);
    const onFocusIn = (e) => {
      const trapContainer = unref(forwardRef);
      if (!trapContainer) return;
      const target = e.target;
      const relatedTarget = e.relatedTarget;
      const isFocusedInTrap = target && trapContainer.contains(target);
      if (!props.trapped) {
        if (!(relatedTarget && trapContainer.contains(relatedTarget))) lastFocusBeforeTrapped = relatedTarget;
      }
      if (isFocusedInTrap) emit("focusin", e);
      if (focusLayer.paused) return;
      if (props.trapped) if (isFocusedInTrap) lastFocusAfterTrapped = target;
      else tryFocus(lastFocusAfterTrapped, true);
    };
    const onFocusOut = (e) => {
      const trapContainer = unref(forwardRef);
      if (focusLayer.paused || !trapContainer) return;
      if (props.trapped) {
        const relatedTarget = e.relatedTarget;
        if (!isNil(relatedTarget) && !trapContainer.contains(relatedTarget)) setTimeout(() => {
          if (!focusLayer.paused && props.trapped) {
            const focusoutPreventedEvent = createFocusOutPreventedEvent({ focusReason: focusReason2.value });
            emit("focusout-prevented", focusoutPreventedEvent);
            if (!focusoutPreventedEvent.defaultPrevented) tryFocus(lastFocusAfterTrapped, true);
          }
        }, 0);
      } else {
        const target = e.target;
        if (!(target && trapContainer.contains(target))) emit("focusout", e);
      }
    };
    async function startTrap() {
      await nextTick();
      const trapContainer = unref(forwardRef);
      if (trapContainer) {
        focusableStack.push(focusLayer);
        const prevFocusedElement = trapContainer.contains(document.activeElement) ? lastFocusBeforeTrapped : document.activeElement;
        lastFocusBeforeTrapped = prevFocusedElement;
        if (!trapContainer.contains(prevFocusedElement)) {
          const focusEvent = new Event(FOCUS_AFTER_TRAPPED, FOCUS_AFTER_TRAPPED_OPTS);
          trapContainer.addEventListener(FOCUS_AFTER_TRAPPED, trapOnFocus);
          trapContainer.dispatchEvent(focusEvent);
          if (!focusEvent.defaultPrevented) nextTick(() => {
            let focusStartEl = props.focusStartEl;
            if (!isString(focusStartEl)) {
              tryFocus(focusStartEl);
              if (document.activeElement !== focusStartEl) focusStartEl = "first";
            }
            if (focusStartEl === "first") focusFirstDescendant(obtainAllFocusableElements(trapContainer), true);
            if (document.activeElement === prevFocusedElement || focusStartEl === "container") tryFocus(trapContainer);
          });
        }
      }
    }
    function stopTrap() {
      const trapContainer = unref(forwardRef);
      if (trapContainer) {
        trapContainer.removeEventListener(FOCUS_AFTER_TRAPPED, trapOnFocus);
        const releasedEvent = new CustomEvent(FOCUS_AFTER_RELEASED, {
          ...FOCUS_AFTER_TRAPPED_OPTS,
          detail: { focusReason: focusReason2.value }
        });
        trapContainer.addEventListener(FOCUS_AFTER_RELEASED, releaseOnFocus);
        trapContainer.dispatchEvent(releasedEvent);
        if (!releasedEvent.defaultPrevented && (focusReason2.value == "keyboard" || !isFocusCausedByUserEvent() || trapContainer.contains(document.activeElement))) tryFocus(lastFocusBeforeTrapped ?? document.body);
        trapContainer.removeEventListener(FOCUS_AFTER_RELEASED, releaseOnFocus);
        focusableStack.remove(focusLayer);
        lastFocusBeforeTrapped = null;
        lastFocusAfterTrapped = null;
      }
    }
    onMounted(() => {
      if (props.trapped) startTrap();
      watch(() => props.trapped, (trapped) => {
        if (trapped) startTrap();
        else stopTrap();
      });
    });
    onBeforeUnmount(() => {
      if (props.trapped) stopTrap();
      if (forwardRef.value) {
        forwardRef.value.removeEventListener("keydown", onKeydown);
        forwardRef.value.removeEventListener("focusin", onFocusIn);
        forwardRef.value.removeEventListener("focusout", onFocusOut);
        forwardRef.value = void 0;
      }
      lastFocusBeforeTrapped = null;
      lastFocusAfterTrapped = null;
    });
    return { onKeydown };
  }
});
var _plugin_vue_export_helper_default = (sfc, props) => {
  const target = sfc.__vccOpts || sfc;
  for (const [key, val] of props) target[key] = val;
  return target;
};
function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
  return renderSlot(_ctx.$slots, "default", { handleKeydown: _ctx.onKeydown });
}
var focus_trap_default = /* @__PURE__ */ _plugin_vue_export_helper_default(focus_trap_vue_vue_type_script_lang_default, [["render", _sfc_render]]);
var focus_trap_default$1 = focus_trap_default;
export {
  FOCUS_TRAP_INJECTION_KEY as F,
  _plugin_vue_export_helper_default as _,
  focus_trap_default$1 as f
};
//# sourceMappingURL=index-Blbk90Jx.js.map
