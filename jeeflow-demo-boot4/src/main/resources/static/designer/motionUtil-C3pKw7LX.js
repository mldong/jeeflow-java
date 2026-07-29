class Keyframe {
  constructor(name, style) {
    this._keyframe = true;
    this.name = name;
    this.style = style;
  }
  getName() {
    let hashId = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : "";
    return hashId ? `${hashId}-${this.name}` : this.name;
  }
}
function getMotion(_ref) {
  let {
    prefixCls,
    animation,
    transitionName
  } = _ref;
  if (animation) {
    return {
      name: `${prefixCls}-${animation}`
    };
  }
  if (transitionName) {
    return {
      name: transitionName
    };
  }
  return {};
}
export {
  Keyframe as K,
  getMotion as g
};
//# sourceMappingURL=motionUtil-C3pKw7LX.js.map
