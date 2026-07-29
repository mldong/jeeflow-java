import { i as isArray, F as tryOnScopeDispose, k as isNumber, b as buildProps, d as definePropType, B as buildProp, h as isFunction, f as isBoolean, j as isClient, G as useGetDerivedNamespace, u as useNamespace, N as NOOP, g as debugWarn, l as isObject, o as isElement, m as isFocusable, w as withInstall, H as focusElement, I as computedEager } from "./error-DiL_p-4A.js";
import { unref, getCurrentInstance, computed, watch, onMounted, onBeforeMount, defineComponent, inject, onBeforeUnmount, openBlock, createElementBlock, normalizeStyle, normalizeClass, ref, provide, renderSlot, withDirectives, cloneVNode, Comment, Fragment, Text, createVNode, createBlock, mergeProps, withCtx, createCommentVNode, shallowRef, toRef, nextTick, Teleport, Transition, vShow, readonly, onDeactivated, toDisplayString } from "vue";
import { u as useAriaProps } from "./index-C-31KauU.js";
import { E as EVENT_CODE, c as composeEventHandlers, g as getEventCode } from "./event-Ka8Z03Z9.js";
import { a as useIdInjection, u as useId } from "./index-DuyY_KOz.js";
import { d as unrefElement, b as useResizeObserver, o as onClickOutside } from "./index-BoUuUOkb.js";
import { h as isNil, g as fromPairs, n as isUndefined } from "./index-D9cY2WMP.js";
import { f as focus_trap_default$1 } from "./index-Blbk90Jx.js";
import { a as formItemContextKey } from "./constants-BXBCHRH4.js";
import { u as useZIndex } from "./index-BHCMzsem.js";
const castArray = (arr) => {
  if (!arr && arr !== 0) return [];
  return isArray(arr) ? arr : [arr];
};
function useTimeout() {
  let timeoutHandle;
  const registerTimeout = (fn2, delay) => {
    cancelTimeout();
    timeoutHandle = globalThis.setTimeout(fn2, delay);
  };
  const cancelTimeout = () => {
    if (timeoutHandle === void 0) return;
    globalThis.clearTimeout(timeoutHandle);
    timeoutHandle = void 0;
  };
  tryOnScopeDispose(() => cancelTimeout());
  return {
    registerTimeout,
    cancelTimeout
  };
}
const useDelayedToggleProps = buildProps({
  /**
  * @description delay of appearance, in millisecond, not valid in controlled mode
  */
  showAfter: {
    type: Number,
    default: 0
  },
  /**
  * @description delay of disappear, in millisecond, not valid in controlled mode
  */
  hideAfter: {
    type: Number,
    default: 200
  },
  /**
  * @description disappear automatically, in millisecond, not valid in controlled mode
  */
  autoClose: {
    type: Number,
    default: 0
  }
});
const useDelayedToggle = ({ showAfter, hideAfter, autoClose, open, close }) => {
  const { registerTimeout } = useTimeout();
  const { registerTimeout: registerTimeoutForAutoClose, cancelTimeout: cancelTimeoutForAutoClose } = useTimeout();
  const onOpen = (event, delay = unref(showAfter)) => {
    registerTimeout(() => {
      open(event);
      const _autoClose = unref(autoClose);
      if (isNumber(_autoClose) && _autoClose > 0) registerTimeoutForAutoClose(() => {
        close(event);
      }, _autoClose);
    }, delay);
  };
  const onClose = (event, delay = unref(hideAfter)) => {
    cancelTimeoutForAutoClose();
    registerTimeout(() => {
      close(event);
    }, delay);
  };
  return {
    onOpen,
    onClose
  };
};
const popperArrowProps = buildProps({ arrowOffset: {
  type: Number,
  default: 5
} });
var L = "top", W = "bottom", T = "right", P = "left", me = "auto", Q = [L, W, T, P], Y = "start", Z = "end", Ye = "clippingParents", je = "viewport", ee = "popper", Ge = "reference", De = Q.reduce(function(e, t) {
  return e.concat([t + "-" + Y, t + "-" + Z]);
}, []), Ee = [].concat(Q, [me]).reduce(function(e, t) {
  return e.concat([t, t + "-" + Y, t + "-" + Z]);
}, []), Je = "beforeRead", Ke = "read", Qe = "afterRead", Ze = "beforeMain", et = "main", tt = "afterMain", nt = "beforeWrite", rt = "write", ot = "afterWrite", it = [Je, Ke, Qe, Ze, et, tt, nt, rt, ot];
function V(e) {
  return e ? (e.nodeName || "").toLowerCase() : null;
}
function B(e) {
  if (e == null) return window;
  if (e.toString() !== "[object Window]") {
    var t = e.ownerDocument;
    return t && t.defaultView || window;
  }
  return e;
}
function G(e) {
  var t = B(e).Element;
  return e instanceof t || e instanceof Element;
}
function R(e) {
  var t = B(e).HTMLElement;
  return e instanceof t || e instanceof HTMLElement;
}
function Ae(e) {
  if (typeof ShadowRoot == "undefined") return false;
  var t = B(e).ShadowRoot;
  return e instanceof t || e instanceof ShadowRoot;
}
function Tt(e) {
  var t = e.state;
  Object.keys(t.elements).forEach(function(n) {
    var r = t.styles[n] || {}, o = t.attributes[n] || {}, a = t.elements[n];
    !R(a) || !V(a) || (Object.assign(a.style, r), Object.keys(o).forEach(function(c) {
      var s = o[c];
      s === false ? a.removeAttribute(c) : a.setAttribute(c, s === true ? "" : s);
    }));
  });
}
function Bt(e) {
  var t = e.state, n = { popper: { position: t.options.strategy, left: "0", top: "0", margin: "0" }, arrow: { position: "absolute" }, reference: {} };
  return Object.assign(t.elements.popper.style, n.popper), t.styles = n, t.elements.arrow && Object.assign(t.elements.arrow.style, n.arrow), function() {
    Object.keys(t.elements).forEach(function(r) {
      var o = t.elements[r], a = t.attributes[r] || {}, c = Object.keys(t.styles.hasOwnProperty(r) ? t.styles[r] : n[r]), s = c.reduce(function(i, f) {
        return i[f] = "", i;
      }, {});
      !R(o) || !V(o) || (Object.assign(o.style, s), Object.keys(a).forEach(function(i) {
        o.removeAttribute(i);
      }));
    });
  };
}
var ke = { name: "applyStyles", enabled: true, phase: "write", fn: Tt, effect: Bt, requires: ["computeStyles"] };
function C(e) {
  return e.split("-")[0];
}
var J = Math.max, ve = Math.min, te = Math.round;
function Le() {
  var e = navigator.userAgentData;
  return e != null && e.brands && Array.isArray(e.brands) ? e.brands.map(function(t) {
    return t.brand + "/" + t.version;
  }).join(" ") : navigator.userAgent;
}
function at() {
  return !/^((?!chrome|android).)*safari/i.test(Le());
}
function ne(e, t, n) {
  t === void 0 && (t = false), n === void 0 && (n = false);
  var r = e.getBoundingClientRect(), o = 1, a = 1;
  t && R(e) && (o = e.offsetWidth > 0 && te(r.width) / e.offsetWidth || 1, a = e.offsetHeight > 0 && te(r.height) / e.offsetHeight || 1);
  var c = G(e) ? B(e) : window, s = c.visualViewport, i = !at() && n, f = (r.left + (i && s ? s.offsetLeft : 0)) / o, u = (r.top + (i && s ? s.offsetTop : 0)) / a, m = r.width / o, h = r.height / a;
  return { width: m, height: h, top: u, right: f + m, bottom: u + h, left: f, x: f, y: u };
}
function Pe(e) {
  var t = ne(e), n = e.offsetWidth, r = e.offsetHeight;
  return Math.abs(t.width - n) <= 1 && (n = t.width), Math.abs(t.height - r) <= 1 && (r = t.height), { x: e.offsetLeft, y: e.offsetTop, width: n, height: r };
}
function st(e, t) {
  var n = t.getRootNode && t.getRootNode();
  if (e.contains(t)) return true;
  if (n && Ae(n)) {
    var r = t;
    do {
      if (r && e.isSameNode(r)) return true;
      r = r.parentNode || r.host;
    } while (r);
  }
  return false;
}
function I(e) {
  return B(e).getComputedStyle(e);
}
function Rt(e) {
  return ["table", "td", "th"].indexOf(V(e)) >= 0;
}
function N(e) {
  return ((G(e) ? e.ownerDocument : e.document) || window.document).documentElement;
}
function ye(e) {
  return V(e) === "html" ? e : e.assignedSlot || e.parentNode || (Ae(e) ? e.host : null) || N(e);
}
function ft(e) {
  return !R(e) || I(e).position === "fixed" ? null : e.offsetParent;
}
function Ht(e) {
  var t = /firefox/i.test(Le()), n = /Trident/i.test(Le());
  if (n && R(e)) {
    var r = I(e);
    if (r.position === "fixed") return null;
  }
  var o = ye(e);
  for (Ae(o) && (o = o.host); R(o) && ["html", "body"].indexOf(V(o)) < 0; ) {
    var a = I(o);
    if (a.transform !== "none" || a.perspective !== "none" || a.contain === "paint" || ["transform", "perspective"].indexOf(a.willChange) !== -1 || t && a.willChange === "filter" || t && a.filter && a.filter !== "none") return o;
    o = o.parentNode;
  }
  return null;
}
function se(e) {
  for (var t = B(e), n = ft(e); n && Rt(n) && I(n).position === "static"; ) n = ft(n);
  return n && (V(n) === "html" || V(n) === "body" && I(n).position === "static") ? t : n || Ht(e) || t;
}
function Me(e) {
  return ["top", "bottom"].indexOf(e) >= 0 ? "x" : "y";
}
function fe(e, t, n) {
  return J(e, ve(t, n));
}
function St(e, t, n) {
  var r = fe(e, t, n);
  return r > n ? n : r;
}
function ct() {
  return { top: 0, right: 0, bottom: 0, left: 0 };
}
function ut(e) {
  return Object.assign({}, ct(), e);
}
function pt(e, t) {
  return t.reduce(function(n, r) {
    return n[r] = e, n;
  }, {});
}
var Vt = function(e, t) {
  return e = typeof e == "function" ? e(Object.assign({}, t.rects, { placement: t.placement })) : e, ut(typeof e != "number" ? e : pt(e, Q));
};
function Ct(e) {
  var t, n = e.state, r = e.name, o = e.options, a = n.elements.arrow, c = n.modifiersData.popperOffsets, s = C(n.placement), i = Me(s), f = [P, T].indexOf(s) >= 0, u = f ? "height" : "width";
  if (!(!a || !c)) {
    var m = Vt(o.padding, n), h = Pe(a), l = i === "y" ? L : P, g = i === "y" ? W : T, p = n.rects.reference[u] + n.rects.reference[i] - c[i] - n.rects.popper[u], y = c[i] - n.rects.reference[i], b = se(a), x = b ? i === "y" ? b.clientHeight || 0 : b.clientWidth || 0 : 0, O = p / 2 - y / 2, d = m[l], v = x - h[u] - m[g], w = x / 2 - h[u] / 2 + O, $ = fe(d, w, v), j = i;
    n.modifiersData[r] = (t = {}, t[j] = $, t.centerOffset = $ - w, t);
  }
}
function qt(e) {
  var t = e.state, n = e.options, r = n.element, o = r === void 0 ? "[data-popper-arrow]" : r;
  o != null && (typeof o == "string" && (o = t.elements.popper.querySelector(o), !o) || st(t.elements.popper, o) && (t.elements.arrow = o));
}
var lt = { name: "arrow", enabled: true, phase: "main", fn: Ct, effect: qt, requires: ["popperOffsets"], requiresIfExists: ["preventOverflow"] };
function re(e) {
  return e.split("-")[1];
}
var It = { top: "auto", right: "auto", bottom: "auto", left: "auto" };
function Nt(e, t) {
  var n = e.x, r = e.y, o = t.devicePixelRatio || 1;
  return { x: te(n * o) / o || 0, y: te(r * o) / o || 0 };
}
function dt(e) {
  var t, n = e.popper, r = e.popperRect, o = e.placement, a = e.variation, c = e.offsets, s = e.position, i = e.gpuAcceleration, f = e.adaptive, u = e.roundOffsets, m = e.isFixed, h = c.x, l = h === void 0 ? 0 : h, g = c.y, p = g === void 0 ? 0 : g, y = typeof u == "function" ? u({ x: l, y: p }) : { x: l, y: p };
  l = y.x, p = y.y;
  var b = c.hasOwnProperty("x"), x = c.hasOwnProperty("y"), O = P, d = L, v = window;
  if (f) {
    var w = se(n), $ = "clientHeight", j = "clientWidth";
    if (w === B(n) && (w = N(n), I(w).position !== "static" && s === "absolute" && ($ = "scrollHeight", j = "scrollWidth")), w = w, o === L || (o === P || o === T) && a === Z) {
      d = W;
      var D = m && w === v && v.visualViewport ? v.visualViewport.height : w[$];
      p -= D - r.height, p *= i ? 1 : -1;
    }
    if (o === P || (o === L || o === W) && a === Z) {
      O = T;
      var E = m && w === v && v.visualViewport ? v.visualViewport.width : w[j];
      l -= E - r.width, l *= i ? 1 : -1;
    }
  }
  var A = Object.assign({ position: s }, f && It), H = u === true ? Nt({ x: l, y: p }, B(n)) : { x: l, y: p };
  if (l = H.x, p = H.y, i) {
    var k;
    return Object.assign({}, A, (k = {}, k[d] = x ? "0" : "", k[O] = b ? "0" : "", k.transform = (v.devicePixelRatio || 1) <= 1 ? "translate(" + l + "px, " + p + "px)" : "translate3d(" + l + "px, " + p + "px, 0)", k));
  }
  return Object.assign({}, A, (t = {}, t[d] = x ? p + "px" : "", t[O] = b ? l + "px" : "", t.transform = "", t));
}
function Ft(e) {
  var t = e.state, n = e.options, r = n.gpuAcceleration, o = r === void 0 ? true : r, a = n.adaptive, c = a === void 0 ? true : a, s = n.roundOffsets, i = s === void 0 ? true : s, f = { placement: C(t.placement), variation: re(t.placement), popper: t.elements.popper, popperRect: t.rects.popper, gpuAcceleration: o, isFixed: t.options.strategy === "fixed" };
  t.modifiersData.popperOffsets != null && (t.styles.popper = Object.assign({}, t.styles.popper, dt(Object.assign({}, f, { offsets: t.modifiersData.popperOffsets, position: t.options.strategy, adaptive: c, roundOffsets: i })))), t.modifiersData.arrow != null && (t.styles.arrow = Object.assign({}, t.styles.arrow, dt(Object.assign({}, f, { offsets: t.modifiersData.arrow, position: "absolute", adaptive: false, roundOffsets: i })))), t.attributes.popper = Object.assign({}, t.attributes.popper, { "data-popper-placement": t.placement });
}
var We = { name: "computeStyles", enabled: true, phase: "beforeWrite", fn: Ft, data: {} }, ge = { passive: true };
function Ut(e) {
  var t = e.state, n = e.instance, r = e.options, o = r.scroll, a = o === void 0 ? true : o, c = r.resize, s = c === void 0 ? true : c, i = B(t.elements.popper), f = [].concat(t.scrollParents.reference, t.scrollParents.popper);
  return a && f.forEach(function(u) {
    u.addEventListener("scroll", n.update, ge);
  }), s && i.addEventListener("resize", n.update, ge), function() {
    a && f.forEach(function(u) {
      u.removeEventListener("scroll", n.update, ge);
    }), s && i.removeEventListener("resize", n.update, ge);
  };
}
var Te = { name: "eventListeners", enabled: true, phase: "write", fn: function() {
}, effect: Ut, data: {} }, _t = { left: "right", right: "left", bottom: "top", top: "bottom" };
function be(e) {
  return e.replace(/left|right|bottom|top/g, function(t) {
    return _t[t];
  });
}
var zt = { start: "end", end: "start" };
function ht(e) {
  return e.replace(/start|end/g, function(t) {
    return zt[t];
  });
}
function Be(e) {
  var t = B(e), n = t.pageXOffset, r = t.pageYOffset;
  return { scrollLeft: n, scrollTop: r };
}
function Re(e) {
  return ne(N(e)).left + Be(e).scrollLeft;
}
function Xt(e, t) {
  var n = B(e), r = N(e), o = n.visualViewport, a = r.clientWidth, c = r.clientHeight, s = 0, i = 0;
  if (o) {
    a = o.width, c = o.height;
    var f = at();
    (f || !f && t === "fixed") && (s = o.offsetLeft, i = o.offsetTop);
  }
  return { width: a, height: c, x: s + Re(e), y: i };
}
function Yt(e) {
  var t, n = N(e), r = Be(e), o = (t = e.ownerDocument) == null ? void 0 : t.body, a = J(n.scrollWidth, n.clientWidth, o ? o.scrollWidth : 0, o ? o.clientWidth : 0), c = J(n.scrollHeight, n.clientHeight, o ? o.scrollHeight : 0, o ? o.clientHeight : 0), s = -r.scrollLeft + Re(e), i = -r.scrollTop;
  return I(o || n).direction === "rtl" && (s += J(n.clientWidth, o ? o.clientWidth : 0) - a), { width: a, height: c, x: s, y: i };
}
function He(e) {
  var t = I(e), n = t.overflow, r = t.overflowX, o = t.overflowY;
  return /auto|scroll|overlay|hidden/.test(n + o + r);
}
function mt(e) {
  return ["html", "body", "#document"].indexOf(V(e)) >= 0 ? e.ownerDocument.body : R(e) && He(e) ? e : mt(ye(e));
}
function ce(e, t) {
  var n;
  t === void 0 && (t = []);
  var r = mt(e), o = r === ((n = e.ownerDocument) == null ? void 0 : n.body), a = B(r), c = o ? [a].concat(a.visualViewport || [], He(r) ? r : []) : r, s = t.concat(c);
  return o ? s : s.concat(ce(ye(c)));
}
function Se(e) {
  return Object.assign({}, e, { left: e.x, top: e.y, right: e.x + e.width, bottom: e.y + e.height });
}
function Gt(e, t) {
  var n = ne(e, false, t === "fixed");
  return n.top = n.top + e.clientTop, n.left = n.left + e.clientLeft, n.bottom = n.top + e.clientHeight, n.right = n.left + e.clientWidth, n.width = e.clientWidth, n.height = e.clientHeight, n.x = n.left, n.y = n.top, n;
}
function vt(e, t, n) {
  return t === je ? Se(Xt(e, n)) : G(t) ? Gt(t, n) : Se(Yt(N(e)));
}
function Jt(e) {
  var t = ce(ye(e)), n = ["absolute", "fixed"].indexOf(I(e).position) >= 0, r = n && R(e) ? se(e) : e;
  return G(r) ? t.filter(function(o) {
    return G(o) && st(o, r) && V(o) !== "body";
  }) : [];
}
function Kt(e, t, n, r) {
  var o = t === "clippingParents" ? Jt(e) : [].concat(t), a = [].concat(o, [n]), c = a[0], s = a.reduce(function(i, f) {
    var u = vt(e, f, r);
    return i.top = J(u.top, i.top), i.right = ve(u.right, i.right), i.bottom = ve(u.bottom, i.bottom), i.left = J(u.left, i.left), i;
  }, vt(e, c, r));
  return s.width = s.right - s.left, s.height = s.bottom - s.top, s.x = s.left, s.y = s.top, s;
}
function yt(e) {
  var t = e.reference, n = e.element, r = e.placement, o = r ? C(r) : null, a = r ? re(r) : null, c = t.x + t.width / 2 - n.width / 2, s = t.y + t.height / 2 - n.height / 2, i;
  switch (o) {
    case L:
      i = { x: c, y: t.y - n.height };
      break;
    case W:
      i = { x: c, y: t.y + t.height };
      break;
    case T:
      i = { x: t.x + t.width, y: s };
      break;
    case P:
      i = { x: t.x - n.width, y: s };
      break;
    default:
      i = { x: t.x, y: t.y };
  }
  var f = o ? Me(o) : null;
  if (f != null) {
    var u = f === "y" ? "height" : "width";
    switch (a) {
      case Y:
        i[f] = i[f] - (t[u] / 2 - n[u] / 2);
        break;
      case Z:
        i[f] = i[f] + (t[u] / 2 - n[u] / 2);
        break;
    }
  }
  return i;
}
function oe(e, t) {
  t === void 0 && (t = {});
  var n = t, r = n.placement, o = r === void 0 ? e.placement : r, a = n.strategy, c = a === void 0 ? e.strategy : a, s = n.boundary, i = s === void 0 ? Ye : s, f = n.rootBoundary, u = f === void 0 ? je : f, m = n.elementContext, h = m === void 0 ? ee : m, l = n.altBoundary, g = l === void 0 ? false : l, p = n.padding, y = p === void 0 ? 0 : p, b = ut(typeof y != "number" ? y : pt(y, Q)), x = h === ee ? Ge : ee, O = e.rects.popper, d = e.elements[g ? x : h], v = Kt(G(d) ? d : d.contextElement || N(e.elements.popper), i, u, c), w = ne(e.elements.reference), $ = yt({ reference: w, element: O, placement: o }), j = Se(Object.assign({}, O, $)), D = h === ee ? j : w, E = { top: v.top - D.top + b.top, bottom: D.bottom - v.bottom + b.bottom, left: v.left - D.left + b.left, right: D.right - v.right + b.right }, A = e.modifiersData.offset;
  if (h === ee && A) {
    var H = A[o];
    Object.keys(E).forEach(function(k) {
      var F = [T, W].indexOf(k) >= 0 ? 1 : -1, U = [L, W].indexOf(k) >= 0 ? "y" : "x";
      E[k] += H[U] * F;
    });
  }
  return E;
}
function Qt(e, t) {
  t === void 0 && (t = {});
  var n = t, r = n.placement, o = n.boundary, a = n.rootBoundary, c = n.padding, s = n.flipVariations, i = n.allowedAutoPlacements, f = i === void 0 ? Ee : i, u = re(r), m = u ? s ? De : De.filter(function(g) {
    return re(g) === u;
  }) : Q, h = m.filter(function(g) {
    return f.indexOf(g) >= 0;
  });
  h.length === 0 && (h = m);
  var l = h.reduce(function(g, p) {
    return g[p] = oe(e, { placement: p, boundary: o, rootBoundary: a, padding: c })[C(p)], g;
  }, {});
  return Object.keys(l).sort(function(g, p) {
    return l[g] - l[p];
  });
}
function Zt(e) {
  if (C(e) === me) return [];
  var t = be(e);
  return [ht(e), t, ht(t)];
}
function en(e) {
  var t = e.state, n = e.options, r = e.name;
  if (!t.modifiersData[r]._skip) {
    for (var o = n.mainAxis, a = o === void 0 ? true : o, c = n.altAxis, s = c === void 0 ? true : c, i = n.fallbackPlacements, f = n.padding, u = n.boundary, m = n.rootBoundary, h = n.altBoundary, l = n.flipVariations, g = l === void 0 ? true : l, p = n.allowedAutoPlacements, y = t.options.placement, b = C(y), x = b === y, O = i || (x || !g ? [be(y)] : Zt(y)), d = [y].concat(O).reduce(function(z, q) {
      return z.concat(C(q) === me ? Qt(t, { placement: q, boundary: u, rootBoundary: m, padding: f, flipVariations: g, allowedAutoPlacements: p }) : q);
    }, []), v = t.rects.reference, w = t.rects.popper, $ = /* @__PURE__ */ new Map(), j = true, D = d[0], E = 0; E < d.length; E++) {
      var A = d[E], H = C(A), k = re(A) === Y, F = [L, W].indexOf(H) >= 0, U = F ? "width" : "height", M = oe(t, { placement: A, boundary: u, rootBoundary: m, altBoundary: h, padding: f }), S = F ? k ? T : P : k ? W : L;
      v[U] > w[U] && (S = be(S));
      var ue = be(S), _ = [];
      if (a && _.push(M[H] <= 0), s && _.push(M[S] <= 0, M[ue] <= 0), _.every(function(z) {
        return z;
      })) {
        D = A, j = false;
        break;
      }
      $.set(A, _);
    }
    if (j) for (var pe = g ? 3 : 1, xe = function(z) {
      var q = d.find(function(de) {
        var ae = $.get(de);
        if (ae) return ae.slice(0, z).every(function(K) {
          return K;
        });
      });
      if (q) return D = q, "break";
    }, ie = pe; ie > 0; ie--) {
      var le = xe(ie);
      if (le === "break") break;
    }
    t.placement !== D && (t.modifiersData[r]._skip = true, t.placement = D, t.reset = true);
  }
}
var gt = { name: "flip", enabled: true, phase: "main", fn: en, requiresIfExists: ["offset"], data: { _skip: false } };
function bt(e, t, n) {
  return n === void 0 && (n = { x: 0, y: 0 }), { top: e.top - t.height - n.y, right: e.right - t.width + n.x, bottom: e.bottom - t.height + n.y, left: e.left - t.width - n.x };
}
function wt(e) {
  return [L, T, W, P].some(function(t) {
    return e[t] >= 0;
  });
}
function tn(e) {
  var t = e.state, n = e.name, r = t.rects.reference, o = t.rects.popper, a = t.modifiersData.preventOverflow, c = oe(t, { elementContext: "reference" }), s = oe(t, { altBoundary: true }), i = bt(c, r), f = bt(s, o, a), u = wt(i), m = wt(f);
  t.modifiersData[n] = { referenceClippingOffsets: i, popperEscapeOffsets: f, isReferenceHidden: u, hasPopperEscaped: m }, t.attributes.popper = Object.assign({}, t.attributes.popper, { "data-popper-reference-hidden": u, "data-popper-escaped": m });
}
var xt = { name: "hide", enabled: true, phase: "main", requiresIfExists: ["preventOverflow"], fn: tn };
function nn(e, t, n) {
  var r = C(e), o = [P, L].indexOf(r) >= 0 ? -1 : 1, a = typeof n == "function" ? n(Object.assign({}, t, { placement: e })) : n, c = a[0], s = a[1];
  return c = c || 0, s = (s || 0) * o, [P, T].indexOf(r) >= 0 ? { x: s, y: c } : { x: c, y: s };
}
function rn(e) {
  var t = e.state, n = e.options, r = e.name, o = n.offset, a = o === void 0 ? [0, 0] : o, c = Ee.reduce(function(u, m) {
    return u[m] = nn(m, t.rects, a), u;
  }, {}), s = c[t.placement], i = s.x, f = s.y;
  t.modifiersData.popperOffsets != null && (t.modifiersData.popperOffsets.x += i, t.modifiersData.popperOffsets.y += f), t.modifiersData[r] = c;
}
var Ot = { name: "offset", enabled: true, phase: "main", requires: ["popperOffsets"], fn: rn };
function on(e) {
  var t = e.state, n = e.name;
  t.modifiersData[n] = yt({ reference: t.rects.reference, element: t.rects.popper, placement: t.placement });
}
var Ve = { name: "popperOffsets", enabled: true, phase: "read", fn: on, data: {} };
function an(e) {
  return e === "x" ? "y" : "x";
}
function sn(e) {
  var t = e.state, n = e.options, r = e.name, o = n.mainAxis, a = o === void 0 ? true : o, c = n.altAxis, s = c === void 0 ? false : c, i = n.boundary, f = n.rootBoundary, u = n.altBoundary, m = n.padding, h = n.tether, l = h === void 0 ? true : h, g = n.tetherOffset, p = g === void 0 ? 0 : g, y = oe(t, { boundary: i, rootBoundary: f, padding: m, altBoundary: u }), b = C(t.placement), x = re(t.placement), O = !x, d = Me(b), v = an(d), w = t.modifiersData.popperOffsets, $ = t.rects.reference, j = t.rects.popper, D = typeof p == "function" ? p(Object.assign({}, t.rects, { placement: t.placement })) : p, E = typeof D == "number" ? { mainAxis: D, altAxis: D } : Object.assign({ mainAxis: 0, altAxis: 0 }, D), A = t.modifiersData.offset ? t.modifiersData.offset[t.placement] : null, H = { x: 0, y: 0 };
  if (w) {
    if (a) {
      var k, F = d === "y" ? L : P, U = d === "y" ? W : T, M = d === "y" ? "height" : "width", S = w[d], ue = S + y[F], _ = S - y[U], pe = l ? -j[M] / 2 : 0, xe = x === Y ? $[M] : j[M], ie = x === Y ? -j[M] : -$[M], le = t.elements.arrow, z = l && le ? Pe(le) : { width: 0, height: 0 }, q = t.modifiersData["arrow#persistent"] ? t.modifiersData["arrow#persistent"].padding : ct(), de = q[F], ae = q[U], K = fe(0, $[M], z[M]), Et = O ? $[M] / 2 - pe - K - de - E.mainAxis : xe - K - de - E.mainAxis, At = O ? -$[M] / 2 + pe + K + ae + E.mainAxis : ie + K + ae + E.mainAxis, Oe = t.elements.arrow && se(t.elements.arrow), kt = Oe ? d === "y" ? Oe.clientTop || 0 : Oe.clientLeft || 0 : 0, Ce = (k = A == null ? void 0 : A[d]) != null ? k : 0, Lt = S + Et - Ce - kt, Pt = S + At - Ce, qe = fe(l ? ve(ue, Lt) : ue, S, l ? J(_, Pt) : _);
      w[d] = qe, H[d] = qe - S;
    }
    if (s) {
      var Ie, Mt = d === "x" ? L : P, Wt = d === "x" ? W : T, X = w[v], he = v === "y" ? "height" : "width", Ne = X + y[Mt], Fe = X - y[Wt], $e = [L, P].indexOf(b) !== -1, Ue = (Ie = A == null ? void 0 : A[v]) != null ? Ie : 0, _e = $e ? Ne : X - $[he] - j[he] - Ue + E.altAxis, ze = $e ? X + $[he] + j[he] - Ue - E.altAxis : Fe, Xe = l && $e ? St(_e, X, ze) : fe(l ? _e : Ne, X, l ? ze : Fe);
      w[v] = Xe, H[v] = Xe - X;
    }
    t.modifiersData[r] = H;
  }
}
var $t = { name: "preventOverflow", enabled: true, phase: "main", fn: sn, requiresIfExists: ["offset"] };
function fn(e) {
  return { scrollLeft: e.scrollLeft, scrollTop: e.scrollTop };
}
function cn(e) {
  return e === B(e) || !R(e) ? Be(e) : fn(e);
}
function un(e) {
  var t = e.getBoundingClientRect(), n = te(t.width) / e.offsetWidth || 1, r = te(t.height) / e.offsetHeight || 1;
  return n !== 1 || r !== 1;
}
function pn(e, t, n) {
  n === void 0 && (n = false);
  var r = R(t), o = R(t) && un(t), a = N(t), c = ne(e, o, n), s = { scrollLeft: 0, scrollTop: 0 }, i = { x: 0, y: 0 };
  return (r || !r && !n) && ((V(t) !== "body" || He(a)) && (s = cn(t)), R(t) ? (i = ne(t, true), i.x += t.clientLeft, i.y += t.clientTop) : a && (i.x = Re(a))), { x: c.left + s.scrollLeft - i.x, y: c.top + s.scrollTop - i.y, width: c.width, height: c.height };
}
function ln(e) {
  var t = /* @__PURE__ */ new Map(), n = /* @__PURE__ */ new Set(), r = [];
  e.forEach(function(a) {
    t.set(a.name, a);
  });
  function o(a) {
    n.add(a.name);
    var c = [].concat(a.requires || [], a.requiresIfExists || []);
    c.forEach(function(s) {
      if (!n.has(s)) {
        var i = t.get(s);
        i && o(i);
      }
    }), r.push(a);
  }
  return e.forEach(function(a) {
    n.has(a.name) || o(a);
  }), r;
}
function dn(e) {
  var t = ln(e);
  return it.reduce(function(n, r) {
    return n.concat(t.filter(function(o) {
      return o.phase === r;
    }));
  }, []);
}
function hn(e) {
  var t;
  return function() {
    return t || (t = new Promise(function(n) {
      Promise.resolve().then(function() {
        t = void 0, n(e());
      });
    })), t;
  };
}
function mn(e) {
  var t = e.reduce(function(n, r) {
    var o = n[r.name];
    return n[r.name] = o ? Object.assign({}, o, r, { options: Object.assign({}, o.options, r.options), data: Object.assign({}, o.data, r.data) }) : r, n;
  }, {});
  return Object.keys(t).map(function(n) {
    return t[n];
  });
}
var jt = { placement: "bottom", modifiers: [], strategy: "absolute" };
function Dt() {
  for (var e = arguments.length, t = new Array(e), n = 0; n < e; n++) t[n] = arguments[n];
  return !t.some(function(r) {
    return !(r && typeof r.getBoundingClientRect == "function");
  });
}
function we(e) {
  e === void 0 && (e = {});
  var t = e, n = t.defaultModifiers, r = n === void 0 ? [] : n, o = t.defaultOptions, a = o === void 0 ? jt : o;
  return function(c, s, i) {
    i === void 0 && (i = a);
    var f = { placement: "bottom", orderedModifiers: [], options: Object.assign({}, jt, a), modifiersData: {}, elements: { reference: c, popper: s }, attributes: {}, styles: {} }, u = [], m = false, h = { state: f, setOptions: function(p) {
      var y = typeof p == "function" ? p(f.options) : p;
      g(), f.options = Object.assign({}, a, f.options, y), f.scrollParents = { reference: G(c) ? ce(c) : c.contextElement ? ce(c.contextElement) : [], popper: ce(s) };
      var b = dn(mn([].concat(r, f.options.modifiers)));
      return f.orderedModifiers = b.filter(function(x) {
        return x.enabled;
      }), l(), h.update();
    }, forceUpdate: function() {
      if (!m) {
        var p = f.elements, y = p.reference, b = p.popper;
        if (Dt(y, b)) {
          f.rects = { reference: pn(y, se(b), f.options.strategy === "fixed"), popper: Pe(b) }, f.reset = false, f.placement = f.options.placement, f.orderedModifiers.forEach(function(j) {
            return f.modifiersData[j.name] = Object.assign({}, j.data);
          });
          for (var x = 0; x < f.orderedModifiers.length; x++) {
            if (f.reset === true) {
              f.reset = false, x = -1;
              continue;
            }
            var O = f.orderedModifiers[x], d = O.fn, v = O.options, w = v === void 0 ? {} : v, $ = O.name;
            typeof d == "function" && (f = d({ state: f, options: w, name: $, instance: h }) || f);
          }
        }
      }
    }, update: hn(function() {
      return new Promise(function(p) {
        h.forceUpdate(), p(f);
      });
    }), destroy: function() {
      g(), m = true;
    } };
    if (!Dt(c, s)) return h;
    h.setOptions(i).then(function(p) {
      !m && i.onFirstUpdate && i.onFirstUpdate(p);
    });
    function l() {
      f.orderedModifiers.forEach(function(p) {
        var y = p.name, b = p.options, x = b === void 0 ? {} : b, O = p.effect;
        if (typeof O == "function") {
          var d = O({ state: f, name: y, instance: h, options: x }), v = function() {
          };
          u.push(d || v);
        }
      });
    }
    function g() {
      u.forEach(function(p) {
        return p();
      }), u = [];
    }
    return h;
  };
}
we();
var yn = [Te, Ve, We, ke];
we({ defaultModifiers: yn });
var bn = [Te, Ve, We, ke, Ot, gt, $t, lt, xt], wn = we({ defaultModifiers: bn });
const popperCoreConfigProps = buildProps({
  boundariesPadding: {
    type: Number,
    default: 0
  },
  fallbackPlacements: {
    type: definePropType(Array),
    default: void 0
  },
  gpuAcceleration: {
    type: Boolean,
    default: true
  },
  /**
  * @description offset of the Tooltip
  */
  offset: {
    type: Number,
    default: 12
  },
  /**
  * @description position of Tooltip
  */
  placement: {
    type: String,
    values: Ee,
    default: "bottom"
  },
  /**
  * @description [popper.js](https://popper.js.org/docs/v2/) parameters
  */
  popperOptions: {
    type: definePropType(Object),
    default: () => ({})
  },
  strategy: {
    type: String,
    values: ["fixed", "absolute"],
    default: "absolute"
  }
});
const popperContentProps = buildProps({
  ...popperCoreConfigProps,
  ...popperArrowProps,
  id: String,
  style: {
    type: definePropType([
      String,
      Array,
      Object,
      Boolean
    ]),
    default: void 0
  },
  className: { type: definePropType([
    String,
    Array,
    Object
  ]) },
  effect: {
    type: definePropType(String),
    default: "dark"
  },
  visible: Boolean,
  enterable: {
    type: Boolean,
    default: true
  },
  pure: Boolean,
  focusOnShow: Boolean,
  trapping: Boolean,
  popperClass: { type: definePropType([
    String,
    Array,
    Object
  ]) },
  popperStyle: {
    type: definePropType([
      String,
      Array,
      Object,
      Boolean
    ]),
    default: void 0
  },
  referenceEl: { type: definePropType(Object) },
  triggerTargetEl: { type: definePropType(Object) },
  stopPopperMouseEvent: {
    type: Boolean,
    default: true
  },
  virtualTriggering: Boolean,
  zIndex: Number,
  ...useAriaProps(["ariaLabel"]),
  loop: Boolean
});
const popperContentEmits = {
  mouseenter: (evt) => evt instanceof MouseEvent,
  mouseleave: (evt) => evt instanceof MouseEvent,
  focus: () => true,
  blur: () => true,
  close: () => true
};
const useTooltipContentProps = buildProps({
  ...useDelayedToggleProps,
  ...popperContentProps,
  /**
  * @description which element the tooltip CONTENT appends to
  */
  appendTo: { type: definePropType([String, Object]) },
  /**
  * @description display content, can be overridden by `slot#content`
  */
  content: {
    type: String,
    default: ""
  },
  /**
  * @description whether `content` is treated as HTML string
  */
  rawContent: Boolean,
  /**
  * @description when tooltip inactive and `persistent` is `false` , popconfirm will be destroyed
  */
  persistent: Boolean,
  /**
  * @description visibility of Tooltip
  */
  visible: {
    type: definePropType(Boolean),
    default: null
  },
  /**
  * @description animation name
  */
  transition: String,
  /**
  * @description whether tooltip content is teleported, if `true` it will be teleported to where `append-to` sets
  */
  teleported: {
    type: Boolean,
    default: true
  },
  /**
  * @description whether Tooltip is disabled
  */
  disabled: Boolean,
  ...useAriaProps(["ariaLabel"])
});
const popperTriggerProps = buildProps({
  /** @description Indicates the reference element to which the popper is attached */
  virtualRef: { type: definePropType(Object) },
  /** @description Indicates whether virtual triggering is enabled */
  virtualTriggering: Boolean,
  onMouseenter: { type: definePropType(Function) },
  onMouseleave: { type: definePropType(Function) },
  onClick: { type: definePropType(Function) },
  onKeydown: { type: definePropType(Function) },
  onFocus: { type: definePropType(Function) },
  onBlur: { type: definePropType(Function) },
  onContextmenu: { type: definePropType(Function) },
  id: String,
  open: Boolean
});
const useTooltipTriggerProps = buildProps({
  ...popperTriggerProps,
  /**
  * @description whether Tooltip is disabled
  */
  disabled: Boolean,
  /**
  * @description How should the tooltip be triggered (to show), not valid in controlled mode
  */
  trigger: {
    type: definePropType([String, Array]),
    default: "hover"
  },
  /**
  * @description When you click the mouse to focus on the trigger element, you can define a set of keyboard codes to control the display of tooltip through the keyboard, not valid in controlled mode
  */
  triggerKeys: {
    type: definePropType(Array),
    default: () => [
      EVENT_CODE.enter,
      EVENT_CODE.numpadEnter,
      EVENT_CODE.space
    ]
  },
  /**
  * @description when triggering tooltips through hover, whether to focus the trigger element, which improves accessibility
  */
  focusOnTarget: Boolean
});
const _prop = buildProp({
  type: definePropType(Boolean),
  default: null
});
const _event = buildProp({ type: definePropType(Function) });
const createModelToggleComposable = (name) => {
  const updateEventKey = `update:${name}`;
  const updateEventKeyRaw = `onUpdate:${name}`;
  const useModelToggleEmits = [updateEventKey];
  const useModelToggleProps = {
    [name]: _prop,
    [updateEventKeyRaw]: _event
  };
  const useModelToggle = ({ indicator, toggleReason, shouldHideWhenRouteChanges, shouldProceed, onShow, onHide }) => {
    const instance = getCurrentInstance();
    const { emit } = instance;
    const props = instance.props;
    const hasUpdateHandler = computed(() => isFunction(props[updateEventKeyRaw]));
    const isModelBindingAbsent = computed(() => props[name] === null);
    const doShow = (event) => {
      if (indicator.value === true) return;
      indicator.value = true;
      if (toggleReason) toggleReason.value = event;
      if (isFunction(onShow)) onShow(event);
    };
    const doHide = (event) => {
      if (indicator.value === false) return;
      indicator.value = false;
      if (toggleReason) toggleReason.value = event;
      if (isFunction(onHide)) onHide(event);
    };
    const show = (event) => {
      if (props.disabled === true || isFunction(shouldProceed) && !shouldProceed()) return;
      const shouldEmit = hasUpdateHandler.value && isClient;
      if (shouldEmit) emit(updateEventKey, true);
      if (isModelBindingAbsent.value || !shouldEmit) doShow(event);
    };
    const hide = (event) => {
      if (props.disabled === true || !isClient) return;
      const shouldEmit = hasUpdateHandler.value && isClient;
      if (shouldEmit) emit(updateEventKey, false);
      if (isModelBindingAbsent.value || !shouldEmit) doHide(event);
    };
    const onChange = (val) => {
      if (!isBoolean(val)) return;
      if (props.disabled && val) {
        if (hasUpdateHandler.value) emit(updateEventKey, false);
      } else if (indicator.value !== val) if (val) doShow();
      else doHide();
    };
    const toggle = () => {
      if (indicator.value) hide();
      else show();
    };
    watch(() => props[name], onChange);
    if (shouldHideWhenRouteChanges && instance.appContext.config.globalProperties.$route !== void 0) watch(() => ({ ...instance.proxy.$route }), () => {
      if (shouldHideWhenRouteChanges.value && indicator.value) hide();
    });
    onMounted(() => {
      onChange(props[name]);
    });
    return {
      hide,
      show,
      toggle,
      hasUpdateHandler
    };
  };
  return {
    useModelToggle,
    useModelToggleProps,
    useModelToggleEmits
  };
};
const roleTypes = [
  "dialog",
  "grid",
  "group",
  "listbox",
  "menu",
  "navigation",
  "tooltip",
  "tree"
];
const popperProps = buildProps({ role: {
  type: String,
  values: roleTypes,
  default: "tooltip"
} });
const { useModelToggleProps: useTooltipModelToggleProps, useModelToggleEmits: useTooltipModelToggleEmits, useModelToggle: useTooltipModelToggle } = createModelToggleComposable("visible");
const useTooltipProps = buildProps({
  ...popperProps,
  ...useTooltipModelToggleProps,
  ...useTooltipContentProps,
  ...useTooltipTriggerProps,
  ...popperArrowProps,
  /**
  * @description whether the tooltip content has an arrow
  */
  showArrow: {
    type: Boolean,
    default: true
  }
});
const tooltipEmits = [
  ...useTooltipModelToggleEmits,
  "before-show",
  "before-hide",
  "show",
  "hide",
  "open",
  "close"
];
const TOOLTIP_INJECTION_KEY = Symbol("elTooltip");
const usePopperContainerId = () => {
  const namespace = useGetDerivedNamespace();
  const idInjection = useIdInjection();
  const id = computed(() => {
    return `${namespace.value}-popper-container-${idInjection.prefix}`;
  });
  return {
    id,
    selector: computed(() => `#${id.value}`)
  };
};
const createContainer = (id) => {
  const container = document.createElement("div");
  container.id = id;
  document.body.appendChild(container);
  return container;
};
const usePopperContainer = () => {
  const { id, selector } = usePopperContainerId();
  onBeforeMount(() => {
    if (!isClient) return;
    if (!document.body.querySelector(selector.value)) createContainer(id.value);
  });
  return {
    id,
    selector
  };
};
const POPPER_INJECTION_KEY = Symbol("popper");
const POPPER_CONTENT_INJECTION_KEY = Symbol("popperContent");
var arrow_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: "ElPopperArrow",
  inheritAttrs: false,
  __name: "arrow",
  setup(__props, { expose: __expose }) {
    const ns = useNamespace("popper");
    const { arrowRef, arrowStyle } = inject(POPPER_CONTENT_INJECTION_KEY, void 0);
    onBeforeUnmount(() => {
      arrowRef.value = void 0;
    });
    __expose({
      /**
      * @description Arrow element
      */
      arrowRef
    });
    return (_ctx, _cache) => {
      return openBlock(), createElementBlock("span", {
        ref_key: "arrowRef",
        ref: arrowRef,
        class: normalizeClass(unref(ns).e("arrow")),
        style: normalizeStyle(unref(arrowStyle)),
        "data-popper-arrow": ""
      }, null, 6);
    };
  }
});
var arrow_default = arrow_vue_vue_type_script_setup_true_lang_default;
var popper_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: "ElPopper",
  inheritAttrs: false,
  __name: "popper",
  props: popperProps,
  setup(__props, { expose: __expose }) {
    const props = __props;
    const popperProvides = {
      /**
      * @description trigger element
      */
      triggerRef: ref(),
      /**
      * @description popperjs instance
      */
      popperInstanceRef: ref(),
      /**
      * @description popper content element
      */
      contentRef: ref(),
      /**
      * @description popper reference element
      */
      referenceRef: ref(),
      /**
      * @description role determines how aria attributes are distributed
      */
      role: computed(() => props.role)
    };
    __expose(popperProvides);
    provide(POPPER_INJECTION_KEY, popperProvides);
    return (_ctx, _cache) => {
      return renderSlot(_ctx.$slots, "default");
    };
  }
});
var popper_default = popper_vue_vue_type_script_setup_true_lang_default;
const FORWARD_REF_INJECTION_KEY = Symbol("elForwardRef");
const useForwardRef = (forwardRef) => {
  const setForwardRef = (el) => {
    forwardRef.value = el;
  };
  provide(FORWARD_REF_INJECTION_KEY, { setForwardRef });
};
const useForwardRefDirective = (setForwardRef) => {
  return {
    mounted(el) {
      setForwardRef(el);
    },
    updated(el) {
      setForwardRef(el);
    },
    unmounted() {
      setForwardRef(null);
    }
  };
};
const NAME = "ElOnlyChild";
const OnlyChild = /* @__PURE__ */ defineComponent({
  name: NAME,
  setup(_, { slots, attrs }) {
    var _a;
    const forwardRefDirective = useForwardRefDirective(((_a = inject(FORWARD_REF_INJECTION_KEY)) == null ? void 0 : _a.setForwardRef) ?? NOOP);
    return () => {
      var _a2;
      const defaultSlot = (_a2 = slots.default) == null ? void 0 : _a2.call(slots, attrs);
      if (!defaultSlot) return null;
      const [firstLegitNode, length] = findFirstLegitChild(defaultSlot);
      if (!firstLegitNode) {
        debugWarn(NAME, "no valid child node found");
        return null;
      }
      if (length > 1) debugWarn(NAME, "requires exact only one valid child.");
      return withDirectives(cloneVNode(firstLegitNode, attrs), [[forwardRefDirective]]);
    };
  }
});
function findFirstLegitChild(node) {
  if (!node) return [null, 0];
  const children = node;
  const len = children.filter((c) => c.type !== Comment).length;
  for (const child of children) {
    if (isObject(child)) switch (child.type) {
      case Comment:
        continue;
      case Text:
      case "svg":
        return [wrapTextContent(child), len];
      case Fragment:
        return findFirstLegitChild(child.children);
      default:
        return [child, len];
    }
    return [wrapTextContent(child), len];
  }
  return [null, 0];
}
function wrapTextContent(s) {
  return createVNode("span", { "class": useNamespace("only-child").e("content") }, [s]);
}
var trigger_vue_vue_type_script_setup_true_lang_default$1 = /* @__PURE__ */ defineComponent({
  name: "ElPopperTrigger",
  inheritAttrs: false,
  __name: "trigger",
  props: popperTriggerProps,
  setup(__props, { expose: __expose }) {
    const props = __props;
    const { role, triggerRef } = inject(POPPER_INJECTION_KEY, void 0);
    useForwardRef(triggerRef);
    const ariaControls = computed(() => {
      return ariaHaspopup.value ? props.id : void 0;
    });
    const ariaDescribedby = computed(() => {
      if (role && role.value === "tooltip") return props.open && props.id ? props.id : void 0;
    });
    const ariaHaspopup = computed(() => {
      if (role && role.value !== "tooltip") return role.value;
    });
    const ariaExpanded = computed(() => {
      return ariaHaspopup.value ? `${props.open}` : void 0;
    });
    let virtualTriggerAriaStopWatch = void 0;
    const TRIGGER_ELE_EVENTS = [
      "onMouseenter",
      "onMouseleave",
      "onClick",
      "onKeydown",
      "onFocus",
      "onBlur",
      "onContextmenu"
    ];
    onMounted(() => {
      watch(() => props.virtualRef, (virtualEl) => {
        if (virtualEl) triggerRef.value = unrefElement(virtualEl);
      }, { immediate: true });
      watch(triggerRef, (el, prevEl) => {
        virtualTriggerAriaStopWatch == null ? void 0 : virtualTriggerAriaStopWatch();
        virtualTriggerAriaStopWatch = void 0;
        if (isElement(prevEl)) TRIGGER_ELE_EVENTS.forEach((eventName) => {
          const handler = props[eventName];
          if (handler) prevEl.removeEventListener(eventName.slice(2).toLowerCase(), handler, ["onFocus", "onBlur"].includes(eventName));
        });
        if (isElement(el)) {
          TRIGGER_ELE_EVENTS.forEach((eventName) => {
            const handler = props[eventName];
            if (handler) el.addEventListener(eventName.slice(2).toLowerCase(), handler, ["onFocus", "onBlur"].includes(eventName));
          });
          if (isFocusable(el)) virtualTriggerAriaStopWatch = watch([
            ariaControls,
            ariaDescribedby,
            ariaHaspopup,
            ariaExpanded
          ], (watches) => {
            [
              "aria-controls",
              "aria-describedby",
              "aria-haspopup",
              "aria-expanded"
            ].forEach((key, idx) => {
              isNil(watches[idx]) ? el.removeAttribute(key) : el.setAttribute(key, watches[idx]);
            });
          }, { immediate: true });
        }
        if (isElement(prevEl) && isFocusable(prevEl)) [
          "aria-controls",
          "aria-describedby",
          "aria-haspopup",
          "aria-expanded"
        ].forEach((key) => prevEl.removeAttribute(key));
      }, { immediate: true });
    });
    onBeforeUnmount(() => {
      virtualTriggerAriaStopWatch == null ? void 0 : virtualTriggerAriaStopWatch();
      virtualTriggerAriaStopWatch = void 0;
      if (triggerRef.value && isElement(triggerRef.value)) {
        const el = triggerRef.value;
        TRIGGER_ELE_EVENTS.forEach((eventName) => {
          const handler = props[eventName];
          if (handler) el.removeEventListener(eventName.slice(2).toLowerCase(), handler, ["onFocus", "onBlur"].includes(eventName));
        });
        triggerRef.value = void 0;
      }
    });
    __expose({
      /**
      * @description trigger element
      */
      triggerRef
    });
    return (_ctx, _cache) => {
      return !__props.virtualTriggering ? (openBlock(), createBlock(unref(OnlyChild), mergeProps({ key: 0 }, _ctx.$attrs, {
        "aria-controls": ariaControls.value,
        "aria-describedby": ariaDescribedby.value,
        "aria-expanded": ariaExpanded.value,
        "aria-haspopup": ariaHaspopup.value
      }), {
        default: withCtx(() => [renderSlot(_ctx.$slots, "default")]),
        _: 3
      }, 16, [
        "aria-controls",
        "aria-describedby",
        "aria-expanded",
        "aria-haspopup"
      ])) : createCommentVNode("v-if", true);
    };
  }
});
var trigger_default$1 = trigger_vue_vue_type_script_setup_true_lang_default$1;
const usePopper = (referenceElementRef, popperElementRef, opts = {}) => {
  const stateUpdater = {
    name: "updateState",
    enabled: true,
    phase: "write",
    fn: ({ state }) => {
      const derivedState = deriveState(state);
      Object.assign(states.value, derivedState);
    },
    requires: ["computeStyles"]
  };
  const options = computed(() => {
    const { onFirstUpdate, placement, strategy, modifiers } = unref(opts);
    return {
      onFirstUpdate,
      placement: placement || "bottom",
      strategy: strategy || "absolute",
      modifiers: [
        ...modifiers || [],
        stateUpdater,
        {
          name: "applyStyles",
          enabled: false
        }
      ]
    };
  });
  const instanceRef = shallowRef();
  const states = ref({
    styles: {
      popper: {
        position: unref(options).strategy,
        left: "0",
        top: "0"
      },
      arrow: { position: "absolute" }
    },
    attributes: {}
  });
  const destroy = () => {
    if (!instanceRef.value) return;
    instanceRef.value.destroy();
    instanceRef.value = void 0;
  };
  watch(options, (newOptions) => {
    const instance = unref(instanceRef);
    if (instance) instance.setOptions(newOptions);
  }, { deep: true });
  watch([referenceElementRef, popperElementRef], ([referenceElement, popperElement]) => {
    destroy();
    if (!referenceElement || !popperElement) return;
    instanceRef.value = wn(referenceElement, popperElement, unref(options));
  });
  onBeforeUnmount(() => {
    destroy();
  });
  return {
    state: computed(() => {
      var _a;
      return { ...((_a = unref(instanceRef)) == null ? void 0 : _a.state) || {} };
    }),
    styles: computed(() => unref(states).styles),
    attributes: computed(() => unref(states).attributes),
    update: () => {
      var _a;
      return (_a = unref(instanceRef)) == null ? void 0 : _a.update();
    },
    forceUpdate: () => {
      var _a;
      return (_a = unref(instanceRef)) == null ? void 0 : _a.forceUpdate();
    },
    instanceRef: computed(() => unref(instanceRef))
  };
};
function deriveState(state) {
  const elements = Object.keys(state.elements);
  return {
    styles: fromPairs(elements.map((element) => [element, state.styles[element] || {}])),
    attributes: fromPairs(elements.map((element) => [element, state.attributes[element]]))
  };
}
const buildPopperOptions = (props, modifiers = []) => {
  const { placement, strategy, popperOptions } = props;
  const options = {
    placement,
    strategy,
    ...popperOptions,
    modifiers: [...genModifiers(props), ...modifiers]
  };
  deriveExtraModifiers(options, popperOptions == null ? void 0 : popperOptions.modifiers);
  return options;
};
const unwrapMeasurableEl = ($el) => {
  if (!isClient) return;
  return unrefElement($el);
};
function genModifiers(options) {
  const { offset, gpuAcceleration, fallbackPlacements } = options;
  return [
    {
      name: "offset",
      options: { offset: [0, offset ?? 12] }
    },
    {
      name: "preventOverflow",
      options: { padding: {
        top: 0,
        bottom: 0,
        left: 0,
        right: 0
      } }
    },
    {
      name: "flip",
      options: {
        padding: 5,
        fallbackPlacements
      }
    },
    {
      name: "computeStyles",
      options: { gpuAcceleration }
    }
  ];
}
function deriveExtraModifiers(options, modifiers) {
  if (modifiers) options.modifiers = [...options.modifiers, ...modifiers ?? []];
}
const DEFAULT_ARROW_OFFSET = 0;
const usePopperContent = (props) => {
  const { popperInstanceRef, contentRef, triggerRef, role } = inject(POPPER_INJECTION_KEY, void 0);
  const arrowRef = ref();
  const arrowOffset = computed(() => props.arrowOffset);
  const eventListenerModifier = computed(() => {
    return {
      name: "eventListeners",
      enabled: !!props.visible
    };
  });
  const arrowModifier = computed(() => {
    const arrowEl = unref(arrowRef);
    const offset = unref(arrowOffset) ?? DEFAULT_ARROW_OFFSET;
    return {
      name: "arrow",
      enabled: !isUndefined(arrowEl),
      options: {
        element: arrowEl,
        padding: offset
      }
    };
  });
  const options = computed(() => {
    return {
      onFirstUpdate: () => {
        update();
      },
      ...buildPopperOptions(props, [unref(arrowModifier), unref(eventListenerModifier)])
    };
  });
  const computedReference = computed(() => unwrapMeasurableEl(props.referenceEl) || unref(triggerRef));
  const { attributes, state, styles, update, forceUpdate, instanceRef } = usePopper(computedReference, contentRef, options);
  watch(instanceRef, (instance) => popperInstanceRef.value = instance, { flush: "sync" });
  onMounted(() => {
    watch(() => {
      var _a, _b;
      return (_b = (_a = unref(computedReference)) == null ? void 0 : _a.getBoundingClientRect) == null ? void 0 : _b.call(_a);
    }, () => {
      update();
    });
  });
  let stopResizeObserver;
  watch(() => props.visible, (visible) => {
    stopResizeObserver == null ? void 0 : stopResizeObserver();
    stopResizeObserver = void 0;
    if (visible) stopResizeObserver = useResizeObserver(contentRef, update).stop;
  });
  onBeforeUnmount(() => {
    popperInstanceRef.value = void 0;
    stopResizeObserver == null ? void 0 : stopResizeObserver();
    stopResizeObserver = void 0;
  });
  return {
    attributes,
    arrowRef,
    contentRef,
    instanceRef,
    state,
    styles,
    role,
    forceUpdate,
    update
  };
};
const usePopperContentDOM = (props, { attributes, styles, role }) => {
  const { nextZIndex } = useZIndex();
  const ns = useNamespace("popper");
  const contentAttrs = computed(() => unref(attributes).popper);
  const contentZIndex = ref(isNumber(props.zIndex) ? props.zIndex : nextZIndex());
  const contentClass = computed(() => [
    ns.b(),
    ns.is("pure", props.pure),
    ns.is(props.effect),
    props.popperClass
  ]);
  const contentStyle = computed(() => {
    return [
      { zIndex: unref(contentZIndex) },
      unref(styles).popper,
      props.popperStyle || {}
    ];
  });
  const ariaModal = computed(() => role.value === "dialog" ? "false" : void 0);
  const arrowStyle = computed(() => unref(styles).arrow || {});
  const updateZIndex = () => {
    contentZIndex.value = isNumber(props.zIndex) ? props.zIndex : nextZIndex();
  };
  return {
    ariaModal,
    arrowStyle,
    contentAttrs,
    contentClass,
    contentStyle,
    contentZIndex,
    updateZIndex
  };
};
const usePopperContentFocusTrap = (props, emit) => {
  const trapped = ref(false);
  const focusStartRef = ref();
  const onFocusAfterTrapped = () => {
    emit("focus");
  };
  const onFocusAfterReleased = (event) => {
    var _a;
    if (((_a = event.detail) == null ? void 0 : _a.focusReason) !== "pointer") {
      focusStartRef.value = "first";
      emit("blur");
    }
  };
  const onFocusInTrap = (event) => {
    if (props.visible && !trapped.value) {
      if (event.target) focusStartRef.value = event.target;
      trapped.value = true;
    }
  };
  const onFocusoutPrevented = (event) => {
    if (!props.trapping) {
      if (event.detail.focusReason === "pointer") event.preventDefault();
      trapped.value = false;
    }
  };
  const onReleaseRequested = () => {
    trapped.value = false;
    emit("close");
  };
  onBeforeUnmount(() => {
    focusStartRef.value = void 0;
  });
  return {
    focusStartRef,
    trapped,
    onFocusAfterReleased,
    onFocusAfterTrapped,
    onFocusInTrap,
    onFocusoutPrevented,
    onReleaseRequested
  };
};
var content_vue_vue_type_script_setup_true_lang_default$1 = /* @__PURE__ */ defineComponent({
  name: "ElPopperContent",
  __name: "content",
  props: popperContentProps,
  emits: popperContentEmits,
  setup(__props, { expose: __expose, emit: __emit }) {
    const emit = __emit;
    const props = __props;
    const { focusStartRef, trapped, onFocusAfterReleased, onFocusAfterTrapped, onFocusInTrap, onFocusoutPrevented, onReleaseRequested } = usePopperContentFocusTrap(props, emit);
    const { attributes, arrowRef, contentRef, styles, instanceRef, role, update } = usePopperContent(props);
    const { ariaModal, arrowStyle, contentAttrs, contentClass, contentStyle, updateZIndex } = usePopperContentDOM(props, {
      styles,
      attributes,
      role
    });
    const formItemContext = inject(formItemContextKey, void 0);
    provide(POPPER_CONTENT_INJECTION_KEY, {
      arrowStyle,
      arrowRef
    });
    if (formItemContext) provide(formItemContextKey, {
      ...formItemContext,
      addInputId: NOOP,
      removeInputId: NOOP
    });
    let triggerTargetAriaStopWatch = void 0;
    const updatePopper = (shouldUpdateZIndex = true) => {
      update();
      shouldUpdateZIndex && updateZIndex();
    };
    const togglePopperAlive = () => {
      updatePopper(false);
      if (props.visible && props.focusOnShow) trapped.value = true;
      else if (props.visible === false) trapped.value = false;
    };
    onMounted(() => {
      watch(() => props.triggerTargetEl, (triggerTargetEl, prevTriggerTargetEl) => {
        triggerTargetAriaStopWatch == null ? void 0 : triggerTargetAriaStopWatch();
        triggerTargetAriaStopWatch = void 0;
        const el = unref(triggerTargetEl || contentRef.value);
        const prevEl = unref(prevTriggerTargetEl || contentRef.value);
        if (isElement(el)) triggerTargetAriaStopWatch = watch([
          role,
          () => props.ariaLabel,
          ariaModal,
          () => props.id
        ], (watches) => {
          [
            "role",
            "aria-label",
            "aria-modal",
            "id"
          ].forEach((key, idx) => {
            isNil(watches[idx]) ? el.removeAttribute(key) : el.setAttribute(key, watches[idx]);
          });
        }, { immediate: true });
        if (prevEl !== el && isElement(prevEl)) [
          "role",
          "aria-label",
          "aria-modal",
          "id"
        ].forEach((key) => {
          prevEl.removeAttribute(key);
        });
      }, { immediate: true });
      watch(() => props.visible, togglePopperAlive, { immediate: true });
    });
    onBeforeUnmount(() => {
      triggerTargetAriaStopWatch == null ? void 0 : triggerTargetAriaStopWatch();
      triggerTargetAriaStopWatch = void 0;
      contentRef.value = void 0;
    });
    __expose({
      /**
      * @description popper content element
      */
      popperContentRef: contentRef,
      /**
      * @description popperjs instance
      */
      popperInstanceRef: instanceRef,
      /**
      * @description method for updating popper
      */
      updatePopper,
      /**
      * @description content style
      */
      contentStyle
    });
    return (_ctx, _cache) => {
      return openBlock(), createElementBlock("div", mergeProps({
        ref_key: "contentRef",
        ref: contentRef
      }, unref(contentAttrs), {
        style: unref(contentStyle),
        class: unref(contentClass),
        tabindex: "-1",
        onMouseenter: _cache[0] || (_cache[0] = (e) => _ctx.$emit("mouseenter", e)),
        onMouseleave: _cache[1] || (_cache[1] = (e) => _ctx.$emit("mouseleave", e))
      }), [createVNode(unref(focus_trap_default$1), {
        loop: __props.loop,
        trapped: unref(trapped),
        "trap-on-focus-in": true,
        "focus-trap-el": unref(contentRef),
        "focus-start-el": unref(focusStartRef),
        onFocusAfterTrapped: unref(onFocusAfterTrapped),
        onFocusAfterReleased: unref(onFocusAfterReleased),
        onFocusin: unref(onFocusInTrap),
        onFocusoutPrevented: unref(onFocusoutPrevented),
        onReleaseRequested: unref(onReleaseRequested)
      }, {
        default: withCtx(() => [renderSlot(_ctx.$slots, "default")]),
        _: 3
      }, 8, [
        "loop",
        "trapped",
        "focus-trap-el",
        "focus-start-el",
        "onFocusAfterTrapped",
        "onFocusAfterReleased",
        "onFocusin",
        "onFocusoutPrevented",
        "onReleaseRequested"
      ])], 16);
    };
  }
});
var content_default$1 = content_vue_vue_type_script_setup_true_lang_default$1;
const ElPopper = withInstall(popper_default);
const isTriggerType = (trigger, type) => {
  if (isArray(trigger)) return trigger.includes(type);
  return trigger === type;
};
const whenTrigger = (trigger, type, handler) => {
  return (e) => {
    isTriggerType(unref(trigger), type) && handler(e);
  };
};
var trigger_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: "ElTooltipTrigger",
  __name: "trigger",
  props: useTooltipTriggerProps,
  setup(__props, { expose: __expose }) {
    const props = __props;
    const ns = useNamespace("tooltip");
    const { controlled, id, open, onOpen, onClose, onToggle } = inject(TOOLTIP_INJECTION_KEY, void 0);
    const triggerRef = ref(null);
    const stopWhenControlledOrDisabled = () => {
      if (unref(controlled) || props.disabled) return true;
    };
    const trigger = toRef(props, "trigger");
    const onMouseenter = composeEventHandlers(stopWhenControlledOrDisabled, whenTrigger(trigger, "hover", (e) => {
      onOpen(e);
      if (props.focusOnTarget && e.target) nextTick(() => {
        focusElement(e.target, { preventScroll: true });
      });
    }));
    const onMouseleave = composeEventHandlers(stopWhenControlledOrDisabled, whenTrigger(trigger, "hover", onClose));
    const onClick = composeEventHandlers(stopWhenControlledOrDisabled, whenTrigger(trigger, "click", (e) => {
      if (e.button === 0) onToggle(e);
    }));
    const onFocus = composeEventHandlers(stopWhenControlledOrDisabled, whenTrigger(trigger, "focus", onOpen));
    const onBlur = composeEventHandlers(stopWhenControlledOrDisabled, whenTrigger(trigger, "focus", onClose));
    const onContextMenu = composeEventHandlers(stopWhenControlledOrDisabled, whenTrigger(trigger, "contextmenu", (e) => {
      e.preventDefault();
      onToggle(e);
    }));
    const onKeydown = composeEventHandlers(stopWhenControlledOrDisabled, (e) => {
      const code = getEventCode(e);
      if (props.triggerKeys.includes(code)) {
        e.preventDefault();
        onToggle(e);
      }
    });
    __expose({
      /**
      * @description trigger element
      */
      triggerRef
    });
    return (_ctx, _cache) => {
      return openBlock(), createBlock(unref(trigger_default$1), {
        id: unref(id),
        "virtual-ref": __props.virtualRef,
        open: unref(open),
        "virtual-triggering": __props.virtualTriggering,
        class: normalizeClass(unref(ns).e("trigger")),
        onBlur: unref(onBlur),
        onClick: unref(onClick),
        onContextmenu: unref(onContextMenu),
        onFocus: unref(onFocus),
        onMouseenter: unref(onMouseenter),
        onMouseleave: unref(onMouseleave),
        onKeydown: unref(onKeydown)
      }, {
        default: withCtx(() => [renderSlot(_ctx.$slots, "default")]),
        _: 3
      }, 8, [
        "id",
        "virtual-ref",
        "open",
        "virtual-triggering",
        "class",
        "onBlur",
        "onClick",
        "onContextmenu",
        "onFocus",
        "onMouseenter",
        "onMouseleave",
        "onKeydown"
      ]);
    };
  }
});
var trigger_default = trigger_vue_vue_type_script_setup_true_lang_default;
var content_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: "ElTooltipContent",
  inheritAttrs: false,
  __name: "content",
  props: useTooltipContentProps,
  setup(__props, { expose: __expose }) {
    const props = __props;
    const { selector } = usePopperContainerId();
    const ns = useNamespace("tooltip");
    const contentRef = ref();
    const popperContentRef = computedEager(() => {
      var _a;
      return (_a = contentRef.value) == null ? void 0 : _a.popperContentRef;
    });
    let stopHandle;
    const { controlled, id, open, trigger, onClose, onOpen, onShow, onHide, onBeforeShow, onBeforeHide } = inject(TOOLTIP_INJECTION_KEY, void 0);
    const transitionClass = computed(() => {
      return props.transition || `${ns.namespace.value}-fade-in-linear`;
    });
    const persistentRef = computed(() => {
      return props.persistent;
    });
    onBeforeUnmount(() => {
      stopHandle == null ? void 0 : stopHandle();
    });
    const shouldRender = computed(() => {
      return unref(persistentRef) ? true : unref(open);
    });
    const shouldShow = computed(() => {
      return props.disabled ? false : unref(open);
    });
    const appendTo = computed(() => {
      return props.appendTo || selector.value;
    });
    const contentStyle = computed(() => props.style ?? {});
    const ariaHidden = ref(true);
    const onTransitionLeave = () => {
      onHide();
      isFocusInsideContent() && focusElement(document.body, { preventScroll: true });
      ariaHidden.value = true;
    };
    const stopWhenControlled = () => {
      if (unref(controlled)) return true;
    };
    const onContentEnter = composeEventHandlers(stopWhenControlled, () => {
      if (props.enterable && isTriggerType(unref(trigger), "hover")) onOpen();
    });
    const onContentLeave = composeEventHandlers(stopWhenControlled, () => {
      if (isTriggerType(unref(trigger), "hover")) onClose();
    });
    const onBeforeEnter = () => {
      var _a, _b;
      (_b = (_a = contentRef.value) == null ? void 0 : _a.updatePopper) == null ? void 0 : _b.call(_a);
      onBeforeShow == null ? void 0 : onBeforeShow();
    };
    const onBeforeLeave = () => {
      onBeforeHide == null ? void 0 : onBeforeHide();
    };
    const onAfterShow = () => {
      onShow();
    };
    const onBlur = () => {
      if (!props.virtualTriggering) onClose();
    };
    const isFocusInsideContent = (event) => {
      var _a;
      const popperContent = (_a = contentRef.value) == null ? void 0 : _a.popperContentRef;
      const activeElement = (event == null ? void 0 : event.relatedTarget) || document.activeElement;
      return popperContent == null ? void 0 : popperContent.contains(activeElement);
    };
    watch(() => unref(open), (val) => {
      if (!val) stopHandle == null ? void 0 : stopHandle();
      else {
        ariaHidden.value = false;
        stopHandle = onClickOutside(popperContentRef, () => {
          if (unref(controlled)) return;
          if (castArray(unref(trigger)).every((item) => {
            return item !== "hover" && item !== "focus";
          })) onClose();
        }, { detectIframe: true });
      }
    }, { flush: "post" });
    __expose({
      /**
      * @description el-popper-content component instance
      */
      contentRef,
      /**
      * @description validate current focus event is trigger inside el-popper-content
      */
      isFocusInsideContent
    });
    return (_ctx, _cache) => {
      return openBlock(), createBlock(Teleport, {
        disabled: !__props.teleported,
        to: appendTo.value
      }, [shouldRender.value || !ariaHidden.value ? (openBlock(), createBlock(Transition, {
        key: 0,
        name: transitionClass.value,
        appear: !persistentRef.value,
        onAfterLeave: onTransitionLeave,
        onBeforeEnter,
        onAfterEnter: onAfterShow,
        onBeforeLeave,
        persisted: ""
      }, {
        default: withCtx(() => [withDirectives(createVNode(unref(content_default$1), mergeProps({
          id: unref(id),
          ref_key: "contentRef",
          ref: contentRef
        }, _ctx.$attrs, {
          "aria-label": __props.ariaLabel,
          "aria-hidden": ariaHidden.value,
          "boundaries-padding": __props.boundariesPadding,
          "fallback-placements": __props.fallbackPlacements,
          "gpu-acceleration": __props.gpuAcceleration,
          offset: __props.offset,
          placement: __props.placement,
          "popper-options": __props.popperOptions,
          "arrow-offset": __props.arrowOffset,
          strategy: __props.strategy,
          effect: __props.effect,
          enterable: __props.enterable,
          pure: __props.pure,
          "popper-class": __props.popperClass,
          "popper-style": [__props.popperStyle, contentStyle.value],
          "reference-el": __props.referenceEl,
          "trigger-target-el": __props.triggerTargetEl,
          visible: shouldShow.value,
          "z-index": __props.zIndex,
          loop: __props.loop,
          onMouseenter: unref(onContentEnter),
          onMouseleave: unref(onContentLeave),
          onBlur,
          onClose: unref(onClose)
        }), {
          default: withCtx(() => [renderSlot(_ctx.$slots, "default")]),
          _: 3
        }, 16, [
          "id",
          "aria-label",
          "aria-hidden",
          "boundaries-padding",
          "fallback-placements",
          "gpu-acceleration",
          "offset",
          "placement",
          "popper-options",
          "arrow-offset",
          "strategy",
          "effect",
          "enterable",
          "pure",
          "popper-class",
          "popper-style",
          "reference-el",
          "trigger-target-el",
          "visible",
          "z-index",
          "loop",
          "onMouseenter",
          "onMouseleave",
          "onClose"
        ]), [[vShow, shouldShow.value]])]),
        _: 3
      }, 8, ["name", "appear"])) : createCommentVNode("v-if", true)], 8, ["disabled", "to"]);
    };
  }
});
var content_default = content_vue_vue_type_script_setup_true_lang_default;
const _hoisted_1 = ["innerHTML"];
const _hoisted_2 = { key: 1 };
var tooltip_vue_vue_type_script_setup_true_lang_default = /* @__PURE__ */ defineComponent({
  name: "ElTooltip",
  __name: "tooltip",
  props: useTooltipProps,
  emits: tooltipEmits,
  setup(__props, { expose: __expose, emit: __emit }) {
    const props = __props;
    const emit = __emit;
    usePopperContainer();
    const ns = useNamespace("tooltip");
    const id = useId();
    const popperRef = ref();
    const contentRef = ref();
    const updatePopper = () => {
      var _a;
      const popperComponent = unref(popperRef);
      if (popperComponent) (_a = popperComponent.popperInstanceRef) == null ? void 0 : _a.update();
    };
    const open = ref(false);
    const toggleReason = ref();
    const { show, hide, hasUpdateHandler } = useTooltipModelToggle({
      indicator: open,
      toggleReason
    });
    const { onOpen, onClose } = useDelayedToggle({
      showAfter: toRef(props, "showAfter"),
      hideAfter: toRef(props, "hideAfter"),
      autoClose: toRef(props, "autoClose"),
      open: show,
      close: hide
    });
    const controlled = computed(() => isBoolean(props.visible) && !hasUpdateHandler.value);
    const kls = computed(() => {
      return [ns.b(), props.popperClass];
    });
    provide(TOOLTIP_INJECTION_KEY, {
      controlled,
      id,
      open: readonly(open),
      trigger: toRef(props, "trigger"),
      onOpen,
      onClose,
      onToggle: (event) => {
        if (unref(open)) onClose(event);
        else onOpen(event);
      },
      onShow: () => {
        emit("show", toggleReason.value);
      },
      onHide: () => {
        emit("hide", toggleReason.value);
      },
      onBeforeShow: () => {
        emit("before-show", toggleReason.value);
      },
      onBeforeHide: () => {
        emit("before-hide", toggleReason.value);
      },
      updatePopper
    });
    watch(() => props.disabled, (disabled) => {
      if (disabled && open.value) open.value = false;
      if (!disabled && isBoolean(props.visible)) open.value = props.visible;
    });
    const isFocusInsideContent = (event) => {
      var _a;
      return (_a = contentRef.value) == null ? void 0 : _a.isFocusInsideContent(event);
    };
    onDeactivated(() => open.value && hide());
    onBeforeUnmount(() => {
      toggleReason.value = void 0;
    });
    __expose({
      /**
      * @description el-popper component instance
      */
      popperRef,
      /**
      * @description el-tooltip-content component instance
      */
      contentRef,
      /**
      * @description validate current focus event is trigger inside el-tooltip-content
      */
      isFocusInsideContent,
      /**
      * @description update el-popper component instance
      */
      updatePopper,
      /**
      * @description expose onOpen function to mange el-tooltip open state
      */
      onOpen,
      /**
      * @description expose onClose function to manage el-tooltip close state
      */
      onClose,
      /**
      * @description expose hide function
      */
      hide
    });
    return (_ctx, _cache) => {
      return openBlock(), createBlock(unref(ElPopper), {
        ref_key: "popperRef",
        ref: popperRef,
        role: __props.role
      }, {
        default: withCtx(() => [createVNode(trigger_default, {
          disabled: __props.disabled,
          trigger: __props.trigger,
          "trigger-keys": __props.triggerKeys,
          "virtual-ref": __props.virtualRef,
          "virtual-triggering": __props.virtualTriggering,
          "focus-on-target": __props.focusOnTarget
        }, {
          default: withCtx(() => [_ctx.$slots.default ? renderSlot(_ctx.$slots, "default", { key: 0 }) : createCommentVNode("v-if", true)]),
          _: 3
        }, 8, [
          "disabled",
          "trigger",
          "trigger-keys",
          "virtual-ref",
          "virtual-triggering",
          "focus-on-target"
        ]), createVNode(content_default, {
          ref_key: "contentRef",
          ref: contentRef,
          "aria-label": __props.ariaLabel,
          "boundaries-padding": __props.boundariesPadding,
          content: __props.content,
          disabled: __props.disabled,
          effect: __props.effect,
          enterable: __props.enterable,
          "fallback-placements": __props.fallbackPlacements,
          "hide-after": __props.hideAfter,
          "gpu-acceleration": __props.gpuAcceleration,
          offset: __props.offset,
          persistent: __props.persistent,
          "popper-class": kls.value,
          "popper-style": __props.popperStyle,
          placement: __props.placement,
          "popper-options": __props.popperOptions,
          "arrow-offset": __props.arrowOffset,
          pure: __props.pure,
          "raw-content": __props.rawContent,
          "reference-el": __props.referenceEl,
          "trigger-target-el": __props.triggerTargetEl,
          "show-after": __props.showAfter,
          strategy: __props.strategy,
          teleported: __props.teleported,
          transition: __props.transition,
          "virtual-triggering": __props.virtualTriggering,
          "z-index": __props.zIndex,
          "append-to": __props.appendTo,
          loop: __props.loop
        }, {
          default: withCtx(() => [renderSlot(_ctx.$slots, "content", {}, () => [__props.rawContent ? (openBlock(), createElementBlock("span", {
            key: 0,
            innerHTML: __props.content
          }, null, 8, _hoisted_1)) : (openBlock(), createElementBlock("span", _hoisted_2, toDisplayString(__props.content), 1))]), __props.showArrow ? (openBlock(), createBlock(unref(arrow_default), { key: 0 })) : createCommentVNode("v-if", true)]),
          _: 3
        }, 8, [
          "aria-label",
          "boundaries-padding",
          "content",
          "disabled",
          "effect",
          "enterable",
          "fallback-placements",
          "hide-after",
          "gpu-acceleration",
          "offset",
          "persistent",
          "popper-class",
          "popper-style",
          "placement",
          "popper-options",
          "arrow-offset",
          "pure",
          "raw-content",
          "reference-el",
          "trigger-target-el",
          "show-after",
          "strategy",
          "teleported",
          "transition",
          "virtual-triggering",
          "z-index",
          "append-to",
          "loop"
        ])]),
        _: 3
      }, 8, ["role"]);
    };
  }
});
var tooltip_default = tooltip_vue_vue_type_script_setup_true_lang_default;
const ElTooltip = withInstall(tooltip_default);
const index = /* @__PURE__ */ Object.freeze(/* @__PURE__ */ Object.defineProperty({
  __proto__: null,
  ElTooltip,
  TOOLTIP_INJECTION_KEY,
  default: ElTooltip,
  tooltipEmits,
  useTooltipContentProps,
  useTooltipModelToggle,
  useTooltipModelToggleEmits,
  useTooltipModelToggleProps,
  useTooltipProps,
  useTooltipTriggerProps
}, Symbol.toStringTag, { value: "Module" }));
export {
  Ee as E,
  ElTooltip as a,
  index as i,
  useTooltipContentProps as u
};
//# sourceMappingURL=index-Ck348kPa.js.map
