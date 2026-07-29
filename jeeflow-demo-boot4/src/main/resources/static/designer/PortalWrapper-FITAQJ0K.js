import { computed, watchEffect, defineComponent, shallowRef, onUpdated, onMounted, watch, nextTick, onBeforeUnmount, createVNode } from "vue";
import { E as canUseDom, R as updateCSS, ac as removeCSS, P as PropTypes, j as booleanType } from "./index-IfV1sCLI.js";
import { P as Portal$1 } from "./transition-AJOM0T1w.js";
import { w as wrapperRaf } from "./raf-DXHxwK-q.js";
let cached;
function getScrollBarSize(fresh) {
  if (typeof document === "undefined") {
    return 0;
  }
  if (cached === void 0) {
    const inner = document.createElement("div");
    inner.style.width = "100%";
    inner.style.height = "200px";
    const outer = document.createElement("div");
    const outerStyle = outer.style;
    outerStyle.position = "absolute";
    outerStyle.top = "0";
    outerStyle.left = "0";
    outerStyle.pointerEvents = "none";
    outerStyle.visibility = "hidden";
    outerStyle.width = "200px";
    outerStyle.height = "150px";
    outerStyle.overflow = "hidden";
    outer.appendChild(inner);
    document.body.appendChild(outer);
    const widthContained = inner.offsetWidth;
    outer.style.overflow = "scroll";
    let widthScroll = inner.offsetWidth;
    if (widthContained === widthScroll) {
      widthScroll = outer.clientWidth;
    }
    document.body.removeChild(outer);
    cached = widthContained - widthScroll;
  }
  return cached;
}
const UNIQUE_ID = `vc-util-locker-${Date.now()}`;
let uuid = 0;
function isBodyOverflowing() {
  return document.body.scrollHeight > (window.innerHeight || document.documentElement.clientHeight) && window.innerWidth > document.body.offsetWidth;
}
function useScrollLocker(lock) {
  const mergedLock = computed(() => !!lock && !!lock.value);
  uuid += 1;
  const id = `${UNIQUE_ID}_${uuid}`;
  watchEffect((onClear) => {
    if (!canUseDom()) {
      return;
    }
    if (mergedLock.value) {
      const scrollbarSize = getScrollBarSize();
      const isOverflow = isBodyOverflowing();
      updateCSS(`
html body {
  overflow-y: hidden;
  ${isOverflow ? `width: calc(100% - ${scrollbarSize}px);` : ""}
}`, id);
    } else {
      removeCSS(id);
    }
    onClear(() => {
      removeCSS(id);
    });
  }, {
    flush: "post"
  });
}
let openCount = 0;
const supportDom = canUseDom();
const getParent = (getContainer) => {
  if (!supportDom) {
    return null;
  }
  if (getContainer) {
    if (typeof getContainer === "string") {
      return document.querySelectorAll(getContainer)[0];
    }
    if (typeof getContainer === "function") {
      return getContainer();
    }
    if (typeof getContainer === "object" && getContainer instanceof window.HTMLElement) {
      return getContainer;
    }
  }
  return document.body;
};
const Portal = defineComponent({
  compatConfig: {
    MODE: 3
  },
  name: "PortalWrapper",
  inheritAttrs: false,
  props: {
    wrapperClassName: String,
    forceRender: {
      type: Boolean,
      default: void 0
    },
    getContainer: PropTypes.any,
    visible: {
      type: Boolean,
      default: void 0
    },
    autoLock: booleanType(),
    didUpdate: Function
  },
  setup(props, _ref) {
    let {
      slots
    } = _ref;
    const container = shallowRef();
    const componentRef = shallowRef();
    const rafId = shallowRef();
    const triggerUpdate = shallowRef(1);
    const defaultContainer = canUseDom() && document.createElement("div");
    const removeCurrentContainer = () => {
      var _a, _b;
      if (container.value === defaultContainer) {
        (_b = (_a = container.value) === null || _a === void 0 ? void 0 : _a.parentNode) === null || _b === void 0 ? void 0 : _b.removeChild(container.value);
      }
      container.value = null;
    };
    let parent = null;
    const attachToParent = function() {
      let force = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : false;
      if (force || container.value && !container.value.parentNode) {
        parent = getParent(props.getContainer);
        if (parent) {
          parent.appendChild(container.value);
          return true;
        }
        return false;
      }
      return true;
    };
    const getContainer = () => {
      if (!supportDom) {
        return null;
      }
      if (!container.value) {
        container.value = defaultContainer;
        attachToParent(true);
      }
      setWrapperClassName();
      return container.value;
    };
    const setWrapperClassName = () => {
      const {
        wrapperClassName
      } = props;
      if (container.value && wrapperClassName && wrapperClassName !== container.value.className) {
        container.value.className = wrapperClassName;
      }
    };
    onUpdated(() => {
      setWrapperClassName();
      attachToParent();
    });
    useScrollLocker(computed(() => {
      return props.autoLock && props.visible && canUseDom() && (container.value === document.body || container.value === defaultContainer);
    }));
    onMounted(() => {
      let init = false;
      watch([() => props.visible, () => props.getContainer], (_ref2, _ref3) => {
        let [visible, getContainer2] = _ref2;
        let [prevVisible, prevGetContainer] = _ref3;
        if (supportDom) {
          parent = getParent(props.getContainer);
          if (parent === document.body) {
            if (visible && !prevVisible) {
              openCount += 1;
            } else if (init) {
              openCount -= 1;
            }
          }
        }
        if (init) {
          const getContainerIsFunc = typeof getContainer2 === "function" && typeof prevGetContainer === "function";
          if (getContainerIsFunc ? getContainer2.toString() !== prevGetContainer.toString() : getContainer2 !== prevGetContainer) {
            removeCurrentContainer();
          }
        }
        init = true;
      }, {
        immediate: true,
        flush: "post"
      });
      nextTick(() => {
        if (!attachToParent()) {
          rafId.value = wrapperRaf(() => {
            triggerUpdate.value += 1;
          });
        }
      });
    });
    onBeforeUnmount(() => {
      const {
        visible
      } = props;
      if (supportDom && parent === document.body) {
        openCount = visible && openCount ? openCount - 1 : openCount;
      }
      removeCurrentContainer();
      wrapperRaf.cancel(rafId.value);
    });
    return () => {
      const {
        forceRender,
        visible
      } = props;
      let portal = null;
      const childProps = {
        getOpenCount: () => openCount,
        getContainer
      };
      if (triggerUpdate.value && (forceRender || visible || componentRef.value)) {
        portal = createVNode(Portal$1, {
          "getContainer": getContainer,
          "ref": componentRef,
          "didUpdate": props.didUpdate
        }, {
          default: () => {
            var _a;
            return (_a = slots.default) === null || _a === void 0 ? void 0 : _a.call(slots, childProps);
          }
        });
      }
      return portal;
    };
  }
});
export {
  Portal as P
};
//# sourceMappingURL=PortalWrapper-FITAQJ0K.js.map
