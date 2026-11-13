function _callSuper(t, o, e) { return o = _getPrototypeOf(o), _possibleConstructorReturn(t, _isNativeReflectConstruct() ? Reflect.construct(o, e || [], _getPrototypeOf(t).constructor) : o.apply(t, e)); }
function _possibleConstructorReturn(t, e) { if (e && ("object" == typeof e || "function" == typeof e)) return e; if (void 0 !== e) throw new TypeError("Derived constructors may only return object or undefined"); return _assertThisInitialized(t); }
function _assertThisInitialized(e) { if (void 0 === e) throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); return e; }
function _inherits(t, e) { if ("function" != typeof e && null !== e) throw new TypeError("Super expression must either be null or a function"); t.prototype = Object.create(e && e.prototype, { constructor: { value: t, writable: !0, configurable: !0 } }), Object.defineProperty(t, "prototype", { writable: !1 }), e && _setPrototypeOf(t, e); }
function _wrapNativeSuper(t) { var r = "function" == typeof Map ? new Map() : void 0; return _wrapNativeSuper = function (t) { if (null === t || !_isNativeFunction(t)) return t; if ("function" != typeof t) throw new TypeError("Super expression must either be null or a function"); if (void 0 !== r) { if (r.has(t)) return r.get(t); r.set(t, Wrapper); } function Wrapper() { return _construct(t, arguments, _getPrototypeOf(this).constructor); } return Wrapper.prototype = Object.create(t.prototype, { constructor: { value: Wrapper, enumerable: !1, writable: !0, configurable: !0 } }), _setPrototypeOf(Wrapper, t); }, _wrapNativeSuper(t); }
function _construct(t, e, r) { if (_isNativeReflectConstruct()) return Reflect.construct.apply(null, arguments); var o = [null]; o.push.apply(o, e); var p = new (t.bind.apply(t, o))(); return r && _setPrototypeOf(p, r.prototype), p; }
function _isNativeReflectConstruct() { try { var t = !Boolean.prototype.valueOf.call(Reflect.construct(Boolean, [], function () {})); } catch (t) {} return (_isNativeReflectConstruct = function () { return !!t; })(); }
function _isNativeFunction(t) { try { return -1 !== Function.toString.call(t).indexOf("[native code]"); } catch (n) { return "function" == typeof t; } }
function _setPrototypeOf(t, e) { return _setPrototypeOf = Object.setPrototypeOf ? Object.setPrototypeOf.bind() : function (t, e) { return t.__proto__ = e, t; }, _setPrototypeOf(t, e); }
function _getPrototypeOf(t) { return _getPrototypeOf = Object.setPrototypeOf ? Object.getPrototypeOf.bind() : function (t) { return t.__proto__ || Object.getPrototypeOf(t); }, _getPrototypeOf(t); }
function _toConsumableArray(r) { return _arrayWithoutHoles(r) || _iterableToArray(r) || _unsupportedIterableToArray(r) || _nonIterableSpread(); }
function _nonIterableSpread() { throw new TypeError("Invalid attempt to spread non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method."); }
function _arrayWithoutHoles(r) { if (Array.isArray(r)) return _arrayLikeToArray(r); }
function _toArray(r) { return _arrayWithHoles(r) || _iterableToArray(r) || _unsupportedIterableToArray(r) || _nonIterableRest(); }
function _iterableToArray(r) { if ("undefined" != typeof Symbol && null != r[Symbol.iterator] || null != r["@@iterator"]) return Array.from(r); }
function _slicedToArray(r, e) { return _arrayWithHoles(r) || _iterableToArrayLimit(r, e) || _unsupportedIterableToArray(r, e) || _nonIterableRest(); }
function _nonIterableRest() { throw new TypeError("Invalid attempt to destructure non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method."); }
function _unsupportedIterableToArray(r, a) { if (r) { if ("string" == typeof r) return _arrayLikeToArray(r, a); var t = {}.toString.call(r).slice(8, -1); return "Object" === t && r.constructor && (t = r.constructor.name), "Map" === t || "Set" === t ? Array.from(r) : "Arguments" === t || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(t) ? _arrayLikeToArray(r, a) : void 0; } }
function _arrayLikeToArray(r, a) { (null == a || a > r.length) && (a = r.length); for (var e = 0, n = Array(a); e < a; e++) n[e] = r[e]; return n; }
function _iterableToArrayLimit(r, l) { var t = null == r ? null : "undefined" != typeof Symbol && r[Symbol.iterator] || r["@@iterator"]; if (null != t) { var e, n, i, u, a = [], f = !0, o = !1; try { if (i = (t = t.call(r)).next, 0 === l) { if (Object(t) !== t) return; f = !1; } else for (; !(f = (e = i.call(t)).done) && (a.push(e.value), a.length !== l); f = !0); } catch (r) { o = !0, n = r; } finally { try { if (!f && null != t.return && (u = t.return(), Object(u) !== u)) return; } finally { if (o) throw n; } } return a; } }
function _arrayWithHoles(r) { if (Array.isArray(r)) return r; }
function _defineProperties(e, r) { for (var t = 0; t < r.length; t++) { var o = r[t]; o.enumerable = o.enumerable || !1, o.configurable = !0, "value" in o && (o.writable = !0), Object.defineProperty(e, _toPropertyKey(o.key), o); } }
function _createClass(e, r, t) { return r && _defineProperties(e.prototype, r), t && _defineProperties(e, t), Object.defineProperty(e, "prototype", { writable: !1 }), e; }
function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }
function _toPrimitive(t, r) { if ("object" != typeof t || !t) return t; var e = t[Symbol.toPrimitive]; if (void 0 !== e) { var i = e.call(t, r || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === r ? String : Number)(t); }
function _classCallCheck(a, n) { if (!(a instanceof n)) throw new TypeError("Cannot call a class as a function"); }
// src/library.ts
var buffer = new ArrayBuffer(8);
var f32 = new Float32Array(buffer);
var f64 = new Float64Array(buffer);
var i32 = new Int32Array(buffer);
var i64 = new BigInt64Array(buffer);
var u64 = new BigUint64Array(buffer);
var exportedFuncs = /* @__PURE__ */new WeakMap();
var emptyArray = [];
var Trampoline = /*#__PURE__*/_createClass(function Trampoline() {
  _classCallCheck(this, Trampoline);
});
var library = {
  N(x, y) {
    return (x < 0 || x === 0 && Object.is(x, -0)) !== (y < 0 || y === 0 && Object.is(y, -0)) ? -x : x;
  },
  p(x) {
    u64[0] = x;
    return i64[0];
  },
  O(x) {
    f32[0] = x;
    return i32[0];
  },
  P(x) {
    i32[0] = x;
    return f32[0];
  },
  Q(x) {
    f64[0] = x;
    return u64[0];
  },
  R(x) {
    u64[0] = x;
    return f64[0];
  },
  S(x, y) {
    return x << y | x >>> 32 - y;
  },
  T(x, y) {
    return x >>> y | x << 32 - y;
  },
  U(x, y) {
    return (x << y | x >> 64n - y) & 0xFFFFFFFFFFFFFFFFn;
  },
  V(x, y) {
    return (x >> y | x << 64n - y) & 0xFFFFFFFFFFFFFFFFn;
  },
  W(x) {
    return x ? Math.clz32(x & -x) ^ 31 : 32;
  },
  X(x) {
    var count = 0;
    while (x) {
      count++;
      x &= x - 1;
    }
    return count;
  },
  Y(x) {
    var count = Math.clz32(Number(x >> 32n & 0xFFFFFFFFn));
    if (count === 32) count += Math.clz32(Number(x & 0xFFFFFFFFn));
    return BigInt(count);
  },
  Z(x) {
    var y = Number(x & 0xFFFFFFFFn);
    if (y) return BigInt(Math.clz32(y & -y) ^ 31);
    y = Number(x >> 32n & 0xFFFFFFFFn);
    return y ? BigInt(32 + Math.clz32(y & -y) ^ 31) : 64n;
  },
  _(x) {
    var count = 0n;
    while (x) {
      count++;
      x &= x - 1n;
    }
    return count;
  },
  z(x) {
    x = Math.trunc(x);
    return x >= 2147483647 ? 2147483647 : x <= -2147483648 ? -2147483648 : x | 0;
  },
  A(x) {
    x = Math.trunc(x);
    return x >= 4294967295 ? -1 : x <= 0 ? 0 : x | 0;
  },
  B(x) {
    x = Math.trunc(x);
    return x >= 9223372036854776e3 ? 0x7FFFFFFFFFFFFFFFn : x <= -9223372036854776e3 ? 0x8000000000000000n : x === x ? BigInt(x) & 0xFFFFFFFFFFFFFFFFn : 0n;
  },
  C(x) {
    x = Math.trunc(x);
    return x >= 18446744073709552e3 ? 0xFFFFFFFFFFFFFFFFn : !(x > 0) ? 0n :
    // NaN must become 0
    BigInt(x);
  },
  $(x) {
    return x & 0x80n ? x | 0xFFFFFFFFFFFFFF00n : x & 0xFFn;
  },
  aa(x) {
    return x & 0x8000n ? x | 0xFFFFFFFFFFFF0000n : x & 0xFFFFn;
  },
  ba(x) {
    return x & 0x80000000n ? x | 0xFFFFFFFF00000000n : x & 0xFFFFFFFFn;
  },
  D(source, destination, target, start, count) {
    if (source === destination) {
      destination.copyWithin(target, start, start + count);
    } else {
      destination.set(source.subarray(start, start + count), target);
    }
  },
  E(x, y, d, s, n) {
    d >>>= 0;
    s >>>= 0;
    n >>>= 0;
    if (s + n > y.length || d + n > x.length) throw RangeError();
    if (d <= s) {
      for (var j = 0; j < n; j++) x[d + j] = y[s + j];
    } else {
      for (var _j = n - 1; _j >= 0; _j--) x[d + _j] = y[s + _j];
    }
  },
  F(x, val, n) {
    var sz = x.length;
    n >>>= 0;
    if (sz + n > x.ca) return -1;
    for (var i = 0; i < n; i++) x.push(val);
    return sz;
  },
  da(x, i, val, n) {
    i >>>= 0;
    n >>>= 0;
    if (i + n > x.length) throw RangeError();
    for (var j = 0; j < n; j++) x[i + j] = val;
  },
  G(context, fn, args) {
    if (context instanceof Trampoline) {
      context.q = fn;
      context.ea = args;
      return emptyArray;
    }
    context = new Trampoline();
    var result;
    while (result = fn.apply(context, args), context.q) {
      fn = context.q;
      args = context.ea;
      context.q = null;
    }
    return result;
  },
  r(fn) {
    if (fn === null) return fn;
    var obj = exportedFuncs.get(fn);
    if (obj) return obj;
    throw Error("Unexpected foreign function object");
  },
  s(obj) {
    if (obj === null) return null;
    if (!obj.t) {
      var _obj$fa = _slicedToArray(obj.fa, 2),
        argTypes = _obj$fa[0],
        returnTypes = _obj$fa[1];
      var argNames = [];
      var argExprs = [];
      for (var i = 0; i < argTypes.length; i++) {
        argNames.push("a" + i);
        argExprs.push(castToWASM("a" + i, argTypes[i]));
      }
      var result = `f.${/* @__KEY__ */"u"}(${argExprs})`;
      if (returnTypes.length === 1) {
        result = "return " + castToJS(result, returnTypes[0]);
      } else if (returnTypes.length > 1) {
        result = `let r=${result};`;
        for (var _i = 0; _i < returnTypes.length; _i++) result += `r[${_i}]=${castToJS(`r[${_i}]`, returnTypes[_i])};`;
        result += "return r";
      }
      exportedFuncs.set(obj.t = new Function("f", "l", `return(${argNames})=>{${result}}`)(obj, this), obj);
    }
    return obj.t;
  }
};

// src/optimize.ts
var isPayload = operand => {
  return typeof operand === "string" ? operand >= "P" && operand <= "S" : operand ? typeof operand[0] !== "string" && operand[0] < 0 : false;
};
var rules = [
// load of (addr + constant) => merge constant into load's offset
{
  a: [["$", 40 /* i32_load */, 41 /* i64_load */, 42 /* f32_load */, 43 /* f64_load */, 44 /* i32_load8_s */, 45 /* i32_load8_u */, 46 /* i32_load16_s */, 47 /* i32_load16_u */, 48 /* i64_load8_s */, 49 /* i64_load8_u */, 50 /* i64_load16_s */, 51 /* i64_load16_u */, 52 /* i64_load32_s */, 53 /* i64_load32_u */], [106 /* i32_add */, "x", [65 /* i32_const */, "Q"]], "P", "R"],
  b: ["$", "x", [-2 /* i32_add */, "P", "Q"], "R"]
},
// store of (addr + constant) => merge constant into store's offset
{
  a: [["$", 54 /* i32_store */, 55 /* i64_store */, 56 /* f32_store */, 57 /* f64_store */, 58 /* i32_store8 */, 59 /* i32_store16 */, 60 /* i64_store8 */, 61 /* i64_store16 */, 62 /* i64_store32 */], [106 /* i32_add */, "x", [65 /* i32_const */, "Q"]], "y", "P", "R"],
  b: ["$", "x", "y", [-2 /* i32_add */, "P", "Q"], "R"]
},
// i64_store8 => i32_store8
{
  a: [60 /* i64_store8 */, "x", "y", "P", "R"],
  f: {
    "y": [{
      a: [66 /* i64_const */, "Q"],
      b: [58 /* i32_store8 */, "x", [65 /* i32_const */, [-1 /* i64_to_i32 */, "Q"]], "P", "R"]
    }, {
      a: [["$", 48 /* i64_load8_s */, 49 /* i64_load8_u */, 50 /* i64_load16_s */, 51 /* i64_load16_u */, 52 /* i64_load32_s */, 53 /* i64_load32_u */, 41 /* i64_load */], "z", "Q", "S"],
      b: [58 /* i32_store8 */, "x", [45 /* i32_load8_u */, "z", "Q", "S"], "P", "R"]
    }, {
      a: [["@", 172 /* i64_extend_i32_s */, 173 /* i64_extend_i32_u */], "z"],
      b: [58 /* i32_store8 */, "x", "z", "P", "R"]
    }]
  }
},
// i64_store16 => i32_store16
{
  a: [61 /* i64_store16 */, "x", "y", "P", "R"],
  f: {
    "y": [{
      a: [66 /* i64_const */, "Q"],
      b: [59 /* i32_store16 */, "x", [65 /* i32_const */, [-1 /* i64_to_i32 */, "Q"]], "P", "R"]
    }, {
      a: [48 /* i64_load8_s */, "z", "Q", "S"],
      b: [59 /* i32_store16 */, "x", [44 /* i32_load8_s */, "z", "Q", "S"], "P", "R"]
    }, {
      a: [49 /* i64_load8_u */, "z", "Q", "S"],
      b: [59 /* i32_store16 */, "x", [45 /* i32_load8_u */, "z", "Q", "S"], "P", "R"]
    }, {
      a: [["$", 50 /* i64_load16_s */, 51 /* i64_load16_u */, 52 /* i64_load32_s */, 53 /* i64_load32_u */, 41 /* i64_load */], "z", "Q", "S"],
      b: [59 /* i32_store16 */, "x", [47 /* i32_load16_u */, "z", "Q", "S"], "P", "R"]
    }, {
      a: [["@", 172 /* i64_extend_i32_s */, 173 /* i64_extend_i32_u */], "z"],
      b: [59 /* i32_store16 */, "x", "z", "P", "R"]
    }]
  }
},
// i64_store32 => i32_store
{
  a: [62 /* i64_store32 */, "x", "y", "P", "R"],
  f: {
    "y": [{
      a: [66 /* i64_const */, "Q"],
      b: [54 /* i32_store */, "x", [65 /* i32_const */, [-1 /* i64_to_i32 */, "Q"]], "P", "R"]
    }, {
      a: [48 /* i64_load8_s */, "z", "Q", "S"],
      b: [54 /* i32_store */, "x", [44 /* i32_load8_s */, "z", "Q", "S"], "P", "R"]
    }, {
      a: [49 /* i64_load8_u */, "z", "Q", "S"],
      b: [54 /* i32_store */, "x", [45 /* i32_load8_u */, "z", "Q", "S"], "P", "R"]
    }, {
      a: [50 /* i64_load16_s */, "z", "Q", "S"],
      b: [54 /* i32_store */, "x", [46 /* i32_load16_s */, "z", "Q", "S"], "P", "R"]
    }, {
      a: [51 /* i64_load16_u */, "z", "Q", "S"],
      b: [54 /* i32_store */, "x", [47 /* i32_load16_u */, "z", "Q", "S"], "P", "R"]
    }, {
      a: [["$", 52 /* i64_load32_s */, 53 /* i64_load32_u */, 41 /* i64_load */], "z", "Q", "S"],
      b: [54 /* i32_store */, "x", [40 /* i32_load */, "z", "Q", "S"], "P", "R"]
    }, {
      a: [["@", 172 /* i64_extend_i32_s */, 173 /* i64_extend_i32_u */], "z"],
      b: [54 /* i32_store */, "x", "z", "P", "R"]
    }]
  }
},
// i64_eqz => i32_eqz
{
  a: [80 /* i64_eqz */, "x"],
  f: {
    "x": [{
      a: [["$", 48 /* i64_load8_s */, 49 /* i64_load8_u */], "y", "P", "R"],
      b: [69 /* i32_eqz */, [45 /* i32_load8_u */, "y", "P", "R"]]
    }, {
      a: [["$", 50 /* i64_load16_s */, 51 /* i64_load16_u */], "y", "P", "R"],
      b: [69 /* i32_eqz */, [47 /* i32_load16_u */, "y", "P", "R"]]
    }, {
      a: [["$", 52 /* i64_load32_s */, 53 /* i64_load32_u */], "y", "P", "R"],
      b: [69 /* i32_eqz */, [40 /* i32_load */, "y", "P", "R"]]
    }, {
      a: [["@", 172 /* i64_extend_i32_s */, 173 /* i64_extend_i32_u */], "y"],
      b: [69 /* i32_eqz */, "y"]
    }]
  }
},
// 64-bit equality on unsigned 32-bit load => 32-bit equality
{
  a: [81 /* i64_eq */, [49 /* i64_load8_u */, "x", "P", "R"], [66 /* i64_const */, "Q"]],
  b: [70 /* i32_eq */, [45 /* i32_load8_u */, "x", "P", "R"], [65 /* i32_const */, [-1 /* i64_to_i32 */, "Q"]]],
  d: ["Q", "<=", 0xFFn]
}, {
  a: [82 /* i64_ne */, [49 /* i64_load8_u */, "x", "P", "R"], [66 /* i64_const */, "Q"]],
  b: [71 /* i32_ne */, [45 /* i32_load8_u */, "x", "P", "R"], [65 /* i32_const */, [-1 /* i64_to_i32 */, "Q"]]],
  d: ["Q", "<=", 0xFFn]
}, {
  a: [81 /* i64_eq */, [51 /* i64_load16_u */, "x", "P", "R"], [66 /* i64_const */, "Q"]],
  b: [70 /* i32_eq */, [47 /* i32_load16_u */, "x", "P", "R"], [65 /* i32_const */, [-1 /* i64_to_i32 */, "Q"]]],
  d: ["Q", "<=", 0xFFFFn]
}, {
  a: [82 /* i64_ne */, [51 /* i64_load16_u */, "x", "P", "R"], [66 /* i64_const */, "Q"]],
  b: [71 /* i32_ne */, [47 /* i32_load16_u */, "x", "P", "R"], [65 /* i32_const */, [-1 /* i64_to_i32 */, "Q"]]],
  d: ["Q", "<=", 0xFFFFn]
}, {
  a: [81 /* i64_eq */, [53 /* i64_load32_u */, "x", "P", "R"], [66 /* i64_const */, "Q"]],
  b: [70 /* i32_eq */, [40 /* i32_load */, "x", "P", "R"], [65 /* i32_const */, [-1 /* i64_to_i32 */, "Q"]]],
  d: ["Q", "<=", 0xFFFFFFFFn]
}, {
  a: [82 /* i64_ne */, [53 /* i64_load32_u */, "x", "P", "R"], [66 /* i64_const */, "Q"]],
  b: [71 /* i32_ne */, [40 /* i32_load */, "x", "P", "R"], [65 /* i32_const */, [-1 /* i64_to_i32 */, "Q"]]],
  d: ["Q", "<=", 0xFFFFFFFFn]
},
// Optimize boolean operations
{
  a: [240 /* BOOL */, "x"],
  f: {
    "x": [
    // "if (x ? 1 : 0)" => "if (x)"
    {
      a: [242 /* BOOL_TO_INT */, "y"],
      b: [240 /* BOOL */, "y"]
    },
    // "if (x ? 0 : 1)" => "if (!x)"
    {
      a: [["@", 69 /* i32_eqz */, 80 /* i64_eqz */], "x"],
      b: [241 /* BOOL_NOT */, "y"]
    }]
  }
}, {
  a: [241 /* BOOL_NOT */, "x"],
  f: {
    "x": [
    // "if (!(x ? 1 : 0))" => "if (!x)"
    {
      a: [242 /* BOOL_TO_INT */, "y"],
      b: [241 /* BOOL_NOT */, "y"]
    },
    // "if (!(x ? 0 : 1)" => "if (x)"
    {
      a: [["@", 69 /* i32_eqz */, 80 /* i64_eqz */], "y"],
      b: [240 /* BOOL */, "y"]
    },
    // "if (!(x === 0))" => "if (x !== 0)" (note: does not apply to floating-point due to NaN)
    {
      a: [70 /* i32_eq */, "y", "z"],
      b: [240 /* BOOL */, [71 /* i32_ne */, "y", "z"]]
    }, {
      a: [71 /* i32_ne */, "y", "z"],
      b: [240 /* BOOL */, [70 /* i32_eq */, "y", "z"]]
    }, {
      a: [72 /* i32_lt_s */, "y", "z"],
      b: [240 /* BOOL */, [78 /* i32_ge_s */, "y", "z"]]
    }, {
      a: [73 /* i32_lt_u */, "y", "z"],
      b: [240 /* BOOL */, [79 /* i32_ge_u */, "y", "z"]]
    }, {
      a: [74 /* i32_gt_s */, "y", "z"],
      b: [240 /* BOOL */, [76 /* i32_le_s */, "y", "z"]]
    }, {
      a: [75 /* i32_gt_u */, "y", "z"],
      b: [240 /* BOOL */, [77 /* i32_le_u */, "y", "z"]]
    }, {
      a: [76 /* i32_le_s */, "y", "z"],
      b: [240 /* BOOL */, [74 /* i32_gt_s */, "y", "z"]]
    }, {
      a: [77 /* i32_le_u */, "y", "z"],
      b: [240 /* BOOL */, [75 /* i32_gt_u */, "y", "z"]]
    }, {
      a: [78 /* i32_ge_s */, "y", "z"],
      b: [240 /* BOOL */, [72 /* i32_lt_s */, "y", "z"]]
    }, {
      a: [79 /* i32_ge_u */, "y", "z"],
      b: [240 /* BOOL */, [73 /* i32_lt_u */, "y", "z"]]
    }, {
      a: [81 /* i64_eq */, "y", "z"],
      b: [240 /* BOOL */, [82 /* i64_ne */, "y", "z"]]
    }, {
      a: [82 /* i64_ne */, "y", "z"],
      b: [240 /* BOOL */, [81 /* i64_eq */, "y", "z"]]
    }, {
      a: [83 /* i64_lt_s */, "y", "z"],
      b: [240 /* BOOL */, [89 /* i64_ge_s */, "y", "z"]]
    }, {
      a: [84 /* i64_lt_u */, "y", "z"],
      b: [240 /* BOOL */, [90 /* i64_ge_u */, "y", "z"]]
    }, {
      a: [85 /* i64_gt_s */, "y", "z"],
      b: [240 /* BOOL */, [87 /* i64_le_s */, "y", "z"]]
    }, {
      a: [86 /* i64_gt_u */, "y", "z"],
      b: [240 /* BOOL */, [88 /* i64_le_u */, "y", "z"]]
    }, {
      a: [87 /* i64_le_s */, "y", "z"],
      b: [240 /* BOOL */, [85 /* i64_gt_s */, "y", "z"]]
    }, {
      a: [88 /* i64_le_u */, "y", "z"],
      b: [240 /* BOOL */, [86 /* i64_gt_u */, "y", "z"]]
    }, {
      a: [89 /* i64_ge_s */, "y", "z"],
      b: [240 /* BOOL */, [83 /* i64_lt_s */, "y", "z"]]
    }, {
      a: [90 /* i64_ge_u */, "y", "z"],
      b: [240 /* BOOL */, [84 /* i64_lt_u */, "y", "z"]]
    }]
  }
},
// Optimize sign conversions
{
  a: [243 /* TO_U32 */, "x"],
  f: {
    "x": [{
      a: [40 /* i32_load */, "y", "P", "R"],
      b: [245 /* U32_LOAD */, "y", "P", "R"]
    }]
  }
}, {
  a: [244 /* TO_S64 */, "x"],
  f: {
    "x": [{
      a: [41 /* i64_load */, "y", "P", "R"],
      b: [246 /* S64_LOAD */, "y", "P", "R"]
    },
    // No sign conversion is needed for values in the shared signed/unsigned range
    {
      a: [66 /* i64_const */, "P"],
      b: [66 /* i64_const */, "P"],
      d: ["P", "<=", 0x7FFFFFFFFFFFFFFFn]
    }, {
      a: [["$", 49 /* i64_load8_u */, 51 /* i64_load16_u */, 53 /* i64_load32_u */], "y", "P", "R"],
      b: ["$", "y", "P", "R"]
    }]
  }
},
// i32_wrap_i64 removal
{
  a: [167 /* i32_wrap_i64 */, "x"],
  f: {
    "x": [{
      a: [66 /* i64_const */, "P"],
      b: [65 /* i32_const */, [-1 /* i64_to_i32 */, "P"]]
    }, {
      a: [48 /* i64_load8_s */, "y", "P", "R"],
      b: [44 /* i32_load8_s */, "y", "P", "R"]
    }, {
      a: [49 /* i64_load8_u */, "y", "P", "R"],
      b: [45 /* i32_load8_u */, "y", "P", "R"]
    }, {
      a: [50 /* i64_load16_s */, "y", "P", "R"],
      b: [46 /* i32_load16_s */, "y", "P", "R"]
    }, {
      a: [51 /* i64_load16_u */, "y", "P", "R"],
      b: [47 /* i32_load16_u */, "y", "P", "R"]
    }, {
      a: [["$", 52 /* i64_load32_s */, 53 /* i64_load32_u */, 41 /* i64_load */], "y", "P", "R"],
      b: [40 /* i32_load */, "y", "P", "R"]
    }, {
      a: [["@", 172 /* i64_extend_i32_s */, 173 /* i64_extend_i32_u */], "y"],
      b: "y"
    }, {
      a: [124 /* i64_add */, [["@", 172 /* i64_extend_i32_s */, 173 /* i64_extend_i32_u */], "y"], [66 /* i64_const */, "P"]],
      b: [106 /* i32_add */, "y", [65 /* i32_const */, [-1 /* i64_to_i32 */, "P"]]]
    }]
  }
},
// i64_and
{
  a: [131 /* i64_and */, "x", [66 /* i64_const */, "P"]],
  f: {
    "x": [{
      a: [66 /* i64_const */, "Q"],
      b: [66 /* i64_const */, [-3 /* i64_and */, "P", "Q"]]
    }, {
      a: [131 /* i64_and */, "y", [66 /* i64_const */, "Q"]],
      b: [131 /* i64_and */, "y", [66 /* i64_const */, [-3 /* i64_and */, "P", "Q"]]]
    }, {
      a: [49 /* i64_load8_u */, "y", "Q", "S"],
      b: [49 /* i64_load8_u */, "y", "Q", "S"],
      d: [["P", "&", 0xFFn], "===", 0xFFn]
    }, {
      a: [48 /* i64_load8_s */, "y", "Q", "S"],
      b: [49 /* i64_load8_u */, "y", "Q", "S"],
      d: ["P", "===", 0xFFn]
    }, {
      a: [51 /* i64_load16_u */, "y", "Q", "S"],
      b: [51 /* i64_load16_u */, "y", "Q", "S"],
      d: [["P", "&", 0xFFFFn], "===", 0xFFFFn]
    }, {
      a: [50 /* i64_load16_s */, "y", "Q", "S"],
      b: [51 /* i64_load16_u */, "y", "Q", "S"],
      d: ["P", "===", 0xFFFFn]
    }, {
      a: [53 /* i64_load32_u */, "y", "Q", "S"],
      b: [53 /* i64_load32_u */, "y", "Q", "S"],
      d: [["P", "&", 0xFFFFFFFFn], "===", 0xFFFFFFFFn]
    }, {
      a: [52 /* i64_load32_s */, "y", "Q", "S"],
      b: [53 /* i64_load32_u */, "y", "Q", "S"],
      d: ["P", "===", 0xFFFFFFFFn]
    }]
  }
}];
var compileOptimizations = () => {
  var nextVar = 0;
  var newVarName = () => "v" + nextVar++;
  var compileOperand = (ptrVar, index, operands, reusableNodes, then) => {
    if (index < operands.length) {
      var operand = operands[index];
      if (typeof operand === "string") {
        placeholderExprs[operand] = `${astVar}[${ptrVar}+${index + 1}]`;
        compileOperand(ptrVar, index + 1, operands, reusableNodes, then);
      } else {
        var childPtr = newVarName();
        var childOp = newVarName();
        code += `var ${childPtr}=${astVar}[${ptrVar}+${index + 1}],${childOp}=${astVar}[${childPtr}]&${65535 /* OpMask */};`;
        compileMatch(childPtr, childOp, operand, reusableNodes, reusableNodes2 => {
          compileOperand(ptrVar, index + 1, operands, reusableNodes2, then);
        });
      }
    } else {
      then(reusableNodes);
    }
  };
  var compileMatch = (ptrVar, opVar, _ref, reusableNodes, then) => {
    var _ref2 = _toArray(_ref),
      pattern = _ref2[0],
      operands = _ref2.slice(1);
    var parts = [];
    if (typeof pattern === "number") {
      parts.push(`${opVar}===${pattern}`);
    } else {
      var _pattern = _toArray(pattern),
        name = _pattern[0],
        oneOf = _pattern.slice(1);
      oneOf.sort((a, b) => a - b);
      oneOfOps[name] = {
        v: ptrVar,
        ga: opVar,
        ha: oneOf.some(x => opCanBeOptimized.has(x))
      };
      for (var i = 0; i < oneOf.length; i++) {
        var run = 1;
        while (i + run < oneOf.length && oneOf[i + run - 1] + 1 === oneOf[i + run]) run++;
        parts.push(run > 2 ? `${opVar}>=${oneOf[i]}&&${opVar}<=${oneOf[i += run - 1]}` : `${opVar}===${oneOf[i]}`);
      }
    }
    reusableNodes = reusableNodes.concat({
      v: ptrVar,
      w: operands.map(x => typeof x === "string" ? x : null)
    });
    code += `if(${parts.join("||")}){`;
    compileOperand(ptrVar, 0, operands, reusableNodes, then);
    code += "}";
  };
  var compileRules = (ptrVar, opVar, rules2, buildStatName, reusableNodes, placeholderVarsFromParent) => {
    var _loop = function (match, nested, replace, onlyIf) {
      compileMatch(ptrVar, opVar, match, reusableNodes, reusableNodes2 => {
        var placeholderVars = Object.create(placeholderVarsFromParent);
        compileOnlyIf(onlyIf, placeholderVars, () => {
          if (nested) {
            for (var operand in nested) {
              storePlaceholderExprToVar(operand, placeholderVars);
            }
            var _loop2 = function (_operand) {
              var childPtrVar = placeholderVars[_operand];
              var childOpVar = newVarName();
              code += `var ${childOpVar}=${astVar}[${childPtrVar}]&${65535 /* OpMask */};`;
              compileRules(childPtrVar, childOpVar, nested[_operand], false ? subMatch => buildStatName(substituteMatch(match, _operand, subMatch)) : null, reusableNodes2, placeholderVars);
            };
            for (var _operand in nested) {
              _loop2(_operand);
            }
          }
          if (replace) {
            if (false) code += `${recordStatsVar}(${JSON.stringify(buildStatName(match))});`;
            var replacePtr = constructReplacement(replace, placeholderVars, reusableNodes2.slice(), `|${astVar}[${rootPtrVar}]&${~0 << 24 /* OutSlotShift */}`);
            var optimizeAgain = typeof replace !== "string" && (typeof replace[0] === "string" ? oneOfOps[replace[0]].ha : opCanBeOptimized.has(replace[0]));
            if (optimizeAgain) {
              if (rootPtrVar !== replacePtr) code += `${rootPtrVar}=${replacePtr};`;
              code += "continue";
            } else {
              code += "return " + replacePtr;
            }
          }
        });
      });
    };
    for (var _ref3 of rules2) {
      var match = _ref3.a;
      var nested = _ref3.f;
      var replace = _ref3.b;
      var onlyIf = _ref3.d;
      _loop(match, nested, replace, onlyIf);
    }
  };
  var compileOnlyIf = (onlyIf, placeholderVars, then) => {
    if (onlyIf) {
      var compileCheck = onlyIf2 => {
        if (typeof onlyIf2 === "string") {
          return `${constantsVar}[${placeholderVars[onlyIf2] || placeholderExprs[onlyIf2]}]&0xFFFFFFFFFFFFFFFFn`;
        }
        if (typeof onlyIf2 === "bigint") {
          return onlyIf2 + "n";
        }
        return `(${compileCheck(onlyIf2[0])})${onlyIf2[1]}(${compileCheck(onlyIf2[2])})`;
      };
      code += `if(${compileCheck(onlyIf)}){`;
      then();
      code += "}";
    } else {
      then();
    }
  };
  var storePlaceholderExprToVar = (operand, placeholderVars) => {
    if (!(operand in placeholderVars)) {
      var childPtr = newVarName();
      code += `var ${childPtr}=${placeholderExprs[operand]};`;
      placeholderVars[operand] = childPtr;
    }
  };
  var constructReplacement = function (replace, placeholderVars, reusableNodes) {
    var outStackSlot = arguments.length > 3 && arguments[3] !== undefined ? arguments[3] : "";
    if (typeof replace === "string") return placeholderVars[replace] || placeholderExprs[replace];
    if (replace[0] === -1 /* i64_to_i32 */) {
      var operand = constructReplacement(replace[1], placeholderVars, reusableNodes);
      return `Number(${constantsVar}[${operand}]&0xFFFFFFFFn)`;
    }
    if (replace[0] === -2 /* i32_add */) {
      var operand1 = constructReplacement(replace[1], placeholderVars, reusableNodes);
      var operand2 = constructReplacement(replace[2], placeholderVars, reusableNodes);
      return `${operand1}+${operand2}`;
    }
    if (replace[0] === -3 /* i64_and */) {
      var replace1 = replace[1];
      if (typeof replace1 === "string") {
        storePlaceholderExprToVar(replace1, placeholderVars);
      }
      var _operand2 = constructReplacement(replace1, placeholderVars, reusableNodes);
      var _operand3 = constructReplacement(replace[2], placeholderVars, reusableNodes);
      code += `${constantsVar}[${_operand2}]&=${constantsVar}[${_operand3}];`;
      return _operand2;
    }
    var _replace = _toArray(replace),
      op = _replace[0],
      operands = _replace.slice(1);
    var childCountWithoutPayloads = operands.length - +isPayload(operands[operands.length - 1]) - +isPayload(operands[operands.length - 2]);
    var shiftedChildCount = childCountWithoutPayloads << 16 /* ChildCountShift */;
    var bestScore = -1;
    var bestIndex;
    var newPtr;
    var existingOperands;
    for (var i = 0; i < reusableNodes.length; i++) {
      var reusableNode = reusableNodes[i];
      if (reusableNode.w.length === operands.length) {
        var score = 0;
        for (var i2 = 0; i2 < operands.length; i2++) {
          if (operands[i2] === reusableNode.w[i2]) {
            score++;
          }
        }
        if (score > bestScore) {
          bestScore = score;
          bestIndex = i;
          newPtr = reusableNode.v;
          existingOperands = reusableNode.w;
        }
      }
    }
    if (typeof op === "string" && oneOfOps[op].v === newPtr) {} else {
      var node = (typeof op === "string" ? `${oneOfOps[op].ga}|${shiftedChildCount}` : `${op | shiftedChildCount}`) + outStackSlot;
      if (newPtr) {
        reusableNodes.splice(bestIndex, 1);
        code += `${astVar}[${newPtr}]=${node};`;
      } else {
        newPtr = newVarName();
        code += `var ${newPtr}=${allocateNode}(${node},${replace.length});`;
      }
    }
    for (var _i2 = 0; _i2 < operands.length; _i2++) {
      if (existingOperands && operands[_i2] !== existingOperands[_i2]) {
        var value = constructReplacement(operands[_i2], placeholderVars, reusableNodes);
        code += `${astVar}[${newPtr}+${_i2 + 1}]=${value};`;
      }
    }
    return newPtr;
  };
  var placeholderExprs = {};
  var oneOfOps = {};
  var recordStatsVar = newVarName();
  var astVar = newVarName();
  var constantsVar = newVarName();
  var allocateNode = newVarName();
  var rootPtrVar = newVarName();
  var rootOpVar = newVarName();
  var opCanBeOptimized = /* @__PURE__ */new Set();
  for (var _ref4 of rules) {
    var _ref4$a = _slicedToArray(_ref4.a, 1);
    var pattern = _ref4$a[0];
    if (typeof pattern === "number") {
      opCanBeOptimized.add(pattern);
    } else {
      var _pattern2 = _toArray(pattern),
        oneOf = _pattern2.slice(1);
      for (var op of oneOf) opCanBeOptimized.add(op);
    }
  }
  var code = `for(;;){var ${rootOpVar}=${astVar}[${rootPtrVar}]&${65535 /* OpMask */};`;
  compileRules(rootPtrVar, rootOpVar, rules, false ? matchToStatName : null, [], {});
  code += `return ${rootPtrVar}}`;
  return false ? new Function(recordStatsVar, `return(${astVar},${constantsVar},${allocateNode},${rootPtrVar})=>{${code}}`)(recordStats) : new Function(astVar, constantsVar, allocateNode, rootPtrVar, code);
};

// src/parse.ts
var parse = bytes => {
  var dataView = new DataView(bytes.buffer);
  var readU32LEB = () => {
    var value = 0;
    var shift = 0;
    var byte;
    do {
      byte = bytes[ptr++];
      value |= (byte & 127) << shift;
      shift += 7;
    } while (byte & 128);
    return value >>> 0;
  };
  var readI32LEB = () => {
    var value = 0;
    var shift = 0;
    var byte;
    do {
      byte = bytes[ptr++];
      value |= (byte & 127) << shift;
      shift += 7;
    } while (byte & 128);
    return shift < 32 && byte & 64 ? value | ~0 << shift : value;
  };
  var readI64LEB = () => {
    var value = 0n;
    var shift = 0n;
    var byte;
    do {
      byte = bytes[ptr++];
      value |= BigInt(byte & 127) << shift;
      shift += 7n;
    } while (byte & 128);
    return shift < 64 && byte & 64 ? value | ~0n << shift : value;
  };
  var readF32 = () => {
    var value = dataView.getFloat32(ptr, true);
    ptr += 4;
    return value;
  };
  var readF64 = () => {
    var value = dataView.getFloat64(ptr, true);
    ptr += 8;
    return value;
  };
  var readValueTypes = function () {
    var count = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : readU32LEB();
    return _toConsumableArray(bytes.slice(ptr, ptr += count));
  };
  var readName = function () {
    var length = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : readU32LEB();
    return decodeURIComponent(escape(String.fromCharCode.apply(String, _toConsumableArray(bytes.slice(ptr, ptr += length)))));
  };
  var readLimits = function () {
    var kind = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : bytes[ptr++];
    return [readU32LEB(), kind === 0 /* OnlyMin */ ? Infinity : readU32LEB()];
  };
  var readConstantU32 = () => {
    var stack = [];
    var op;
    var _loop3 = function () {
      if (op === 65 /* i32_const */) {
        var value = readU32LEB();
        stack.push(() => value);
      } else if (op === 35 /* global_get */) {
        var index = readU32LEB();
        stack.push(globals => {
          if (index >= globals.length) throw RangeError();
          return globals[index];
        });
      } else if (op === 106 /* i32_add */) {
        var right = stack.pop(),
          left = stack.pop();
        stack.push(globals => left(globals) + right(globals) | 0);
      } else if (op === 107 /* i32_sub */) {
        var _right = stack.pop(),
          _left = stack.pop();
        stack.push(globals => _left(globals) - _right(globals) | 0);
      } else if (op === 108 /* i32_mul */) {
        var _right2 = stack.pop(),
          _left2 = stack.pop();
        stack.push(globals => Math.imul(_left2(globals), _right2(globals)));
      } else {
        throw new CompileError("Unsupported constant instruction: " + formatHexByte(op));
      }
    };
    while ((op = bytes[ptr++]) !== 11 /* end */) {
      _loop3();
    }
    if (stack.length !== 1) throw new CompileError("Unsupported constant");
    return stack[0];
  };
  var readConstantFuncIndex = () => {
    var op = bytes[ptr++];
    var value;
    if (op === 210 /* ref_func */) value = readU32LEB();else if (op === 208 /* ref_null */) {
      if (bytes[ptr++] !== 112 /* FuncRef */) throw new CompileError("Unsupported reference type: " + formatHexByte(bytes[ptr - 1]));
      value = null;
    } else throw new CompileError("Unsupported constant instruction: " + formatHexByte(op));
    if (bytes[ptr++] !== 11 /* end */) throw new CompileError("Expected end after constant: " + formatHexByte(bytes[ptr - 1]));
    return value;
  };
  var readInitializer = type => {
    var op = bytes[ptr++];
    var initializer;
    if (op === 65 /* i32_const */ && type === 127 /* I32 */) {
      var value = readI32LEB();
      initializer = () => value;
    } else if (op === 66 /* i64_const */ && type === 126 /* I64 */) {
      var _value = readI64LEB();
      initializer = () => _value;
    } else if (op === 67 /* f32_const */ && type === 125 /* F32 */) {
      var _value2 = readF32();
      initializer = () => _value2;
    } else if (op === 68 /* f64_const */ && type === 124 /* F64 */) {
      var _value3 = readF64();
      initializer = () => _value3;
    } else if (op === 208 /* ref_null */ && (type === 112 /* FuncRef */ || type === 111 /* ExternRef */)) {
      ptr++;
      initializer = () => null;
    } else if (op === 210 /* ref_func */ && type === 112 /* FuncRef */) {
      var index = readU32LEB();
      initializer = (_, createLazyFunc) => createLazyFunc(index);
    } else if (op === 35 /* global_get */) {
      var _index = readU32LEB();
      initializer = globals => {
        if (_index >= globals.length) throw RangeError();
        return globals[_index];
      };
    } else throw new CompileError("Unsupported constant instruction: " + formatHexByte(op));
    if (bytes[ptr++] !== 11 /* end */) throw new CompileError("Expected end after constant: " + formatHexByte(bytes[ptr - 1]));
    return initializer;
  };
  var codeSection = [];
  var customSections = [];
  var dataSection = [];
  var elementSection = [];
  var exportSection = [];
  var functionSection = [];
  var globalSection = [];
  var importSection = [];
  var memorySection = [];
  var nameSection = /* @__PURE__ */new Map();
  var tableSection = [];
  var typeSection = [];
  var startSection = -1;
  var ptr = 8;
  if (bytes.slice(0, 8).join(",") !== "0,97,115,109,1,0,0,0") throw new CompileError("Invalid file header");
  while (ptr + 5 < bytes.length) {
    var sectionType = bytes[ptr++];
    var contentsSize = readU32LEB();
    var sectionEnd = ptr + contentsSize;
    if (sectionType === 0 /* Custom */) {
      var sectionName = readName();
      customSections.push([sectionName, bytes.slice(ptr, sectionEnd)]);
      if (sectionName === "name") {
        var subsection = bytes[ptr++];
        var subsectionEnd = ptr + readU32LEB();
        if (subsection === 1 /* Function */) {
          for (var i = 0, count = readU32LEB(); i < count && ptr < subsectionEnd; i++) {
            nameSection.set(readU32LEB(), readName());
          }
        }
      }
    } else if (sectionType === 1 /* Type */) {
      for (var _i3 = 0, typeCount = readU32LEB(); _i3 < typeCount; _i3++) {
        if (bytes[ptr++] !== 96) throw new CompileError("Invalid function type: " + formatHexByte(bytes[ptr - 1]));
        typeSection.push([readValueTypes(), readValueTypes()]);
      }
    } else if (sectionType === 2 /* Import */) {
      for (var _i4 = 0, importCount = readU32LEB(); _i4 < importCount; _i4++) {
        var module = readName();
        var name = readName();
        var desc = bytes[ptr++];
        if (desc === 0 /* Func */) importSection.push([module, name, desc, readU32LEB()]);else if (desc === 1 /* Table */) importSection.push([module, name, desc, bytes[ptr++]].concat(_toConsumableArray(readLimits())));else if (desc === 2 /* Mem */) importSection.push([module, name, desc].concat(_toConsumableArray(readLimits())));else if (desc === 3 /* Global */) importSection.push([module, name, desc, bytes[ptr++], bytes[ptr++]]);else throw new CompileError("Unsupported import type: " + formatHexByte(desc));
      }
    } else if (sectionType === 3 /* Function */) {
      var functionCount = readU32LEB();
      for (var _i5 = 0; _i5 < functionCount; _i5++) {
        functionSection.push(readU32LEB());
      }
    } else if (sectionType === 4 /* Table */) {
      for (var _i6 = 0, tableCount = readU32LEB(); _i6 < tableCount; _i6++) {
        tableSection.push([bytes[ptr++]].concat(_toConsumableArray(readLimits())));
      }
    } else if (sectionType === 5 /* Memory */) {
      for (var _i7 = 0, memoryCount = readU32LEB(); _i7 < memoryCount; _i7++) {
        memorySection.push(readLimits());
      }
    } else if (sectionType === 6 /* Global */) {
      for (var _i8 = 0, globalCount = readU32LEB(); _i8 < globalCount; _i8++) {
        var type = bytes[ptr++];
        var mutable = bytes[ptr++];
        var initializer = readInitializer(type);
        globalSection.push([type, mutable, initializer]);
      }
    } else if (sectionType === 7 /* Export */) {
      for (var _i9 = 0, exportCount = readU32LEB(); _i9 < exportCount; _i9++) {
        var _name = readName();
        var _desc = bytes[ptr++];
        var index = readU32LEB();
        exportSection.push([_name, _desc, index]);
      }
    } else if (sectionType === 8 /* Start */) {
      startSection = readU32LEB();
    } else if (sectionType === 9 /* Element */) {
      for (var _i0 = 0, elementCount = readU32LEB(); _i0 < elementCount; _i0++) {
        var flags = readU32LEB();
        if (flags > (1 /* Passive */ | 2 /* TableIndex */ | 4 /* Expression */)) {
          throw new CompileError("Unsupported element kind: " + flags);
        }
        var mode = flags & (1 /* Passive */ | 2 /* TableIndex */);
        var tableIndex = mode === 2 /* TableIndex */ ? readU32LEB() : mode === 0 ? 0 : null;
        var offset = flags & 1 /* Passive */ ? null : readConstantU32();
        if (mode && bytes[ptr++] !== (flags & 4 /* Expression */ ? 112 /* FuncRef */ : 0)) {
          throw new CompileError("Unsupported element type: " + formatHexByte(bytes[ptr - 1]));
        }
        var indices = [];
        for (var j = 0, _count = readU32LEB(); j < _count; j++) {
          indices.push(flags & 4 /* Expression */ ? readConstantFuncIndex() : readU32LEB());
        }
        elementSection.push([tableIndex, offset, indices]);
      }
    } else if (sectionType === 10 /* Code */) {
      for (var _i1 = 0, codeCount = readU32LEB(); _i1 < codeCount; _i1++) {
        var codeEnd = readU32LEB() + ptr;
        var localsCount = readU32LEB();
        var locals = [];
        for (var _j2 = 0; _j2 < localsCount; _j2++) locals.push([readU32LEB(), bytes[ptr++]]);
        codeSection.push([locals, ptr, codeEnd]);
        ptr = codeEnd;
      }
    } else if (sectionType === 11 /* Data */) {
      for (var _i10 = 0, dataCount = readU32LEB(); _i10 < dataCount; _i10++) {
        var _mode = readU32LEB();
        if (_mode > 2 /* Active */) throw new CompileError("Unsupported data mode: " + _mode);
        var memory = _mode === 2 /* Active */ ? readU32LEB() : 0;
        var _offset = _mode === 1 /* Passive */ ? null : readConstantU32();
        var length = readU32LEB();
        dataSection.push([memory, _offset, bytes.slice(ptr, ptr += length)]);
      }
    } else if (sectionType === 12 /* DataCount */) {} else {
      throw new CompileError("Unsupported section type: " + formatHexByte(sectionType));
    }
    ptr = sectionEnd;
  }
  return {
    ia: bytes,
    h: dataView,
    H: codeSection,
    ta: customSections,
    ja: dataSection,
    ka: elementSection,
    la: exportSection,
    I: functionSection,
    ma: globalSection,
    na: importSection,
    oa: memorySection,
    pa: nameSection,
    qa: startSection,
    ra: tableSection,
    J: typeSection
  };
};
var formatHexByte = x => {
  return "0x" + x.toString(16).toUpperCase().padStart(2, "0");
};
var moduleMap = /* @__PURE__ */new Map();
var Module = /*#__PURE__*/_createClass(function Module(source) {
  _classCallCheck(this, Module);
  moduleMap.set(this, parse(source instanceof Uint8Array ? source : new Uint8Array(source instanceof ArrayBuffer ? source : source.buffer)));
});
var CompileError = /*#__PURE__*/function (_Error) {
  function CompileError(message) {
    var _this;
    _classCallCheck(this, CompileError);
    _this = _callSuper(this, CompileError, [message]);
    _this.name = "CompileError";
    return _this;
  }
  _inherits(CompileError, _Error);
  return _createClass(CompileError);
}(/*#__PURE__*/_wrapNativeSuper(Error));

// src/compile.ts
var liveCastToWASM = (value, type) => {
  if (type === 125 /* F32 */ || type === 124 /* F64 */) return +value;
  if (type === 127 /* I32 */) return value | 0;
  if (type === 126 /* I64 */) return BigInt(value) & 0xFFFFFFFFFFFFFFFFn;
  if (type === 111 /* ExternRef */) return value;
  throw Error("Unsupported cast to type: " + formatHexByte(type));
};
var castToWASM = (code, type) => {
  if (type === 125 /* F32 */ || type === 124 /* F64 */) return "+" + code;
  if (type === 127 /* I32 */) return code + "|0";
  if (type === 126 /* I64 */) return `BigInt(${code})&0xFFFFFFFFFFFFFFFFn`;
  if (type === 111 /* ExternRef */) return code;
  if (type === 112 /* FuncRef */) return `l.${/* @__KEY__ */"r"}(${code})`;
  throw Error("Unsupported cast to type: " + formatHexByte(type));
};
var castToJS = (code, type) => {
  if (type === 124 /* F64 */ || type === 127 /* I32 */) return code;
  if (type === 125 /* F32 */) return `Math.fround(${code})`;
  if (type === 126 /* I64 */) return `l.${/* @__KEY__ */"p"}(${code})`;
  if (type === 111 /* ExternRef */) return code;
  if (type === 112 /* FuncRef */) return `l.${/* @__KEY__ */"s"}(${code})`;
  throw Error("Unsupported cast to type: " + formatHexByte(type));
};
var metaTable = {
  [1 /* nop */]: 512 /* Omit */ | 8 /* Simple */,
  [26 /* drop */]: 1 | 512 /* Omit */ | 8 /* Simple */,
  [32 /* local_get */]: 4 /* Push */ | 16 /* HasIndex */ | 8 /* Simple */,
  [33 /* local_set */]: 1 | 16 /* HasIndex */ | 8 /* Simple */,
  [34 /* local_tee */]: 1 | 4 /* Push */ | 16 /* HasIndex */ | 8 /* Simple */,
  [35 /* global_get */]: 4 /* Push */ | 16 /* HasIndex */ | 8 /* Simple */,
  [36 /* global_set */]: 1 | 16 /* HasIndex */ | 8 /* Simple */,
  [37 /* table_get */]: 1 | 4 /* Push */ | 16 /* HasIndex */ | 8 /* Simple */,
  [38 /* table_set */]: 2 | 16 /* HasIndex */ | 8 /* Simple */,
  [40 /* i32_load */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [41 /* i64_load */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [42 /* f32_load */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [43 /* f64_load */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [44 /* i32_load8_s */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [45 /* i32_load8_u */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [46 /* i32_load16_s */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [47 /* i32_load16_u */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [48 /* i64_load8_s */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [49 /* i64_load8_u */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [50 /* i64_load16_s */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [51 /* i64_load16_u */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [52 /* i64_load32_s */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [53 /* i64_load32_u */]: 1 | 4 /* Push */ | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [54 /* i32_store */]: 2 | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [55 /* i64_store */]: 2 | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [56 /* f32_store */]: 2 | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [57 /* f64_store */]: 2 | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [58 /* i32_store8 */]: 2 | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [59 /* i32_store16 */]: 2 | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [60 /* i64_store8 */]: 2 | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [61 /* i64_store16 */]: 2 | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [62 /* i64_store32 */]: 2 | 32 /* HasMemory */ | 16 /* HasIndex */ | 8 /* Simple */,
  [63 /* memory_size */]: 4 /* Push */ | 16 /* HasIndex */ | 8 /* Simple */,
  [64 /* memory_grow */]: 1 | 4 /* Push */ | 16 /* HasIndex */ | 8 /* Simple */,
  [69 /* i32_eqz */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [70 /* i32_eq */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [71 /* i32_ne */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [72 /* i32_lt_s */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [73 /* i32_lt_u */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */ | 128 /* ToU32 */,
  [74 /* i32_gt_s */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [75 /* i32_gt_u */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */ | 128 /* ToU32 */,
  [76 /* i32_le_s */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [77 /* i32_le_u */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */ | 128 /* ToU32 */,
  [78 /* i32_ge_s */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [79 /* i32_ge_u */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */ | 128 /* ToU32 */,
  [80 /* i64_eqz */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [81 /* i64_eq */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [82 /* i64_ne */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [83 /* i64_lt_s */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */ | 256 /* ToS64 */,
  [84 /* i64_lt_u */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [85 /* i64_gt_s */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */ | 256 /* ToS64 */,
  [86 /* i64_gt_u */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [87 /* i64_le_s */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */ | 256 /* ToS64 */,
  [88 /* i64_le_u */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [89 /* i64_ge_s */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */ | 256 /* ToS64 */,
  [90 /* i64_ge_u */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [91 /* f32_eq */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [92 /* f32_ne */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [93 /* f32_lt */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [94 /* f32_gt */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [95 /* f32_le */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [96 /* f32_ge */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [97 /* f64_eq */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [98 /* f64_ne */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [99 /* f64_lt */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [100 /* f64_gt */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [101 /* f64_le */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [102 /* f64_ge */]: 2 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [103 /* i32_clz */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [104 /* i32_ctz */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [105 /* i32_popcnt */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [106 /* i32_add */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [107 /* i32_sub */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [108 /* i32_mul */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [109 /* i32_div_s */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [110 /* i32_div_u */]: 2 | 4 /* Push */ | 8 /* Simple */ | 128 /* ToU32 */,
  [111 /* i32_rem_s */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [112 /* i32_rem_u */]: 2 | 4 /* Push */ | 8 /* Simple */ | 128 /* ToU32 */,
  [113 /* i32_and */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [114 /* i32_or */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [115 /* i32_xor */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [116 /* i32_shl */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [117 /* i32_shr_s */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [118 /* i32_shr_u */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [119 /* i32_rotl */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [120 /* i32_rotr */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [121 /* i64_clz */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [122 /* i64_ctz */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [123 /* i64_popcnt */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [124 /* i64_add */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [125 /* i64_sub */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [126 /* i64_mul */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [127 /* i64_div_s */]: 2 | 4 /* Push */ | 8 /* Simple */ | 256 /* ToS64 */,
  [128 /* i64_div_u */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [129 /* i64_rem_s */]: 2 | 4 /* Push */ | 8 /* Simple */ | 256 /* ToS64 */,
  [130 /* i64_rem_u */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [131 /* i64_and */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [132 /* i64_or */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [133 /* i64_xor */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [134 /* i64_shl */]: 2 | 4 /* Push */ | 8 /* Simple */ | 1024 /* And63 */,
  [135 /* i64_shr_s */]: 2 | 4 /* Push */ | 8 /* Simple */ | 1024 /* And63 */,
  [136 /* i64_shr_u */]: 2 | 4 /* Push */ | 8 /* Simple */ | 1024 /* And63 */,
  [137 /* i64_rotl */]: 2 | 4 /* Push */ | 8 /* Simple */ | 1024 /* And63 */,
  [138 /* i64_rotr */]: 2 | 4 /* Push */ | 8 /* Simple */ | 1024 /* And63 */,
  [139 /* f32_abs */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [140 /* f32_neg */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [141 /* f32_ceil */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [142 /* f32_floor */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [143 /* f32_trunc */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [144 /* f32_nearest */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [145 /* f32_sqrt */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [146 /* f32_add */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [147 /* f32_sub */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [148 /* f32_mul */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [149 /* f32_div */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [150 /* f32_min */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [151 /* f32_max */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [152 /* f32_copysign */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [153 /* f64_abs */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [154 /* f64_neg */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [155 /* f64_ceil */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [156 /* f64_floor */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [157 /* f64_trunc */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [158 /* f64_nearest */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [159 /* f64_sqrt */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [160 /* f64_add */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [161 /* f64_sub */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [162 /* f64_mul */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [163 /* f64_div */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [164 /* f64_min */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [165 /* f64_max */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [166 /* f64_copysign */]: 2 | 4 /* Push */ | 8 /* Simple */,
  [167 /* i32_wrap_i64 */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [168 /* i32_trunc_f32_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [169 /* i32_trunc_f32_u */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [170 /* i32_trunc_f64_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [171 /* i32_trunc_f64_u */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [172 /* i64_extend_i32_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [173 /* i64_extend_i32_u */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [174 /* i64_trunc_f32_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [175 /* i64_trunc_f32_u */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [176 /* i64_trunc_f64_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [177 /* i64_trunc_f64_u */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [178 /* f32_convert_i32_s */]: 1 | 4 /* Push */ | 512 /* Omit */ | 8 /* Simple */,
  [179 /* f32_convert_i32_u */]: 1 | 4 /* Push */ | 512 /* Omit */ | 8 /* Simple */ | 128 /* ToU32 */,
  [180 /* f32_convert_i64_s */]: 1 | 4 /* Push */ | 8 /* Simple */ | 256 /* ToS64 */,
  [181 /* f32_convert_i64_u */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [182 /* f32_demote_f64 */]: 1 | 4 /* Push */ | 512 /* Omit */ | 8 /* Simple */,
  [183 /* f64_convert_i32_s */]: 1 | 4 /* Push */ | 512 /* Omit */ | 8 /* Simple */,
  [184 /* f64_convert_i32_u */]: 1 | 4 /* Push */ | 512 /* Omit */ | 8 /* Simple */ | 128 /* ToU32 */,
  [185 /* f64_convert_i64_s */]: 1 | 4 /* Push */ | 8 /* Simple */ | 256 /* ToS64 */,
  [186 /* f64_convert_i64_u */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [187 /* f64_promote_f32 */]: 1 | 4 /* Push */ | 512 /* Omit */ | 8 /* Simple */,
  [188 /* i32_reinterpret_f32 */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [189 /* i64_reinterpret_f64 */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [190 /* f32_reinterpret_i32 */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [191 /* f64_reinterpret_i64 */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [192 /* i32_extend8_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [193 /* i32_extend16_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [194 /* i64_extend8_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [195 /* i64_extend16_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [196 /* i64_extend32_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [209 /* ref_is_null */]: 1 | 4 /* Push */ | 8 /* Simple */ | 64 /* BoolToInt */,
  [210 /* ref_func */]: 4 /* Push */ | 16 /* HasIndex */ | 8 /* Simple */,
  [64512 /* i32_trunc_sat_f32_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [64513 /* i32_trunc_sat_f32_u */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [64514 /* i32_trunc_sat_f64_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [64515 /* i32_trunc_sat_f64_u */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [64516 /* i64_trunc_sat_f32_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [64517 /* i64_trunc_sat_f32_u */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [64518 /* i64_trunc_sat_f64_s */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [64519 /* i64_trunc_sat_f64_u */]: 1 | 4 /* Push */ | 8 /* Simple */,
  [64521 /* data_drop */]: 16 /* HasIndex */ | 8 /* Simple */,
  [64525 /* elem_drop */]: 16 /* HasIndex */ | 8 /* Simple */,
  [64527 /* table_grow */]: 2 | 4 /* Push */ | 16 /* HasIndex */ | 8 /* Simple */,
  [64528 /* table_size */]: 4 /* Push */ | 16 /* HasIndex */ | 8 /* Simple */,
  [64529 /* table_fill */]: 3 | 16 /* HasIndex */ | 8 /* Simple */
};
var astBufferSingleton = new Int32Array(1 << 16);
var optimizeNode = compileOptimizations();
var compileCode = (funcs, funcTypes, createLazyFunc, tables, dataSegments, elementSegments, globals, context, wasm, codeIndex, funcIndex) => {
  var readU32LEB = () => {
    var value = 0;
    var shift = 0;
    var byte;
    do {
      byte = bytes[bytesPtr++];
      value |= (byte & 127) << shift;
      shift += 7;
    } while (byte & 128);
    return value >>> 0;
  };
  var readI32LEB = () => {
    var value = 0;
    var shift = 0;
    var byte;
    do {
      byte = bytes[bytesPtr++];
      value |= (byte & 127) << shift;
      shift += 7;
    } while (byte & 128);
    return shift < 32 && byte & 64 ? value | ~0 << shift : value;
  };
  var readI64LEB = () => {
    var value = 0n;
    var shift = 0n;
    var byte;
    do {
      byte = bytes[bytesPtr++];
      value |= BigInt(byte & 127) << shift;
      shift += 7n;
    } while (byte & 128);
    return shift < 64 && byte & 64 ? value | ~0n << shift : value;
  };
  var readBlockType = () => {
    var byte = bytes[bytesPtr];
    if (byte === 64) {
      bytesPtr++;
      return [0, 0];
    }
    if (byte & 64) {
      bytesPtr++;
      return [0, 1];
    }
    var typeIndex = readU32LEB();
    var _typeSection$typeInde = _slicedToArray(typeSection[typeIndex], 2),
      argTypes2 = _typeSection$typeInde[0],
      returnTypes2 = _typeSection$typeInde[1];
    return [argTypes2.length, returnTypes2.length];
  };
  var ast = astBufferSingleton;
  var astPtrs = [];
  var astNextPtr = 0;
  var constants = [];
  var stackLimit = 0;
  var stackSlotName = stackSlot => {
    while (stackLimit < stackSlot) decls.push("s" + ++stackLimit);
    return "s" + stackSlot;
  };
  var usedTables = {};
  var tableName = index => {
    if (!usedTables[index]) {
      decls.push(`t${index}=t[${index}]`);
      usedTables[index] = true;
    }
    return "t" + index;
  };
  var load8 = (field, addr, offset, index) => {
    return `c.${field + index}[${emit(addr)}${offset ? "+" + offset : ""}]`;
  };
  var store8 = (field, addr, offset, index, value) => {
    return `c.${field + index}[${emit(addr)}${offset ? "+" + offset : ""}]=${value}`;
  };
  var load = (get, addr, offset, index) => {
    return `c.${/* @__KEY__ */"h" + index}.get${get}(${emit(addr)}${offset ? "+" + offset : ""},1)`;
  };
  var store = (set, addr, offset, index, value) => {
    return `c.${/* @__KEY__ */"h" + index}.set${set}(${emit(addr)}${offset ? "+" + offset : ""},${value},1)`;
  };
  var emit = ptr => {
    return ptr < 0 ? stackSlotName(-ptr) : `(${emitUnwrapped(ptr)})`;
  };
  var emitUnwrapped = ptr => {
    var node = ast[ptr];
    var op = node & 65535 /* OpMask */;
    switch (op) {
      case 16 /* call */:
      case 18 /* return_call */:
        {
          var childCount = node >> 16 /* ChildCountShift */ & 255 /* ChildCountMask */;
          var funcIndex2 = ast[ptr + childCount + 1];
          var _funcTypes$funcIndex = _slicedToArray(funcTypes[funcIndex2], 2),
            argTypes2 = _funcTypes$funcIndex[0],
            returnTypes2 = _funcTypes$funcIndex[1];
          var args = [];
          for (var i = 1; i <= childCount; i++) args.push(emit(ast[ptr + i]));
          var fn = `f[${funcIndex2}]`;
          var code = op === 18 /* return_call */ ? `l.${/* @__KEY__ */"G"}(this,${fn},[${args}])` : `${fn}(${args})`;
          if (returnTypes2.length < 2) return code;
          var slot = ast[ptr + childCount + 2];
          var returns = [];
          for (var _i11 = 0; _i11 < returnTypes2.length; _i11++) returns.push(stackSlotName(slot + _i11));
          return `[${returns}]=${code}`;
        }
      case 17 /* call_indirect */:
      case 19 /* return_call_indirect */:
        {
          var _childCount = node >> 16 /* ChildCountShift */ & 255 /* ChildCountMask */;
          var tableIndex = ast[ptr + _childCount + 2];
          var typeIndex = ast[ptr + _childCount + 3];
          var _typeSection$typeInde2 = _slicedToArray(typeSection[typeIndex], 2),
            _argTypes = _typeSection$typeInde2[0],
            _returnTypes = _typeSection$typeInde2[1];
          var _args = [];
          var _funcIndex = emit(ast[ptr + 1]);
          for (var _i12 = 1; _i12 <= _childCount; _i12++) _args.push(emit(ast[ptr + _i12 + 1]));
          var _fn = `${tableName(tableIndex)}[${_funcIndex}].${/* @__KEY__ */"u"}`;
          var _code = op === 19 /* return_call_indirect */ ? `l.${/* @__KEY__ */"G"}(this,${_fn},[${_args}])` : `${_fn}(${_args})`;
          if (_returnTypes.length < 2) return _code;
          var _slot = ast[ptr + _childCount + 4];
          var _returns = [];
          for (var _i13 = 0; _i13 < _returnTypes.length; _i13++) _returns.push(stackSlotName(_slot + _i13));
          return `[${_returns}]=${_code}`;
        }
      case 27 /* select */:
      case 28 /* select_type */:
        return `${emit(ast[ptr + 1])}?${emit(ast[ptr + 2])}:${emit(ast[ptr + 3])}`;
      case 32 /* local_get */:
        return names[ast[ptr + 1]];
      case 33 /* local_set */:
      case 34 /* local_tee */:
        return `${names[ast[ptr + 2]]}=${emit(ast[ptr + 1])}`;
      case 35 /* global_get */:
        return `g[${ast[ptr + 1]}]`;
      case 36 /* global_set */:
        return `g[${ast[ptr + 2]}]=${emit(ast[ptr + 1])}`;
      case 37 /* table_get */:
        return tableName(ast[ptr + 2]) + `[${emit(ast[ptr + 1])}]`;
      case 38 /* table_set */:
        return tableName(ast[ptr + 3]) + `[${emit(ast[ptr + 1])}]=${emit(ast[ptr + 2])}`;
      case 40 /* i32_load */:
        return load("Int32", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3]);
      case 245 /* U32_LOAD */:
        return load("Uint32", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3]);
      case 41 /* i64_load */:
        return load("BigUint64", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3]);
      case 246 /* S64_LOAD */:
        return load("BigInt64", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3]);
      case 42 /* f32_load */:
        return load("Float32", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3]);
      case 43 /* f64_load */:
        return load("Float64", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3]);
      case 44 /* i32_load8_s */:
        return load8(/* @__KEY__ */"k", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3]);
      case 45 /* i32_load8_u */:
        return load8(/* @__KEY__ */"e", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3]);
      case 46 /* i32_load16_s */:
        return load("Int16", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3]);
      case 47 /* i32_load16_u */:
        return load("Uint16", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3]);
      case 48 /* i64_load8_s */:
        return `BigInt(${load8(/* @__KEY__ */"k", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3])})&0xFFFFFFFFFFFFFFFFn`;
      case 49 /* i64_load8_u */:
        return `BigInt(${load8(/* @__KEY__ */"e", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3])})`;
      case 50 /* i64_load16_s */:
        return `BigInt(${load("Int16", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3])})&0xFFFFFFFFFFFFFFFFn`;
      case 51 /* i64_load16_u */:
        return `BigInt(${load("Uint16", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3])})`;
      case 52 /* i64_load32_s */:
        return `BigInt(${load("Int32", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3])})&0xFFFFFFFFFFFFFFFFn`;
      case 53 /* i64_load32_u */:
        return `BigInt(${load("Uint32", ast[ptr + 1], ast[ptr + 2], ast[ptr + 3])})`;
      case 54 /* i32_store */:
        return store("Int32", ast[ptr + 1], ast[ptr + 3], ast[ptr + 4], emit(ast[ptr + 2]));
      case 55 /* i64_store */:
        return store("BigUint64", ast[ptr + 1], ast[ptr + 3], ast[ptr + 4], emit(ast[ptr + 2]));
      case 56 /* f32_store */:
        return store("Float32", ast[ptr + 1], ast[ptr + 3], ast[ptr + 4], emit(ast[ptr + 2]));
      case 57 /* f64_store */:
        return store("Float64", ast[ptr + 1], ast[ptr + 3], ast[ptr + 4], emit(ast[ptr + 2]));
      case 58 /* i32_store8 */:
        return store8(/* @__KEY__ */"e", ast[ptr + 1], ast[ptr + 3], ast[ptr + 4], emit(ast[ptr + 2]));
      case 59 /* i32_store16 */:
        return store("Int16", ast[ptr + 1], ast[ptr + 3], ast[ptr + 4], emit(ast[ptr + 2]));
      case 60 /* i64_store8 */:
        return store8(/* @__KEY__ */"e", ast[ptr + 1], ast[ptr + 3], ast[ptr + 4], `Number(${emit(ast[ptr + 2])}&255n)`);
      case 61 /* i64_store16 */:
        return store("Int16", ast[ptr + 1], ast[ptr + 3], ast[ptr + 4], `Number(${emit(ast[ptr + 2])}&65535n)`);
      case 62 /* i64_store32 */:
        return store("Int32", ast[ptr + 1], ast[ptr + 3], ast[ptr + 4], `Number(${emit(ast[ptr + 2])}&0xFFFFFFFFn)`);
      case 63 /* memory_size */:
        return `c.${/* @__KEY__ */"K" + ast[ptr + 1]}.${/* @__KEY__ */"n"}`;
      case 64 /* memory_grow */:
        return `c.${/* @__KEY__ */"K" + ast[ptr + 2]}.${/* @__KEY__ */"L"}(${emit(ast[ptr + 1])})`;
      case 65 /* i32_const */:
        return ast[ptr + 1] + "";
      case 66 /* i64_const */:
        return (constants[ast[ptr + 1]] & 0xFFFFFFFFFFFFFFFFn) + "n";
      case 67 /* f32_const */:
        {
          var value = dataView.getFloat32(ast[ptr + 1], true);
          return Object.is(value, -0) ? "-0" : value + "";
        }
      case 68 /* f64_const */:
        {
          var _value4 = dataView.getFloat64(ast[ptr + 1], true);
          return Object.is(_value4, -0) ? "-0" : _value4 + "";
        }
      case 240 /* BOOL */:
        return emit(ast[ptr + 1]);
      case 241 /* BOOL_NOT */:
        return `!${emit(ast[ptr + 1])}`;
      case 242 /* BOOL_TO_INT */:
        return `${emit(ast[ptr + 1])}?1:0`;
      case 243 /* TO_U32 */:
        return `${emit(ast[ptr + 1])}>>>0`;
      case 244 /* TO_S64 */:
        return `l.${/* @__KEY__ */"p"}(${emit(ast[ptr + 1])})`;
      case 69 /* i32_eqz */:
      case 80 /* i64_eqz */:
        return `${emit(ast[ptr + 1])}?0:1`;
      case 70 /* i32_eq */:
      case 81 /* i64_eq */:
      case 91 /* f32_eq */:
      case 97 /* f64_eq */:
        return `${emit(ast[ptr + 1])}===${emit(ast[ptr + 2])}`;
      case 71 /* i32_ne */:
      case 82 /* i64_ne */:
      case 92 /* f32_ne */:
      case 98 /* f64_ne */:
        return `${emit(ast[ptr + 1])}!==${emit(ast[ptr + 2])}`;
      case 72 /* i32_lt_s */:
      case 73 /* i32_lt_u */:
      case 83 /* i64_lt_s */:
      case 84 /* i64_lt_u */:
      case 93 /* f32_lt */:
      case 99 /* f64_lt */:
        return `${emit(ast[ptr + 1])}<${emit(ast[ptr + 2])}`;
      case 74 /* i32_gt_s */:
      case 75 /* i32_gt_u */:
      case 85 /* i64_gt_s */:
      case 86 /* i64_gt_u */:
      case 94 /* f32_gt */:
      case 100 /* f64_gt */:
        return `${emit(ast[ptr + 1])}>${emit(ast[ptr + 2])}`;
      case 76 /* i32_le_s */:
      case 77 /* i32_le_u */:
      case 87 /* i64_le_s */:
      case 88 /* i64_le_u */:
      case 95 /* f32_le */:
      case 101 /* f64_le */:
        return `${emit(ast[ptr + 1])}<=${emit(ast[ptr + 2])}`;
      case 78 /* i32_ge_s */:
      case 79 /* i32_ge_u */:
      case 89 /* i64_ge_s */:
      case 90 /* i64_ge_u */:
      case 96 /* f32_ge */:
      case 102 /* f64_ge */:
        return `${emit(ast[ptr + 1])}>=${emit(ast[ptr + 2])}`;
      case 103 /* i32_clz */:
        return `Math.clz32(${emit(ast[ptr + 1])})`;
      case 104 /* i32_ctz */:
        return `l.${/* @__KEY__ */"W"}(${emit(ast[ptr + 1])})`;
      case 105 /* i32_popcnt */:
        return `l.${/* @__KEY__ */"X"}(${emit(ast[ptr + 1])})`;
      case 106 /* i32_add */:
        return `${emit(ast[ptr + 1])}+${emit(ast[ptr + 2])}|0`;
      case 107 /* i32_sub */:
        return `${emit(ast[ptr + 1])}-${emit(ast[ptr + 2])}|0`;
      case 108 /* i32_mul */:
        return `Math.imul(${emit(ast[ptr + 1])},${emit(ast[ptr + 2])})`;
      case 110 /* i32_div_u */:
      case 109 /* i32_div_s */:
        return `${emit(ast[ptr + 1])}/${emit(ast[ptr + 2])}|0`;
      case 112 /* i32_rem_u */:
      case 111 /* i32_rem_s */:
        return `${emit(ast[ptr + 1])}%${emit(ast[ptr + 2])}|0`;
      case 113 /* i32_and */:
        return `${emit(ast[ptr + 1])}&${emit(ast[ptr + 2])}`;
      case 114 /* i32_or */:
        return `${emit(ast[ptr + 1])}|${emit(ast[ptr + 2])}`;
      case 115 /* i32_xor */:
        return `${emit(ast[ptr + 1])}^${emit(ast[ptr + 2])}`;
      case 116 /* i32_shl */:
        return `${emit(ast[ptr + 1])}<<${emit(ast[ptr + 2])}`;
      case 117 /* i32_shr_s */:
        return `${emit(ast[ptr + 1])}>>${emit(ast[ptr + 2])}`;
      case 118 /* i32_shr_u */:
        return `${emit(ast[ptr + 1])}>>>${emit(ast[ptr + 2])}|0`;
      case 119 /* i32_rotl */:
        return `l.${/* @__KEY__ */"S"}(${emit(ast[ptr + 1])},${emit(ast[ptr + 2])})`;
      case 120 /* i32_rotr */:
        return `l.${/* @__KEY__ */"T"}(${emit(ast[ptr + 1])},${emit(ast[ptr + 2])})`;
      case 121 /* i64_clz */:
        return `l.${/* @__KEY__ */"Y"}(${emit(ast[ptr + 1])})`;
      case 122 /* i64_ctz */:
        return `l.${/* @__KEY__ */"Z"}(${emit(ast[ptr + 1])})`;
      case 123 /* i64_popcnt */:
        return `l.${/* @__KEY__ */"_"}(${emit(ast[ptr + 1])})`;
      case 124 /* i64_add */:
        return `(${emit(ast[ptr + 1])}+${emit(ast[ptr + 2])})&0xFFFFFFFFFFFFFFFFn`;
      case 125 /* i64_sub */:
        return `(${emit(ast[ptr + 1])}-${emit(ast[ptr + 2])})&0xFFFFFFFFFFFFFFFFn`;
      case 126 /* i64_mul */:
        return `(${emit(ast[ptr + 1])}*${emit(ast[ptr + 2])})&0xFFFFFFFFFFFFFFFFn`;
      case 127 /* i64_div_s */:
        return `${emit(ast[ptr + 1])}/${emit(ast[ptr + 2])}&0xFFFFFFFFFFFFFFFFn`;
      case 128 /* i64_div_u */:
        return `${emit(ast[ptr + 1])}/${emit(ast[ptr + 2])}`;
      case 129 /* i64_rem_s */:
        return `${emit(ast[ptr + 1])}%${emit(ast[ptr + 2])}&0xFFFFFFFFFFFFFFFFn`;
      case 130 /* i64_rem_u */:
        return `${emit(ast[ptr + 1])}%${emit(ast[ptr + 2])}`;
      case 131 /* i64_and */:
        return `${emit(ast[ptr + 1])}&${emit(ast[ptr + 2])}`;
      case 132 /* i64_or */:
        return `${emit(ast[ptr + 1])}|${emit(ast[ptr + 2])}`;
      case 133 /* i64_xor */:
        return `${emit(ast[ptr + 1])}^${emit(ast[ptr + 2])}`;
      case 134 /* i64_shl */:
        return `${emit(ast[ptr + 1])}<<${emit(ast[ptr + 2])}&0xFFFFFFFFFFFFFFFFn`;
      case 135 /* i64_shr_s */:
        return `l.${/* @__KEY__ */"p"}(${emit(ast[ptr + 1])})>>${emit(ast[ptr + 2])}&0xFFFFFFFFFFFFFFFFn`;
      case 136 /* i64_shr_u */:
        return `${emit(ast[ptr + 1])}>>${emit(ast[ptr + 2])}`;
      case 137 /* i64_rotl */:
        return `l.${/* @__KEY__ */"U"}(${emit(ast[ptr + 1])},${emit(ast[ptr + 2])})`;
      case 138 /* i64_rotr */:
        return `l.${/* @__KEY__ */"V"}(${emit(ast[ptr + 1])},${emit(ast[ptr + 2])})`;
      case 139 /* f32_abs */:
      case 153 /* f64_abs */:
        return `Math.abs(${emit(ast[ptr + 1])})`;
      case 140 /* f32_neg */:
      case 154 /* f64_neg */:
        return `-${emit(ast[ptr + 1])}`;
      case 141 /* f32_ceil */:
      case 155 /* f64_ceil */:
        return `Math.ceil(${emit(ast[ptr + 1])})`;
      case 142 /* f32_floor */:
      case 156 /* f64_floor */:
        return `Math.floor(${emit(ast[ptr + 1])})`;
      case 143 /* f32_trunc */:
      case 157 /* f64_trunc */:
        return `Math.trunc(${emit(ast[ptr + 1])})`;
      case 144 /* f32_nearest */:
      case 158 /* f64_nearest */:
        return `Math.round(${emit(ast[ptr + 1])})`;
      case 145 /* f32_sqrt */:
      case 159 /* f64_sqrt */:
        return `Math.sqrt(${emit(ast[ptr + 1])})`;
      case 146 /* f32_add */:
      case 160 /* f64_add */:
        return `${emit(ast[ptr + 1])}+${emit(ast[ptr + 2])}`;
      case 147 /* f32_sub */:
      case 161 /* f64_sub */:
        return `${emit(ast[ptr + 1])}-${emit(ast[ptr + 2])}`;
      case 148 /* f32_mul */:
      case 162 /* f64_mul */:
        return `${emit(ast[ptr + 1])}*${emit(ast[ptr + 2])}`;
      case 149 /* f32_div */:
      case 163 /* f64_div */:
        return `${emit(ast[ptr + 1])}/${emit(ast[ptr + 2])}`;
      case 150 /* f32_min */:
      case 164 /* f64_min */:
        return `Math.min(${emit(ast[ptr + 1])},${emit(ast[ptr + 2])})`;
      case 151 /* f32_max */:
      case 165 /* f64_max */:
        return `Math.max(${emit(ast[ptr + 1])},${emit(ast[ptr + 2])})`;
      case 152 /* f32_copysign */:
      case 166 /* f64_copysign */:
        return `l.${/* @__KEY__ */"N"}(${emit(ast[ptr + 1])},${emit(ast[ptr + 2])})`;
      case 167 /* i32_wrap_i64 */:
        return `Number(${emit(ast[ptr + 1])}&0xFFFFFFFFn)|0`;
      case 168 /* i32_trunc_f32_s */:
      case 169 /* i32_trunc_f32_u */:
      case 170 /* i32_trunc_f64_s */:
      case 171 /* i32_trunc_f64_u */:
        return `Math.trunc(${emit(ast[ptr + 1])})|0`;
      case 172 /* i64_extend_i32_s */:
        return `BigInt(${emit(ast[ptr + 1])})`;
      case 173 /* i64_extend_i32_u */:
        return `BigInt(${emit(ast[ptr + 1])}>>>0)`;
      case 174 /* i64_trunc_f32_s */:
      case 175 /* i64_trunc_f32_u */:
      case 176 /* i64_trunc_f64_s */:
      case 177 /* i64_trunc_f64_u */:
        return `BigInt(Math.trunc(${emit(ast[ptr + 1])}))&0xFFFFFFFFFFFFFFFFn`;
      case 180 /* f32_convert_i64_s */:
      case 181 /* f32_convert_i64_u */:
      case 186 /* f64_convert_i64_u */:
      case 185 /* f64_convert_i64_s */:
        return `Number(${emit(ast[ptr + 1])})`;
      case 188 /* i32_reinterpret_f32 */:
        return `l.${/* @__KEY__ */"O"}(${emit(ast[ptr + 1])})`;
      case 189 /* i64_reinterpret_f64 */:
        return `l.${/* @__KEY__ */"Q"}(${emit(ast[ptr + 1])})`;
      case 190 /* f32_reinterpret_i32 */:
        return `l.${/* @__KEY__ */"P"}(${emit(ast[ptr + 1])})`;
      case 191 /* f64_reinterpret_i64 */:
        return `l.${/* @__KEY__ */"R"}(${emit(ast[ptr + 1])})`;
      case 192 /* i32_extend8_s */:
        return `${emit(ast[ptr + 1])}<<24>>24`;
      case 193 /* i32_extend16_s */:
        return `${emit(ast[ptr + 1])}<<16>>16`;
      case 194 /* i64_extend8_s */:
        return `l.${/* @__KEY__ */"$"}(${emit(ast[ptr + 1])})`;
      case 195 /* i64_extend16_s */:
        return `l.${/* @__KEY__ */"aa"}(${emit(ast[ptr + 1])})`;
      case 196 /* i64_extend32_s */:
        return `l.${/* @__KEY__ */"ba"}(${emit(ast[ptr + 1])})`;
      case 208 /* ref_null */:
        return "null";
      case 209 /* ref_is_null */:
        return `${emit(ast[ptr + 1])}===null`;
      case 210 /* ref_func */:
        return `F(${ast[ptr + 1]})`;
      case 64512 /* i32_trunc_sat_f32_s */:
        return `l.${/* @__KEY__ */"z"}(${emit(ast[ptr + 1])})`;
      case 64513 /* i32_trunc_sat_f32_u */:
        return `l.${/* @__KEY__ */"A"}(${emit(ast[ptr + 1])})`;
      case 64514 /* i32_trunc_sat_f64_s */:
        return `l.${/* @__KEY__ */"z"}(${emit(ast[ptr + 1])})`;
      case 64515 /* i32_trunc_sat_f64_u */:
        return `l.${/* @__KEY__ */"A"}(${emit(ast[ptr + 1])})`;
      case 64516 /* i64_trunc_sat_f32_s */:
        return `l.${/* @__KEY__ */"B"}(${emit(ast[ptr + 1])})`;
      case 64517 /* i64_trunc_sat_f32_u */:
        return `l.${/* @__KEY__ */"C"}(${emit(ast[ptr + 1])})`;
      case 64518 /* i64_trunc_sat_f64_s */:
        return `l.${/* @__KEY__ */"B"}(${emit(ast[ptr + 1])})`;
      case 64519 /* i64_trunc_sat_f64_u */:
        return `l.${/* @__KEY__ */"C"}(${emit(ast[ptr + 1])})`;
      case 64520 /* memory_init */:
        return `l.${/* @__KEY__ */"D"}(d[${ast[ptr + 4]}],c.${/* @__KEY__ */"e" + ast[ptr + 5]},${emit(ast[ptr + 1])},${emit(ast[ptr + 2])},${emit(ast[ptr + 3])})`;
      case 64521 /* data_drop */:
        return `d[${ast[ptr + 1]}]=new Uint8Array`;
      case 64522 /* memory_copy */:
        return `l.${/* @__KEY__ */"D"}(c.${/* @__KEY__ */"e" + ast[ptr + 4]},c.${/* @__KEY__ */"e" + ast[ptr + 5]},${emit(ast[ptr + 1])},${emit(ast[ptr + 2])},${emit(ast[ptr + 3])})`;
      case 64523 /* memory_fill */:
        return `c.${/* @__KEY__ */"e" + ast[ptr + 4]}.fill(${emit(ast[ptr + 1])},T=${emit(ast[ptr + 2])},T+${emit(ast[ptr + 3])})`;
      case 64524 /* table_init */:
        return `l.${/* @__KEY__ */"E"}(${tableName(ast[ptr + 4])},e[${ast[ptr + 5]}],${emit(ast[ptr + 1])},${emit(ast[ptr + 2])},${emit(ast[ptr + 3])})`;
      case 64525 /* elem_drop */:
        return `e[${ast[ptr + 1]}]=[]`;
      case 64526 /* table_copy */:
        return `l.${/* @__KEY__ */"E"}(${tableName(ast[ptr + 4])},${tableName(ast[ptr + 5])},${emit(ast[ptr + 1])},${emit(ast[ptr + 2])},${emit(ast[ptr + 3])})`;
      case 64527 /* table_grow */:
        return `l.${/* @__KEY__ */"F"}(${tableName(ast[ptr + 3])},${emit(ast[ptr + 1])},${emit(ast[ptr + 2])})`;
      case 64528 /* table_size */:
        return tableName(ast[ptr + 1]) + ".length";
      case 64529 /* table_fill */:
        return `l.${/* @__KEY__ */"da"}(${tableName(ast[ptr + 4])},${emit(ast[ptr + 1])},${emit(ast[ptr + 2])},${emit(ast[ptr + 3])})`;
      default:
        throw "Internal error";
    }
  };
  var allocateNode = (node, length) => {
    var ptr = astNextPtr;
    ast[ptr] = node;
    astNextPtr += length;
    return ptr;
  };
  var pushUnary = function (op) {
    var stackSlot = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : stackTop;
    astPtrs.push(astNextPtr);
    ast[astNextPtr++] = op | 1 << 16 /* ChildCountShift */ | stackSlot << 24 /* OutSlotShift */;
    ast[astNextPtr++] = -stackSlot;
  };
  var handleReturn = () => {
    finalizeBasicBlock();
    jump(0);
    blocks[blocks.length - 1].c = true;
  };
  var finalizeBasicBlock = function () {
    var popStackTop = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : false;
    var parts = [];
    var i = astPtrs.length - 1;
    var optimizeChildrenAndSelf = ptr2 => {
      var node = ast[ptr2];
      var op = node & 65535 /* OpMask */;
      var childCount = node >> 16 /* ChildCountShift */ & 255 /* ChildCountMask */;
      var usesTypedArrays = op >= 40 /* i32_load */ && op <= 62 /* i64_store32 */ || op >= 64520 /* memory_init */ && op <= 64523 /* memory_fill */;
      for (var j = childCount - 1; i >= 0 && j >= 0; j--) {
        var stackSlot = -ast[ptr2 + j + 1];
        var didSkip = false;
        for (var k = i; k >= 0; k--) {
          var prevPtr = astPtrs[k];
          if (prevPtr === null) continue;
          var prevNode = ast[prevPtr];
          var prevOp = prevNode & 65535 /* OpMask */;
          if (usesTypedArrays && (
          // The only exception we make is for nodes that are trivially safe,
          // which include terminal nodes without any children that don't
          // have side effects. The common ones are special-cased below.
          prevOp < 65 /* i32_const */ || prevOp > 66 /* i64_const */) && prevOp != 32 /* local_get */) {
            break;
          }
          if (prevNode >>> 24 /* OutSlotShift */ === stackSlot) {
            astPtrs[k] = null;
            if (!didSkip) i = k - 1;
            ast[ptr2 + j + 1] = optimizeChildrenAndSelf(prevPtr);
            break;
          }
          if (prevOp !== 243 /* TO_U32 */ && prevOp !== 244 /* TO_S64 */) break;
          didSkip = true;
        }
      }
      return optimizeNode(ast, constants, allocateNode, ptr2);
    };
    var ptr;
    while (i >= 0) {
      var index = i--;
      if ((ptr = astPtrs[index]) !== null) {
        astPtrs[index] = optimizeChildrenAndSelf(ptr);
      }
    }
    var result;
    i = astPtrs.length - 1;
    if (popStackTop) {
      if (i >= 0 && (ptr = astPtrs[i]) !== null && ast[ptr] >>> 24 /* OutSlotShift */ === stackTop) {
        result = emitUnwrapped(ptr);
        i--;
      } else {
        result = "s" + stackTop;
      }
      stackTop--;
    }
    while (i >= 0) {
      if ((ptr = astPtrs[i--]) !== null) {
        var stackSlot = ast[ptr] >>> 24 /* OutSlotShift */;
        parts.push(`${stackSlot ? stackSlotName(stackSlot) + "=" : ""}${emitUnwrapped(ptr)};`);
      }
    }
    body += parts.reverse().join("");
    constants.length = 0;
    astPtrs.length = 0;
    astNextPtr = 0;
    return result;
  };
  var bytes = wasm.ia,
    dataView = wasm.h,
    codeSection = wasm.H,
    functionSection = wasm.I,
    nameSection = wasm.pa,
    typeSection = wasm.J;
  var _typeSection$function = _slicedToArray(typeSection[functionSection[codeIndex]], 2),
    argTypes = _typeSection$function[0],
    returnTypes = _typeSection$function[1];
  var _codeSection$codeInde = _slicedToArray(codeSection[codeIndex], 3),
    locals = _codeSection$codeInde[0],
    codeStart = _codeSection$codeInde[1],
    codeEnd = _codeSection$codeInde[2];
  var names = [];
  var argCount = argTypes.length;
  for (var i = 0; i < argCount; i++) {
    names.push("a" + i);
  }
  var decls = ["L", "T"];
  for (var _ref5 of locals) {
    var _ref6 = _slicedToArray(_ref5, 2);
    var count = _ref6[0];
    var type = _ref6[1];
    for (var _i14 = 0; _i14 < count; _i14++) {
      var name2 = "l" + decls.length;
      names.push(name2);
      decls.push(name2 + (type === 126 /* I64 */ ? "=0n" : "=0"));
    }
  }
  var blockDepthLimit = 256;
  var pushBlock = kind => {
    var isBelowLimit = blocks.length < blockDepthLimit;
    if (isBelowLimit) {
      body += `b${blocks.length}:`;
    } else if (blocks.length === blockDepthLimit) {
      body += `L=1;b${blocks.length}:for(;;){switch(L){case 1:`;
      nextLabel = 2;
    }
    var labelBreak = isBelowLimit ? -1 : nextLabel++;
    var labelContinueOrElse = isBelowLimit ? -1 : kind !== 0 /* Normal */ ? nextLabel++ : 0;
    var _readBlockType = readBlockType(),
      _readBlockType2 = _slicedToArray(_readBlockType, 2),
      argCount2 = _readBlockType2[0],
      returnCount = _readBlockType2[1];
    blocks.push({
      l: argCount2,
      c: false,
      m: kind,
      x: labelBreak,
      j: labelContinueOrElse,
      i: stackTop - argCount2,
      g: returnCount
    });
    return labelContinueOrElse;
  };
  var jump = function () {
    var index = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : blocks.length - readU32LEB() - 1;
    if (blocks[blocks.length - 1].c) return;
    var block = blocks[index];
    if (!index) {
      if (block.g === 1) {
        body += `return s${stackTop};`;
      } else if (block.g > 1) {
        var values = [];
        for (var _i15 = block.g - 1; _i15 >= 0; _i15--) values.push("s" + (stackTop - _i15));
        body += `return[${values}];`;
      } else {
        body += `return;`;
      }
    } else if (block.m === 1 /* Loop */) {
      if (stackTop > block.i + block.l) {
        for (var _i16 = 1; _i16 <= block.l; _i16++) {
          body += `s${block.i + _i16}=s${stackTop - block.l + _i16};`;
        }
      }
      body += index < blockDepthLimit ? `continue b${index};` : `L=${block.j};continue;`;
    } else {
      if (stackTop > block.i + block.g) {
        for (var _i17 = 1; _i17 <= block.g; _i17++) {
          body += `s${block.i + _i17}=s${stackTop - block.g + _i17};`;
        }
      }
      body += index <= blockDepthLimit ? `break b${index};` : `L=${block.x};continue;`;
    }
  };
  var blocks = [{
    l: 0,
    c: false,
    m: 0 /* Normal */,
    x: -1,
    j: -1,
    i: 0,
    g: returnTypes.length
  }];
  var handleSimpleOp = op => {
    var flags = metaTable[op] | 0;
    if (!(flags & 8 /* Simple */)) return false;
    if (flags & 8 /* Simple */) {
      if (!blocks[blocks.length - 1].c) {
        var childCount = flags & 3 /* PopMask */;
        if (flags & 1024 /* And63 */) {
          astPtrs.push(astNextPtr);
          ast[astNextPtr++] = 66 /* i64_const */ | stackTop + 1 << 24 /* OutSlotShift */;
          ast[astNextPtr++] = constants.length;
          constants.push(63n);
          astPtrs.push(astNextPtr);
          ast[astNextPtr++] = 131 /* i64_and */ | 2 << 16 /* ChildCountShift */ | stackTop << 24 /* OutSlotShift */;
          ast[astNextPtr++] = -stackTop;
          ast[astNextPtr++] = -(stackTop + 1);
        }
        stackTop -= childCount;
        if (flags & (128 /* ToU32 */ | 256 /* ToS64 */)) {
          for (var _i18 = 0; _i18 < childCount; _i18++) {
            pushUnary(flags & 128 /* ToU32 */ ? 243 /* TO_U32 */ : 244 /* TO_S64 */, stackTop + _i18 + 1);
          }
        }
        if (!(flags & 512 /* Omit */)) {
          var memoryIndex = 0;
          if (flags & 32 /* HasMemory */ && bytes[bytesPtr++] & 64) memoryIndex = readU32LEB();
          astPtrs.push(astNextPtr);
          if (flags & 4 /* Push */) op |= stackTop + 1 << 24 /* OutSlotShift */;
          ast[astNextPtr++] = op | childCount << 16 /* ChildCountShift */;
          for (var _i19 = 1; _i19 <= childCount; _i19++) ast[astNextPtr++] = -(stackTop + _i19);
          if (flags & 16 /* HasIndex */) ast[astNextPtr++] = readU32LEB();
          if (flags & 32 /* HasMemory */) ast[astNextPtr++] = memoryIndex;
        }
        if (flags & 4 /* Push */) stackTop++;
        if (flags & 64 /* BoolToInt */) pushUnary(242 /* BOOL_TO_INT */);
      } else {
        if (flags & 32 /* HasMemory */ && bytes[bytesPtr++] & 64) readU32LEB();
        if (flags & 16 /* HasIndex */) readU32LEB();
      }
    }
    return true;
  };
  var stackTop = 0;
  var bytesPtr = codeStart;
  var nextLabel = 0;
  var body = "b0:{";
  while (bytesPtr < codeEnd) {
    var op = bytes[bytesPtr++];
    if (handleSimpleOp(op)) {
      continue;
    }
    switch (op) {
      case 0 /* unreachable */:
        {
          var block = blocks[blocks.length - 1];
          finalizeBasicBlock();
          if (!block.c) {
            body += '"unreachable"();';
            block.c = true;
          }
          break;
        }
      case 2 /* block */:
        finalizeBasicBlock();
        if (pushBlock(0 /* Normal */) < 0) body += "{";
        break;
      case 3 /* loop */:
        {
          finalizeBasicBlock();
          var label = pushBlock(1 /* Loop */);
          body += label < 0 ? "for(;;){" : `case ${label}:`;
          break;
        }
      case 4 /* if */:
        {
          if (!blocks[blocks.length - 1].c) {
            pushUnary(blocks.length < blockDepthLimit ? 240 /* BOOL */ : 241 /* BOOL_NOT */);
          }
          var test = finalizeBasicBlock(true);
          var _label = pushBlock(2 /* IfElse */);
          body += _label < 0 ? `if(${test}){` : `if(${test}){L=${_label};continue}`;
          break;
        }
      case 5 /* else */:
        {
          finalizeBasicBlock();
          var index = blocks.length - 1,
            _block = blocks[index];
          jump(index);
          body += index < blockDepthLimit ? "}else{" : `case ${_block.j}:`;
          _block.m = 0 /* Normal */;
          stackTop = _block.i + _block.l;
          _block.c = false;
          break;
        }
      case 11 /* end */:
        {
          finalizeBasicBlock();
          var _index2 = blocks.length - 1,
            _block2 = blocks[_index2];
          if (_block2.m !== 2 /* IfElse */) _block2.j = 0;
          _block2.m = 0 /* Normal */;
          jump(_index2);
          if (_index2 < blockDepthLimit) {
            body += `}`;
          } else {
            if (_block2.j) body += `case ${_block2.j}:`;
            body += `case ${_block2.x}:`;
            if (_index2 == blockDepthLimit) body += `}break}`;
          }
          stackTop = _block2.i + _block2.g;
          blocks.pop();
          break;
        }
      case 12 /* br */:
        finalizeBasicBlock();
        jump();
        blocks[blocks.length - 1].c = true;
        break;
      case 13 /* br_if */:
        {
          if (!blocks[blocks.length - 1].c) pushUnary(240 /* BOOL */);
          var _test = finalizeBasicBlock(true);
          body += `if(${_test}){`;
          jump();
          body += "}";
          break;
        }
      case 14 /* br_table */:
        {
          var _test2 = finalizeBasicBlock(true);
          body += `switch(${_test2}){`;
          for (var _i20 = 0, tableCount = readU32LEB(); _i20 < tableCount; _i20++) {
            body += `case ${_i20}:`;
            jump();
          }
          body += "default:";
          jump();
          body += "}";
          blocks[blocks.length - 1].c = true;
          break;
        }
      case 15 /* return */:
        handleReturn();
        break;
      case 16 /* call */:
      case 18 /* return_call */:
        {
          var needsReturn = op === 18 /* return_call */;
          var funcIndex2 = readU32LEB();
          if (!blocks[blocks.length - 1].c) {
            var _funcTypes$funcIndex2 = _slicedToArray(funcTypes[funcIndex2], 2),
              argTypes2 = _funcTypes$funcIndex2[0],
              returnTypes2 = _funcTypes$funcIndex2[1];
            stackTop -= argTypes2.length;
            astPtrs.push(astNextPtr);
            if (returnTypes2.length === 1) op |= stackTop + 1 << 24 /* OutSlotShift */;
            ast[astNextPtr++] = op | argTypes2.length << 16 /* ChildCountShift */;
            for (var _i21 = 1; _i21 <= argTypes2.length; _i21++) ast[astNextPtr++] = -(stackTop + _i21);
            ast[astNextPtr++] = funcIndex2;
            if (returnTypes2.length > 1) ast[astNextPtr++] = stackTop + 1;
            stackTop += returnTypes2.length;
          }
          if (needsReturn) handleReturn();
          break;
        }
      case 17 /* call_indirect */:
      case 19 /* return_call_indirect */:
        {
          var _needsReturn = op === 19 /* return_call_indirect */;
          var typeIndex = readU32LEB();
          var tableIndex = readU32LEB();
          if (!blocks[blocks.length - 1].c) {
            var _typeSection$typeInde3 = _slicedToArray(typeSection[typeIndex], 2),
              _argTypes2 = _typeSection$typeInde3[0],
              _returnTypes2 = _typeSection$typeInde3[1];
            stackTop -= _argTypes2.length + 1;
            astPtrs.push(astNextPtr);
            if (_returnTypes2.length === 1) op |= stackTop + 1 << 24 /* OutSlotShift */;
            ast[astNextPtr++] = op | _argTypes2.length << 16 /* ChildCountShift */;
            ast[astNextPtr++] = -(stackTop + _argTypes2.length + 1);
            for (var _i22 = 1; _i22 <= _argTypes2.length; _i22++) ast[astNextPtr++] = -(stackTop + _i22);
            ast[astNextPtr++] = tableIndex;
            ast[astNextPtr++] = typeIndex;
            if (_returnTypes2.length > 1) ast[astNextPtr++] = stackTop + 1;
            stackTop += _returnTypes2.length;
          }
          if (_needsReturn) handleReturn();
          break;
        }
      case 27 /* select */:
      case 28 /* select_type */:
        {
          if (op === 28 /* select_type */) {
            var _count2 = readU32LEB();
            if (_count2 !== 1) throw Error("Unsupported select type count " + _count2);
            bytesPtr++;
          }
          if (!blocks[blocks.length - 1].c) {
            pushUnary(240 /* BOOL */);
            stackTop -= 2;
            astPtrs.push(astNextPtr);
            ast[astNextPtr++] = op | 3 << 16 /* ChildCountShift */ | stackTop << 24 /* OutSlotShift */;
            ast[astNextPtr++] = -(stackTop + 2);
            ast[astNextPtr++] = -stackTop;
            ast[astNextPtr++] = -(stackTop + 1);
          }
          break;
        }
      case 65 /* i32_const */:
        if (!blocks[blocks.length - 1].c) {
          astPtrs.push(astNextPtr);
          ast[astNextPtr++] = op | ++stackTop << 24 /* OutSlotShift */;
          ast[astNextPtr++] = readI32LEB();
        } else {
          readI32LEB();
        }
        break;
      case 66 /* i64_const */:
        if (!blocks[blocks.length - 1].c) {
          astPtrs.push(astNextPtr);
          ast[astNextPtr++] = op | ++stackTop << 24 /* OutSlotShift */;
          ast[astNextPtr++] = constants.length;
          constants.push(readI64LEB());
        } else {
          readI64LEB();
        }
        break;
      case 67 /* f32_const */:
        if (!blocks[blocks.length - 1].c) {
          astPtrs.push(astNextPtr);
          ast[astNextPtr++] = op | ++stackTop << 24 /* OutSlotShift */;
          ast[astNextPtr++] = bytesPtr;
        }
        bytesPtr += 4;
        break;
      case 68 /* f64_const */:
        if (!blocks[blocks.length - 1].c) {
          astPtrs.push(astNextPtr);
          ast[astNextPtr++] = op | ++stackTop << 24 /* OutSlotShift */;
          ast[astNextPtr++] = bytesPtr;
        }
        bytesPtr += 8;
        break;
      case 208 /* ref_null */:
        bytesPtr++;
        if (!blocks[blocks.length - 1].c) {
          astPtrs.push(astNextPtr);
          ast[astNextPtr++] = op | ++stackTop << 24 /* OutSlotShift */;
        }
        break;
      case 252:
        op = 64512 | bytes[bytesPtr++];
        if (handleSimpleOp(op)) {
          continue;
        }
        switch (op) {
          case 64520 /* memory_init */:
            {
              var dataIndex = readU32LEB();
              var destinationIndex = readU32LEB();
              if (!blocks[blocks.length - 1].c) {
                stackTop -= 2;
                astPtrs.push(astNextPtr);
                ast[astNextPtr++] = op | 3 << 16 /* ChildCountShift */ | stackTop << 24 /* OutSlotShift */;
                ast[astNextPtr++] = -stackTop;
                ast[astNextPtr++] = -(stackTop + 1);
                ast[astNextPtr++] = -(stackTop + 2);
                ast[astNextPtr++] = dataIndex;
                ast[astNextPtr++] = destinationIndex;
              }
              break;
            }
          case 64522 /* memory_copy */:
            {
              var _destinationIndex = readU32LEB();
              var sourceIndex = readU32LEB();
              if (!blocks[blocks.length - 1].c) {
                stackTop -= 2;
                astPtrs.push(astNextPtr);
                ast[astNextPtr++] = op | 3 << 16 /* ChildCountShift */ | stackTop << 24 /* OutSlotShift */;
                ast[astNextPtr++] = -stackTop;
                ast[astNextPtr++] = -(stackTop + 1);
                ast[astNextPtr++] = -(stackTop + 2);
                ast[astNextPtr++] = sourceIndex;
                ast[astNextPtr++] = _destinationIndex;
              }
              break;
            }
          case 64523 /* memory_fill */:
            {
              var _destinationIndex2 = readU32LEB();
              if (!blocks[blocks.length - 1].c) {
                stackTop -= 2;
                astPtrs.push(astNextPtr);
                ast[astNextPtr++] = op | 3 << 16 /* ChildCountShift */ | stackTop << 24 /* OutSlotShift */;
                ast[astNextPtr++] = -(stackTop + 1);
                ast[astNextPtr++] = -stackTop;
                ast[astNextPtr++] = -(stackTop + 2);
                ast[astNextPtr++] = _destinationIndex2;
              }
              break;
            }
          case 64524 /* table_init */:
            {
              var elementIndex = readU32LEB();
              var _tableIndex = readU32LEB();
              if (!blocks[blocks.length - 1].c) {
                stackTop -= 2;
                astPtrs.push(astNextPtr);
                ast[astNextPtr++] = op | 3 << 16 /* ChildCountShift */ | stackTop << 24 /* OutSlotShift */;
                ast[astNextPtr++] = -stackTop;
                ast[astNextPtr++] = -(stackTop + 1);
                ast[astNextPtr++] = -(stackTop + 2);
                ast[astNextPtr++] = _tableIndex;
                ast[astNextPtr++] = elementIndex;
              }
              break;
            }
          case 64526 /* table_copy */:
            {
              var destination = readU32LEB();
              var source = readU32LEB();
              if (!blocks[blocks.length - 1].c) {
                stackTop -= 2;
                astPtrs.push(astNextPtr);
                ast[astNextPtr++] = op | 3 << 16 /* ChildCountShift */ | stackTop << 24 /* OutSlotShift */;
                ast[astNextPtr++] = -stackTop;
                ast[astNextPtr++] = -(stackTop + 1);
                ast[astNextPtr++] = -(stackTop + 2);
                ast[astNextPtr++] = destination;
                ast[astNextPtr++] = source;
              }
              break;
            }
          default:
            throw Error("Unsupported instruction: 0xFC " + formatHexByte(op & 255));
        }
        break;
      default:
        throw Error("Unsupported instruction: " + formatHexByte(op));
    }
  }
  if (stackLimit > 255) throw Error("Deep stacks are not supported");
  var name = JSON.stringify("wasm:" + (nameSection.get(funcIndex) || `function[${codeIndex}]`));
  var js = `return{${name}(${names.slice(0, argCount)}){var ${decls};${body}}}[${name}]`;
  return new Function("f", "F", "c", "t", "d", "e", "g", "l", js)(funcs, createLazyFunc, context, tables, dataSegments, elementSegments, globals, library);
};

// src/instantiate.ts
var Global = /*#__PURE__*/function () {
  function Global() {
    _classCallCheck(this, Global);
  }
  return _createClass(Global, [{
    key: "valueOf",
    value: function valueOf() {
      return this.value;
    }
  }]);
}();
var compileImportFunc = (funcType, value) => {
  var _funcType = _slicedToArray(funcType, 2),
    argTypes = _funcType[0],
    returnTypes = _funcType[1];
  var argNames = [];
  var argExprs = [];
  for (var i = 0; i < argTypes.length; i++) {
    argNames.push("a" + i);
    argExprs.push(castToJS("a" + i, argTypes[i]));
  }
  var result = `f(${argExprs})`;
  if (returnTypes.length === 1) {
    result = "return " + castToWASM(result, returnTypes[0]);
  } else if (returnTypes.length > 1) {
    result = `let r=${result};`;
    for (var _i23 = 0; _i23 < returnTypes.length; _i23++) result += `r[${_i23}]=${castToWASM(`r[${_i23}]`, returnTypes[_i23])};`;
    result += "return r";
  }
  return new Function("f", "l", `return(${argNames})=>{${result}}`)(value, library);
};
var Instance = /*#__PURE__*/_createClass(function Instance(module, importObject) {
  _classCallCheck(this, Instance);
  var wasm = moduleMap.get(module);
  var codeSection = wasm.H,
    dataSection = wasm.ja,
    elementSection = wasm.ka,
    exportSection = wasm.la,
    functionSection = wasm.I,
    globalSection = wasm.ma,
    importSection = wasm.na,
    memorySection = wasm.oa,
    startSection = wasm.qa,
    tableSection = wasm.ra,
    typeSection = wasm.J;
  var exports = this.exports = /* @__PURE__ */Object.create(null);
  var memories = [];
  var funcs = [];
  var funcTypes = [];
  var globals = [];
  var globalTypes = [];
  var lazyFuncs = {};
  var tables = [];
  var createLazyFunc = index => {
    var obj = lazyFuncs[index] || (lazyFuncs[index] = {
      ua: index,
      fa: funcTypes[index],
      t: null,
      u: function () {
        var result = funcs[index].apply(funcs, arguments);
        obj.u = funcs[index];
        return result;
      }
    });
    return obj;
  };
  var _loop4 = function () {
    var _tuple = _slicedToArray(tuple, 4),
      module2 = _tuple[0],
      name = _tuple[1],
      desc = _tuple[2],
      payload = _tuple[3];
    var value = importObject[module2][name];
    if (desc === 0 /* Func */) {
      var funcType = typeSection[payload];
      var _index5 = funcs.length;
      funcs.push(function () {
        return (funcs[_index5] = compileImportFunc(funcType, value)).apply(void 0, arguments);
      });
      funcTypes.push(funcType);
    } else if (desc === 1 /* Table */) {
      tables.push(tableRegistry.get(value));
    } else if (desc === 2 /* Mem */) {
      memories.push(memoryRegistry.get(value));
    } else if (desc === 3 /* Global */) {
      globals.push(liveCastToWASM(value, payload));
      globalTypes.push(payload);
    } else {
      throw Error(`Unsupported import type ${formatHexByte(desc)} for "${module2}"."${name}"`);
    }
  };
  for (var tuple of importSection) {
    _loop4();
  }
  var context = {};
  for (var _ref7 of memorySection) {
    var _ref8 = _slicedToArray(_ref7, 2);
    var initial = _ref8[0];
    var maximum = _ref8[1];
    memories.push(memoryRegistry.get(new Memory({
      initial,
      maximum: initial > maximum ? initial : maximum
    })));
  }
  var _loop5 = function (i) {
    var memory = memories[i];
    var patch = () => {
      context[/* @__KEY__ */"e" + i] = memory.e;
      context[/* @__KEY__ */"k" + i] = memory.k;
      context[/* @__KEY__ */"h" + i] = memory.h;
    };
    context[/* @__KEY__ */"K" + i] = memory;
    memory.M.push(patch);
    patch();
  };
  for (var i = 0; i < memories.length; i++) {
    _loop5(i);
  }
  for (var _ref9 of globalSection) {
    var _ref0 = _slicedToArray(_ref9, 3);
    var type = _ref0[0];
    var mutable = _ref0[1];
    var initializer = _ref0[2];
    globals.push(initializer(globals, createLazyFunc));
    globalTypes.push(type);
  }
  var dataSegments = [];
  for (var _ref1 of dataSection) {
    var _ref10 = _slicedToArray(_ref1, 3);
    var index = _ref10[0];
    var _initializer = _ref10[1];
    var data = _ref10[2];
    if (_initializer !== null) {
      context[/* @__KEY__ */"e" + index].set(data, _initializer(globals));
      data = new Uint8Array();
    }
    dataSegments.push(data);
  }
  var _loop6 = function (_i24) {
    var index = funcs.length;
    funcTypes.push(typeSection[functionSection[_i24]]);
    funcs.push(function () {
      return (funcs[index] = compileCode(funcs, funcTypes, createLazyFunc, tables, dataSegments, elementSegments, globals, context, wasm, _i24, index)).apply(void 0, arguments);
    });
  };
  for (var _i24 = 0; _i24 < codeSection.length; _i24++) {
    _loop6(_i24);
  }
  var elementSegments = [];
  for (var _ref11 of tableSection) {
    var _ref12 = _slicedToArray(_ref11, 3);
    var _type = _ref12[0];
    var _initial = _ref12[1];
    var _maximum = _ref12[2];
    if (_type !== 112 /* FuncRef */ && _type !== 111 /* ExternRef */) throw Error("Unsupported element type: " + formatHexByte(_type));
    tables.push(tableRegistry.get(new Table({
      element: _type === 111 /* ExternRef */ ? "externref" : "anyfunc",
      initial: _initial,
      maximum: _initial > _maximum ? _initial : _maximum
    })));
  }
  for (var _ref13 of elementSection) {
    var _ref14 = _slicedToArray(_ref13, 3);
    var tableIndex = _ref14[0];
    var _initializer2 = _ref14[1];
    var indices = _ref14[2];
    var segment = [];
    for (var _index3 of indices) segment.push(_index3 === null ? null : createLazyFunc(_index3));
    elementSegments.push(segment);
    if (tableIndex !== null && _initializer2 !== null) {
      var table = tables[tableIndex];
      var offset = _initializer2(globals);
      for (var value of segment) table[offset++] = value;
    }
  }
  var _loop7 = function (_index4) {
    if (desc === 0 /* Func */) {
      exports[name] = library.s(createLazyFunc(_index4));
    } else if (desc === 1 /* Table */) {
      exports[name] = tables[_index4].y;
    } else if (desc === 2 /* Mem */) {
      exports[name] = memories[_index4].y;
    } else if (desc === 3 /* Global */) {
      var _value5 = new Global();
      var _type2 = globalTypes[_index4];
      Object.defineProperty(_value5, "value", {
        get: () => globals[_index4],
        set: x => {
          globals[_index4] = liveCastToWASM(x, _type2);
        }
      });
      exports[name] = _value5;
    } else {
      throw Error(`Unsupported export type ${formatHexByte(desc)} for "${name}"`);
    }
  };
  for (var _ref15 of exportSection) {
    var _ref16 = _slicedToArray(_ref15, 3);
    var name = _ref16[0];
    var desc = _ref16[1];
    var _index4 = _ref16[2];
    _loop7(_index4);
  }
  if (startSection >= 0) funcs[startSection]();
});
var memoryRegistry = /* @__PURE__ */new WeakMap();
var clampPageCount = x => Math.max(-1, Math.min(x, 65535)) | 0;
var Memory = /*#__PURE__*/_createClass(function Memory(_ref17) {
  var initial = _ref17.initial,
    maximum = _ref17.maximum;
  _classCallCheck(this, Memory);
  initial = clampPageCount(initial);
  maximum = clampPageCount(maximum ?? Infinity);
  if (initial < 0 || initial > maximum) throw RangeError();
  var initialBuffer = new ArrayBuffer(initial << 16);
  var internal = {
    y: this,
    o: initialBuffer,
    e: new Uint8Array(initialBuffer),
    k: new Int8Array(initialBuffer),
    h: new DataView(initialBuffer),
    n: initial,
    sa: maximum,
    M: [],
    L(pageDelta) {
      var oldPageCount = this.n;
      var oldBytes = this.e;
      pageDelta = clampPageCount(pageDelta);
      if (pageDelta < 0 || this.n + pageDelta > this.sa) return -1;
      if (!pageDelta) return oldPageCount;
      var newBuffer = new ArrayBuffer((this.n += pageDelta) << 16);
      var newBytes = new Uint8Array(newBuffer);
      newBytes.set(oldBytes);
      try {
        structuredClone(this.o, {
          transfer: [this.o]
        });
      } catch {}
      this.o = newBuffer;
      this.e = newBytes;
      this.k = new Int8Array(newBuffer);
      this.h = new DataView(newBuffer);
      for (var patch of this.M) patch();
      return oldPageCount;
    }
  };
  memoryRegistry.set(this, internal);
  Object.defineProperty(this, "buffer", {
    get: () => internal.o
  });
  this.grow = pageDelta => {
    var pageCount = internal.L(pageDelta);
    if (pageCount < 0) throw RangeError();
    return pageCount;
  };
});
var tableRegistry = /* @__PURE__ */new WeakMap();
var Table = /*#__PURE__*/_createClass(function Table(_ref18) {
  var element = _ref18.element,
    initial = _ref18.initial,
    maximum = _ref18.maximum;
  _classCallCheck(this, Table);
  var isAnyFunc = element == "anyfunc";
  var internal = [];
  if (!isAnyFunc && element !== "externref") throw TypeError();
  internal.y = this;
  internal.ca = Math.min(4294967295, maximum ?? Infinity);
  internal.length = initial;
  for (var i = 0; i < initial; i++) internal[i] = null;
  tableRegistry.set(this, internal);
  Object.defineProperty(this, "length", {
    get: () => internal.length
  });
  this.get = i => {
    i >>>= 0;
    if (i >= internal.length) throw RangeError();
    return isAnyFunc ? library.s(internal[i]) : internal[i];
  };
  this.set = (i, value) => {
    i >>>= 0;
    if (i >= internal.length) throw RangeError();
    internal[i] = isAnyFunc ? library.r(value) : value;
  };
  this.grow = (delta, value) => {
    var oldLength = library.F(internal, isAnyFunc ? library.r(value) : value, delta);
    if (oldLength < 0) throw RangeError();
    return oldLength;
  };
});

// src/index.ts
var compile = input => {
  return Promise.resolve(new Module(input));
};

var compileStreaming = source => {
  return Promise.resolve(source)
    .then(response => response.arrayBuffer())
    .then(arrayBuffer => new Module(arrayBuffer));
};

var instantiate = (input, importObject) => {
  if (input instanceof Module) {
    return Promise.resolve(new Instance(input, importObject));
  }
  const module = new Module(input);
  return Promise.resolve({
    module,
    instance: new Instance(module, importObject)
  });
};

var instantiateStreaming = (source, importObject) => {
  return Promise.resolve(source)
    .then(response => response.arrayBuffer())
    .then(arrayBuffer => {
      const module = new Module(arrayBuffer);
      return {
        module,
        instance: new Instance(module, importObject)
      };
    });
};
function compileSync(input){
    return new Module(input);
}
function compileStreamingSync(source){
    return new Module(source.arrayBuffer())
}
function instantiateSync(input, importObject){
  if (input instanceof Module) {
    return new Instance(input, importObject);
  }
  const module = new Module(input);
  return {
    module,
    instance: new Instance(module, importObject)
  };
}
var instantiateStreamingSync = (source, importObject) => {
    const module=new Module(source.arrayBuffer())
    return {
        module,
        instance: new Instance(module, importObject)
    };
};
var validate = input => {
  if (!ArrayBuffer.isView(input) && !(input instanceof ArrayBuffer)) {
    throw TypeError("Invalid buffer source");
  }
  try {
    new Module(input);
    return true;
  } catch {
    return false;
  }
};
var wasmAPI = {
  Global,
  Instance,
  compile,
  compileStreaming,
  instantiate,
  instantiateStreaming,
  validate,
  Memory,
  Module,
  Table,
  // This cast ignores the lack of a call signature without "new", which we don't support
  CompileError,
  compileSync,
  compileStreamingSync,
  instantiateSync,
  instantiateStreamingSync,
};
$.exports=wasmAPI;