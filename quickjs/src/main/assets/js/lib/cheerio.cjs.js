var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to2, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to2, key) && key !== except)
        __defProp(to2, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to2;
};
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);
var cheerio_min_exports = {};
__export(cheerio_min_exports, {
  contains: () => mh,
  default: () => Eh,
  html: () => Oi,
  jinja2: () => dh,
  jp: () => ph,
  jpo: () => fh,
  load: () => uh,
  merge: () => Th,
  parseHTML: () => _h,
  root: () => Ah,
  text: () => Li,
  xml: () => yi
});
module.exports = __toCommonJS(cheerio_min_exports);
var e, t = function() {
  return t = Object.assign || function(e8) {
    for (var t2, n2 = 1, r2 = arguments.length; n2 < r2; n2++) for (var i2 in t2 = arguments[n2]) Object.prototype.hasOwnProperty.call(t2, i2) && (e8[i2] = t2[i2]);
    return e8;
  }, t.apply(this, arguments);
}, n = { xml: false, decodeEntities: true }, r = { _useHtmlParser2: true, xmlMode: true };
function i(e8) {
  return (null == e8 ? void 0 : e8.xml) ? "boolean" == typeof e8.xml ? r : t(t({}, r), e8.xml) : null != e8 ? e8 : void 0;
}
!(function(e8) {
  e8.Root = "root", e8.Text = "text", e8.Directive = "directive", e8.Comment = "comment", e8.Script = "script", e8.Style = "style", e8.Tag = "tag", e8.CDATA = "cdata", e8.Doctype = "doctype";
})(e || (e = {}));
const s = e.Root, a = e.Text, o = e.Directive, c = e.Comment, l = e.Script, h = e.Style, u = e.Tag, p = e.CDATA, f = e.Doctype;
class d {
  constructor() {
    this.parent = null, this.prev = null, this.next = null, this.startIndex = null, this.endIndex = null;
  }
  get parentNode() {
    return this.parent;
  }
  set parentNode(e8) {
    this.parent = e8;
  }
  get previousSibling() {
    return this.prev;
  }
  set previousSibling(e8) {
    this.prev = e8;
  }
  get nextSibling() {
    return this.next;
  }
  set nextSibling(e8) {
    this.next = e8;
  }
  cloneNode(e8 = false) {
    return v(this, e8);
  }
}
class E extends d {
  constructor(e8) {
    super(), this.data = e8;
  }
  get nodeValue() {
    return this.data;
  }
  set nodeValue(e8) {
    this.data = e8;
  }
}
class m extends E {
  constructor() {
    super(...arguments), this.type = e.Text;
  }
  get nodeType() {
    return 3;
  }
}
class T extends E {
  constructor() {
    super(...arguments), this.type = e.Comment;
  }
  get nodeType() {
    return 8;
  }
}
class _ extends E {
  constructor(t2, n2) {
    super(n2), this.name = t2, this.type = e.Directive;
  }
  get nodeType() {
    return 1;
  }
}
class A extends d {
  constructor(e8) {
    super(), this.children = e8;
  }
  get firstChild() {
    var e8;
    return null !== (e8 = this.children[0]) && void 0 !== e8 ? e8 : null;
  }
  get lastChild() {
    return this.children.length > 0 ? this.children[this.children.length - 1] : null;
  }
  get childNodes() {
    return this.children;
  }
  set childNodes(e8) {
    this.children = e8;
  }
}
class g extends A {
  constructor() {
    super(...arguments), this.type = e.CDATA;
  }
  get nodeType() {
    return 4;
  }
}
class N extends A {
  constructor() {
    super(...arguments), this.type = e.Root;
  }
  get nodeType() {
    return 9;
  }
}
class C extends A {
  constructor(t2, n2, r2 = [], i2 = "script" === t2 ? e.Script : "style" === t2 ? e.Style : e.Tag) {
    super(r2), this.name = t2, this.attribs = n2, this.type = i2;
  }
  get nodeType() {
    return 1;
  }
  get tagName() {
    return this.name;
  }
  set tagName(e8) {
    this.name = e8;
  }
  get attributes() {
    return Object.keys(this.attribs).map(((e8) => {
      var t2, n2;
      return { name: e8, value: this.attribs[e8], namespace: null === (t2 = this["x-attribsNamespace"]) || void 0 === t2 ? void 0 : t2[e8], prefix: null === (n2 = this["x-attribsPrefix"]) || void 0 === n2 ? void 0 : n2[e8] };
    }));
  }
}
function I(t2) {
  return (n2 = t2).type === e.Tag || n2.type === e.Script || n2.type === e.Style;
  var n2;
}
function S(t2) {
  return t2.type === e.CDATA;
}
function b(t2) {
  return t2.type === e.Text;
}
function O(t2) {
  return t2.type === e.Comment;
}
function y(t2) {
  return t2.type === e.Directive;
}
function L(t2) {
  return t2.type === e.Root;
}
function k(e8) {
  return Object.prototype.hasOwnProperty.call(e8, "children");
}
function v(e8, t2 = false) {
  let n2;
  if (b(e8)) n2 = new m(e8.data);
  else if (O(e8)) n2 = new T(e8.data);
  else if (I(e8)) {
    const r2 = t2 ? D(e8.children) : [], i2 = new C(e8.name, { ...e8.attribs }, r2);
    r2.forEach(((e9) => e9.parent = i2)), null != e8.namespace && (i2.namespace = e8.namespace), e8["x-attribsNamespace"] && (i2["x-attribsNamespace"] = { ...e8["x-attribsNamespace"] }), e8["x-attribsPrefix"] && (i2["x-attribsPrefix"] = { ...e8["x-attribsPrefix"] }), n2 = i2;
  } else if (S(e8)) {
    const r2 = t2 ? D(e8.children) : [], i2 = new g(r2);
    r2.forEach(((e9) => e9.parent = i2)), n2 = i2;
  } else if (L(e8)) {
    const r2 = t2 ? D(e8.children) : [], i2 = new N(r2);
    r2.forEach(((e9) => e9.parent = i2)), e8["x-mode"] && (i2["x-mode"] = e8["x-mode"]), n2 = i2;
  } else {
    if (!y(e8)) throw new Error(`Not implemented yet: ${e8.type}`);
    {
      const t3 = new _(e8.name, e8.data);
      null != e8["x-name"] && (t3["x-name"] = e8["x-name"], t3["x-publicId"] = e8["x-publicId"], t3["x-systemId"] = e8["x-systemId"]), n2 = t3;
    }
  }
  return n2.startIndex = e8.startIndex, n2.endIndex = e8.endIndex, null != e8.sourceCodeLocation && (n2.sourceCodeLocation = e8.sourceCodeLocation), n2;
}
function D(e8) {
  const t2 = e8.map(((e9) => v(e9, true)));
  for (let e9 = 1; e9 < t2.length; e9++) t2[e9].prev = t2[e9 - 1], t2[e9 - 1].next = t2[e9];
  return t2;
}
const R = { withStartIndices: false, withEndIndices: false, xmlMode: false };
class M {
  constructor(e8, t2, n2) {
    this.dom = [], this.root = new N(this.dom), this.done = false, this.tagStack = [this.root], this.lastNode = null, this.parser = null, "function" == typeof t2 && (n2 = t2, t2 = R), "object" == typeof e8 && (t2 = e8, e8 = void 0), this.callback = null != e8 ? e8 : null, this.options = null != t2 ? t2 : R, this.elementCB = null != n2 ? n2 : null;
  }
  onparserinit(e8) {
    this.parser = e8;
  }
  onreset() {
    this.dom = [], this.root = new N(this.dom), this.done = false, this.tagStack = [this.root], this.lastNode = null, this.parser = null;
  }
  onend() {
    this.done || (this.done = true, this.parser = null, this.handleCallback(null));
  }
  onerror(e8) {
    this.handleCallback(e8);
  }
  onclosetag() {
    this.lastNode = null;
    const e8 = this.tagStack.pop();
    this.options.withEndIndices && (e8.endIndex = this.parser.endIndex), this.elementCB && this.elementCB(e8);
  }
  onopentag(t2, n2) {
    const r2 = this.options.xmlMode ? e.Tag : void 0, i2 = new C(t2, n2, void 0, r2);
    this.addNode(i2), this.tagStack.push(i2);
  }
  ontext(t2) {
    const { lastNode: n2 } = this;
    if (n2 && n2.type === e.Text) n2.data += t2, this.options.withEndIndices && (n2.endIndex = this.parser.endIndex);
    else {
      const e8 = new m(t2);
      this.addNode(e8), this.lastNode = e8;
    }
  }
  oncomment(t2) {
    if (this.lastNode && this.lastNode.type === e.Comment) return void (this.lastNode.data += t2);
    const n2 = new T(t2);
    this.addNode(n2), this.lastNode = n2;
  }
  oncommentend() {
    this.lastNode = null;
  }
  oncdatastart() {
    const e8 = new m(""), t2 = new g([e8]);
    this.addNode(t2), e8.parent = t2, this.lastNode = e8;
  }
  oncdataend() {
    this.lastNode = null;
  }
  onprocessinginstruction(e8, t2) {
    const n2 = new _(e8, t2);
    this.addNode(n2);
  }
  handleCallback(e8) {
    if ("function" == typeof this.callback) this.callback(e8, this.dom);
    else if (e8) throw e8;
  }
  addNode(e8) {
    const t2 = this.tagStack[this.tagStack.length - 1], n2 = t2.children[t2.children.length - 1];
    this.options.withStartIndices && (e8.startIndex = this.parser.startIndex), this.options.withEndIndices && (e8.endIndex = this.parser.endIndex), t2.children.push(e8), n2 && (e8.prev = n2, n2.next = e8), e8.parent = t2, this.lastNode = null;
  }
}
const P = /["&'<>$\x80-\uFFFF]/g, x = /* @__PURE__ */ new Map([[34, "&quot;"], [38, "&amp;"], [39, "&apos;"], [60, "&lt;"], [62, "&gt;"]]), w = null != String.prototype.codePointAt ? (e8, t2) => e8.codePointAt(t2) : (e8, t2) => 55296 == (64512 & e8.charCodeAt(t2)) ? 1024 * (e8.charCodeAt(t2) - 55296) + e8.charCodeAt(t2 + 1) - 56320 + 65536 : e8.charCodeAt(t2);
function B(e8) {
  let t2, n2 = "", r2 = 0;
  for (; null !== (t2 = P.exec(e8)); ) {
    const i2 = t2.index, s2 = e8.charCodeAt(i2), a2 = x.get(s2);
    void 0 !== a2 ? (n2 += e8.substring(r2, i2) + a2, r2 = i2 + 1) : (n2 += `${e8.substring(r2, i2)}&#x${w(e8, i2).toString(16)};`, r2 = P.lastIndex += Number(55296 == (64512 & s2)));
  }
  return n2 + e8.substr(r2);
}
function F(e8, t2) {
  return function(n2) {
    let r2, i2 = 0, s2 = "";
    for (; r2 = e8.exec(n2); ) i2 !== r2.index && (s2 += n2.substring(i2, r2.index)), s2 += t2.get(r2[0].charCodeAt(0)), i2 = r2.index + 1;
    return s2 + n2.substring(i2);
  };
}
const U = F(/["&\u00A0]/g, /* @__PURE__ */ new Map([[34, "&quot;"], [38, "&amp;"], [160, "&nbsp;"]])), H = F(/[&<>\u00A0]/g, /* @__PURE__ */ new Map([[38, "&amp;"], [60, "&lt;"], [62, "&gt;"], [160, "&nbsp;"]])), G = new Map(["altGlyph", "altGlyphDef", "altGlyphItem", "animateColor", "animateMotion", "animateTransform", "clipPath", "feBlend", "feColorMatrix", "feComponentTransfer", "feComposite", "feConvolveMatrix", "feDiffuseLighting", "feDisplacementMap", "feDistantLight", "feDropShadow", "feFlood", "feFuncA", "feFuncB", "feFuncG", "feFuncR", "feGaussianBlur", "feImage", "feMerge", "feMergeNode", "feMorphology", "feOffset", "fePointLight", "feSpecularLighting", "feSpotLight", "feTile", "feTurbulence", "foreignObject", "glyphRef", "linearGradient", "radialGradient", "textPath"].map(((e8) => [e8.toLowerCase(), e8]))), Y = new Map(["definitionURL", "attributeName", "attributeType", "baseFrequency", "baseProfile", "calcMode", "clipPathUnits", "diffuseConstant", "edgeMode", "filterUnits", "glyphRef", "gradientTransform", "gradientUnits", "kernelMatrix", "kernelUnitLength", "keyPoints", "keySplines", "keyTimes", "lengthAdjust", "limitingConeAngle", "markerHeight", "markerUnits", "markerWidth", "maskContentUnits", "maskUnits", "numOctaves", "pathLength", "patternContentUnits", "patternTransform", "patternUnits", "pointsAtX", "pointsAtY", "pointsAtZ", "preserveAlpha", "preserveAspectRatio", "primitiveUnits", "refX", "refY", "repeatCount", "repeatDur", "requiredExtensions", "requiredFeatures", "specularConstant", "specularExponent", "spreadMethod", "startOffset", "stdDeviation", "stitchTiles", "surfaceScale", "systemLanguage", "tableValues", "targetX", "targetY", "textLength", "viewBox", "viewTarget", "xChannelSelector", "yChannelSelector", "zoomAndPan"].map(((e8) => [e8.toLowerCase(), e8]))), q = /* @__PURE__ */ new Set(["style", "script", "xmp", "iframe", "noembed", "noframes", "plaintext", "noscript"]);
function K(e8) {
  return e8.replace(/"/g, "&quot;");
}
const j = /* @__PURE__ */ new Set(["area", "base", "basefont", "br", "col", "command", "embed", "frame", "hr", "img", "input", "isindex", "keygen", "link", "meta", "param", "source", "track", "wbr"]);
function V(e8, t2 = {}) {
  const n2 = "length" in e8 ? e8 : [e8];
  let r2 = "";
  for (let e9 = 0; e9 < n2.length; e9++) r2 += W(n2[e9], t2);
  return r2;
}
function W(e8, t2) {
  switch (e8.type) {
    case s:
      return V(e8.children, t2);
    case f:
    case o:
      return `<${e8.data}>`;
    case c:
      return (function(e9) {
        return `<!--${e9.data}-->`;
      })(e8);
    case p:
      return (function(e9) {
        return `<![CDATA[${e9.children[0].data}]]>`;
      })(e8);
    case l:
    case h:
    case u:
      return (function(e9, t3) {
        var n2;
        "foreign" === t3.xmlMode && (e9.name = null !== (n2 = G.get(e9.name)) && void 0 !== n2 ? n2 : e9.name, e9.parent && Q.has(e9.parent.name) && (t3 = { ...t3, xmlMode: false }));
        !t3.xmlMode && X.has(e9.name) && (t3 = { ...t3, xmlMode: "foreign" });
        let r2 = `<${e9.name}`;
        const i2 = (function(e10, t4) {
          var n3;
          if (!e10) return;
          const r3 = false === (null !== (n3 = t4.encodeEntities) && void 0 !== n3 ? n3 : t4.decodeEntities) ? K : t4.xmlMode || "utf8" !== t4.encodeEntities ? B : U;
          return Object.keys(e10).map(((n4) => {
            var i3, s2;
            const a2 = null !== (i3 = e10[n4]) && void 0 !== i3 ? i3 : "";
            return "foreign" === t4.xmlMode && (n4 = null !== (s2 = Y.get(n4)) && void 0 !== s2 ? s2 : n4), t4.emptyAttrs || t4.xmlMode || "" !== a2 ? `${n4}="${r3(a2)}"` : n4;
          })).join(" ");
        })(e9.attribs, t3);
        i2 && (r2 += ` ${i2}`);
        0 === e9.children.length && (t3.xmlMode ? false !== t3.selfClosingTags : t3.selfClosingTags && j.has(e9.name)) ? (t3.xmlMode || (r2 += " "), r2 += "/>") : (r2 += ">", e9.children.length > 0 && (r2 += V(e9.children, t3)), !t3.xmlMode && j.has(e9.name) || (r2 += `</${e9.name}>`));
        return r2;
      })(e8, t2);
    case a:
      return (function(e9, t3) {
        var n2;
        let r2 = e9.data || "";
        false === (null !== (n2 = t3.encodeEntities) && void 0 !== n2 ? n2 : t3.decodeEntities) || !t3.xmlMode && e9.parent && q.has(e9.parent.name) || (r2 = t3.xmlMode || "utf8" !== t3.encodeEntities ? B(r2) : H(r2));
        return r2;
      })(e8, t2);
  }
}
const Q = /* @__PURE__ */ new Set(["mi", "mo", "mn", "ms", "mtext", "annotation-xml", "foreignObject", "desc", "title"]), X = /* @__PURE__ */ new Set(["svg", "math"]);
function $(e8, t2) {
  return V(e8, t2);
}
function z(e8) {
  return Array.isArray(e8) ? e8.map(z).join("") : k(e8) && !O(e8) ? z(e8.children) : b(e8) ? e8.data : "";
}
function J(t2) {
  return Array.isArray(t2) ? t2.map(J).join("") : k(t2) && (t2.type === e.Tag || S(t2)) ? J(t2.children) : b(t2) ? t2.data : "";
}
function Z(e8) {
  return k(e8) ? e8.children : [];
}
function ee(e8) {
  return e8.parent || null;
}
function te(e8) {
  const t2 = ee(e8);
  if (null != t2) return Z(t2);
  const n2 = [e8];
  let { prev: r2, next: i2 } = e8;
  for (; null != r2; ) n2.unshift(r2), { prev: r2 } = r2;
  for (; null != i2; ) n2.push(i2), { next: i2 } = i2;
  return n2;
}
function ne(e8) {
  let { next: t2 } = e8;
  for (; null !== t2 && !I(t2); ) ({ next: t2 } = t2);
  return t2;
}
function re(e8) {
  let { prev: t2 } = e8;
  for (; null !== t2 && !I(t2); ) ({ prev: t2 } = t2);
  return t2;
}
function ie(e8) {
  if (e8.prev && (e8.prev.next = e8.next), e8.next && (e8.next.prev = e8.prev), e8.parent) {
    const t2 = e8.parent.children;
    t2.splice(t2.lastIndexOf(e8), 1);
  }
}
function se(e8, t2, n2 = true, r2 = 1 / 0) {
  return Array.isArray(t2) || (t2 = [t2]), ae(e8, t2, n2, r2);
}
function ae(e8, t2, n2, r2) {
  const i2 = [];
  for (const s2 of t2) {
    if (e8(s2) && (i2.push(s2), --r2 <= 0)) break;
    if (n2 && k(s2) && s2.children.length > 0) {
      const t3 = ae(e8, s2.children, n2, r2);
      if (i2.push(...t3), r2 -= t3.length, r2 <= 0) break;
    }
  }
  return i2;
}
function oe(e8, t2, n2 = true) {
  let r2 = null;
  for (let i2 = 0; i2 < t2.length && !r2; i2++) {
    const s2 = t2[i2];
    I(s2) && (e8(s2) ? r2 = s2 : n2 && s2.children.length > 0 && (r2 = oe(e8, s2.children, true)));
  }
  return r2;
}
const ce = { tag_name: (e8) => "function" == typeof e8 ? (t2) => I(t2) && e8(t2.name) : "*" === e8 ? I : (t2) => I(t2) && t2.name === e8, tag_type: (e8) => "function" == typeof e8 ? (t2) => e8(t2.type) : (t2) => t2.type === e8, tag_contains: (e8) => "function" == typeof e8 ? (t2) => b(t2) && e8(t2.data) : (t2) => b(t2) && t2.data === e8 };
function le(e8, t2) {
  return "function" == typeof t2 ? (n2) => I(n2) && t2(n2.attribs[e8]) : (n2) => I(n2) && n2.attribs[e8] === t2;
}
function he(e8, t2) {
  return (n2) => e8(n2) || t2(n2);
}
function ue(e8) {
  const t2 = Object.keys(e8).map(((t3) => {
    const n2 = e8[t3];
    return Object.prototype.hasOwnProperty.call(ce, t3) ? ce[t3](n2) : le(t3, n2);
  }));
  return 0 === t2.length ? null : t2.reduce(he);
}
function pe(e8, t2, n2 = true, r2 = 1 / 0) {
  return se(ce.tag_name(e8), t2, n2, r2);
}
var fe;
function de(e8, t2) {
  const n2 = [], r2 = [];
  if (e8 === t2) return 0;
  let i2 = k(e8) ? e8 : e8.parent;
  for (; i2; ) n2.unshift(i2), i2 = i2.parent;
  for (i2 = k(t2) ? t2 : t2.parent; i2; ) r2.unshift(i2), i2 = i2.parent;
  const s2 = Math.min(n2.length, r2.length);
  let a2 = 0;
  for (; a2 < s2 && n2[a2] === r2[a2]; ) a2++;
  if (0 === a2) return fe.DISCONNECTED;
  const o2 = n2[a2 - 1], c2 = o2.children, l2 = n2[a2], h2 = r2[a2];
  return c2.indexOf(l2) > c2.indexOf(h2) ? o2 === t2 ? fe.FOLLOWING | fe.CONTAINED_BY : fe.FOLLOWING : o2 === e8 ? fe.PRECEDING | fe.CONTAINS : fe.PRECEDING;
}
function Ee(e8) {
  return (e8 = e8.filter(((e9, t2, n2) => !n2.includes(e9, t2 + 1)))).sort(((e9, t2) => {
    const n2 = de(e9, t2);
    return n2 & fe.PRECEDING ? -1 : n2 & fe.FOLLOWING ? 1 : 0;
  })), e8;
}
!(function(e8) {
  e8[e8.DISCONNECTED = 1] = "DISCONNECTED", e8[e8.PRECEDING = 2] = "PRECEDING", e8[e8.FOLLOWING = 4] = "FOLLOWING", e8[e8.CONTAINS = 8] = "CONTAINS", e8[e8.CONTAINED_BY = 16] = "CONTAINED_BY";
})(fe || (fe = {}));
const me = ["url", "type", "lang"], Te = ["fileSize", "bitrate", "framerate", "samplingrate", "channels", "duration", "height", "width"];
function _e(e8) {
  return pe("media:content", e8).map(((e9) => {
    const { attribs: t2 } = e9, n2 = { medium: t2.medium, isDefault: !!t2.isDefault };
    for (const e10 of me) t2[e10] && (n2[e10] = t2[e10]);
    for (const e10 of Te) t2[e10] && (n2[e10] = parseInt(t2[e10], 10));
    return t2.expression && (n2.expression = t2.expression), n2;
  }));
}
function Ae(e8, t2) {
  return pe(e8, t2, true, 1)[0];
}
function ge(e8, t2, n2 = false) {
  return z(pe(e8, t2, n2, 1)).trim();
}
function Ne(e8, t2, n2, r2, i2 = false) {
  const s2 = ge(n2, r2, i2);
  s2 && (e8[t2] = s2);
}
function Ce(e8) {
  return "rss" === e8 || "feed" === e8 || "rdf:RDF" === e8;
}
var Ie, Se = Object.freeze({ __proto__: null, isTag: I, isCDATA: S, isText: b, isComment: O, isDocument: L, hasChildren: k, getOuterHTML: $, getInnerHTML: function(e8, t2) {
  return k(e8) ? e8.children.map(((e9) => $(e9, t2))).join("") : "";
}, getText: function e2(t2) {
  return Array.isArray(t2) ? t2.map(e2).join("") : I(t2) ? "br" === t2.name ? "\n" : e2(t2.children) : S(t2) ? e2(t2.children) : b(t2) ? t2.data : "";
}, textContent: z, innerText: J, getChildren: Z, getParent: ee, getSiblings: te, getAttributeValue: function(e8, t2) {
  var n2;
  return null === (n2 = e8.attribs) || void 0 === n2 ? void 0 : n2[t2];
}, hasAttrib: function(e8, t2) {
  return null != e8.attribs && Object.prototype.hasOwnProperty.call(e8.attribs, t2) && null != e8.attribs[t2];
}, getName: function(e8) {
  return e8.name;
}, nextElementSibling: ne, prevElementSibling: re, removeElement: ie, replaceElement: function(e8, t2) {
  const n2 = t2.prev = e8.prev;
  n2 && (n2.next = t2);
  const r2 = t2.next = e8.next;
  r2 && (r2.prev = t2);
  const i2 = t2.parent = e8.parent;
  if (i2) {
    const n3 = i2.children;
    n3[n3.lastIndexOf(e8)] = t2, e8.parent = null;
  }
}, appendChild: function(e8, t2) {
  if (ie(t2), t2.next = null, t2.parent = e8, e8.children.push(t2) > 1) {
    const n2 = e8.children[e8.children.length - 2];
    n2.next = t2, t2.prev = n2;
  } else t2.prev = null;
}, append: function(e8, t2) {
  ie(t2);
  const { parent: n2 } = e8, r2 = e8.next;
  if (t2.next = r2, t2.prev = e8, e8.next = t2, t2.parent = n2, r2) {
    if (r2.prev = t2, n2) {
      const e9 = n2.children;
      e9.splice(e9.lastIndexOf(r2), 0, t2);
    }
  } else n2 && n2.children.push(t2);
}, prependChild: function(e8, t2) {
  if (ie(t2), t2.parent = e8, t2.prev = null, 1 !== e8.children.unshift(t2)) {
    const n2 = e8.children[1];
    n2.prev = t2, t2.next = n2;
  } else t2.next = null;
}, prepend: function(e8, t2) {
  ie(t2);
  const { parent: n2 } = e8;
  if (n2) {
    const r2 = n2.children;
    r2.splice(r2.indexOf(e8), 0, t2);
  }
  e8.prev && (e8.prev.next = t2), t2.parent = n2, t2.prev = e8.prev, t2.next = e8, e8.prev = t2;
}, filter: se, find: ae, findOneChild: function(e8, t2) {
  return t2.find(e8);
}, findOne: oe, existsOne: function e3(t2, n2) {
  return n2.some(((n3) => I(n3) && (t2(n3) || n3.children.length > 0 && e3(t2, n3.children))));
}, findAll: function(e8, t2) {
  var n2;
  const r2 = [], i2 = t2.filter(I);
  let s2;
  for (; s2 = i2.shift(); ) {
    const t3 = null === (n2 = s2.children) || void 0 === n2 ? void 0 : n2.filter(I);
    t3 && t3.length > 0 && i2.unshift(...t3), e8(s2) && r2.push(s2);
  }
  return r2;
}, testElement: function(e8, t2) {
  const n2 = ue(e8);
  return !n2 || n2(t2);
}, getElements: function(e8, t2, n2, r2 = 1 / 0) {
  const i2 = ue(e8);
  return i2 ? se(i2, t2, n2, r2) : [];
}, getElementById: function(e8, t2, n2 = true) {
  return Array.isArray(t2) || (t2 = [t2]), oe(le("id", e8), t2, n2);
}, getElementsByTagName: pe, getElementsByTagType: function(e8, t2, n2 = true, r2 = 1 / 0) {
  return se(ce.tag_type(e8), t2, n2, r2);
}, removeSubsets: function(e8) {
  let t2 = e8.length;
  for (; --t2 >= 0; ) {
    const n2 = e8[t2];
    if (t2 > 0 && e8.lastIndexOf(n2, t2 - 1) >= 0) e8.splice(t2, 1);
    else for (let r2 = n2.parent; r2; r2 = r2.parent) if (e8.includes(r2)) {
      e8.splice(t2, 1);
      break;
    }
  }
  return e8;
}, get DocumentPosition() {
  return fe;
}, compareDocumentPosition: de, uniqueSort: Ee, getFeed: function(e8) {
  const t2 = Ae(Ce, e8);
  return t2 ? "feed" === t2.name ? (function(e9) {
    var t3;
    const n2 = e9.children, r2 = { type: "atom", items: pe("entry", n2).map(((e10) => {
      var t4;
      const { children: n3 } = e10, r3 = { media: _e(n3) };
      Ne(r3, "id", "id", n3), Ne(r3, "title", "title", n3);
      const i3 = null === (t4 = Ae("link", n3)) || void 0 === t4 ? void 0 : t4.attribs.href;
      i3 && (r3.link = i3);
      const s3 = ge("summary", n3) || ge("content", n3);
      s3 && (r3.description = s3);
      const a2 = ge("updated", n3);
      return a2 && (r3.pubDate = new Date(a2)), r3;
    })) };
    Ne(r2, "id", "id", n2), Ne(r2, "title", "title", n2);
    const i2 = null === (t3 = Ae("link", n2)) || void 0 === t3 ? void 0 : t3.attribs.href;
    i2 && (r2.link = i2);
    Ne(r2, "description", "subtitle", n2);
    const s2 = ge("updated", n2);
    s2 && (r2.updated = new Date(s2));
    return Ne(r2, "author", "email", n2, true), r2;
  })(t2) : (function(e9) {
    var t3, n2;
    const r2 = null !== (n2 = null === (t3 = Ae("channel", e9.children)) || void 0 === t3 ? void 0 : t3.children) && void 0 !== n2 ? n2 : [], i2 = { type: e9.name.substr(0, 3), id: "", items: pe("item", e9.children).map(((e10) => {
      const { children: t4 } = e10, n3 = { media: _e(t4) };
      Ne(n3, "id", "guid", t4), Ne(n3, "title", "title", t4), Ne(n3, "link", "link", t4), Ne(n3, "description", "description", t4);
      const r3 = ge("pubDate", t4);
      return r3 && (n3.pubDate = new Date(r3)), n3;
    })) };
    Ne(i2, "title", "title", r2), Ne(i2, "link", "link", r2), Ne(i2, "description", "description", r2);
    const s2 = ge("lastBuildDate", r2);
    s2 && (i2.updated = new Date(s2));
    return Ne(i2, "author", "managingEditor", r2, true), i2;
  })(t2) : null;
} }), be = "undefined" != typeof global ? global : "undefined" != typeof self ? self : "undefined" != typeof window ? window : {};
function Oe(e8) {
  if (Object.keys) return Object.keys(e8);
  var t2 = [];
  for (var n2 in e8) t2.push(n2);
  return t2;
}
function ye(e8, t2) {
  if (e8.forEach) return e8.forEach(t2);
  for (var n2 = 0; n2 < e8.length; n2++) t2(e8[n2], n2, e8);
}
function Le() {
  try {
    return Object.defineProperty({}, "_", {}), function(e8, t2, n2) {
      Object.defineProperty(e8, t2, { writable: true, enumerable: false, configurable: true, value: n2 });
    };
  } catch (e8) {
    return function(e9, t2, n2) {
      e9[t2] = n2;
    };
  }
}
var ke = ["Array", "Boolean", "Date", "Error", "EvalError", "Function", "Infinity", "JSON", "Math", "NaN", "Number", "Object", "RangeError", "ReferenceError", "RegExp", "String", "SyntaxError", "TypeError", "URIError", "decodeURI", "decodeURIComponent", "encodeURI", "encodeURIComponent", "escape", "eval", "isFinite", "isNaN", "parseFloat", "parseInt", "undefined", "unescape"];
function ve() {
}
function De(e8) {
  if (!(this instanceof De)) return new De(e8);
  this.code = e8;
}
function Re(e8) {
  if (Me(e8)) return e8;
  var t2 = new ve();
  return "object" == typeof e8 && ye(Oe(e8), (function(n2) {
    t2[n2] = e8[n2];
  })), t2;
}
function Me(e8) {
  return e8 instanceof ve;
}
ve.prototype = {}, De.prototype.runInContext = function(e8) {
  if (!(e8 instanceof ve)) throw new TypeError("needs a 'context' argument.");
  if (be.document) {
    var t2 = be.document.createElement("iframe");
    t2.style || (t2.style = {}), t2.style.display = "none", be.document.body.appendChild(t2);
    var n2 = t2.contentWindow, r2 = n2.eval, i2 = n2.execScript;
    !r2 && i2 && (i2.call(n2, "null"), r2 = n2.eval), ye(Oe(e8), (function(t3) {
      n2[t3] = e8[t3];
    })), ye(ke, (function(t3) {
      e8[t3] && (n2[t3] = e8[t3]);
    }));
    var s2 = Oe(n2), a2 = r2.call(n2, this.code);
    return ye(Oe(n2), (function(t3) {
      (t3 in e8 || -1 === (function(e9, t4) {
        if (xe) return e9.indexOf(t4);
        for (var n3 = 0; n3 < e9.length; ++n3) if (e9[n3] === t4) return n3;
        return -1;
      })(s2, t3)) && (e8[t3] = n2[t3]);
    })), ye(ke, (function(t3) {
      var r3, i3, s3;
      t3 in e8 || (r3 = e8, i3 = t3, s3 = n2[t3], "function" != typeof Ie && (Ie = Le), Ie(r3, i3, s3));
    })), be.document.body.removeChild(t2), a2;
  }
  return (function(e9, t3) {
    var n3 = Oe(be);
    return n3.push("with (this.__ctx__){return eval(this.__code__)}"), Function.apply(null, n3).apply({ __code__: e9, __ctx__: t3 });
  })(this.code, e8);
}, De.prototype.runInThisContext = function() {
  return new Function("code", "return eval(code);").call(be, this.code);
}, De.prototype.runInNewContext = function(e8) {
  var t2 = Re(e8), n2 = this.runInContext(t2);
  return e8 && ye(Oe(t2), (function(n3) {
    e8[n3] = t2[n3];
  })), n2;
};
var Pe = { runInContext: function(e8, t2, n2) {
  return new De(e8).runInContext(t2, n2);
}, isContext: Me, createContext: Re, createScript: function(e8) {
  return new De(e8);
}, Script: De, runInThisContext: function(e8, t2) {
  return new De(e8).runInThisContext(t2);
}, runInNewContext: function(e8, t2, n2) {
  return new De(e8).runInNewContext(t2, n2);
} }, xe = [].indexOf;
const { hasOwnProperty: we } = Object.prototype;
function Be(e8, t2) {
  return (e8 = e8.slice()).push(t2), e8;
}
function Fe(e8, t2) {
  return (t2 = t2.slice()).unshift(e8), t2;
}
class Ue extends Error {
  constructor(e8) {
    super('JSONPath should not be called with "new" (it prevents return of (unwrapped) scalar values)'), this.avoidNew = true, this.value = e8, this.name = "NewError";
  }
}
function He(e8, t2, n2, r2, i2) {
  if (!(this instanceof He)) try {
    return new He(e8, t2, n2, r2, i2);
  } catch (e9) {
    if (!e9.avoidNew) throw e9;
    return e9.value;
  }
  "string" == typeof e8 && (i2 = r2, r2 = n2, n2 = t2, t2 = e8, e8 = null);
  const s2 = e8 && "object" == typeof e8;
  if (e8 = e8 || {}, this.json = e8.json || n2, this.path = e8.path || t2, this.resultType = e8.resultType || "value", this.flatten = e8.flatten || false, this.wrap = !we.call(e8, "wrap") || e8.wrap, this.sandbox = e8.sandbox || {}, this.preventEval = e8.preventEval || false, this.parent = e8.parent || null, this.parentProperty = e8.parentProperty || null, this.callback = e8.callback || r2 || null, this.otherTypeCallback = e8.otherTypeCallback || i2 || function() {
    throw new TypeError("You must supply an otherTypeCallback callback option with the @other() operator.");
  }, false !== e8.autostart) {
    const r3 = { path: s2 ? e8.path : t2 };
    s2 ? "json" in e8 && (r3.json = e8.json) : r3.json = n2;
    const i3 = this.evaluate(r3);
    if (!i3 || "object" != typeof i3) throw new Ue(i3);
    return i3;
  }
}
He.prototype.evaluate = function(e8, t2, n2, r2) {
  let i2 = this.parent, s2 = this.parentProperty, { flatten: a2, wrap: o2 } = this;
  if (this.currResultType = this.resultType, this.currPreventEval = this.preventEval, this.currSandbox = this.sandbox, n2 = n2 || this.callback, this.currOtherTypeCallback = r2 || this.otherTypeCallback, t2 = t2 || this.json, (e8 = e8 || this.path) && "object" == typeof e8 && !Array.isArray(e8)) {
    if (!e8.path && "" !== e8.path) throw new TypeError('You must supply a "path" property when providing an object argument to JSONPath.evaluate().');
    if (!we.call(e8, "json")) throw new TypeError('You must supply a "json" property when providing an object argument to JSONPath.evaluate().');
    ({ json: t2 } = e8), a2 = we.call(e8, "flatten") ? e8.flatten : a2, this.currResultType = we.call(e8, "resultType") ? e8.resultType : this.currResultType, this.currSandbox = we.call(e8, "sandbox") ? e8.sandbox : this.currSandbox, o2 = we.call(e8, "wrap") ? e8.wrap : o2, this.currPreventEval = we.call(e8, "preventEval") ? e8.preventEval : this.currPreventEval, n2 = we.call(e8, "callback") ? e8.callback : n2, this.currOtherTypeCallback = we.call(e8, "otherTypeCallback") ? e8.otherTypeCallback : this.currOtherTypeCallback, i2 = we.call(e8, "parent") ? e8.parent : i2, s2 = we.call(e8, "parentProperty") ? e8.parentProperty : s2, e8 = e8.path;
  }
  if (i2 = i2 || null, s2 = s2 || null, Array.isArray(e8) && (e8 = He.toPathString(e8)), !e8 && "" !== e8 || !t2) return;
  const c2 = He.toPathArray(e8);
  "$" === c2[0] && c2.length > 1 && c2.shift(), this._hasParentSelector = null;
  const l2 = this._trace(c2, t2, ["$"], i2, s2, n2).filter((function(e9) {
    return e9 && !e9.isParentSelector;
  }));
  return l2.length ? o2 || 1 !== l2.length || l2[0].hasArrExpr ? l2.reduce(((e9, t3) => {
    const n3 = this._getPreferredOutput(t3);
    return a2 && Array.isArray(n3) ? e9 = e9.concat(n3) : e9.push(n3), e9;
  }), []) : this._getPreferredOutput(l2[0]) : o2 ? [] : void 0;
}, He.prototype._getPreferredOutput = function(e8) {
  const t2 = this.currResultType;
  switch (t2) {
    case "all": {
      const t3 = Array.isArray(e8.path) ? e8.path : He.toPathArray(e8.path);
      return e8.pointer = He.toPointer(t3), e8.path = "string" == typeof e8.path ? e8.path : He.toPathString(e8.path), e8;
    }
    case "value":
    case "parent":
    case "parentProperty":
      return e8[t2];
    case "path":
      return He.toPathString(e8[t2]);
    case "pointer":
      return He.toPointer(e8.path);
    default:
      throw new TypeError("Unknown result type");
  }
}, He.prototype._handleCallback = function(e8, t2, n2) {
  if (t2) {
    const r2 = this._getPreferredOutput(e8);
    e8.path = "string" == typeof e8.path ? e8.path : He.toPathString(e8.path), t2(r2, n2, e8);
  }
}, He.prototype._trace = function(e8, t2, n2, r2, i2, s2, a2, o2) {
  let c2;
  if (!e8.length) return c2 = { path: n2, value: t2, parent: r2, parentProperty: i2, hasArrExpr: a2 }, this._handleCallback(c2, s2, "value"), c2;
  const l2 = e8[0], h2 = e8.slice(1), u2 = [];
  function p2(e9) {
    Array.isArray(e9) ? e9.forEach(((e10) => {
      u2.push(e10);
    })) : u2.push(e9);
  }
  if (("string" != typeof l2 || o2) && t2 && we.call(t2, l2)) p2(this._trace(h2, t2[l2], Be(n2, l2), t2, l2, s2, a2));
  else if ("*" === l2) this._walk(t2, ((e9) => {
    p2(this._trace(h2, t2[e9], Be(n2, e9), t2, e9, s2, true, true));
  }));
  else if (".." === l2) p2(this._trace(h2, t2, n2, r2, i2, s2, a2)), this._walk(t2, ((r3) => {
    "object" == typeof t2[r3] && p2(this._trace(e8.slice(), t2[r3], Be(n2, r3), t2, r3, s2, true));
  }));
  else {
    if ("^" === l2) return this._hasParentSelector = true, { path: n2.slice(0, -1), expr: h2, isParentSelector: true };
    if ("~" === l2) return c2 = { path: Be(n2, l2), value: i2, parent: r2, parentProperty: null }, this._handleCallback(c2, s2, "property"), c2;
    if ("$" === l2) p2(this._trace(h2, t2, n2, null, null, s2, a2));
    else if (/^(-?\d*):(-?\d*):?(\d*)$/u.test(l2)) p2(this._slice(l2, h2, t2, n2, r2, i2, s2));
    else if (0 === l2.indexOf("?(")) {
      if (this.currPreventEval) throw new Error("Eval [?(expr)] prevented in JSONPath expression.");
      const e9 = l2.replace(/^\?\((.*?)\)$/u, "$1");
      this._walk(t2, ((a3) => {
        this._eval(e9, t2[a3], a3, n2, r2, i2) && p2(this._trace(h2, t2[a3], Be(n2, a3), t2, a3, s2, true));
      }));
    } else if ("(" === l2[0]) {
      if (this.currPreventEval) throw new Error("Eval [(expr)] prevented in JSONPath expression.");
      p2(this._trace(Fe(this._eval(l2, t2, n2[n2.length - 1], n2.slice(0, -1), r2, i2), h2), t2, n2, r2, i2, s2, a2));
    } else if ("@" === l2[0]) {
      let e9 = false;
      const a3 = l2.slice(1, -2);
      switch (a3) {
        case "scalar":
          t2 && ["object", "function"].includes(typeof t2) || (e9 = true);
          break;
        case "boolean":
        case "string":
        case "undefined":
        case "function":
          typeof t2 === a3 && (e9 = true);
          break;
        case "integer":
          !Number.isFinite(t2) || t2 % 1 || (e9 = true);
          break;
        case "number":
          Number.isFinite(t2) && (e9 = true);
          break;
        case "nonFinite":
          "number" != typeof t2 || Number.isFinite(t2) || (e9 = true);
          break;
        case "object":
          t2 && typeof t2 === a3 && (e9 = true);
          break;
        case "array":
          Array.isArray(t2) && (e9 = true);
          break;
        case "other":
          e9 = this.currOtherTypeCallback(t2, n2, r2, i2);
          break;
        case "null":
          null === t2 && (e9 = true);
          break;
        default:
          throw new TypeError("Unknown value type " + a3);
      }
      if (e9) return c2 = { path: n2, value: t2, parent: r2, parentProperty: i2 }, this._handleCallback(c2, s2, "value"), c2;
    } else if ("`" === l2[0] && t2 && we.call(t2, l2.slice(1))) {
      const e9 = l2.slice(1);
      p2(this._trace(h2, t2[e9], Be(n2, e9), t2, e9, s2, a2, true));
    } else if (l2.includes(",")) {
      const e9 = l2.split(",");
      for (const a3 of e9) p2(this._trace(Fe(a3, h2), t2, n2, r2, i2, s2, true));
    } else !o2 && t2 && we.call(t2, l2) && p2(this._trace(h2, t2[l2], Be(n2, l2), t2, l2, s2, a2, true));
  }
  if (this._hasParentSelector) for (let e9 = 0; e9 < u2.length; e9++) {
    const n3 = u2[e9];
    if (n3 && n3.isParentSelector) {
      const o3 = this._trace(n3.expr, t2, n3.path, r2, i2, s2, a2);
      if (Array.isArray(o3)) {
        u2[e9] = o3[0];
        const t3 = o3.length;
        for (let n4 = 1; n4 < t3; n4++) e9++, u2.splice(e9, 0, o3[n4]);
      } else u2[e9] = o3;
    }
  }
  return u2;
}, He.prototype._walk = function(e8, t2) {
  if (Array.isArray(e8)) {
    const n2 = e8.length;
    for (let e9 = 0; e9 < n2; e9++) t2(e9);
  } else e8 && "object" == typeof e8 && Object.keys(e8).forEach(((e9) => {
    t2(e9);
  }));
}, He.prototype._slice = function(e8, t2, n2, r2, i2, s2, a2) {
  if (!Array.isArray(n2)) return;
  const o2 = n2.length, c2 = e8.split(":"), l2 = c2[2] && Number.parseInt(c2[2]) || 1;
  let h2 = c2[0] && Number.parseInt(c2[0]) || 0, u2 = c2[1] && Number.parseInt(c2[1]) || o2;
  h2 = h2 < 0 ? Math.max(0, h2 + o2) : Math.min(o2, h2), u2 = u2 < 0 ? Math.max(0, u2 + o2) : Math.min(o2, u2);
  const p2 = [];
  for (let e9 = h2; e9 < u2; e9 += l2) {
    this._trace(Fe(e9, t2), n2, r2, i2, s2, a2, true).forEach(((e10) => {
      p2.push(e10);
    }));
  }
  return p2;
}, He.prototype._eval = function(e8, t2, n2, r2, i2, s2) {
  this.currSandbox._$_parentProperty = s2, this.currSandbox._$_parent = i2, this.currSandbox._$_property = n2, this.currSandbox._$_root = this.json, this.currSandbox._$_v = t2;
  const a2 = e8.includes("@path");
  a2 && (this.currSandbox._$_path = He.toPathString(r2.concat([n2])));
  const o2 = "script:" + e8;
  if (!He.cache[o2]) {
    let t3 = e8.replace(/@parentProperty/gu, "_$_parentProperty").replace(/@parent/gu, "_$_parent").replace(/@property/gu, "_$_property").replace(/@root/gu, "_$_root").replace(/@([.\s)[])/gu, "_$_v$1");
    a2 && (t3 = t3.replace(/@path/gu, "_$_path")), He.cache[o2] = new this.vm.Script(t3);
  }
  try {
    return He.cache[o2].runInNewContext(this.currSandbox);
  } catch (t3) {
    throw new Error("jsonPath: " + t3.message + ": " + e8);
  }
}, He.cache = {}, He.toPathString = function(e8) {
  const t2 = e8, n2 = t2.length;
  let r2 = "$";
  for (let e9 = 1; e9 < n2; e9++) /^(~|\^|@.*?\(\))$/u.test(t2[e9]) || (r2 += /^[0-9*]+$/u.test(t2[e9]) ? "[" + t2[e9] + "]" : "['" + t2[e9] + "']");
  return r2;
}, He.toPointer = function(e8) {
  const t2 = e8, n2 = t2.length;
  let r2 = "";
  for (let e9 = 1; e9 < n2; e9++) /^(~|\^|@.*?\(\))$/u.test(t2[e9]) || (r2 += "/" + t2[e9].toString().replace(/~/gu, "~0").replace(/\//gu, "~1"));
  return r2;
}, He.toPathArray = function(e8) {
  const { cache: t2 } = He;
  if (t2[e8]) return t2[e8].concat();
  const n2 = [], r2 = e8.replace(/@(?:null|boolean|number|string|integer|undefined|nonFinite|scalar|array|object|function|other)\(\)/gu, ";$&;").replace(/[['](\??\(.*?\))[\]']/gu, (function(e9, t3) {
    return "[#" + (n2.push(t3) - 1) + "]";
  })).replace(/\[['"]([^'\]]*)['"]\]/gu, (function(e9, t3) {
    return "['" + t3.replace(/\./gu, "%@%").replace(/~/gu, "%%@@%%") + "']";
  })).replace(/~/gu, ";~;").replace(/['"]?\.['"]?(?![^[]*\])|\[['"]?/gu, ";").replace(/%@%/gu, ".").replace(/%%@@%%/gu, "~").replace(/(?:;)?(\^+)(?:;)?/gu, (function(e9, t3) {
    return ";" + t3.split("").join(";") + ";";
  })).replace(/;;;|;;/gu, ";..;").replace(/;$|'?\]|'$/gu, ""), i2 = r2.split(";").map((function(e9) {
    const t3 = e9.match(/#(\d+)/u);
    return t3 && t3[1] ? n2[t3[1]] : e9;
  }));
  return t2[e8] = i2, t2[e8].concat();
}, He.prototype.vm = Pe;
var Ge = "undefined" != typeof globalThis ? globalThis : "undefined" != typeof window ? window : "undefined" != typeof global ? global : "undefined" != typeof self ? self : {};
function Ye() {
  throw new Error("Dynamic requires are not currently supported by rollup-plugin-commonjs");
}
function qe(e8) {
  return e8 && e8.__esModule && Object.prototype.hasOwnProperty.call(e8, "default") ? e8.default : e8;
}
function Ke(e8, t2) {
  return e8(t2 = { exports: {} }, t2.exports), t2.exports;
}
var je = Ke((function(e8) {
  var t2 = Array.prototype, n2 = Object.prototype, r2 = { "&": "&amp;", '"': "&quot;", "'": "&#39;", "<": "&lt;", ">": "&gt;" }, i2 = /[&"'<>]/g, s2 = e8.exports = {};
  function a2(e9, t3) {
    return n2.hasOwnProperty.call(e9, t3);
  }
  function o2(e9) {
    return r2[e9];
  }
  function c2(e9, t3, n3) {
    var r3, i3, s3;
    if (e9 instanceof Error && (e9 = (i3 = e9).name + ": " + i3.message), Object.setPrototypeOf ? (r3 = new Error(e9), Object.setPrototypeOf(r3, c2.prototype)) : (r3 = this, Object.defineProperty(r3, "message", { enumerable: false, writable: true, value: e9 })), Object.defineProperty(r3, "name", { value: "Template render error" }), Error.captureStackTrace && Error.captureStackTrace(r3, this.constructor), i3) {
      var a3 = Object.getOwnPropertyDescriptor(i3, "stack");
      (s3 = a3 && (a3.get || function() {
        return a3.value;
      })) || (s3 = function() {
        return i3.stack;
      });
    } else {
      var o3 = new Error(e9).stack;
      s3 = function() {
        return o3;
      };
    }
    return Object.defineProperty(r3, "stack", { get: function() {
      return s3.call(r3);
    } }), Object.defineProperty(r3, "cause", { value: i3 }), r3.lineno = t3, r3.colno = n3, r3.firstUpdate = true, r3.Update = function(e10) {
      var t4 = "(" + (e10 || "unknown path") + ")";
      return this.firstUpdate && (this.lineno && this.colno ? t4 += " [Line " + this.lineno + ", Column " + this.colno + "]" : this.lineno && (t4 += " [Line " + this.lineno + "]")), t4 += "\n ", this.firstUpdate && (t4 += " "), this.message = t4 + (this.message || ""), this.firstUpdate = false, this;
    }, r3;
  }
  function l2(e9) {
    return "[object Function]" === n2.toString.call(e9);
  }
  function h2(e9) {
    return "[object Array]" === n2.toString.call(e9);
  }
  function u2(e9) {
    return "[object String]" === n2.toString.call(e9);
  }
  function p2(e9) {
    return "[object Object]" === n2.toString.call(e9);
  }
  function f2(e9) {
    var t3 = (function(e10) {
      return e10 ? "string" == typeof e10 ? e10.split(".") : [e10] : [];
    })(e9);
    return function(e10) {
      for (var n3 = e10, r3 = 0; r3 < t3.length; r3++) {
        var i3 = t3[r3];
        if (!a2(n3, i3)) return;
        n3 = n3[i3];
      }
      return n3;
    };
  }
  function d2(e9) {
    return Array.prototype.slice.call(e9);
  }
  function E2(e9, t3, n3) {
    return Array.prototype.indexOf.call(e9 || [], t3, n3);
  }
  function m2(e9) {
    var t3 = [];
    for (var n3 in e9) a2(e9, n3) && t3.push(n3);
    return t3;
  }
  s2.hasOwnProp = a2, s2._prettifyError = function(e9, t3, n3) {
    if (n3.Update || (n3 = new s2.TemplateError(n3)), n3.Update(e9), !t3) {
      var r3 = n3;
      (n3 = new Error(r3.message)).name = r3.name;
    }
    return n3;
  }, Object.setPrototypeOf ? Object.setPrototypeOf(c2.prototype, Error.prototype) : c2.prototype = Object.create(Error.prototype, { constructor: { value: c2 } }), s2.TemplateError = c2, s2.escape = function(e9) {
    return e9.replace(i2, o2);
  }, s2.isFunction = l2, s2.isArray = h2, s2.isString = u2, s2.isObject = p2, s2.getAttrGetter = f2, s2.groupBy = function(e9, t3, n3) {
    for (var r3 = {}, i3 = l2(t3) ? t3 : f2(t3), s3 = 0; s3 < e9.length; s3++) {
      var a3 = e9[s3], o3 = i3(a3, s3);
      if (void 0 === o3 && true === n3) throw new TypeError('groupby: attribute "' + t3 + '" resolved to undefined');
      (r3[o3] || (r3[o3] = [])).push(a3);
    }
    return r3;
  }, s2.toArray = d2, s2.without = function(e9) {
    var t3 = [];
    if (!e9) return t3;
    for (var n3 = e9.length, r3 = d2(arguments).slice(1), i3 = -1; ++i3 < n3; ) -1 === E2(r3, e9[i3]) && t3.push(e9[i3]);
    return t3;
  }, s2.repeat = function(e9, t3) {
    for (var n3 = "", r3 = 0; r3 < t3; r3++) n3 += e9;
    return n3;
  }, s2.each = function(e9, n3, r3) {
    if (null != e9) {
      if (t2.forEach && e9.forEach === t2.forEach) e9.forEach(n3, r3);
      else if (e9.length === +e9.length) for (var i3 = 0, s3 = e9.length; i3 < s3; i3++) n3.call(r3, e9[i3], i3, e9);
    }
  }, s2.map = function(e9, n3) {
    var r3 = [];
    if (null == e9) return r3;
    if (t2.map && e9.map === t2.map) return e9.map(n3);
    for (var i3 = 0; i3 < e9.length; i3++) r3[r3.length] = n3(e9[i3], i3);
    return e9.length === +e9.length && (r3.length = e9.length), r3;
  }, s2.asyncIter = function(e9, t3, n3) {
    var r3 = -1;
    !(function i3() {
      ++r3 < e9.length ? t3(e9[r3], r3, i3, n3) : n3();
    })();
  }, s2.asyncFor = function(e9, t3, n3) {
    var r3 = m2(e9 || {}), i3 = r3.length, s3 = -1;
    !(function a3() {
      s3++;
      var o3 = r3[s3];
      s3 < i3 ? t3(o3, e9[o3], s3, i3, a3) : n3();
    })();
  }, s2.indexOf = E2, s2.keys = m2, s2._entries = function(e9) {
    return m2(e9).map((function(t3) {
      return [t3, e9[t3]];
    }));
  }, s2._values = function(e9) {
    return m2(e9).map((function(t3) {
      return e9[t3];
    }));
  }, s2._assign = s2.extend = function(e9, t3) {
    return e9 = e9 || {}, m2(t3).forEach((function(n3) {
      e9[n3] = t3[n3];
    })), e9;
  }, s2.inOperator = function(e9, t3) {
    if (h2(t3) || u2(t3)) return -1 !== t3.indexOf(e9);
    if (p2(t3)) return e9 in t3;
    throw new Error('Cannot use "in" operator to search for "' + e9 + '" in unexpected types.');
  };
}));
function Ve() {
  throw new Error("setTimeout has not been defined");
}
function We() {
  throw new Error("clearTimeout has not been defined");
}
var Qe = Ve, Xe = We;
function $e(e8) {
  if (Qe === setTimeout) return setTimeout(e8, 0);
  if ((Qe === Ve || !Qe) && setTimeout) return Qe = setTimeout, setTimeout(e8, 0);
  try {
    return Qe(e8, 0);
  } catch (t2) {
    try {
      return Qe.call(null, e8, 0);
    } catch (t3) {
      return Qe.call(this, e8, 0);
    }
  }
}
"function" == typeof be.setTimeout && (Qe = setTimeout), "function" == typeof be.clearTimeout && (Xe = clearTimeout);
var ze, Je = [], Ze = false, et = -1;
function tt() {
  Ze && ze && (Ze = false, ze.length ? Je = ze.concat(Je) : et = -1, Je.length && nt());
}
function nt() {
  if (!Ze) {
    var e8 = $e(tt);
    Ze = true;
    for (var t2 = Je.length; t2; ) {
      for (ze = Je, Je = []; ++et < t2; ) ze && ze[et].run();
      et = -1, t2 = Je.length;
    }
    ze = null, Ze = false, (function(e9) {
      if (Xe === clearTimeout) return clearTimeout(e9);
      if ((Xe === We || !Xe) && clearTimeout) return Xe = clearTimeout, clearTimeout(e9);
      try {
        Xe(e9);
      } catch (t3) {
        try {
          return Xe.call(null, e9);
        } catch (t4) {
          return Xe.call(this, e9);
        }
      }
    })(e8);
  }
}
function rt(e8, t2) {
  this.fun = e8, this.array = t2;
}
rt.prototype.run = function() {
  this.fun.apply(null, this.array);
};
function it() {
}
var st = it, at = it, ot = it, ct = it, lt = it, ht = it, ut = it;
var pt = be.performance || {}, ft = pt.now || pt.mozNow || pt.msNow || pt.oNow || pt.webkitNow || function() {
  return (/* @__PURE__ */ new Date()).getTime();
};
var dt = /* @__PURE__ */ new Date();
var Et = { nextTick: function(e8) {
  var t2 = new Array(arguments.length - 1);
  if (arguments.length > 1) for (var n2 = 1; n2 < arguments.length; n2++) t2[n2 - 1] = arguments[n2];
  Je.push(new rt(e8, t2)), 1 !== Je.length || Ze || $e(nt);
}, title: "browser", browser: true, env: {}, argv: [], version: "", versions: {}, on: st, addListener: at, once: ot, off: ct, removeListener: lt, removeAllListeners: ht, emit: ut, binding: function(e8) {
  throw new Error("process.binding is not supported");
}, cwd: function() {
  return "/";
}, chdir: function(e8) {
  throw new Error("process.chdir is not supported");
}, umask: function() {
  return 0;
}, hrtime: function(e8) {
  var t2 = 1e-3 * ft.call(pt), n2 = Math.floor(t2), r2 = Math.floor(t2 % 1 * 1e9);
  return e8 && (n2 -= e8[0], (r2 -= e8[1]) < 0 && (n2--, r2 += 1e9)), [n2, r2];
}, platform: "browser", release: {}, config: {}, uptime: function() {
  return (/* @__PURE__ */ new Date() - dt) / 1e3;
} };
function mt() {
}
function Tt() {
  Tt.init.call(this);
}
function _t(e8) {
  return void 0 === e8._maxListeners ? Tt.defaultMaxListeners : e8._maxListeners;
}
function At(e8, t2, n2) {
  if (t2) e8.call(n2);
  else for (var r2 = e8.length, i2 = yt(e8, r2), s2 = 0; s2 < r2; ++s2) i2[s2].call(n2);
}
function gt(e8, t2, n2, r2) {
  if (t2) e8.call(n2, r2);
  else for (var i2 = e8.length, s2 = yt(e8, i2), a2 = 0; a2 < i2; ++a2) s2[a2].call(n2, r2);
}
function Nt(e8, t2, n2, r2, i2) {
  if (t2) e8.call(n2, r2, i2);
  else for (var s2 = e8.length, a2 = yt(e8, s2), o2 = 0; o2 < s2; ++o2) a2[o2].call(n2, r2, i2);
}
function Ct(e8, t2, n2, r2, i2, s2) {
  if (t2) e8.call(n2, r2, i2, s2);
  else for (var a2 = e8.length, o2 = yt(e8, a2), c2 = 0; c2 < a2; ++c2) o2[c2].call(n2, r2, i2, s2);
}
function It(e8, t2, n2, r2) {
  if (t2) e8.apply(n2, r2);
  else for (var i2 = e8.length, s2 = yt(e8, i2), a2 = 0; a2 < i2; ++a2) s2[a2].apply(n2, r2);
}
function St(e8, t2, n2, r2) {
  var i2, s2, a2;
  if ("function" != typeof n2) throw new TypeError('"listener" argument must be a function');
  if ((s2 = e8._events) ? (s2.newListener && (e8.emit("newListener", t2, n2.listener ? n2.listener : n2), s2 = e8._events), a2 = s2[t2]) : (s2 = e8._events = new mt(), e8._eventsCount = 0), a2) {
    if ("function" == typeof a2 ? a2 = s2[t2] = r2 ? [n2, a2] : [a2, n2] : r2 ? a2.unshift(n2) : a2.push(n2), !a2.warned && (i2 = _t(e8)) && i2 > 0 && a2.length > i2) {
      a2.warned = true;
      var o2 = new Error("Possible EventEmitter memory leak detected. " + a2.length + " " + t2 + " listeners added. Use emitter.setMaxListeners() to increase limit");
      o2.name = "MaxListenersExceededWarning", o2.emitter = e8, o2.type = t2, o2.count = a2.length, (function(e9) {
        "function" == typeof console.warn ? console.warn(e9) : console.log(e9);
      })(o2);
    }
  } else a2 = s2[t2] = n2, ++e8._eventsCount;
  return e8;
}
function bt(e8, t2, n2) {
  var r2 = false;
  function i2() {
    e8.removeListener(t2, i2), r2 || (r2 = true, n2.apply(e8, arguments));
  }
  return i2.listener = n2, i2;
}
function Ot(e8) {
  var t2 = this._events;
  if (t2) {
    var n2 = t2[e8];
    if ("function" == typeof n2) return 1;
    if (n2) return n2.length;
  }
  return 0;
}
function yt(e8, t2) {
  for (var n2 = new Array(t2); t2--; ) n2[t2] = e8[t2];
  return n2;
}
function Lt() {
  var e8;
  Tt.call(this), this.__emitError = (e8 = this, function(t2) {
    e8.emit("error", t2);
  });
}
function kt() {
  return new Lt();
}
mt.prototype = /* @__PURE__ */ Object.create(null), Tt.EventEmitter = Tt, Tt.usingDomains = false, Tt.prototype.domain = void 0, Tt.prototype._events = void 0, Tt.prototype._maxListeners = void 0, Tt.defaultMaxListeners = 10, Tt.init = function() {
  this.domain = null, Tt.usingDomains && (void 0).active, this._events && this._events !== Object.getPrototypeOf(this)._events || (this._events = new mt(), this._eventsCount = 0), this._maxListeners = this._maxListeners || void 0;
}, Tt.prototype.setMaxListeners = function(e8) {
  if ("number" != typeof e8 || e8 < 0 || isNaN(e8)) throw new TypeError('"n" argument must be a positive number');
  return this._maxListeners = e8, this;
}, Tt.prototype.getMaxListeners = function() {
  return _t(this);
}, Tt.prototype.emit = function(e8) {
  var t2, n2, r2, i2, s2, a2, o2, c2 = "error" === e8;
  if (a2 = this._events) c2 = c2 && null == a2.error;
  else if (!c2) return false;
  if (o2 = this.domain, c2) {
    if (t2 = arguments[1], !o2) {
      if (t2 instanceof Error) throw t2;
      var l2 = new Error('Uncaught, unspecified "error" event. (' + t2 + ")");
      throw l2.context = t2, l2;
    }
    return t2 || (t2 = new Error('Uncaught, unspecified "error" event')), t2.domainEmitter = this, t2.domain = o2, t2.domainThrown = false, o2.emit("error", t2), false;
  }
  if (!(n2 = a2[e8])) return false;
  var h2 = "function" == typeof n2;
  switch (r2 = arguments.length) {
    case 1:
      At(n2, h2, this);
      break;
    case 2:
      gt(n2, h2, this, arguments[1]);
      break;
    case 3:
      Nt(n2, h2, this, arguments[1], arguments[2]);
      break;
    case 4:
      Ct(n2, h2, this, arguments[1], arguments[2], arguments[3]);
      break;
    default:
      for (i2 = new Array(r2 - 1), s2 = 1; s2 < r2; s2++) i2[s2 - 1] = arguments[s2];
      It(n2, h2, this, i2);
  }
  return true;
}, Tt.prototype.addListener = function(e8, t2) {
  return St(this, e8, t2, false);
}, Tt.prototype.on = Tt.prototype.addListener, Tt.prototype.prependListener = function(e8, t2) {
  return St(this, e8, t2, true);
}, Tt.prototype.once = function(e8, t2) {
  if ("function" != typeof t2) throw new TypeError('"listener" argument must be a function');
  return this.on(e8, bt(this, e8, t2)), this;
}, Tt.prototype.prependOnceListener = function(e8, t2) {
  if ("function" != typeof t2) throw new TypeError('"listener" argument must be a function');
  return this.prependListener(e8, bt(this, e8, t2)), this;
}, Tt.prototype.removeListener = function(e8, t2) {
  var n2, r2, i2, s2, a2;
  if ("function" != typeof t2) throw new TypeError('"listener" argument must be a function');
  if (!(r2 = this._events)) return this;
  if (!(n2 = r2[e8])) return this;
  if (n2 === t2 || n2.listener && n2.listener === t2) 0 == --this._eventsCount ? this._events = new mt() : (delete r2[e8], r2.removeListener && this.emit("removeListener", e8, n2.listener || t2));
  else if ("function" != typeof n2) {
    for (i2 = -1, s2 = n2.length; s2-- > 0; ) if (n2[s2] === t2 || n2[s2].listener && n2[s2].listener === t2) {
      a2 = n2[s2].listener, i2 = s2;
      break;
    }
    if (i2 < 0) return this;
    if (1 === n2.length) {
      if (n2[0] = void 0, 0 == --this._eventsCount) return this._events = new mt(), this;
      delete r2[e8];
    } else !(function(e9, t3) {
      for (var n3 = t3, r3 = n3 + 1, i3 = e9.length; r3 < i3; n3 += 1, r3 += 1) e9[n3] = e9[r3];
      e9.pop();
    })(n2, i2);
    r2.removeListener && this.emit("removeListener", e8, a2 || t2);
  }
  return this;
}, Tt.prototype.removeAllListeners = function(e8) {
  var t2, n2;
  if (!(n2 = this._events)) return this;
  if (!n2.removeListener) return 0 === arguments.length ? (this._events = new mt(), this._eventsCount = 0) : n2[e8] && (0 == --this._eventsCount ? this._events = new mt() : delete n2[e8]), this;
  if (0 === arguments.length) {
    for (var r2, i2 = Object.keys(n2), s2 = 0; s2 < i2.length; ++s2) "removeListener" !== (r2 = i2[s2]) && this.removeAllListeners(r2);
    return this.removeAllListeners("removeListener"), this._events = new mt(), this._eventsCount = 0, this;
  }
  if ("function" == typeof (t2 = n2[e8])) this.removeListener(e8, t2);
  else if (t2) do {
    this.removeListener(e8, t2[t2.length - 1]);
  } while (t2[0]);
  return this;
}, Tt.prototype.listeners = function(e8) {
  var t2, n2 = this._events;
  return n2 && (t2 = n2[e8]) ? "function" == typeof t2 ? [t2.listener || t2] : (function(e9) {
    for (var t3 = new Array(e9.length), n3 = 0; n3 < t3.length; ++n3) t3[n3] = e9[n3].listener || e9[n3];
    return t3;
  })(t2) : [];
}, Tt.listenerCount = function(e8, t2) {
  return "function" == typeof e8.listenerCount ? e8.listenerCount(t2) : Ot.call(e8, t2);
}, Tt.prototype.listenerCount = Ot, Tt.prototype.eventNames = function() {
  return this._eventsCount > 0 ? Reflect.ownKeys(this._events) : [];
}, ("function" == typeof Object.create ? function(e8, t2) {
  e8.super_ = t2, e8.prototype = Object.create(t2.prototype, { constructor: { value: e8, enumerable: false, writable: true, configurable: true } });
} : function(e8, t2) {
  e8.super_ = t2;
  var n2 = function() {
  };
  n2.prototype = t2.prototype, e8.prototype = new n2(), e8.prototype.constructor = e8;
})(Lt, Tt), Lt.prototype.add = function(e8) {
  e8.on("error", this.__emitError);
}, Lt.prototype.remove = function(e8) {
  e8.removeListener("error", this.__emitError);
}, Lt.prototype.bind = function(e8) {
  var t2 = this.__emitError;
  return function() {
    var n2 = Array.prototype.slice.call(arguments);
    try {
      e8.apply(null, n2);
    } catch (e9) {
      t2(e9);
    }
  };
}, Lt.prototype.intercept = function(e8) {
  var t2 = this.__emitError;
  return function(n2) {
    if (n2) t2(n2);
    else {
      var r2 = Array.prototype.slice.call(arguments, 1);
      try {
        e8.apply(null, r2);
      } catch (n3) {
        t2(n3);
      }
    }
  };
}, Lt.prototype.run = function(e8) {
  var t2 = this.__emitError;
  try {
    e8();
  } catch (e9) {
    t2(e9);
  }
  return this;
}, Lt.prototype.dispose = function() {
  return this.removeAllListeners(), this;
}, Lt.prototype.enter = Lt.prototype.exit = function() {
  return this;
};
var vt, Dt = { Domain: Lt, createDomain: kt, create: kt }, Rt = "function" == typeof setImmediate, Mt = Pt;
function Pt(e8) {
  xt.length || (Ut(), wt = true), xt[xt.length] = e8;
}
var xt = [], wt = false, Bt = 0;
function Ft() {
  for (; Bt < xt.length; ) {
    var e8 = Bt;
    if (Bt += 1, xt[e8].call(), Bt > 1024) {
      for (var t2 = 0, n2 = xt.length - Bt; t2 < n2; t2++) xt[t2] = xt[t2 + Bt];
      xt.length -= Bt, Bt = 0;
    }
  }
  xt.length = 0, Bt = 0, wt = false;
}
function Ut() {
  var e8 = Et.domain;
  e8 && (vt || (vt = Dt), vt.active = Et.domain = null), wt && Rt ? setImmediate(Ft) : Et.nextTick(Ft), e8 && (vt.active = Et.domain = e8);
}
Pt.requestFlush = Ut;
var Ht = [], Gt = function(e8) {
  var t2;
  t2 = Ht.length ? Ht.pop() : new Yt();
  t2.task = e8, t2.domain = Et.domain, Mt(t2);
};
function Yt() {
  this.task = null, this.domain = null;
}
Yt.prototype.call = function() {
  this.domain && this.domain.enter();
  var e8 = true;
  try {
    this.task.call(), e8 = false, this.domain && this.domain.exit();
  } finally {
    e8 && Mt.requestFlush(), this.task = null, this.domain = null, Ht.push(this);
  }
};
var qt = Ke((function(e8) {
  !(function(t2) {
    var n2 = function() {
      var e9 = Array.prototype.slice.call(arguments);
      "function" == typeof e9[0] && e9[0].apply(null, e9.splice(1));
    }, r2 = function(e9) {
      "function" == typeof setImmediate ? setImmediate(e9) : void 0 !== Et && Et.nextTick ? Et.nextTick(e9) : setTimeout(e9, 0);
    }, i2 = Array.isArray || function(e9) {
      return "[object Array]" === Object.prototype.toString.call(e9);
    }, s2 = function(e9, t3, s3) {
      var a2 = s3 ? r2 : n2;
      if (t3 = t3 || function() {
      }, !i2(e9)) {
        var o2 = new Error("First argument to waterfall must be an array of functions");
        return t3(o2);
      }
      if (!e9.length) return t3();
      var c2 = function(e10) {
        return function(n3) {
          if (n3) t3.apply(null, arguments), t3 = function() {
          };
          else {
            var r3 = Array.prototype.slice.call(arguments, 1), i3 = e10.next();
            i3 ? r3.push(c2(i3)) : r3.push(t3), a2((function() {
              e10.apply(null, r3);
            }));
          }
        };
      };
      c2((function(e10) {
        var t4 = function(n3) {
          var r3 = function() {
            return e10.length && e10[n3].apply(null, arguments), r3.next();
          };
          return r3.next = function() {
            return n3 < e10.length - 1 ? t4(n3 + 1) : null;
          }, r3;
        };
        return t4(0);
      })(e9))();
    };
    e8.exports ? e8.exports = s2 : t2.waterfall = s2;
  })(Ge);
}));
function Kt(e8, t2, n2, r2) {
  return { type: e8, value: t2, lineno: n2, colno: r2 };
}
var jt = (function() {
  function e8(e9, t3) {
    this.str = e9, this.index = 0, this.len = e9.length, this.lineno = 0, this.colno = 0, this.in_code = false;
    var n2 = (t3 = t3 || {}).tags || {};
    this.tags = { BLOCK_START: n2.blockStart || "{%", BLOCK_END: n2.blockEnd || "%}", VARIABLE_START: n2.variableStart || "{{", VARIABLE_END: n2.variableEnd || "}}", COMMENT_START: n2.commentStart || "{#", COMMENT_END: n2.commentEnd || "#}" }, this.trimBlocks = !!t3.trimBlocks, this.lstripBlocks = !!t3.lstripBlocks;
  }
  var t2 = e8.prototype;
  return t2.nextToken = function() {
    var e9, t3 = this.lineno, n2 = this.colno;
    if (this.in_code) {
      var r2 = this.current();
      if (this.isFinished()) return null;
      if ('"' === r2 || "'" === r2) return Kt("string", this._parseString(r2), t3, n2);
      if (e9 = this._extract(" \n	\r\xA0")) return Kt("whitespace", e9, t3, n2);
      if ((e9 = this._extractString(this.tags.BLOCK_END)) || (e9 = this._extractString("-" + this.tags.BLOCK_END))) return this.in_code = false, this.trimBlocks && ("\n" === (r2 = this.current()) ? this.forward() : "\r" === r2 && (this.forward(), "\n" === (r2 = this.current()) ? this.forward() : this.back())), Kt("block-end", e9, t3, n2);
      if ((e9 = this._extractString(this.tags.VARIABLE_END)) || (e9 = this._extractString("-" + this.tags.VARIABLE_END))) return this.in_code = false, Kt("variable-end", e9, t3, n2);
      if ("r" === r2 && "/" === this.str.charAt(this.index + 1)) {
        this.forwardN(2);
        for (var i2 = ""; !this.isFinished(); ) {
          if ("/" === this.current() && "\\" !== this.previous()) {
            this.forward();
            break;
          }
          i2 += this.current(), this.forward();
        }
        for (var s2 = ["g", "i", "m", "y"], a2 = ""; !this.isFinished(); ) {
          if (!(-1 !== s2.indexOf(this.current()))) break;
          a2 += this.current(), this.forward();
        }
        return Kt("regex", { body: i2, flags: a2 }, t3, n2);
      }
      if (-1 !== "()[]{}%*-+~/#,:|.<>=!".indexOf(r2)) {
        this.forward();
        var o2, c2 = ["==", "===", "!=", "!==", "<=", ">=", "//", "**"], l2 = r2 + this.current();
        switch (-1 !== je.indexOf(c2, l2) && (this.forward(), r2 = l2, -1 !== je.indexOf(c2, l2 + this.current()) && (r2 = l2 + this.current(), this.forward())), r2) {
          case "(":
            o2 = "left-paren";
            break;
          case ")":
            o2 = "right-paren";
            break;
          case "[":
            o2 = "left-bracket";
            break;
          case "]":
            o2 = "right-bracket";
            break;
          case "{":
            o2 = "left-curly";
            break;
          case "}":
            o2 = "right-curly";
            break;
          case ",":
            o2 = "comma";
            break;
          case ":":
            o2 = "colon";
            break;
          case "~":
            o2 = "tilde";
            break;
          case "|":
            o2 = "pipe";
            break;
          default:
            o2 = "operator";
        }
        return Kt(o2, r2, t3, n2);
      }
      if ((e9 = this._extractUntil(" \n	\r\xA0()[]{}%*-+~/#,:|.<>=!")).match(/^[-+]?[0-9]+$/)) return "." === this.current() ? (this.forward(), Kt("float", e9 + "." + this._extract("0123456789"), t3, n2)) : Kt("int", e9, t3, n2);
      if (e9.match(/^(true|false)$/)) return Kt("boolean", e9, t3, n2);
      if ("none" === e9) return Kt("none", e9, t3, n2);
      if ("null" === e9) return Kt("none", e9, t3, n2);
      if (e9) return Kt("symbol", e9, t3, n2);
      throw new Error("Unexpected value while parsing: " + e9);
    }
    var h2, u2 = this.tags.BLOCK_START.charAt(0) + this.tags.VARIABLE_START.charAt(0) + this.tags.COMMENT_START.charAt(0) + this.tags.COMMENT_END.charAt(0);
    if (this.isFinished()) return null;
    if ((e9 = this._extractString(this.tags.BLOCK_START + "-")) || (e9 = this._extractString(this.tags.BLOCK_START))) return this.in_code = true, Kt("block-start", e9, t3, n2);
    if ((e9 = this._extractString(this.tags.VARIABLE_START + "-")) || (e9 = this._extractString(this.tags.VARIABLE_START))) return this.in_code = true, Kt("variable-start", e9, t3, n2);
    e9 = "";
    var p2 = false;
    for (this._matches(this.tags.COMMENT_START) && (p2 = true, e9 = this._extractString(this.tags.COMMENT_START)); null !== (h2 = this._extractUntil(u2)); ) {
      if (e9 += h2, (this._matches(this.tags.BLOCK_START) || this._matches(this.tags.VARIABLE_START) || this._matches(this.tags.COMMENT_START)) && !p2) {
        if (this.lstripBlocks && this._matches(this.tags.BLOCK_START) && this.colno > 0 && this.colno <= e9.length) {
          var f2 = e9.slice(-this.colno);
          if (/^\s+$/.test(f2) && !(e9 = e9.slice(0, -this.colno)).length) return this.nextToken();
        }
        break;
      }
      if (this._matches(this.tags.COMMENT_END)) {
        if (!p2) throw new Error("unexpected end of comment");
        e9 += this._extractString(this.tags.COMMENT_END);
        break;
      }
      e9 += this.current(), this.forward();
    }
    if (null === h2 && p2) throw new Error("expected end of comment, got end of file");
    return Kt(p2 ? "comment" : "data", e9, t3, n2);
  }, t2._parseString = function(e9) {
    this.forward();
    for (var t3 = ""; !this.isFinished() && this.current() !== e9; ) {
      var n2 = this.current();
      if ("\\" === n2) {
        switch (this.forward(), this.current()) {
          case "n":
            t3 += "\n";
            break;
          case "t":
            t3 += "	";
            break;
          case "r":
            t3 += "\r";
            break;
          default:
            t3 += this.current();
        }
        this.forward();
      } else t3 += n2, this.forward();
    }
    return this.forward(), t3;
  }, t2._matches = function(e9) {
    return this.index + e9.length > this.len ? null : this.str.slice(this.index, this.index + e9.length) === e9;
  }, t2._extractString = function(e9) {
    return this._matches(e9) ? (this.forwardN(e9.length), e9) : null;
  }, t2._extractUntil = function(e9) {
    return this._extractMatching(true, e9 || "");
  }, t2._extract = function(e9) {
    return this._extractMatching(false, e9);
  }, t2._extractMatching = function(e9, t3) {
    if (this.isFinished()) return null;
    var n2 = t3.indexOf(this.current());
    if (e9 && -1 === n2 || !e9 && -1 !== n2) {
      var r2 = this.current();
      this.forward();
      for (var i2 = t3.indexOf(this.current()); (e9 && -1 === i2 || !e9 && -1 !== i2) && !this.isFinished(); ) r2 += this.current(), this.forward(), i2 = t3.indexOf(this.current());
      return r2;
    }
    return "";
  }, t2._extractRegex = function(e9) {
    var t3 = this.currentStr().match(e9);
    return t3 ? (this.forwardN(t3[0].length), t3) : null;
  }, t2.isFinished = function() {
    return this.index >= this.len;
  }, t2.forwardN = function(e9) {
    for (var t3 = 0; t3 < e9; t3++) this.forward();
  }, t2.forward = function() {
    this.index++, "\n" === this.previous() ? (this.lineno++, this.colno = 0) : this.colno++;
  }, t2.backN = function(e9) {
    for (var t3 = 0; t3 < e9; t3++) this.back();
  }, t2.back = function() {
    if (this.index--, "\n" === this.current()) {
      this.lineno--;
      var e9 = this.src.lastIndexOf("\n", this.index - 1);
      this.colno = -1 === e9 ? this.index : this.index - e9;
    } else this.colno--;
  }, t2.current = function() {
    return this.isFinished() ? "" : this.str.charAt(this.index);
  }, t2.currentStr = function() {
    return this.isFinished() ? "" : this.str.substr(this.index);
  }, t2.previous = function() {
    return this.str.charAt(this.index - 1);
  }, e8;
})(), Vt = { lex: function(e8, t2) {
  return new jt(e8, t2);
}, TOKEN_STRING: "string", TOKEN_WHITESPACE: "whitespace", TOKEN_DATA: "data", TOKEN_BLOCK_START: "block-start", TOKEN_BLOCK_END: "block-end", TOKEN_VARIABLE_START: "variable-start", TOKEN_VARIABLE_END: "variable-end", TOKEN_COMMENT: "comment", TOKEN_LEFT_PAREN: "left-paren", TOKEN_RIGHT_PAREN: "right-paren", TOKEN_LEFT_BRACKET: "left-bracket", TOKEN_RIGHT_BRACKET: "right-bracket", TOKEN_LEFT_CURLY: "left-curly", TOKEN_RIGHT_CURLY: "right-curly", TOKEN_OPERATOR: "operator", TOKEN_COMMA: "comma", TOKEN_COLON: "colon", TOKEN_TILDE: "tilde", TOKEN_PIPE: "pipe", TOKEN_INT: "int", TOKEN_FLOAT: "float", TOKEN_BOOLEAN: "boolean", TOKEN_NONE: "none", TOKEN_SYMBOL: "symbol", TOKEN_SPECIAL: "special", TOKEN_REGEX: "regex" };
function Wt(e8, t2) {
  for (var n2 = 0; n2 < t2.length; n2++) {
    var r2 = t2[n2];
    r2.enumerable = r2.enumerable || false, r2.configurable = true, "value" in r2 && (r2.writable = true), Object.defineProperty(e8, r2.key, r2);
  }
}
function Qt(e8, t2, n2) {
  return t2 && Wt(e8.prototype, t2), n2 && Wt(e8, n2), e8;
}
function Xt(e8, t2) {
  e8.prototype = Object.create(t2.prototype), e8.prototype.constructor = e8, $t(e8, t2);
}
function $t(e8, t2) {
  return $t = Object.setPrototypeOf || function(e9, t3) {
    return e9.__proto__ = t3, e9;
  }, $t(e8, t2);
}
function zt(e8, t2, n2) {
  n2 = n2 || {}, je.keys(n2).forEach((function(t3) {
    n2[t3] = /* @__PURE__ */ (function(e9, t4) {
      return "function" != typeof e9 || "function" != typeof t4 ? t4 : function() {
        var n3 = this.parent;
        this.parent = e9;
        var r3 = t4.apply(this, arguments);
        return this.parent = n3, r3;
      };
    })(e8.prototype[t3], n2[t3]);
  }));
  var r2 = (function(e9) {
    function n3() {
      return e9.apply(this, arguments) || this;
    }
    return Xt(n3, e9), Qt(n3, [{ key: "typename", get: function() {
      return t2;
    } }]), n3;
  })(e8);
  return je._assign(r2.prototype, n2), r2;
}
var Jt = (function() {
  function e8() {
    this.init.apply(this, arguments);
  }
  return e8.prototype.init = function() {
  }, e8.extend = function(e9, t2) {
    return "object" == typeof e9 && (t2 = e9, e9 = "anonymous"), zt(this, e9, t2);
  }, Qt(e8, [{ key: "typename", get: function() {
    return this.constructor.name;
  } }]), e8;
})(), Zt = (function(e8) {
  function t2() {
    var t3, n2;
    return (t3 = n2 = e8.call(this) || this).init.apply(t3, arguments), n2;
  }
  return Xt(t2, e8), t2.prototype.init = function() {
  }, t2.extend = function(e9, t3) {
    return "object" == typeof e9 && (t3 = e9, e9 = "anonymous"), zt(this, e9, t3);
  }, Qt(t2, [{ key: "typename", get: function() {
    return this.constructor.name;
  } }]), t2;
})(Tt), en = { Obj: Jt, EmitterObj: Zt };
function tn(e8, t2) {
  for (var n2 = 0; n2 < t2.length; n2++) {
    var r2 = t2[n2];
    r2.enumerable = r2.enumerable || false, r2.configurable = true, "value" in r2 && (r2.writable = true), Object.defineProperty(e8, r2.key, r2);
  }
}
function nn(e8, t2, n2) {
  return t2 && tn(e8.prototype, t2), n2 && tn(e8, n2), e8;
}
function rn(e8, t2) {
  e8.prototype = Object.create(t2.prototype), e8.prototype.constructor = e8, sn(e8, t2);
}
function sn(e8, t2) {
  return sn = Object.setPrototypeOf || function(e9, t3) {
    return e9.__proto__ = t3, e9;
  }, sn(e8, t2);
}
function an(e8, t2, n2) {
  e8 instanceof t2 && n2.push(e8), e8 instanceof on && e8.findAll(t2, n2);
}
var on = (function(e8) {
  function t2() {
    return e8.apply(this, arguments) || this;
  }
  rn(t2, e8);
  var n2 = t2.prototype;
  return n2.init = function(e9, t3) {
    for (var n3 = arguments, r2 = this, i2 = arguments.length, s2 = new Array(i2 > 2 ? i2 - 2 : 0), a2 = 2; a2 < i2; a2++) s2[a2 - 2] = arguments[a2];
    this.lineno = e9, this.colno = t3, this.fields.forEach((function(e10, t4) {
      var i3 = n3[t4 + 2];
      void 0 === i3 && (i3 = null), r2[e10] = i3;
    }));
  }, n2.findAll = function(e9, t3) {
    var n3 = this;
    return t3 = t3 || [], this instanceof ln ? this.children.forEach((function(n4) {
      return an(n4, e9, t3);
    })) : this.fields.forEach((function(r2) {
      return an(n3[r2], e9, t3);
    })), t3;
  }, n2.iterFields = function(e9) {
    var t3 = this;
    this.fields.forEach((function(n3) {
      e9(t3[n3], n3);
    }));
  }, t2;
})(en.Obj), cn = (function(e8) {
  function t2() {
    return e8.apply(this, arguments) || this;
  }
  return rn(t2, e8), nn(t2, [{ key: "typename", get: function() {
    return "Value";
  } }, { key: "fields", get: function() {
    return ["value"];
  } }]), t2;
})(on), ln = (function(e8) {
  function t2() {
    return e8.apply(this, arguments) || this;
  }
  rn(t2, e8);
  var n2 = t2.prototype;
  return n2.init = function(t3, n3, r2) {
    e8.prototype.init.call(this, t3, n3, r2 || []);
  }, n2.addChild = function(e9) {
    this.children.push(e9);
  }, nn(t2, [{ key: "typename", get: function() {
    return "NodeList";
  } }, { key: "fields", get: function() {
    return ["children"];
  } }]), t2;
})(on), hn = ln.extend("Root"), un = cn.extend("Literal"), pn = cn.extend("Symbol"), fn = ln.extend("Group"), dn = ln.extend("Array"), En = on.extend("Pair", { fields: ["key", "value"] }), mn = ln.extend("Dict"), Tn = on.extend("LookupVal", { fields: ["target", "val"] }), _n = on.extend("If", { fields: ["cond", "body", "else_"] }), An = _n.extend("IfAsync"), gn = on.extend("InlineIf", { fields: ["cond", "body", "else_"] }), Nn = on.extend("For", { fields: ["arr", "name", "body", "else_"] }), Cn = Nn.extend("AsyncEach"), In = Nn.extend("AsyncAll"), Sn = on.extend("Macro", { fields: ["name", "args", "body"] }), bn = Sn.extend("Caller"), On = on.extend("Import", { fields: ["template", "target", "withContext"] }), yn = (function(e8) {
  function t2() {
    return e8.apply(this, arguments) || this;
  }
  return rn(t2, e8), t2.prototype.init = function(t3, n2, r2, i2, s2) {
    e8.prototype.init.call(this, t3, n2, r2, i2 || new ln(), s2);
  }, nn(t2, [{ key: "typename", get: function() {
    return "FromImport";
  } }, { key: "fields", get: function() {
    return ["template", "names", "withContext"];
  } }]), t2;
})(on), Ln = on.extend("FunCall", { fields: ["name", "args"] }), kn = Ln.extend("Filter"), vn = kn.extend("FilterAsync", { fields: ["name", "args", "symbol"] }), Dn = mn.extend("KeywordArgs"), Rn = on.extend("Block", { fields: ["name", "body"] }), Mn = on.extend("Super", { fields: ["blockName", "symbol"] }), Pn = on.extend("TemplateRef", { fields: ["template"] }).extend("Extends"), xn = on.extend("Include", { fields: ["template", "ignoreMissing"] }), wn = on.extend("Set", { fields: ["targets", "value"] }), Bn = on.extend("Switch", { fields: ["expr", "cases", "default"] }), Fn = on.extend("Case", { fields: ["cond", "body"] }), Un = ln.extend("Output"), Hn = on.extend("Capture", { fields: ["body"] }), Gn = un.extend("TemplateData"), Yn = on.extend("UnaryOp", { fields: ["target"] }), qn = on.extend("BinOp", { fields: ["left", "right"] }), Kn = qn.extend("In"), jn = qn.extend("Is"), Vn = qn.extend("Or"), Wn = qn.extend("And"), Qn = Yn.extend("Not"), Xn = qn.extend("Add"), $n = qn.extend("Concat"), zn = qn.extend("Sub"), Jn = qn.extend("Mul"), Zn = qn.extend("Div"), er = qn.extend("FloorDiv"), tr = qn.extend("Mod"), nr = qn.extend("Pow"), rr = Yn.extend("Neg"), ir = Yn.extend("Pos"), sr = on.extend("Compare", { fields: ["expr", "ops"] }), ar = on.extend("CompareOperand", { fields: ["expr", "type"] }), or = on.extend("CallExtension", { init: function(e8, t2, n2, r2) {
  this.parent(), this.extName = e8.__name || e8, this.prop = t2, this.args = n2 || new ln(), this.contentArgs = r2 || [], this.autoescape = e8.autoescape;
}, fields: ["extName", "prop", "args", "contentArgs"] }), cr = or.extend("CallExtensionAsync");
function lr(e8, t2, n2) {
  var r2 = e8.split("\n");
  r2.forEach((function(e9, i2) {
    e9 && (n2 && i2 > 0 || !n2) && Et.stdout.write(" ".repeat(t2));
    var s2 = i2 === r2.length - 1 ? "" : "\n";
    Et.stdout.write("" + e9 + s2);
  }));
}
var hr = { Node: on, Root: hn, NodeList: ln, Value: cn, Literal: un, Symbol: pn, Group: fn, Array: dn, Pair: En, Dict: mn, Output: Un, Capture: Hn, TemplateData: Gn, If: _n, IfAsync: An, InlineIf: gn, For: Nn, AsyncEach: Cn, AsyncAll: In, Macro: Sn, Caller: bn, Import: On, FromImport: yn, FunCall: Ln, Filter: kn, FilterAsync: vn, KeywordArgs: Dn, Block: Rn, Super: Mn, Extends: Pn, Include: xn, Set: wn, Switch: Bn, Case: Fn, LookupVal: Tn, BinOp: qn, In: Kn, Is: jn, Or: Vn, And: Wn, Not: Qn, Add: Xn, Concat: $n, Sub: zn, Mul: Jn, Div: Zn, FloorDiv: er, Mod: tr, Pow: nr, Neg: rr, Pos: ir, Compare: sr, CompareOperand: ar, CallExtension: or, CallExtensionAsync: cr, printNodes: function e4(t2, n2) {
  if (n2 = n2 || 0, lr(t2.typename + ": ", n2), t2 instanceof ln) lr("\n"), t2.children.forEach((function(t3) {
    e4(t3, n2 + 2);
  }));
  else if (t2 instanceof or) lr(t2.extName + "." + t2.prop + "\n"), t2.args && e4(t2.args, n2 + 2), t2.contentArgs && t2.contentArgs.forEach((function(t3) {
    e4(t3, n2 + 2);
  }));
  else {
    var r2 = [], i2 = null;
    t2.iterFields((function(e8, t3) {
      e8 instanceof on ? r2.push([t3, e8]) : (i2 = i2 || {})[t3] = e8;
    })), i2 ? lr(JSON.stringify(i2, null, 2) + "\n", null, true) : lr("\n"), r2.forEach((function(t3) {
      var r3 = t3[0], i3 = t3[1];
      lr("[" + r3 + "] =>", n2 + 2), e4(i3, n2 + 4);
    }));
  }
} };
function ur(e8, t2) {
  return ur = Object.setPrototypeOf || function(e9, t3) {
    return e9.__proto__ = t3, e9;
  }, ur(e8, t2);
}
hr.Node, hr.Root, hr.NodeList, hr.Value, hr.Literal, hr.Group, hr.Pair, hr.Dict, hr.Output, hr.Capture, hr.TemplateData, hr.If, hr.IfAsync, hr.InlineIf, hr.For, hr.AsyncEach, hr.AsyncAll, hr.Macro, hr.Caller, hr.Import, hr.FromImport, hr.FunCall, hr.Filter, hr.FilterAsync, hr.KeywordArgs, hr.Block, hr.Super, hr.Extends, hr.Include, hr.Switch, hr.Case, hr.LookupVal, hr.BinOp, hr.In, hr.Is, hr.Or, hr.And, hr.Not, hr.Add, hr.Concat, hr.Sub, hr.Mul, hr.Div, hr.FloorDiv, hr.Mod, hr.Pow, hr.Neg, hr.Pos, hr.Compare, hr.CompareOperand, hr.CallExtension, hr.CallExtensionAsync, hr.printNodes;
var pr = (function(e8) {
  var t2, n2;
  function r2() {
    return e8.apply(this, arguments) || this;
  }
  n2 = e8, (t2 = r2).prototype = Object.create(n2.prototype), t2.prototype.constructor = t2, ur(t2, n2);
  var i2 = r2.prototype;
  return i2.init = function(e9) {
    this.tokens = e9, this.peeked = null, this.breakOnBlocks = null, this.dropLeadingWhitespace = false, this.extensions = [];
  }, i2.nextToken = function(e9) {
    var t3;
    if (this.peeked) {
      if (e9 || this.peeked.type !== Vt.TOKEN_WHITESPACE) return t3 = this.peeked, this.peeked = null, t3;
      this.peeked = null;
    }
    if (t3 = this.tokens.nextToken(), !e9) for (; t3 && t3.type === Vt.TOKEN_WHITESPACE; ) t3 = this.tokens.nextToken();
    return t3;
  }, i2.peekToken = function() {
    return this.peeked = this.peeked || this.nextToken(), this.peeked;
  }, i2.pushToken = function(e9) {
    if (this.peeked) throw new Error("pushToken: can only push one token on between reads");
    this.peeked = e9;
  }, i2.error = function(e9, t3, n3) {
    if (void 0 === t3 || void 0 === n3) {
      var r3 = this.peekToken() || {};
      t3 = r3.lineno, n3 = r3.colno;
    }
    return void 0 !== t3 && (t3 += 1), void 0 !== n3 && (n3 += 1), new je.TemplateError(e9, t3, n3);
  }, i2.fail = function(e9, t3, n3) {
    throw this.error(e9, t3, n3);
  }, i2.skip = function(e9) {
    var t3 = this.nextToken();
    return !(!t3 || t3.type !== e9) || (this.pushToken(t3), false);
  }, i2.expect = function(e9) {
    var t3 = this.nextToken();
    return t3.type !== e9 && this.fail("expected " + e9 + ", got " + t3.type, t3.lineno, t3.colno), t3;
  }, i2.skipValue = function(e9, t3) {
    var n3 = this.nextToken();
    return !(!n3 || n3.type !== e9 || n3.value !== t3) || (this.pushToken(n3), false);
  }, i2.skipSymbol = function(e9) {
    return this.skipValue(Vt.TOKEN_SYMBOL, e9);
  }, i2.advanceAfterBlockEnd = function(e9) {
    var t3;
    return e9 || ((t3 = this.peekToken()) || this.fail("unexpected end of file"), t3.type !== Vt.TOKEN_SYMBOL && this.fail("advanceAfterBlockEnd: expected symbol token or explicit name to be passed"), e9 = this.nextToken().value), (t3 = this.nextToken()) && t3.type === Vt.TOKEN_BLOCK_END ? "-" === t3.value.charAt(0) && (this.dropLeadingWhitespace = true) : this.fail("expected block end in " + e9 + " statement"), t3;
  }, i2.advanceAfterVariableEnd = function() {
    var e9 = this.nextToken();
    e9 && e9.type === Vt.TOKEN_VARIABLE_END ? this.dropLeadingWhitespace = "-" === e9.value.charAt(e9.value.length - this.tokens.tags.VARIABLE_END.length - 1) : (this.pushToken(e9), this.fail("expected variable end"));
  }, i2.parseFor = function() {
    var e9, t3, n3 = this.peekToken();
    if (this.skipSymbol("for") ? (e9 = new hr.For(n3.lineno, n3.colno), t3 = "endfor") : this.skipSymbol("asyncEach") ? (e9 = new hr.AsyncEach(n3.lineno, n3.colno), t3 = "endeach") : this.skipSymbol("asyncAll") ? (e9 = new hr.AsyncAll(n3.lineno, n3.colno), t3 = "endall") : this.fail("parseFor: expected for{Async}", n3.lineno, n3.colno), e9.name = this.parsePrimary(), e9.name instanceof hr.Symbol || this.fail("parseFor: variable name expected for loop"), this.peekToken().type === Vt.TOKEN_COMMA) {
      var r3 = e9.name;
      for (e9.name = new hr.Array(r3.lineno, r3.colno), e9.name.addChild(r3); this.skip(Vt.TOKEN_COMMA); ) {
        var i3 = this.parsePrimary();
        e9.name.addChild(i3);
      }
    }
    return this.skipSymbol("in") || this.fail('parseFor: expected "in" keyword for loop', n3.lineno, n3.colno), e9.arr = this.parseExpression(), this.advanceAfterBlockEnd(n3.value), e9.body = this.parseUntilBlocks(t3, "else"), this.skipSymbol("else") && (this.advanceAfterBlockEnd("else"), e9.else_ = this.parseUntilBlocks(t3)), this.advanceAfterBlockEnd(), e9;
  }, i2.parseMacro = function() {
    var e9 = this.peekToken();
    this.skipSymbol("macro") || this.fail("expected macro");
    var t3 = this.parsePrimary(true), n3 = this.parseSignature(), r3 = new hr.Macro(e9.lineno, e9.colno, t3, n3);
    return this.advanceAfterBlockEnd(e9.value), r3.body = this.parseUntilBlocks("endmacro"), this.advanceAfterBlockEnd(), r3;
  }, i2.parseCall = function() {
    var e9 = this.peekToken();
    this.skipSymbol("call") || this.fail("expected call");
    var t3 = this.parseSignature(true) || new hr.NodeList(), n3 = this.parsePrimary();
    this.advanceAfterBlockEnd(e9.value);
    var r3 = this.parseUntilBlocks("endcall");
    this.advanceAfterBlockEnd();
    var i3 = new hr.Symbol(e9.lineno, e9.colno, "caller"), s2 = new hr.Caller(e9.lineno, e9.colno, i3, t3, r3), a2 = n3.args.children;
    return a2[a2.length - 1] instanceof hr.KeywordArgs || a2.push(new hr.KeywordArgs()), a2[a2.length - 1].addChild(new hr.Pair(e9.lineno, e9.colno, i3, s2)), new hr.Output(e9.lineno, e9.colno, [n3]);
  }, i2.parseWithContext = function() {
    var e9 = this.peekToken(), t3 = null;
    return this.skipSymbol("with") ? t3 = true : this.skipSymbol("without") && (t3 = false), null !== t3 && (this.skipSymbol("context") || this.fail("parseFrom: expected context after with/without", e9.lineno, e9.colno)), t3;
  }, i2.parseImport = function() {
    var e9 = this.peekToken();
    this.skipSymbol("import") || this.fail("parseImport: expected import", e9.lineno, e9.colno);
    var t3 = this.parseExpression();
    this.skipSymbol("as") || this.fail('parseImport: expected "as" keyword', e9.lineno, e9.colno);
    var n3 = this.parseExpression(), r3 = this.parseWithContext(), i3 = new hr.Import(e9.lineno, e9.colno, t3, n3, r3);
    return this.advanceAfterBlockEnd(e9.value), i3;
  }, i2.parseFrom = function() {
    var e9 = this.peekToken();
    this.skipSymbol("from") || this.fail("parseFrom: expected from");
    var t3 = this.parseExpression();
    this.skipSymbol("import") || this.fail("parseFrom: expected import", e9.lineno, e9.colno);
    for (var n3, r3 = new hr.NodeList(); ; ) {
      var i3 = this.peekToken();
      if (i3.type === Vt.TOKEN_BLOCK_END) {
        r3.children.length || this.fail("parseFrom: Expected at least one import name", e9.lineno, e9.colno), "-" === i3.value.charAt(0) && (this.dropLeadingWhitespace = true), this.nextToken();
        break;
      }
      r3.children.length > 0 && !this.skip(Vt.TOKEN_COMMA) && this.fail("parseFrom: expected comma", e9.lineno, e9.colno);
      var s2 = this.parsePrimary();
      if ("_" === s2.value.charAt(0) && this.fail("parseFrom: names starting with an underscore cannot be imported", s2.lineno, s2.colno), this.skipSymbol("as")) {
        var a2 = this.parsePrimary();
        r3.addChild(new hr.Pair(s2.lineno, s2.colno, s2, a2));
      } else r3.addChild(s2);
      n3 = this.parseWithContext();
    }
    return new hr.FromImport(e9.lineno, e9.colno, t3, r3, n3);
  }, i2.parseBlock = function() {
    var e9 = this.peekToken();
    this.skipSymbol("block") || this.fail("parseBlock: expected block", e9.lineno, e9.colno);
    var t3 = new hr.Block(e9.lineno, e9.colno);
    t3.name = this.parsePrimary(), t3.name instanceof hr.Symbol || this.fail("parseBlock: variable name expected", e9.lineno, e9.colno), this.advanceAfterBlockEnd(e9.value), t3.body = this.parseUntilBlocks("endblock"), this.skipSymbol("endblock"), this.skipSymbol(t3.name.value);
    var n3 = this.peekToken();
    return n3 || this.fail("parseBlock: expected endblock, got end of file"), this.advanceAfterBlockEnd(n3.value), t3;
  }, i2.parseExtends = function() {
    var e9 = "extends", t3 = this.peekToken();
    this.skipSymbol(e9) || this.fail("parseTemplateRef: expected extends");
    var n3 = new hr.Extends(t3.lineno, t3.colno);
    return n3.template = this.parseExpression(), this.advanceAfterBlockEnd(t3.value), n3;
  }, i2.parseInclude = function() {
    var e9 = "include", t3 = this.peekToken();
    this.skipSymbol(e9) || this.fail("parseInclude: expected include");
    var n3 = new hr.Include(t3.lineno, t3.colno);
    return n3.template = this.parseExpression(), this.skipSymbol("ignore") && this.skipSymbol("missing") && (n3.ignoreMissing = true), this.advanceAfterBlockEnd(t3.value), n3;
  }, i2.parseIf = function() {
    var e9, t3 = this.peekToken();
    this.skipSymbol("if") || this.skipSymbol("elif") || this.skipSymbol("elseif") ? e9 = new hr.If(t3.lineno, t3.colno) : this.skipSymbol("ifAsync") ? e9 = new hr.IfAsync(t3.lineno, t3.colno) : this.fail("parseIf: expected if, elif, or elseif", t3.lineno, t3.colno), e9.cond = this.parseExpression(), this.advanceAfterBlockEnd(t3.value), e9.body = this.parseUntilBlocks("elif", "elseif", "else", "endif");
    var n3 = this.peekToken();
    switch (n3 && n3.value) {
      case "elseif":
      case "elif":
        e9.else_ = this.parseIf();
        break;
      case "else":
        this.advanceAfterBlockEnd(), e9.else_ = this.parseUntilBlocks("endif"), this.advanceAfterBlockEnd();
        break;
      case "endif":
        e9.else_ = null, this.advanceAfterBlockEnd();
        break;
      default:
        this.fail("parseIf: expected elif, else, or endif, got end of file");
    }
    return e9;
  }, i2.parseSet = function() {
    var e9 = this.peekToken();
    this.skipSymbol("set") || this.fail("parseSet: expected set", e9.lineno, e9.colno);
    for (var t3, n3 = new hr.Set(e9.lineno, e9.colno, []); (t3 = this.parsePrimary()) && (n3.targets.push(t3), this.skip(Vt.TOKEN_COMMA)); ) ;
    return this.skipValue(Vt.TOKEN_OPERATOR, "=") ? (n3.value = this.parseExpression(), this.advanceAfterBlockEnd(e9.value)) : this.skip(Vt.TOKEN_BLOCK_END) ? (n3.body = new hr.Capture(e9.lineno, e9.colno, this.parseUntilBlocks("endset")), n3.value = null, this.advanceAfterBlockEnd()) : this.fail("parseSet: expected = or block end in set tag", e9.lineno, e9.colno), n3;
  }, i2.parseSwitch = function() {
    var e9 = "switch", t3 = "endswitch", n3 = "case", r3 = "default", i3 = this.peekToken();
    this.skipSymbol(e9) || this.skipSymbol(n3) || this.skipSymbol(r3) || this.fail('parseSwitch: expected "switch," "case" or "default"', i3.lineno, i3.colno);
    var s2 = this.parseExpression();
    this.advanceAfterBlockEnd(e9), this.parseUntilBlocks(n3, r3, t3);
    var a2, o2 = this.peekToken(), c2 = [];
    do {
      this.skipSymbol(n3);
      var l2 = this.parseExpression();
      this.advanceAfterBlockEnd(e9);
      var h2 = this.parseUntilBlocks(n3, r3, t3);
      c2.push(new hr.Case(o2.line, o2.col, l2, h2)), o2 = this.peekToken();
    } while (o2 && o2.value === n3);
    switch (o2.value) {
      case r3:
        this.advanceAfterBlockEnd(), a2 = this.parseUntilBlocks(t3), this.advanceAfterBlockEnd();
        break;
      case t3:
        this.advanceAfterBlockEnd();
        break;
      default:
        this.fail('parseSwitch: expected "case," "default" or "endswitch," got EOF.');
    }
    return new hr.Switch(i3.lineno, i3.colno, s2, c2, a2);
  }, i2.parseStatement = function() {
    var e9 = this.peekToken();
    if (e9.type !== Vt.TOKEN_SYMBOL && this.fail("tag name expected", e9.lineno, e9.colno), this.breakOnBlocks && -1 !== je.indexOf(this.breakOnBlocks, e9.value)) return null;
    switch (e9.value) {
      case "raw":
        return this.parseRaw();
      case "verbatim":
        return this.parseRaw("verbatim");
      case "if":
      case "ifAsync":
        return this.parseIf();
      case "for":
      case "asyncEach":
      case "asyncAll":
        return this.parseFor();
      case "block":
        return this.parseBlock();
      case "extends":
        return this.parseExtends();
      case "include":
        return this.parseInclude();
      case "set":
        return this.parseSet();
      case "macro":
        return this.parseMacro();
      case "call":
        return this.parseCall();
      case "import":
        return this.parseImport();
      case "from":
        return this.parseFrom();
      case "filter":
        return this.parseFilterStatement();
      case "switch":
        return this.parseSwitch();
      default:
        if (this.extensions.length) for (var t3 = 0; t3 < this.extensions.length; t3++) {
          var n3 = this.extensions[t3];
          if (-1 !== je.indexOf(n3.tags || [], e9.value)) return n3.parse(this, hr, Vt);
        }
        this.fail("unknown block tag: " + e9.value, e9.lineno, e9.colno);
    }
  }, i2.parseRaw = function(e9) {
    for (var t3 = "end" + (e9 = e9 || "raw"), n3 = new RegExp("([\\s\\S]*?){%\\s*(" + e9 + "|" + t3 + ")\\s*(?=%})%}"), r3 = 1, i3 = "", s2 = null, a2 = this.advanceAfterBlockEnd(); (s2 = this.tokens._extractRegex(n3)) && r3 > 0; ) {
      var o2 = s2[0], c2 = s2[1], l2 = s2[2];
      l2 === e9 ? r3 += 1 : l2 === t3 && (r3 -= 1), 0 === r3 ? (i3 += c2, this.tokens.backN(o2.length - c2.length)) : i3 += o2;
    }
    return new hr.Output(a2.lineno, a2.colno, [new hr.TemplateData(a2.lineno, a2.colno, i3)]);
  }, i2.parsePostfix = function(e9) {
    for (var t3, n3 = this.peekToken(); n3; ) {
      if (n3.type === Vt.TOKEN_LEFT_PAREN) e9 = new hr.FunCall(n3.lineno, n3.colno, e9, this.parseSignature());
      else if (n3.type === Vt.TOKEN_LEFT_BRACKET) (t3 = this.parseAggregate()).children.length > 1 && this.fail("invalid index"), e9 = new hr.LookupVal(n3.lineno, n3.colno, e9, t3.children[0]);
      else {
        if (n3.type !== Vt.TOKEN_OPERATOR || "." !== n3.value) break;
        this.nextToken();
        var r3 = this.nextToken();
        r3.type !== Vt.TOKEN_SYMBOL && this.fail("expected name as lookup value, got " + r3.value, r3.lineno, r3.colno), t3 = new hr.Literal(r3.lineno, r3.colno, r3.value), e9 = new hr.LookupVal(n3.lineno, n3.colno, e9, t3);
      }
      n3 = this.peekToken();
    }
    return e9;
  }, i2.parseExpression = function() {
    return this.parseInlineIf();
  }, i2.parseInlineIf = function() {
    var e9 = this.parseOr();
    if (this.skipSymbol("if")) {
      var t3 = this.parseOr(), n3 = e9;
      (e9 = new hr.InlineIf(e9.lineno, e9.colno)).body = n3, e9.cond = t3, this.skipSymbol("else") ? e9.else_ = this.parseOr() : e9.else_ = null;
    }
    return e9;
  }, i2.parseOr = function() {
    for (var e9 = this.parseAnd(); this.skipSymbol("or"); ) {
      var t3 = this.parseAnd();
      e9 = new hr.Or(e9.lineno, e9.colno, e9, t3);
    }
    return e9;
  }, i2.parseAnd = function() {
    for (var e9 = this.parseNot(); this.skipSymbol("and"); ) {
      var t3 = this.parseNot();
      e9 = new hr.And(e9.lineno, e9.colno, e9, t3);
    }
    return e9;
  }, i2.parseNot = function() {
    var e9 = this.peekToken();
    return this.skipSymbol("not") ? new hr.Not(e9.lineno, e9.colno, this.parseNot()) : this.parseIn();
  }, i2.parseIn = function() {
    for (var e9 = this.parseIs(); ; ) {
      var t3 = this.nextToken();
      if (!t3) break;
      var n3 = t3.type === Vt.TOKEN_SYMBOL && "not" === t3.value;
      if (n3 || this.pushToken(t3), !this.skipSymbol("in")) {
        n3 && this.pushToken(t3);
        break;
      }
      var r3 = this.parseIs();
      e9 = new hr.In(e9.lineno, e9.colno, e9, r3), n3 && (e9 = new hr.Not(e9.lineno, e9.colno, e9));
    }
    return e9;
  }, i2.parseIs = function() {
    var e9 = this.parseCompare();
    if (this.skipSymbol("is")) {
      var t3 = this.skipSymbol("not"), n3 = this.parseCompare();
      e9 = new hr.Is(e9.lineno, e9.colno, e9, n3), t3 && (e9 = new hr.Not(e9.lineno, e9.colno, e9));
    }
    return e9;
  }, i2.parseCompare = function() {
    for (var e9 = ["==", "===", "!=", "!==", "<", ">", "<=", ">="], t3 = this.parseConcat(), n3 = []; ; ) {
      var r3 = this.nextToken();
      if (!r3) break;
      if (-1 === e9.indexOf(r3.value)) {
        this.pushToken(r3);
        break;
      }
      n3.push(new hr.CompareOperand(r3.lineno, r3.colno, this.parseConcat(), r3.value));
    }
    return n3.length ? new hr.Compare(n3[0].lineno, n3[0].colno, t3, n3) : t3;
  }, i2.parseConcat = function() {
    for (var e9 = this.parseAdd(); this.skipValue(Vt.TOKEN_TILDE, "~"); ) {
      var t3 = this.parseAdd();
      e9 = new hr.Concat(e9.lineno, e9.colno, e9, t3);
    }
    return e9;
  }, i2.parseAdd = function() {
    for (var e9 = this.parseSub(); this.skipValue(Vt.TOKEN_OPERATOR, "+"); ) {
      var t3 = this.parseSub();
      e9 = new hr.Add(e9.lineno, e9.colno, e9, t3);
    }
    return e9;
  }, i2.parseSub = function() {
    for (var e9 = this.parseMul(); this.skipValue(Vt.TOKEN_OPERATOR, "-"); ) {
      var t3 = this.parseMul();
      e9 = new hr.Sub(e9.lineno, e9.colno, e9, t3);
    }
    return e9;
  }, i2.parseMul = function() {
    for (var e9 = this.parseDiv(); this.skipValue(Vt.TOKEN_OPERATOR, "*"); ) {
      var t3 = this.parseDiv();
      e9 = new hr.Mul(e9.lineno, e9.colno, e9, t3);
    }
    return e9;
  }, i2.parseDiv = function() {
    for (var e9 = this.parseFloorDiv(); this.skipValue(Vt.TOKEN_OPERATOR, "/"); ) {
      var t3 = this.parseFloorDiv();
      e9 = new hr.Div(e9.lineno, e9.colno, e9, t3);
    }
    return e9;
  }, i2.parseFloorDiv = function() {
    for (var e9 = this.parseMod(); this.skipValue(Vt.TOKEN_OPERATOR, "//"); ) {
      var t3 = this.parseMod();
      e9 = new hr.FloorDiv(e9.lineno, e9.colno, e9, t3);
    }
    return e9;
  }, i2.parseMod = function() {
    for (var e9 = this.parsePow(); this.skipValue(Vt.TOKEN_OPERATOR, "%"); ) {
      var t3 = this.parsePow();
      e9 = new hr.Mod(e9.lineno, e9.colno, e9, t3);
    }
    return e9;
  }, i2.parsePow = function() {
    for (var e9 = this.parseUnary(); this.skipValue(Vt.TOKEN_OPERATOR, "**"); ) {
      var t3 = this.parseUnary();
      e9 = new hr.Pow(e9.lineno, e9.colno, e9, t3);
    }
    return e9;
  }, i2.parseUnary = function(e9) {
    var t3, n3 = this.peekToken();
    return t3 = this.skipValue(Vt.TOKEN_OPERATOR, "-") ? new hr.Neg(n3.lineno, n3.colno, this.parseUnary(true)) : this.skipValue(Vt.TOKEN_OPERATOR, "+") ? new hr.Pos(n3.lineno, n3.colno, this.parseUnary(true)) : this.parsePrimary(), e9 || (t3 = this.parseFilter(t3)), t3;
  }, i2.parsePrimary = function(e9) {
    var t3, n3 = this.nextToken(), r3 = null;
    if (n3 ? n3.type === Vt.TOKEN_STRING ? t3 = n3.value : n3.type === Vt.TOKEN_INT ? t3 = parseInt(n3.value, 10) : n3.type === Vt.TOKEN_FLOAT ? t3 = parseFloat(n3.value) : n3.type === Vt.TOKEN_BOOLEAN ? "true" === n3.value ? t3 = true : "false" === n3.value ? t3 = false : this.fail("invalid boolean: " + n3.value, n3.lineno, n3.colno) : n3.type === Vt.TOKEN_NONE ? t3 = null : n3.type === Vt.TOKEN_REGEX && (t3 = new RegExp(n3.value.body, n3.value.flags)) : this.fail("expected expression, got end of file"), void 0 !== t3 ? r3 = new hr.Literal(n3.lineno, n3.colno, t3) : n3.type === Vt.TOKEN_SYMBOL ? r3 = new hr.Symbol(n3.lineno, n3.colno, n3.value) : (this.pushToken(n3), r3 = this.parseAggregate()), e9 || (r3 = this.parsePostfix(r3)), r3) return r3;
    throw this.error("unexpected token: " + n3.value, n3.lineno, n3.colno);
  }, i2.parseFilterName = function() {
    for (var e9 = this.expect(Vt.TOKEN_SYMBOL), t3 = e9.value; this.skipValue(Vt.TOKEN_OPERATOR, "."); ) t3 += "." + this.expect(Vt.TOKEN_SYMBOL).value;
    return new hr.Symbol(e9.lineno, e9.colno, t3);
  }, i2.parseFilterArgs = function(e9) {
    return this.peekToken().type === Vt.TOKEN_LEFT_PAREN ? this.parsePostfix(e9).args.children : [];
  }, i2.parseFilter = function(e9) {
    for (; this.skip(Vt.TOKEN_PIPE); ) {
      var t3 = this.parseFilterName();
      e9 = new hr.Filter(t3.lineno, t3.colno, t3, new hr.NodeList(t3.lineno, t3.colno, [e9].concat(this.parseFilterArgs(e9))));
    }
    return e9;
  }, i2.parseFilterStatement = function() {
    var e9 = this.peekToken();
    this.skipSymbol("filter") || this.fail("parseFilterStatement: expected filter");
    var t3 = this.parseFilterName(), n3 = this.parseFilterArgs(t3);
    this.advanceAfterBlockEnd(e9.value);
    var r3 = new hr.Capture(t3.lineno, t3.colno, this.parseUntilBlocks("endfilter"));
    this.advanceAfterBlockEnd();
    var i3 = new hr.Filter(t3.lineno, t3.colno, t3, new hr.NodeList(t3.lineno, t3.colno, [r3].concat(n3)));
    return new hr.Output(t3.lineno, t3.colno, [i3]);
  }, i2.parseAggregate = function() {
    var e9, t3 = this.nextToken();
    switch (t3.type) {
      case Vt.TOKEN_LEFT_PAREN:
        e9 = new hr.Group(t3.lineno, t3.colno);
        break;
      case Vt.TOKEN_LEFT_BRACKET:
        e9 = new hr.Array(t3.lineno, t3.colno);
        break;
      case Vt.TOKEN_LEFT_CURLY:
        e9 = new hr.Dict(t3.lineno, t3.colno);
        break;
      default:
        return null;
    }
    for (; ; ) {
      var n3 = this.peekToken().type;
      if (n3 === Vt.TOKEN_RIGHT_PAREN || n3 === Vt.TOKEN_RIGHT_BRACKET || n3 === Vt.TOKEN_RIGHT_CURLY) {
        this.nextToken();
        break;
      }
      if (e9.children.length > 0 && (this.skip(Vt.TOKEN_COMMA) || this.fail("parseAggregate: expected comma after expression", t3.lineno, t3.colno)), e9 instanceof hr.Dict) {
        var r3 = this.parsePrimary();
        this.skip(Vt.TOKEN_COLON) || this.fail("parseAggregate: expected colon after dict key", t3.lineno, t3.colno);
        var i3 = this.parseExpression();
        e9.addChild(new hr.Pair(r3.lineno, r3.colno, r3, i3));
      } else {
        var s2 = this.parseExpression();
        e9.addChild(s2);
      }
    }
    return e9;
  }, i2.parseSignature = function(e9, t3) {
    var n3 = this.peekToken();
    if (!t3 && n3.type !== Vt.TOKEN_LEFT_PAREN) {
      if (e9) return null;
      this.fail("expected arguments", n3.lineno, n3.colno);
    }
    n3.type === Vt.TOKEN_LEFT_PAREN && (n3 = this.nextToken());
    for (var r3 = new hr.NodeList(n3.lineno, n3.colno), i3 = new hr.KeywordArgs(n3.lineno, n3.colno), s2 = false; ; ) {
      if (n3 = this.peekToken(), !t3 && n3.type === Vt.TOKEN_RIGHT_PAREN) {
        this.nextToken();
        break;
      }
      if (t3 && n3.type === Vt.TOKEN_BLOCK_END) break;
      if (s2 && !this.skip(Vt.TOKEN_COMMA)) this.fail("parseSignature: expected comma after expression", n3.lineno, n3.colno);
      else {
        var a2 = this.parseExpression();
        this.skipValue(Vt.TOKEN_OPERATOR, "=") ? i3.addChild(new hr.Pair(a2.lineno, a2.colno, a2, this.parseExpression())) : r3.addChild(a2);
      }
      s2 = true;
    }
    return i3.children.length && r3.addChild(i3), r3;
  }, i2.parseUntilBlocks = function() {
    for (var e9 = this.breakOnBlocks, t3 = arguments.length, n3 = new Array(t3), r3 = 0; r3 < t3; r3++) n3[r3] = arguments[r3];
    this.breakOnBlocks = n3;
    var i3 = this.parse();
    return this.breakOnBlocks = e9, i3;
  }, i2.parseNodes = function() {
    for (var e9, t3 = []; e9 = this.nextToken(); ) if (e9.type === Vt.TOKEN_DATA) {
      var n3 = e9.value, r3 = this.peekToken(), i3 = r3 && r3.value;
      this.dropLeadingWhitespace && (n3 = n3.replace(/^\s*/, ""), this.dropLeadingWhitespace = false), r3 && (r3.type === Vt.TOKEN_BLOCK_START && "-" === i3.charAt(i3.length - 1) || r3.type === Vt.TOKEN_VARIABLE_START && "-" === i3.charAt(this.tokens.tags.VARIABLE_START.length) || r3.type === Vt.TOKEN_COMMENT && "-" === i3.charAt(this.tokens.tags.COMMENT_START.length)) && (n3 = n3.replace(/\s*$/, "")), t3.push(new hr.Output(e9.lineno, e9.colno, [new hr.TemplateData(e9.lineno, e9.colno, n3)]));
    } else if (e9.type === Vt.TOKEN_BLOCK_START) {
      this.dropLeadingWhitespace = false;
      var s2 = this.parseStatement();
      if (!s2) break;
      t3.push(s2);
    } else if (e9.type === Vt.TOKEN_VARIABLE_START) {
      var a2 = this.parseExpression();
      this.dropLeadingWhitespace = false, this.advanceAfterVariableEnd(), t3.push(new hr.Output(e9.lineno, e9.colno, [a2]));
    } else e9.type === Vt.TOKEN_COMMENT ? this.dropLeadingWhitespace = "-" === e9.value.charAt(e9.value.length - this.tokens.tags.COMMENT_END.length - 1) : this.fail("Unexpected token at top-level: " + e9.type, e9.lineno, e9.colno);
    return t3;
  }, i2.parse = function() {
    return new hr.NodeList(0, 0, this.parseNodes());
  }, i2.parseAsRoot = function() {
    return new hr.Root(0, 0, this.parseNodes());
  }, r2;
})(en.Obj), fr = { parse: function(e8, t2, n2) {
  var r2 = new pr(Vt.lex(e8, n2));
  return void 0 !== t2 && (r2.extensions = t2), r2.parseAsRoot();
}, Parser: pr }, dr = 0;
function Er() {
  return "hole_" + dr++;
}
function mr(e8, t2) {
  for (var n2 = null, r2 = 0; r2 < e8.length; r2++) {
    var i2 = t2(e8[r2]);
    i2 !== e8[r2] && (n2 || (n2 = e8.slice()), n2[r2] = i2);
  }
  return n2 || e8;
}
function Tr(e8, t2, n2) {
  if (!(e8 instanceof hr.Node)) return e8;
  if (!n2) {
    var r2 = t2(e8);
    if (r2 && r2 !== e8) return r2;
  }
  if (e8 instanceof hr.NodeList) {
    var i2 = mr(e8.children, (function(e9) {
      return Tr(e9, t2, n2);
    }));
    i2 !== e8.children && (e8 = new hr[e8.typename](e8.lineno, e8.colno, i2));
  } else if (e8 instanceof hr.CallExtension) {
    var s2 = Tr(e8.args, t2, n2), a2 = mr(e8.contentArgs, (function(e9) {
      return Tr(e9, t2, n2);
    }));
    s2 === e8.args && a2 === e8.contentArgs || (e8 = new hr[e8.typename](e8.extName, e8.prop, s2, a2));
  } else {
    var o2 = e8.fields.map((function(t3) {
      return e8[t3];
    })), c2 = mr(o2, (function(e9) {
      return Tr(e9, t2, n2);
    }));
    c2 !== o2 && (e8 = new hr[e8.typename](e8.lineno, e8.colno), c2.forEach((function(t3, n3) {
      e8[e8.fields[n3]] = t3;
    })));
  }
  return n2 && t2(e8) || e8;
}
function _r(e8, t2) {
  return Tr(e8, t2, true);
}
function Ar(e8, t2, n2) {
  var r2 = [], i2 = _r(n2 ? e8[n2] : e8, (function(e9) {
    var n3;
    return e9 instanceof hr.Block ? e9 : ((e9 instanceof hr.Filter && -1 !== je.indexOf(t2, e9.name.value) || e9 instanceof hr.CallExtensionAsync) && (n3 = new hr.Symbol(e9.lineno, e9.colno, Er()), r2.push(new hr.FilterAsync(e9.lineno, e9.colno, e9.name, e9.args, n3))), n3);
  }));
  return n2 ? e8[n2] = i2 : e8 = i2, r2.length ? (r2.push(e8), new hr.NodeList(e8.lineno, e8.colno, r2)) : e8;
}
function gr(e8, t2) {
  return (function(e9) {
    return _r(e9, (function(e10) {
      if (e10 instanceof hr.If || e10 instanceof hr.For) {
        var t3 = false;
        if (Tr(e10, (function(e11) {
          if (e11 instanceof hr.FilterAsync || e11 instanceof hr.IfAsync || e11 instanceof hr.AsyncEach || e11 instanceof hr.AsyncAll || e11 instanceof hr.CallExtensionAsync) return t3 = true, e11;
        })), t3) {
          if (e10 instanceof hr.If) return new hr.IfAsync(e10.lineno, e10.colno, e10.cond, e10.body, e10.else_);
          if (e10 instanceof hr.For && !(e10 instanceof hr.AsyncAll)) return new hr.AsyncEach(e10.lineno, e10.colno, e10.arr, e10.name, e10.body, e10.else_);
        }
      }
    }));
  })((function(e9) {
    return Tr(e9, (function(e10) {
      if (e10 instanceof hr.Block) {
        var t3 = false, n2 = Er();
        e10.body = Tr(e10.body, (function(e11) {
          if (e11 instanceof hr.FunCall && "super" === e11.name.value) return t3 = true, new hr.Symbol(e11.lineno, e11.colno, n2);
        })), t3 && e10.body.children.unshift(new hr.Super(0, 0, e10.name, new hr.Symbol(0, 0, n2)));
      }
    }));
  })((function(e9, t3) {
    return _r(e9, (function(e10) {
      return e10 instanceof hr.Output ? Ar(e10, t3) : e10 instanceof hr.Set ? Ar(e10, t3, "value") : e10 instanceof hr.For ? Ar(e10, t3, "arr") : e10 instanceof hr.If ? Ar(e10, t3, "cond") : e10 instanceof hr.CallExtension ? Ar(e10, t3, "args") : void 0;
    }));
  })(e8, t2)));
}
var Nr = { transform: function(e8, t2) {
  return gr(e8, t2 || []);
} }, Cr = Array.from, Ir = "function" == typeof Symbol && Symbol.iterator && "function" == typeof Cr, Sr = (function() {
  function e8(e9, t3) {
    this.variables = /* @__PURE__ */ Object.create(null), this.parent = e9, this.topLevel = false, this.isolateWrites = t3;
  }
  var t2 = e8.prototype;
  return t2.set = function(e9, t3, n2) {
    var r2 = e9.split("."), i2 = this.variables, s2 = this;
    if (n2 && (s2 = this.resolve(r2[0], true))) s2.set(e9, t3);
    else {
      for (var a2 = 0; a2 < r2.length - 1; a2++) {
        var o2 = r2[a2];
        i2[o2] || (i2[o2] = {}), i2 = i2[o2];
      }
      i2[r2[r2.length - 1]] = t3;
    }
  }, t2.get = function(e9) {
    var t3 = this.variables[e9];
    return void 0 !== t3 ? t3 : null;
  }, t2.lookup = function(e9) {
    var t3 = this.parent, n2 = this.variables[e9];
    return void 0 !== n2 ? n2 : t3 && t3.lookup(e9);
  }, t2.resolve = function(e9, t3) {
    var n2 = t3 && this.isolateWrites ? void 0 : this.parent;
    return void 0 !== this.variables[e9] ? this : n2 && n2.resolve(e9);
  }, t2.push = function(t3) {
    return new e8(this, t3);
  }, t2.pop = function() {
    return this.parent;
  }, e8;
})();
function br(e8) {
  return e8 && Object.prototype.hasOwnProperty.call(e8, "__keywords");
}
function Or(e8) {
  var t2 = e8.length;
  if (t2) {
    var n2 = e8[t2 - 1];
    if (br(n2)) return n2;
  }
  return {};
}
function yr(e8) {
  var t2 = e8.length;
  return 0 === t2 ? 0 : br(e8[t2 - 1]) ? t2 - 1 : t2;
}
function Lr(e8) {
  if ("string" != typeof e8) return e8;
  this.val = e8, Object.defineProperty(this, "length", { writable: true, configurable: true, value: e8.length });
}
Lr.prototype = Object.create(String.prototype, { length: { writable: true, configurable: true, value: 0 } }), Lr.prototype.valueOf = function() {
  return this.val;
}, Lr.prototype.toString = function() {
  return this.val;
};
var kr = { Frame: Sr, makeMacro: function(e8, t2, n2) {
  return function() {
    for (var r2 = arguments.length, i2 = new Array(r2), s2 = 0; s2 < r2; s2++) i2[s2] = arguments[s2];
    var a2, o2 = yr(i2), c2 = Or(i2);
    if (o2 > e8.length) a2 = i2.slice(0, e8.length), i2.slice(a2.length, o2).forEach((function(e9, n3) {
      n3 < t2.length && (c2[t2[n3]] = e9);
    })), a2.push(c2);
    else if (o2 < e8.length) {
      a2 = i2.slice(0, o2);
      for (var l2 = o2; l2 < e8.length; l2++) {
        var h2 = e8[l2];
        a2.push(c2[h2]), delete c2[h2];
      }
      a2.push(c2);
    } else a2 = i2;
    return n2.apply(this, a2);
  };
}, makeKeywordArgs: function(e8) {
  return e8.__keywords = true, e8;
}, numArgs: yr, suppressValue: function(e8, t2) {
  return "object" == typeof (e8 = null != e8 ? e8 : "") ? new Lr(JSON.stringify(e8).toString()) : (!t2 || e8 instanceof Lr || (e8 = je.escape(e8.toString())), e8);
}, ensureDefined: function(e8, t2, n2) {
  if (null == e8) throw new je.TemplateError("attempted to output null or undefined value", t2 + 1, n2 + 1);
  return e8;
}, memberLookup: function(e8, t2) {
  if (null != e8) return "function" == typeof e8[t2] ? function() {
    for (var n2 = arguments.length, r2 = new Array(n2), i2 = 0; i2 < n2; i2++) r2[i2] = arguments[i2];
    return e8[t2].apply(e8, r2);
  } : e8[t2];
}, contextOrFrameLookup: function(e8, t2, n2) {
  var r2 = t2.lookup(n2);
  return void 0 !== r2 ? r2 : e8.lookup(n2);
}, callWrap: function(e8, t2, n2, r2) {
  if (!e8) throw new Error("Unable to call `" + t2 + "`, which is undefined or falsey");
  if ("function" != typeof e8) throw new Error("Unable to call `" + t2 + "`, which is not a function");
  return e8.apply(n2, r2);
}, handleError: function(e8, t2, n2) {
  return e8.lineno ? e8 : new je.TemplateError(e8, t2, n2);
}, isArray: je.isArray, keys: je.keys, SafeString: Lr, copySafeness: function(e8, t2) {
  return e8 instanceof Lr ? new Lr(t2) : t2.toString();
}, markSafe: function(e8) {
  var t2 = typeof e8;
  return "string" === t2 ? new Lr(e8) : "function" !== t2 ? e8 : function(t3) {
    var n2 = e8.apply(this, arguments);
    return "string" == typeof n2 ? new Lr(n2) : n2;
  };
}, asyncEach: function(e8, t2, n2, r2) {
  if (je.isArray(e8)) {
    var i2 = e8.length;
    je.asyncIter(e8, (function(e9, r3, s2) {
      switch (t2) {
        case 1:
          n2(e9, r3, i2, s2);
          break;
        case 2:
          n2(e9[0], e9[1], r3, i2, s2);
          break;
        case 3:
          n2(e9[0], e9[1], e9[2], r3, i2, s2);
          break;
        default:
          e9.push(r3, i2, s2), n2.apply(this, e9);
      }
    }), r2);
  } else je.asyncFor(e8, (function(e9, t3, r3, i3, s2) {
    n2(e9, t3, r3, i3, s2);
  }), r2);
}, asyncAll: function(e8, t2, n2, r2) {
  var i2, s2, a2 = 0;
  function o2(e9, t3) {
    a2++, s2[e9] = t3, a2 === i2 && r2(null, s2.join(""));
  }
  if (je.isArray(e8)) if (i2 = e8.length, s2 = new Array(i2), 0 === i2) r2(null, "");
  else for (var c2 = 0; c2 < e8.length; c2++) {
    var l2 = e8[c2];
    switch (t2) {
      case 1:
        n2(l2, c2, i2, o2);
        break;
      case 2:
        n2(l2[0], l2[1], c2, i2, o2);
        break;
      case 3:
        n2(l2[0], l2[1], l2[2], c2, i2, o2);
        break;
      default:
        l2.push(c2, i2, o2), n2.apply(this, l2);
    }
  }
  else {
    var h2 = je.keys(e8 || {});
    if (i2 = h2.length, s2 = new Array(i2), 0 === i2) r2(null, "");
    else for (var u2 = 0; u2 < h2.length; u2++) {
      var p2 = h2[u2];
      n2(p2, e8[p2], u2, i2, o2);
    }
  }
}, inOperator: je.inOperator, fromIterator: function(e8) {
  return "object" != typeof e8 || null === e8 || je.isArray(e8) ? e8 : Ir && Symbol.iterator in e8 ? Cr(e8) : e8;
} };
function vr(e8, t2) {
  return vr = Object.setPrototypeOf || function(e9, t3) {
    return e9.__proto__ = t3, e9;
  }, vr(e8, t2);
}
kr.Frame, kr.makeMacro, kr.makeKeywordArgs, kr.numArgs, kr.suppressValue, kr.ensureDefined, kr.memberLookup, kr.contextOrFrameLookup, kr.callWrap, kr.handleError, kr.isArray, kr.keys, kr.SafeString, kr.copySafeness, kr.markSafe, kr.asyncEach, kr.asyncAll, kr.inOperator, kr.fromIterator;
var Dr = je.TemplateError, Rr = kr.Frame, Mr = { "==": "==", "===": "===", "!=": "!=", "!==": "!==", "<": "<", ">": ">", "<=": "<=", ">=": ">=" }, Pr = (function(e8) {
  var t2, n2;
  function r2() {
    return e8.apply(this, arguments) || this;
  }
  n2 = e8, (t2 = r2).prototype = Object.create(n2.prototype), t2.prototype.constructor = t2, vr(t2, n2);
  var i2 = r2.prototype;
  return i2.init = function(e9, t3) {
    this.templateName = e9, this.codebuf = [], this.lastId = 0, this.buffer = null, this.bufferStack = [], this._scopeClosers = "", this.inBlock = false, this.throwOnUndefined = t3;
  }, i2.fail = function(e9, t3, n3) {
    throw void 0 !== t3 && (t3 += 1), void 0 !== n3 && (n3 += 1), new Dr(e9, t3, n3);
  }, i2._pushBuffer = function() {
    var e9 = this._tmpid();
    return this.bufferStack.push(this.buffer), this.buffer = e9, this._emit("var " + this.buffer + ' = "";'), e9;
  }, i2._popBuffer = function() {
    this.buffer = this.bufferStack.pop();
  }, i2._emit = function(e9) {
    this.codebuf.push(e9);
  }, i2._emitLine = function(e9) {
    this._emit(e9 + "\n");
  }, i2._emitLines = function() {
    for (var e9 = this, t3 = arguments.length, n3 = new Array(t3), r3 = 0; r3 < t3; r3++) n3[r3] = arguments[r3];
    n3.forEach((function(t4) {
      return e9._emitLine(t4);
    }));
  }, i2._emitFuncBegin = function(e9, t3) {
    this.buffer = "output", this._scopeClosers = "", this._emitLine("function " + t3 + "(env, context, frame, runtime, cb) {"), this._emitLine("var lineno = " + e9.lineno + ";"), this._emitLine("var colno = " + e9.colno + ";"), this._emitLine("var " + this.buffer + ' = "";'), this._emitLine("try {");
  }, i2._emitFuncEnd = function(e9) {
    e9 || this._emitLine("cb(null, " + this.buffer + ");"), this._closeScopeLevels(), this._emitLine("} catch (e) {"), this._emitLine("  cb(runtime.handleError(e, lineno, colno));"), this._emitLine("}"), this._emitLine("}"), this.buffer = null;
  }, i2._addScopeLevel = function() {
    this._scopeClosers += "})";
  }, i2._closeScopeLevels = function() {
    this._emitLine(this._scopeClosers + ";"), this._scopeClosers = "";
  }, i2._withScopedSyntax = function(e9) {
    var t3 = this._scopeClosers;
    this._scopeClosers = "", e9.call(this), this._closeScopeLevels(), this._scopeClosers = t3;
  }, i2._makeCallback = function(e9) {
    var t3 = this._tmpid();
    return "function(" + t3 + (e9 ? "," + e9 : "") + ") {\nif(" + t3 + ") { cb(" + t3 + "); return; }";
  }, i2._tmpid = function() {
    return this.lastId++, "t_" + this.lastId;
  }, i2._templateName = function() {
    return null == this.templateName ? "undefined" : JSON.stringify(this.templateName);
  }, i2._compileChildren = function(e9, t3) {
    var n3 = this;
    e9.children.forEach((function(e10) {
      n3.compile(e10, t3);
    }));
  }, i2._compileAggregate = function(e9, t3, n3, r3) {
    var i3 = this;
    n3 && this._emit(n3), e9.children.forEach((function(e10, n4) {
      n4 > 0 && i3._emit(","), i3.compile(e10, t3);
    })), r3 && this._emit(r3);
  }, i2._compileExpression = function(e9, t3) {
    this.assertType(e9, hr.Literal, hr.Symbol, hr.Group, hr.Array, hr.Dict, hr.FunCall, hr.Caller, hr.Filter, hr.LookupVal, hr.Compare, hr.InlineIf, hr.In, hr.Is, hr.And, hr.Or, hr.Not, hr.Add, hr.Concat, hr.Sub, hr.Mul, hr.Div, hr.FloorDiv, hr.Mod, hr.Pow, hr.Neg, hr.Pos, hr.Compare, hr.NodeList), this.compile(e9, t3);
  }, i2.assertType = function(e9) {
    for (var t3 = arguments.length, n3 = new Array(t3 > 1 ? t3 - 1 : 0), r3 = 1; r3 < t3; r3++) n3[r3 - 1] = arguments[r3];
    n3.some((function(t4) {
      return e9 instanceof t4;
    })) || this.fail("assertType: invalid type: " + e9.typename, e9.lineno, e9.colno);
  }, i2.compileCallExtension = function(e9, t3, n3) {
    var r3 = this, i3 = e9.args, s2 = e9.contentArgs, a2 = "boolean" != typeof e9.autoescape || e9.autoescape;
    if (n3 || this._emit(this.buffer + " += runtime.suppressValue("), this._emit('env.getExtension("' + e9.extName + '")["' + e9.prop + '"]('), this._emit("context"), (i3 || s2) && this._emit(","), i3 && (i3 instanceof hr.NodeList || this.fail("compileCallExtension: arguments must be a NodeList, use `parser.parseSignature`"), i3.children.forEach((function(e10, n4) {
      r3._compileExpression(e10, t3), (n4 !== i3.children.length - 1 || s2.length) && r3._emit(",");
    }))), s2.length && s2.forEach((function(e10, n4) {
      if (n4 > 0 && r3._emit(","), e10) {
        r3._emitLine("function(cb) {"), r3._emitLine("if(!cb) { cb = function(err) { if(err) { throw err; }}}");
        var i4 = r3._pushBuffer();
        r3._withScopedSyntax((function() {
          r3.compile(e10, t3), r3._emitLine("cb(null, " + i4 + ");");
        })), r3._popBuffer(), r3._emitLine("return " + i4 + ";"), r3._emitLine("}");
      } else r3._emit("null");
    })), n3) {
      var o2 = this._tmpid();
      this._emitLine(", " + this._makeCallback(o2)), this._emitLine(this.buffer + " += runtime.suppressValue(" + o2 + ", " + a2 + " && env.opts.autoescape);"), this._addScopeLevel();
    } else this._emit(")"), this._emit(", " + a2 + " && env.opts.autoescape);\n");
  }, i2.compileCallExtensionAsync = function(e9, t3) {
    this.compileCallExtension(e9, t3, true);
  }, i2.compileNodeList = function(e9, t3) {
    this._compileChildren(e9, t3);
  }, i2.compileLiteral = function(e9) {
    if ("string" == typeof e9.value) {
      var t3 = e9.value.replace(/\\/g, "\\\\");
      t3 = (t3 = (t3 = (t3 = (t3 = t3.replace(/"/g, '\\"')).replace(/\n/g, "\\n")).replace(/\r/g, "\\r")).replace(/\t/g, "\\t")).replace(/\u2028/g, "\\u2028"), this._emit('"' + t3 + '"');
    } else null === e9.value ? this._emit("null") : this._emit(e9.value.toString());
  }, i2.compileSymbol = function(e9, t3) {
    var n3 = e9.value, r3 = t3.lookup(n3);
    r3 ? this._emit(r3) : this._emit('runtime.contextOrFrameLookup(context, frame, "' + n3 + '")');
  }, i2.compileGroup = function(e9, t3) {
    this._compileAggregate(e9, t3, "(", ")");
  }, i2.compileArray = function(e9, t3) {
    this._compileAggregate(e9, t3, "[", "]");
  }, i2.compileDict = function(e9, t3) {
    this._compileAggregate(e9, t3, "{", "}");
  }, i2.compilePair = function(e9, t3) {
    var n3 = e9.key, r3 = e9.value;
    n3 instanceof hr.Symbol ? n3 = new hr.Literal(n3.lineno, n3.colno, n3.value) : n3 instanceof hr.Literal && "string" == typeof n3.value || this.fail("compilePair: Dict keys must be strings or names", n3.lineno, n3.colno), this.compile(n3, t3), this._emit(": "), this._compileExpression(r3, t3);
  }, i2.compileInlineIf = function(e9, t3) {
    this._emit("("), this.compile(e9.cond, t3), this._emit("?"), this.compile(e9.body, t3), this._emit(":"), null !== e9.else_ ? this.compile(e9.else_, t3) : this._emit('""'), this._emit(")");
  }, i2.compileIn = function(e9, t3) {
    this._emit("runtime.inOperator("), this.compile(e9.left, t3), this._emit(","), this.compile(e9.right, t3), this._emit(")");
  }, i2.compileIs = function(e9, t3) {
    var n3 = e9.right.name ? e9.right.name.value : e9.right.value;
    this._emit('env.getTest("' + n3 + '").call(context, '), this.compile(e9.left, t3), e9.right.args && (this._emit(","), this.compile(e9.right.args, t3)), this._emit(") === true");
  }, i2._binOpEmitter = function(e9, t3, n3) {
    this.compile(e9.left, t3), this._emit(n3), this.compile(e9.right, t3);
  }, i2.compileOr = function(e9, t3) {
    return this._binOpEmitter(e9, t3, " || ");
  }, i2.compileAnd = function(e9, t3) {
    return this._binOpEmitter(e9, t3, " && ");
  }, i2.compileAdd = function(e9, t3) {
    return this._binOpEmitter(e9, t3, " + ");
  }, i2.compileConcat = function(e9, t3) {
    return this._binOpEmitter(e9, t3, ' + "" + ');
  }, i2.compileSub = function(e9, t3) {
    return this._binOpEmitter(e9, t3, " - ");
  }, i2.compileMul = function(e9, t3) {
    return this._binOpEmitter(e9, t3, " * ");
  }, i2.compileDiv = function(e9, t3) {
    return this._binOpEmitter(e9, t3, " / ");
  }, i2.compileMod = function(e9, t3) {
    return this._binOpEmitter(e9, t3, " % ");
  }, i2.compileNot = function(e9, t3) {
    this._emit("!"), this.compile(e9.target, t3);
  }, i2.compileFloorDiv = function(e9, t3) {
    this._emit("Math.floor("), this.compile(e9.left, t3), this._emit(" / "), this.compile(e9.right, t3), this._emit(")");
  }, i2.compilePow = function(e9, t3) {
    this._emit("Math.pow("), this.compile(e9.left, t3), this._emit(", "), this.compile(e9.right, t3), this._emit(")");
  }, i2.compileNeg = function(e9, t3) {
    this._emit("-"), this.compile(e9.target, t3);
  }, i2.compilePos = function(e9, t3) {
    this._emit("+"), this.compile(e9.target, t3);
  }, i2.compileCompare = function(e9, t3) {
    var n3 = this;
    this.compile(e9.expr, t3), e9.ops.forEach((function(e10) {
      n3._emit(" " + Mr[e10.type] + " "), n3.compile(e10.expr, t3);
    }));
  }, i2.compileLookupVal = function(e9, t3) {
    this._emit("runtime.memberLookup(("), this._compileExpression(e9.target, t3), this._emit("),"), this._compileExpression(e9.val, t3), this._emit(")");
  }, i2._getNodeName = function(e9) {
    switch (e9.typename) {
      case "Symbol":
        return e9.value;
      case "FunCall":
        return "the return value of (" + this._getNodeName(e9.name) + ")";
      case "LookupVal":
        return this._getNodeName(e9.target) + '["' + this._getNodeName(e9.val) + '"]';
      case "Literal":
        return e9.value.toString();
      default:
        return "--expression--";
    }
  }, i2.compileFunCall = function(e9, t3) {
    this._emit("(lineno = " + e9.lineno + ", colno = " + e9.colno + ", "), this._emit("runtime.callWrap("), this._compileExpression(e9.name, t3), this._emit(', "' + this._getNodeName(e9.name).replace(/"/g, '\\"') + '", context, '), this._compileAggregate(e9.args, t3, "[", "])"), this._emit(")");
  }, i2.compileFilter = function(e9, t3) {
    var n3 = e9.name;
    this.assertType(n3, hr.Symbol), this._emit('env.getFilter("' + n3.value + '").call(context, '), this._compileAggregate(e9.args, t3), this._emit(")");
  }, i2.compileFilterAsync = function(e9, t3) {
    var n3 = e9.name, r3 = e9.symbol.value;
    this.assertType(n3, hr.Symbol), t3.set(r3, r3), this._emit('env.getFilter("' + n3.value + '").call(context, '), this._compileAggregate(e9.args, t3), this._emitLine(", " + this._makeCallback(r3)), this._addScopeLevel();
  }, i2.compileKeywordArgs = function(e9, t3) {
    this._emit("runtime.makeKeywordArgs("), this.compileDict(e9, t3), this._emit(")");
  }, i2.compileSet = function(e9, t3) {
    var n3 = this, r3 = [];
    e9.targets.forEach((function(e10) {
      var i3 = e10.value, s2 = t3.lookup(i3);
      null == s2 && (s2 = n3._tmpid(), n3._emitLine("var " + s2 + ";")), r3.push(s2);
    })), e9.value ? (this._emit(r3.join(" = ") + " = "), this._compileExpression(e9.value, t3), this._emitLine(";")) : (this._emit(r3.join(" = ") + " = "), this.compile(e9.body, t3), this._emitLine(";")), e9.targets.forEach((function(e10, t4) {
      var i3 = r3[t4], s2 = e10.value;
      n3._emitLine('frame.set("' + s2 + '", ' + i3 + ", true);"), n3._emitLine("if(frame.topLevel) {"), n3._emitLine('context.setVariable("' + s2 + '", ' + i3 + ");"), n3._emitLine("}"), "_" !== s2.charAt(0) && (n3._emitLine("if(frame.topLevel) {"), n3._emitLine('context.addExport("' + s2 + '", ' + i3 + ");"), n3._emitLine("}"));
    }));
  }, i2.compileSwitch = function(e9, t3) {
    var n3 = this;
    this._emit("switch ("), this.compile(e9.expr, t3), this._emit(") {"), e9.cases.forEach((function(e10, r3) {
      n3._emit("case "), n3.compile(e10.cond, t3), n3._emit(": "), n3.compile(e10.body, t3), e10.body.children.length && n3._emitLine("break;");
    })), e9.default && (this._emit("default:"), this.compile(e9.default, t3)), this._emit("}");
  }, i2.compileIf = function(e9, t3, n3) {
    var r3 = this;
    this._emit("if("), this._compileExpression(e9.cond, t3), this._emitLine(") {"), this._withScopedSyntax((function() {
      r3.compile(e9.body, t3), n3 && r3._emit("cb()");
    })), e9.else_ ? (this._emitLine("}\nelse {"), this._withScopedSyntax((function() {
      r3.compile(e9.else_, t3), n3 && r3._emit("cb()");
    }))) : n3 && (this._emitLine("}\nelse {"), this._emit("cb()")), this._emitLine("}");
  }, i2.compileIfAsync = function(e9, t3) {
    this._emit("(function(cb) {"), this.compileIf(e9, t3, true), this._emit("})(" + this._makeCallback()), this._addScopeLevel();
  }, i2._emitLoopBindings = function(e9, t3, n3, r3) {
    var i3 = this;
    [{ name: "index", val: n3 + " + 1" }, { name: "index0", val: n3 }, { name: "revindex", val: r3 + " - " + n3 }, { name: "revindex0", val: r3 + " - " + n3 + " - 1" }, { name: "first", val: n3 + " === 0" }, { name: "last", val: n3 + " === " + r3 + " - 1" }, { name: "length", val: r3 }].forEach((function(e10) {
      i3._emitLine('frame.set("loop.' + e10.name + '", ' + e10.val + ");");
    }));
  }, i2.compileFor = function(e9, t3) {
    var n3 = this, r3 = this._tmpid(), i3 = this._tmpid(), s2 = this._tmpid();
    if (t3 = t3.push(), this._emitLine("frame = frame.push();"), this._emit("var " + s2 + " = "), this._compileExpression(e9.arr, t3), this._emitLine(";"), this._emit("if(" + s2 + ") {"), this._emitLine(s2 + " = runtime.fromIterator(" + s2 + ");"), e9.name instanceof hr.Array) {
      this._emitLine("var " + r3 + ";"), this._emitLine("if(runtime.isArray(" + s2 + ")) {"), this._emitLine("var " + i3 + " = " + s2 + ".length;"), this._emitLine("for(" + r3 + "=0; " + r3 + " < " + s2 + ".length; " + r3 + "++) {"), e9.name.children.forEach((function(i4, a3) {
        var o3 = n3._tmpid();
        n3._emitLine("var " + o3 + " = " + s2 + "[" + r3 + "][" + a3 + "];"), n3._emitLine('frame.set("' + i4 + '", ' + s2 + "[" + r3 + "][" + a3 + "]);"), t3.set(e9.name.children[a3].value, o3);
      })), this._emitLoopBindings(e9, s2, r3, i3), this._withScopedSyntax((function() {
        n3.compile(e9.body, t3);
      })), this._emitLine("}"), this._emitLine("} else {");
      var a2 = e9.name.children, o2 = a2[0], c2 = a2[1], l2 = this._tmpid(), h2 = this._tmpid();
      t3.set(o2.value, l2), t3.set(c2.value, h2), this._emitLine(r3 + " = -1;"), this._emitLine("var " + i3 + " = runtime.keys(" + s2 + ").length;"), this._emitLine("for(var " + l2 + " in " + s2 + ") {"), this._emitLine(r3 + "++;"), this._emitLine("var " + h2 + " = " + s2 + "[" + l2 + "];"), this._emitLine('frame.set("' + o2.value + '", ' + l2 + ");"), this._emitLine('frame.set("' + c2.value + '", ' + h2 + ");"), this._emitLoopBindings(e9, s2, r3, i3), this._withScopedSyntax((function() {
        n3.compile(e9.body, t3);
      })), this._emitLine("}"), this._emitLine("}");
    } else {
      var u2 = this._tmpid();
      t3.set(e9.name.value, u2), this._emitLine("var " + i3 + " = " + s2 + ".length;"), this._emitLine("for(var " + r3 + "=0; " + r3 + " < " + s2 + ".length; " + r3 + "++) {"), this._emitLine("var " + u2 + " = " + s2 + "[" + r3 + "];"), this._emitLine('frame.set("' + e9.name.value + '", ' + u2 + ");"), this._emitLoopBindings(e9, s2, r3, i3), this._withScopedSyntax((function() {
        n3.compile(e9.body, t3);
      })), this._emitLine("}");
    }
    this._emitLine("}"), e9.else_ && (this._emitLine("if (!" + i3 + ") {"), this.compile(e9.else_, t3), this._emitLine("}")), this._emitLine("frame = frame.pop();");
  }, i2._compileAsyncLoop = function(e9, t3, n3) {
    var r3 = this, i3 = this._tmpid(), s2 = this._tmpid(), a2 = this._tmpid(), o2 = n3 ? "asyncAll" : "asyncEach";
    if (t3 = t3.push(), this._emitLine("frame = frame.push();"), this._emit("var " + a2 + " = runtime.fromIterator("), this._compileExpression(e9.arr, t3), this._emitLine(");"), e9.name instanceof hr.Array) {
      var c2 = e9.name.children.length;
      this._emit("runtime." + o2 + "(" + a2 + ", " + c2 + ", function("), e9.name.children.forEach((function(e10) {
        r3._emit(e10.value + ",");
      })), this._emit(i3 + "," + s2 + ",next) {"), e9.name.children.forEach((function(e10) {
        var n4 = e10.value;
        t3.set(n4, n4), r3._emitLine('frame.set("' + n4 + '", ' + n4 + ");");
      }));
    } else {
      var l2 = e9.name.value;
      this._emitLine("runtime." + o2 + "(" + a2 + ", 1, function(" + l2 + ", " + i3 + ", " + s2 + ",next) {"), this._emitLine('frame.set("' + l2 + '", ' + l2 + ");"), t3.set(l2, l2);
    }
    this._emitLoopBindings(e9, a2, i3, s2), this._withScopedSyntax((function() {
      var s3;
      n3 && (s3 = r3._pushBuffer()), r3.compile(e9.body, t3), r3._emitLine("next(" + i3 + (s3 ? "," + s3 : "") + ");"), n3 && r3._popBuffer();
    }));
    var h2 = this._tmpid();
    this._emitLine("}, " + this._makeCallback(h2)), this._addScopeLevel(), n3 && this._emitLine(this.buffer + " += " + h2 + ";"), e9.else_ && (this._emitLine("if (!" + a2 + ".length) {"), this.compile(e9.else_, t3), this._emitLine("}")), this._emitLine("frame = frame.pop();");
  }, i2.compileAsyncEach = function(e9, t3) {
    this._compileAsyncLoop(e9, t3);
  }, i2.compileAsyncAll = function(e9, t3) {
    this._compileAsyncLoop(e9, t3, true);
  }, i2._compileMacro = function(e9, t3) {
    var n3 = this, r3 = [], i3 = null, s2 = "macro_" + this._tmpid(), a2 = void 0 !== t3;
    e9.args.children.forEach((function(t4, s3) {
      s3 === e9.args.children.length - 1 && t4 instanceof hr.Dict ? i3 = t4 : (n3.assertType(t4, hr.Symbol), r3.push(t4));
    }));
    var o2, c2 = [].concat(r3.map((function(e10) {
      return "l_" + e10.value;
    })), ["kwargs"]), l2 = r3.map((function(e10) {
      return '"' + e10.value + '"';
    })), h2 = (i3 && i3.children || []).map((function(e10) {
      return '"' + e10.key.value + '"';
    }));
    o2 = a2 ? t3.push(true) : new Rr(), this._emitLines("var " + s2 + " = runtime.makeMacro(", "[" + l2.join(", ") + "], ", "[" + h2.join(", ") + "], ", "function (" + c2.join(", ") + ") {", "var callerFrame = frame;", "frame = " + (a2 ? "frame.push(true);" : "new runtime.Frame();"), "kwargs = kwargs || {};", 'if (Object.prototype.hasOwnProperty.call(kwargs, "caller")) {', 'frame.set("caller", kwargs.caller); }'), r3.forEach((function(e10) {
      n3._emitLine('frame.set("' + e10.value + '", l_' + e10.value + ");"), o2.set(e10.value, "l_" + e10.value);
    })), i3 && i3.children.forEach((function(e10) {
      var t4 = e10.key.value;
      n3._emit('frame.set("' + t4 + '", '), n3._emit('Object.prototype.hasOwnProperty.call(kwargs, "' + t4 + '")'), n3._emit(' ? kwargs["' + t4 + '"] : '), n3._compileExpression(e10.value, o2), n3._emit(");");
    }));
    var u2 = this._pushBuffer();
    return this._withScopedSyntax((function() {
      n3.compile(e9.body, o2);
    })), this._emitLine("frame = " + (a2 ? "frame.pop();" : "callerFrame;")), this._emitLine("return new runtime.SafeString(" + u2 + ");"), this._emitLine("});"), this._popBuffer(), s2;
  }, i2.compileMacro = function(e9, t3) {
    var n3 = this._compileMacro(e9), r3 = e9.name.value;
    t3.set(r3, n3), t3.parent ? this._emitLine('frame.set("' + r3 + '", ' + n3 + ");") : ("_" !== e9.name.value.charAt(0) && this._emitLine('context.addExport("' + r3 + '");'), this._emitLine('context.setVariable("' + r3 + '", ' + n3 + ");"));
  }, i2.compileCaller = function(e9, t3) {
    this._emit("(function (){");
    var n3 = this._compileMacro(e9, t3);
    this._emit("return " + n3 + ";})()");
  }, i2._compileGetTemplate = function(e9, t3, n3, r3) {
    var i3 = this._tmpid(), s2 = this._templateName(), a2 = this._makeCallback(i3), o2 = n3 ? "true" : "false", c2 = r3 ? "true" : "false";
    return this._emit("env.getTemplate("), this._compileExpression(e9.template, t3), this._emitLine(", " + o2 + ", " + s2 + ", " + c2 + ", " + a2), i3;
  }, i2.compileImport = function(e9, t3) {
    var n3 = e9.target.value, r3 = this._compileGetTemplate(e9, t3, false, false);
    this._addScopeLevel(), this._emitLine(r3 + ".getExported(" + (e9.withContext ? "context.getVariables(), frame, " : "") + this._makeCallback(r3)), this._addScopeLevel(), t3.set(n3, r3), t3.parent ? this._emitLine('frame.set("' + n3 + '", ' + r3 + ");") : this._emitLine('context.setVariable("' + n3 + '", ' + r3 + ");");
  }, i2.compileFromImport = function(e9, t3) {
    var n3 = this, r3 = this._compileGetTemplate(e9, t3, false, false);
    this._addScopeLevel(), this._emitLine(r3 + ".getExported(" + (e9.withContext ? "context.getVariables(), frame, " : "") + this._makeCallback(r3)), this._addScopeLevel(), e9.names.children.forEach((function(e10) {
      var i3, s2, a2 = n3._tmpid();
      e10 instanceof hr.Pair ? (i3 = e10.key.value, s2 = e10.value.value) : s2 = i3 = e10.value, n3._emitLine("if(Object.prototype.hasOwnProperty.call(" + r3 + ', "' + i3 + '")) {'), n3._emitLine("var " + a2 + " = " + r3 + "." + i3 + ";"), n3._emitLine("} else {"), n3._emitLine(`cb(new Error("cannot import '` + i3 + `'")); return;`), n3._emitLine("}"), t3.set(s2, a2), t3.parent ? n3._emitLine('frame.set("' + s2 + '", ' + a2 + ");") : n3._emitLine('context.setVariable("' + s2 + '", ' + a2 + ");");
    }));
  }, i2.compileBlock = function(e9) {
    var t3 = this._tmpid();
    this.inBlock || this._emit('(parentTemplate ? function(e, c, f, r, cb) { cb(""); } : '), this._emit('context.getBlock("' + e9.name.value + '")'), this.inBlock || this._emit(")"), this._emitLine("(env, context, frame, runtime, " + this._makeCallback(t3)), this._emitLine(this.buffer + " += " + t3 + ";"), this._addScopeLevel();
  }, i2.compileSuper = function(e9, t3) {
    var n3 = e9.blockName.value, r3 = e9.symbol.value, i3 = this._makeCallback(r3);
    this._emitLine('context.getSuper(env, "' + n3 + '", b_' + n3 + ", frame, runtime, " + i3), this._emitLine(r3 + " = runtime.markSafe(" + r3 + ");"), this._addScopeLevel(), t3.set(r3, r3);
  }, i2.compileExtends = function(e9, t3) {
    var n3 = this._tmpid(), r3 = this._compileGetTemplate(e9, t3, true, false);
    this._emitLine("parentTemplate = " + r3), this._emitLine("for(var " + n3 + " in parentTemplate.blocks) {"), this._emitLine("context.addBlock(" + n3 + ", parentTemplate.blocks[" + n3 + "]);"), this._emitLine("}"), this._addScopeLevel();
  }, i2.compileInclude = function(e9, t3) {
    this._emitLine("var tasks = [];"), this._emitLine("tasks.push("), this._emitLine("function(callback) {");
    var n3 = this._compileGetTemplate(e9, t3, false, e9.ignoreMissing);
    this._emitLine("callback(null," + n3 + ");});"), this._emitLine("});");
    var r3 = this._tmpid();
    this._emitLine("tasks.push("), this._emitLine("function(template, callback){"), this._emitLine("template.render(context.getVariables(), frame, " + this._makeCallback(r3)), this._emitLine("callback(null," + r3 + ");});"), this._emitLine("});"), this._emitLine("tasks.push("), this._emitLine("function(result, callback){"), this._emitLine(this.buffer + " += result;"), this._emitLine("callback(null);"), this._emitLine("});"), this._emitLine("env.waterfall(tasks, function(){"), this._addScopeLevel();
  }, i2.compileTemplateData = function(e9, t3) {
    this.compileLiteral(e9, t3);
  }, i2.compileCapture = function(e9, t3) {
    var n3 = this, r3 = this.buffer;
    this.buffer = "output", this._emitLine("(function() {"), this._emitLine('var output = "";'), this._withScopedSyntax((function() {
      n3.compile(e9.body, t3);
    })), this._emitLine("return output;"), this._emitLine("})()"), this.buffer = r3;
  }, i2.compileOutput = function(e9, t3) {
    var n3 = this;
    e9.children.forEach((function(r3) {
      r3 instanceof hr.TemplateData ? r3.value && (n3._emit(n3.buffer + " += "), n3.compileLiteral(r3, t3), n3._emitLine(";")) : (n3._emit(n3.buffer + " += runtime.suppressValue("), n3.throwOnUndefined && n3._emit("runtime.ensureDefined("), n3.compile(r3, t3), n3.throwOnUndefined && n3._emit("," + e9.lineno + "," + e9.colno + ")"), n3._emit(", env.opts.autoescape);\n"));
    }));
  }, i2.compileRoot = function(e9, t3) {
    var n3 = this;
    t3 && this.fail("compileRoot: root node can't have frame"), t3 = new Rr(), this._emitFuncBegin(e9, "root"), this._emitLine("var parentTemplate = null;"), this._compileChildren(e9, t3), this._emitLine("if(parentTemplate) {"), this._emitLine("parentTemplate.rootRenderFunc(env, context, frame, runtime, cb);"), this._emitLine("} else {"), this._emitLine("cb(null, " + this.buffer + ");"), this._emitLine("}"), this._emitFuncEnd(true), this.inBlock = true;
    var r3 = [], i3 = e9.findAll(hr.Block);
    i3.forEach((function(e10, t4) {
      var i4 = e10.name.value;
      if (-1 !== r3.indexOf(i4)) throw new Error('Block "' + i4 + '" defined more than once.');
      r3.push(i4), n3._emitFuncBegin(e10, "b_" + i4);
      var s2 = new Rr();
      n3._emitLine("var frame = frame.push(true);"), n3.compile(e10.body, s2), n3._emitFuncEnd();
    })), this._emitLine("return {"), i3.forEach((function(e10, t4) {
      var r4 = "b_" + e10.name.value;
      n3._emitLine(r4 + ": " + r4 + ",");
    })), this._emitLine("root: root\n};");
  }, i2.compile = function(e9, t3) {
    var n3 = this["compile" + e9.typename];
    n3 ? n3.call(this, e9, t3) : this.fail("compile: Cannot compile node: " + e9.typename, e9.lineno, e9.colno);
  }, i2.getCode = function() {
    return this.codebuf.join("");
  }, r2;
})(en.Obj), xr = { compile: function(e8, t2, n2, r2, i2) {
  void 0 === i2 && (i2 = {});
  var s2 = new Pr(r2, i2.throwOnUndefined), a2 = (n2 || []).map((function(e9) {
    return e9.preprocess;
  })).filter((function(e9) {
    return !!e9;
  })).reduce((function(e9, t3) {
    return t3(e9);
  }), e8);
  return s2.compile(Nr.transform(fr.parse(a2, n2, i2), t2, r2)), s2.getCode();
}, Compiler: Pr }, wr = Ke((function(e8) {
  var t2 = e8.exports = {};
  function n2(e9, t3) {
    return null == e9 || false === e9 ? t3 : e9;
  }
  function r2(e9) {
    return e9 != e9;
  }
  function i2(e9) {
    var t3 = (e9 = n2(e9, "")).toLowerCase();
    return kr.copySafeness(e9, t3.charAt(0).toUpperCase() + t3.slice(1));
  }
  function s2(e9) {
    if (je.isString(e9)) return e9.split("");
    if (je.isObject(e9)) return je._entries(e9 || {}).map((function(e10) {
      return { key: e10[0], value: e10[1] };
    }));
    if (je.isArray(e9)) return e9;
    throw new je.TemplateError("list filter: type not iterable");
  }
  function a2(e9) {
    return function(t3, n3, r3) {
      void 0 === n3 && (n3 = "truthy");
      var i3 = this, s3 = i3.env.getTest(n3);
      return je.toArray(t3).filter((function(t4) {
        return s3.call(i3, t4, r3) === e9;
      }));
    };
  }
  function o2(e9) {
    return kr.copySafeness(e9, e9.replace(/^\s*|\s*$/g, ""));
  }
  t2.abs = Math.abs, t2.batch = function(e9, t3, n3) {
    var r3, i3 = [], s3 = [];
    for (r3 = 0; r3 < e9.length; r3++) r3 % t3 == 0 && s3.length && (i3.push(s3), s3 = []), s3.push(e9[r3]);
    if (s3.length) {
      if (n3) for (r3 = s3.length; r3 < t3; r3++) s3.push(n3);
      i3.push(s3);
    }
    return i3;
  }, t2.capitalize = i2, t2.center = function(e9, t3) {
    if (t3 = t3 || 80, (e9 = n2(e9, "")).length >= t3) return e9;
    var r3 = t3 - e9.length, i3 = je.repeat(" ", r3 / 2 - r3 % 2), s3 = je.repeat(" ", r3 / 2);
    return kr.copySafeness(e9, i3 + e9 + s3);
  }, t2.default = function(e9, t3, n3) {
    return n3 ? e9 || t3 : void 0 !== e9 ? e9 : t3;
  }, t2.dictsort = function(e9, t3, n3) {
    if (!je.isObject(e9)) throw new je.TemplateError("dictsort filter: val must be an object");
    var r3, i3 = [];
    for (var s3 in e9) i3.push([s3, e9[s3]]);
    if (void 0 === n3 || "key" === n3) r3 = 0;
    else {
      if ("value" !== n3) throw new je.TemplateError("dictsort filter: You can only sort by either key or value");
      r3 = 1;
    }
    return i3.sort((function(e10, n4) {
      var i4 = e10[r3], s4 = n4[r3];
      return t3 || (je.isString(i4) && (i4 = i4.toUpperCase()), je.isString(s4) && (s4 = s4.toUpperCase())), i4 > s4 ? 1 : i4 === s4 ? 0 : -1;
    })), i3;
  }, t2.dump = function(e9, t3) {
    return JSON.stringify(e9, null, t3);
  }, t2.escape = function(e9) {
    return e9 instanceof kr.SafeString ? e9 : (e9 = null == e9 ? "" : e9, kr.markSafe(je.escape(e9.toString())));
  }, t2.safe = function(e9) {
    return e9 instanceof kr.SafeString ? e9 : (e9 = null == e9 ? "" : e9, kr.markSafe(e9.toString()));
  }, t2.first = function(e9) {
    return e9[0];
  }, t2.forceescape = function(e9) {
    return e9 = null == e9 ? "" : e9, kr.markSafe(je.escape(e9.toString()));
  }, t2.groupby = function(e9, t3) {
    return je.groupBy(e9, t3, this.env.opts.throwOnUndefined);
  }, t2.indent = function(e9, t3, r3) {
    if ("" === (e9 = n2(e9, ""))) return "";
    t3 = t3 || 4;
    var i3 = e9.split("\n"), s3 = je.repeat(" ", t3), a3 = i3.map((function(e10, t4) {
      return 0 !== t4 || r3 ? "" + s3 + e10 : e10;
    })).join("\n");
    return kr.copySafeness(e9, a3);
  }, t2.join = function(e9, t3, n3) {
    return t3 = t3 || "", n3 && (e9 = je.map(e9, (function(e10) {
      return e10[n3];
    }))), e9.join(t3);
  }, t2.last = function(e9) {
    return e9[e9.length - 1];
  }, t2.length = function(e9) {
    var t3 = n2(e9, "");
    return void 0 !== t3 ? "function" == typeof Map && t3 instanceof Map || "function" == typeof Set && t3 instanceof Set ? t3.size : !je.isObject(t3) || t3 instanceof kr.SafeString ? t3.length : je.keys(t3).length : 0;
  }, t2.list = s2, t2.lower = function(e9) {
    return (e9 = n2(e9, "")).toLowerCase();
  }, t2.nl2br = function(e9) {
    return null == e9 ? "" : kr.copySafeness(e9, e9.replace(/\r\n|\n/g, "<br />\n"));
  }, t2.random = function(e9) {
    return e9[Math.floor(Math.random() * e9.length)];
  }, t2.reject = a2(false), t2.rejectattr = function(e9, t3) {
    return e9.filter((function(e10) {
      return !e10[t3];
    }));
  }, t2.select = a2(true), t2.selectattr = function(e9, t3) {
    return e9.filter((function(e10) {
      return !!e10[t3];
    }));
  }, t2.replace = function(e9, t3, n3, r3) {
    var i3 = e9;
    if (t3 instanceof RegExp) return e9.replace(t3, n3);
    void 0 === r3 && (r3 = -1);
    var s3 = "";
    if ("number" == typeof t3) t3 = "" + t3;
    else if ("string" != typeof t3) return e9;
    if ("number" == typeof e9 && (e9 = "" + e9), "string" != typeof e9 && !(e9 instanceof kr.SafeString)) return e9;
    if ("" === t3) return s3 = n3 + e9.split("").join(n3) + n3, kr.copySafeness(e9, s3);
    var a3 = e9.indexOf(t3);
    if (0 === r3 || -1 === a3) return e9;
    for (var o3 = 0, c3 = 0; a3 > -1 && (-1 === r3 || c3 < r3); ) s3 += e9.substring(o3, a3) + n3, o3 = a3 + t3.length, c3++, a3 = e9.indexOf(t3, o3);
    return o3 < e9.length && (s3 += e9.substring(o3)), kr.copySafeness(i3, s3);
  }, t2.reverse = function(e9) {
    var t3;
    return (t3 = je.isString(e9) ? s2(e9) : je.map(e9, (function(e10) {
      return e10;
    }))).reverse(), je.isString(e9) ? kr.copySafeness(e9, t3.join("")) : t3;
  }, t2.round = function(e9, t3, n3) {
    t3 = t3 || 0;
    var r3 = Math.pow(10, t3);
    return ("ceil" === n3 ? Math.ceil : "floor" === n3 ? Math.floor : Math.round)(e9 * r3) / r3;
  }, t2.slice = function(e9, t3, n3) {
    for (var r3 = Math.floor(e9.length / t3), i3 = e9.length % t3, s3 = [], a3 = 0, o3 = 0; o3 < t3; o3++) {
      var c3 = a3 + o3 * r3;
      o3 < i3 && a3++;
      var l3 = a3 + (o3 + 1) * r3, h3 = e9.slice(c3, l3);
      n3 && o3 >= i3 && h3.push(n3), s3.push(h3);
    }
    return s3;
  }, t2.sum = function(e9, t3, n3) {
    return void 0 === n3 && (n3 = 0), t3 && (e9 = je.map(e9, (function(e10) {
      return e10[t3];
    }))), n3 + e9.reduce((function(e10, t4) {
      return e10 + t4;
    }), 0);
  }, t2.sort = kr.makeMacro(["value", "reverse", "case_sensitive", "attribute"], [], (function(e9, t3, n3, r3) {
    var i3 = this, s3 = je.map(e9, (function(e10) {
      return e10;
    })), a3 = je.getAttrGetter(r3);
    return s3.sort((function(e10, s4) {
      var o3 = r3 ? a3(e10) : e10, c3 = r3 ? a3(s4) : s4;
      if (i3.env.opts.throwOnUndefined && r3 && (void 0 === o3 || void 0 === c3)) throw new TypeError('sort: attribute "' + r3 + '" resolved to undefined');
      return !n3 && je.isString(o3) && je.isString(c3) && (o3 = o3.toLowerCase(), c3 = c3.toLowerCase()), o3 < c3 ? t3 ? 1 : -1 : o3 > c3 ? t3 ? -1 : 1 : 0;
    })), s3;
  })), t2.string = function(e9) {
    return kr.copySafeness(e9, e9);
  }, t2.striptags = function(e9, t3) {
    var r3 = o2((e9 = n2(e9, "")).replace(/<\/?([a-z][a-z0-9]*)\b[^>]*>|<!--[\s\S]*?-->/gi, "")), i3 = "";
    return i3 = t3 ? r3.replace(/^ +| +$/gm, "").replace(/ +/g, " ").replace(/(\r\n)/g, "\n").replace(/\n\n\n+/g, "\n\n") : r3.replace(/\s+/gi, " "), kr.copySafeness(e9, i3);
  }, t2.title = function(e9) {
    var t3 = (e9 = n2(e9, "")).split(" ").map((function(e10) {
      return i2(e10);
    }));
    return kr.copySafeness(e9, t3.join(" "));
  }, t2.trim = o2, t2.truncate = function(e9, t3, r3, i3) {
    var s3 = e9;
    if (t3 = t3 || 255, (e9 = n2(e9, "")).length <= t3) return e9;
    if (r3) e9 = e9.substring(0, t3);
    else {
      var a3 = e9.lastIndexOf(" ", t3);
      -1 === a3 && (a3 = t3), e9 = e9.substring(0, a3);
    }
    return e9 += null != i3 ? i3 : "...", kr.copySafeness(s3, e9);
  }, t2.upper = function(e9) {
    return (e9 = n2(e9, "")).toUpperCase();
  }, t2.urlencode = function(e9) {
    var t3 = encodeURIComponent;
    return je.isString(e9) ? t3(e9) : (je.isArray(e9) ? e9 : je._entries(e9)).map((function(e10) {
      var n3 = e10[0], r3 = e10[1];
      return t3(n3) + "=" + t3(r3);
    })).join("&");
  };
  var c2 = /^(?:\(|<|&lt;)?(.*?)(?:\.|,|\)|\n|&gt;)?$/, l2 = /^[\w.!#$%&'*+\-\/=?\^`{|}~]+@[a-z\d\-]+(\.[a-z\d\-]+)+$/i, h2 = /^https?:\/\/.*$/, u2 = /^www\./, p2 = /\.(?:org|net|com)(?:\:|\/|$)/;
  t2.urlize = function(e9, t3, n3) {
    r2(t3) && (t3 = 1 / 0);
    var i3 = true === n3 ? ' rel="nofollow"' : "";
    return e9.split(/(\s+)/).filter((function(e10) {
      return e10 && e10.length;
    })).map((function(e10) {
      var n4 = e10.match(c2), r3 = n4 ? n4[1] : e10, s3 = r3.substr(0, t3);
      return h2.test(r3) ? '<a href="' + r3 + '"' + i3 + ">" + s3 + "</a>" : u2.test(r3) ? '<a href="http://' + r3 + '"' + i3 + ">" + s3 + "</a>" : l2.test(r3) ? '<a href="mailto:' + r3 + '">' + r3 + "</a>" : p2.test(r3) ? '<a href="http://' + r3 + '"' + i3 + ">" + s3 + "</a>" : e10;
    })).join("");
  }, t2.wordcount = function(e9) {
    var t3 = (e9 = n2(e9, "")) ? e9.match(/\w+/g) : null;
    return t3 ? t3.length : null;
  }, t2.float = function(e9, t3) {
    var n3 = parseFloat(e9);
    return r2(n3) ? t3 : n3;
  };
  var f2 = kr.makeMacro(["value", "default", "base"], [], (function(e9, t3, n3) {
    void 0 === n3 && (n3 = 10);
    var i3 = parseInt(e9, n3);
    return r2(i3) ? t3 : i3;
  }));
  t2.int = f2, t2.d = t2.default, t2.e = t2.escape;
})), Br = {};
function Fr(e8, t2) {
  for (var n2 = 0, r2 = e8.length - 1; r2 >= 0; r2--) {
    var i2 = e8[r2];
    "." === i2 ? e8.splice(r2, 1) : ".." === i2 ? (e8.splice(r2, 1), n2++) : n2 && (e8.splice(r2, 1), n2--);
  }
  if (t2) for (; n2--; n2) e8.unshift("..");
  return e8;
}
var Ur = /^(\/?|)([\s\S]*?)((?:\.{1,2}|[^\/]+?|)(\.[^.\/]*|))(?:[\/]*)$/, Hr = function(e8) {
  return Ur.exec(e8).slice(1);
};
function Gr() {
  for (var e8 = "", t2 = false, n2 = arguments.length - 1; n2 >= -1 && !t2; n2--) {
    var r2 = n2 >= 0 ? arguments[n2] : "/";
    if ("string" != typeof r2) throw new TypeError("Arguments to path.resolve must be strings");
    r2 && (e8 = r2 + "/" + e8, t2 = "/" === r2.charAt(0));
  }
  return (t2 ? "/" : "") + (e8 = Fr(jr(e8.split("/"), (function(e9) {
    return !!e9;
  })), !t2).join("/")) || ".";
}
function Yr(e8) {
  var t2 = qr(e8), n2 = "/" === Vr(e8, -1);
  return (e8 = Fr(jr(e8.split("/"), (function(e9) {
    return !!e9;
  })), !t2).join("/")) || t2 || (e8 = "."), e8 && n2 && (e8 += "/"), (t2 ? "/" : "") + e8;
}
function qr(e8) {
  return "/" === e8.charAt(0);
}
var Kr = { extname: function(e8) {
  return Hr(e8)[3];
}, basename: function(e8, t2) {
  var n2 = Hr(e8)[2];
  return t2 && n2.substr(-1 * t2.length) === t2 && (n2 = n2.substr(0, n2.length - t2.length)), n2;
}, dirname: function(e8) {
  var t2 = Hr(e8), n2 = t2[0], r2 = t2[1];
  return n2 || r2 ? (r2 && (r2 = r2.substr(0, r2.length - 1)), n2 + r2) : ".";
}, sep: "/", delimiter: ":", relative: function(e8, t2) {
  function n2(e9) {
    for (var t3 = 0; t3 < e9.length && "" === e9[t3]; t3++) ;
    for (var n3 = e9.length - 1; n3 >= 0 && "" === e9[n3]; n3--) ;
    return t3 > n3 ? [] : e9.slice(t3, n3 - t3 + 1);
  }
  e8 = Gr(e8).substr(1), t2 = Gr(t2).substr(1);
  for (var r2 = n2(e8.split("/")), i2 = n2(t2.split("/")), s2 = Math.min(r2.length, i2.length), a2 = s2, o2 = 0; o2 < s2; o2++) if (r2[o2] !== i2[o2]) {
    a2 = o2;
    break;
  }
  var c2 = [];
  for (o2 = a2; o2 < r2.length; o2++) c2.push("..");
  return (c2 = c2.concat(i2.slice(a2))).join("/");
}, join: function() {
  var e8 = Array.prototype.slice.call(arguments, 0);
  return Yr(jr(e8, (function(e9, t2) {
    if ("string" != typeof e9) throw new TypeError("Arguments to path.join must be strings");
    return e9;
  })).join("/"));
}, isAbsolute: qr, normalize: Yr, resolve: Gr };
function jr(e8, t2) {
  if (e8.filter) return e8.filter(t2);
  for (var n2 = [], r2 = 0; r2 < e8.length; r2++) t2(e8[r2], r2, e8) && n2.push(e8[r2]);
  return n2;
}
var Vr = "b" === "ab".substr(-1) ? function(e8, t2, n2) {
  return e8.substr(t2, n2);
} : function(e8, t2, n2) {
  return t2 < 0 && (t2 = e8.length + t2), e8.substr(t2, n2);
};
function Wr(e8, t2) {
  return Wr = Object.setPrototypeOf || function(e9, t3) {
    return e9.__proto__ = t3, e9;
  }, Wr(e8, t2);
}
var Qr = (function(e8) {
  var t2, n2;
  function r2() {
    return e8.apply(this, arguments) || this;
  }
  n2 = e8, (t2 = r2).prototype = Object.create(n2.prototype), t2.prototype.constructor = t2, Wr(t2, n2);
  var i2 = r2.prototype;
  return i2.resolve = function(e9, t3) {
    return Kr.resolve(Kr.dirname(e9), t3);
  }, i2.isRelative = function(e9) {
    return 0 === e9.indexOf("./") || 0 === e9.indexOf("../");
  }, r2;
})(en.EmitterObj);
function Xr(e8, t2) {
  return Xr = Object.setPrototypeOf || function(e9, t3) {
    return e9.__proto__ = t3, e9;
  }, Xr(e8, t2);
}
var $r = (function(e8) {
  var t2, n2;
  function r2(t3) {
    var n3;
    return (n3 = e8.call(this) || this).precompiled = t3 || {}, n3;
  }
  return n2 = e8, (t2 = r2).prototype = Object.create(n2.prototype), t2.prototype.constructor = t2, Xr(t2, n2), r2.prototype.getSource = function(e9) {
    return this.precompiled[e9] ? { src: { type: "code", obj: this.precompiled[e9] }, path: e9 } : null;
  }, r2;
})(Qr);
function zr(e8, t2) {
  e8.prototype = Object.create(t2.prototype), e8.prototype.constructor = e8, Jr(e8, t2);
}
function Jr(e8, t2) {
  return Jr = Object.setPrototypeOf || function(e9, t3) {
    return e9.__proto__ = t3, e9;
  }, Jr(e8, t2);
}
var Zr = { PrecompiledLoader: $r }.PrecompiledLoader, ei = (function(e8) {
  function t2(t3, n2) {
    var r2;
    return n2 = n2 || {}, (r2 = e8.call(this) || this).pathsToNames = {}, r2.noCache = !!n2.noCache, t3 ? (t3 = Array.isArray(t3) ? t3 : [t3], r2.searchPaths = t3.map(Kr.normalize)) : r2.searchPaths = ["."], r2;
  }
  return zr(t2, e8), t2.prototype.getSource = function(e9) {
    for (var t3 = null, n2 = this.searchPaths, r2 = 0; r2 < n2.length; r2++) {
      var i2 = Kr.resolve(n2[r2]), s2 = Kr.resolve(n2[r2], e9);
      if (0 === s2.indexOf(i2) && Br.existsSync(s2)) {
        t3 = s2;
        break;
      }
    }
    if (!t3) return null;
    this.pathsToNames[t3] = e9;
    var a2 = { src: Br.readFileSync(t3, "utf-8"), path: t3, noCache: this.noCache };
    return this.emit("load", e9, a2), a2;
  }, t2;
})(Qr), ti = { FileSystemLoader: ei, PrecompiledLoader: Zr, NodeResolveLoader: (function(e8) {
  function t2(t3) {
    var n2;
    return t3 = t3 || {}, (n2 = e8.call(this) || this).pathsToNames = {}, n2.noCache = !!t3.noCache, n2;
  }
  return zr(t2, e8), t2.prototype.getSource = function(e9) {
    if (/^\.?\.?(\/|\\)/.test(e9)) return null;
    if (/^[A-Z]:/.test(e9)) return null;
    var t3;
    try {
      t3 = Ye.resolve(e9);
    } catch (e10) {
      return null;
    }
    this.pathsToNames[t3] = e9;
    var n2 = { src: Br.readFileSync(t3, "utf-8"), path: t3, noCache: this.noCache };
    return this.emit("load", e9, n2), n2;
  }, t2;
})(Qr) }, ni = Ke((function(e8, t2) {
  var n2 = kr.SafeString;
  t2.callable = function(e9) {
    return "function" == typeof e9;
  }, t2.defined = function(e9) {
    return void 0 !== e9;
  }, t2.divisibleby = function(e9, t3) {
    return e9 % t3 == 0;
  }, t2.escaped = function(e9) {
    return e9 instanceof n2;
  }, t2.equalto = function(e9, t3) {
    return e9 === t3;
  }, t2.eq = t2.equalto, t2.sameas = t2.equalto, t2.even = function(e9) {
    return e9 % 2 == 0;
  }, t2.falsy = function(e9) {
    return !e9;
  }, t2.ge = function(e9, t3) {
    return e9 >= t3;
  }, t2.greaterthan = function(e9, t3) {
    return e9 > t3;
  }, t2.gt = t2.greaterthan, t2.le = function(e9, t3) {
    return e9 <= t3;
  }, t2.lessthan = function(e9, t3) {
    return e9 < t3;
  }, t2.lt = t2.lessthan, t2.lower = function(e9) {
    return e9.toLowerCase() === e9;
  }, t2.ne = function(e9, t3) {
    return e9 !== t3;
  }, t2.null = function(e9) {
    return null === e9;
  }, t2.number = function(e9) {
    return "number" == typeof e9;
  }, t2.odd = function(e9) {
    return e9 % 2 == 1;
  }, t2.string = function(e9) {
    return "string" == typeof e9;
  }, t2.truthy = function(e9) {
    return !!e9;
  }, t2.undefined = function(e9) {
    return void 0 === e9;
  }, t2.upper = function(e9) {
    return e9.toUpperCase() === e9;
  }, t2.iterable = function(e9) {
    return "undefined" != typeof Symbol ? !!e9[Symbol.iterator] : Array.isArray(e9) || "string" == typeof e9;
  }, t2.mapping = function(e9) {
    var t3 = null != e9 && "object" == typeof e9 && !Array.isArray(e9);
    return Set ? t3 && !(e9 instanceof Set) : t3;
  };
}));
function ri(e8) {
  var t2 = -1;
  return { current: null, reset: function() {
    t2 = -1, this.current = null;
  }, next: function() {
    return ++t2 >= e8.length && (t2 = 0), this.current = e8[t2], this.current;
  } };
}
ni.callable, ni.defined, ni.divisibleby, ni.escaped, ni.equalto, ni.eq, ni.sameas, ni.even, ni.falsy, ni.ge, ni.greaterthan, ni.gt, ni.le, ni.lessthan, ni.lt, ni.lower, ni.ne, ni.number, ni.odd, ni.string, ni.truthy, ni.undefined, ni.upper, ni.iterable, ni.mapping;
var ii = function() {
  return { range: function(e8, t2, n2) {
    void 0 === t2 ? (t2 = e8, e8 = 0, n2 = 1) : n2 || (n2 = 1);
    var r2 = [];
    if (n2 > 0) for (var i2 = e8; i2 < t2; i2 += n2) r2.push(i2);
    else for (var s2 = e8; s2 > t2; s2 += n2) r2.push(s2);
    return r2;
  }, cycler: function() {
    return ri(Array.prototype.slice.call(arguments));
  }, joiner: function(e8) {
    return (function(e9) {
      e9 = e9 || ",";
      var t2 = true;
      return function() {
        var n2 = t2 ? "" : e9;
        return t2 = false, n2;
      };
    })(e8);
  } };
};
function si(e8, t2) {
  e8.prototype = Object.create(t2.prototype), e8.prototype.constructor = e8, ai(e8, t2);
}
function ai(e8, t2) {
  return ai = Object.setPrototypeOf || function(e9, t3) {
    return e9.__proto__ = t3, e9;
  }, ai(e8, t2);
}
var oi = ti.FileSystemLoader, ci = ti.WebLoader, li = ti.PrecompiledLoader, hi = en.Obj, ui = en.EmitterObj, pi = kr.handleError, fi = kr.Frame;
function di(e8, t2, n2) {
  Gt((function() {
    e8(t2, n2);
  }));
}
var Ei = { type: "code", obj: { root: function(e8, t2, n2, r2, i2) {
  try {
    i2(null, "");
  } catch (e9) {
    i2(pi(e9, null, null));
  }
} } }, mi = (function(e8) {
  function t2() {
    return e8.apply(this, arguments) || this;
  }
  si(t2, e8);
  var n2 = t2.prototype;
  return n2.init = function(e9, t3) {
    var n3 = this;
    t3 = this.opts = t3 || {}, this.opts.dev = !!t3.dev, this.opts.autoescape = null == t3.autoescape || t3.autoescape, this.opts.throwOnUndefined = !!t3.throwOnUndefined, this.opts.trimBlocks = !!t3.trimBlocks, this.opts.lstripBlocks = !!t3.lstripBlocks, this.loaders = [], e9 ? this.loaders = je.isArray(e9) ? e9 : [e9] : oi ? this.loaders = [new oi("views")] : ci && (this.loaders = [new ci("/views")]), "undefined" != typeof window && window.jinja2Precompiled && this.loaders.unshift(new li(window.jinja2Precompiled)), this._initLoaders(), this.globals = ii(), this.filters = {}, this.tests = {}, this.asyncFilters = [], this.extensions = {}, this.extensionsList = [], je._entries(wr).forEach((function(e10) {
      var t4 = e10[0], r2 = e10[1];
      return n3.addFilter(t4, r2);
    })), je._entries(ni).forEach((function(e10) {
      var t4 = e10[0], r2 = e10[1];
      return n3.addTest(t4, r2);
    }));
  }, n2._initLoaders = function() {
    var e9 = this;
    this.loaders.forEach((function(t3) {
      t3.cache = {}, "function" == typeof t3.on && (t3.on("update", (function(n3, r2) {
        t3.cache[n3] = null, e9.emit("update", n3, r2, t3);
      })), t3.on("load", (function(n3, r2) {
        e9.emit("load", n3, r2, t3);
      })));
    }));
  }, n2.invalidateCache = function() {
    this.loaders.forEach((function(e9) {
      e9.cache = {};
    }));
  }, n2.addExtension = function(e9, t3) {
    return t3.__name = e9, this.extensions[e9] = t3, this.extensionsList.push(t3), this;
  }, n2.removeExtension = function(e9) {
    var t3 = this.getExtension(e9);
    t3 && (this.extensionsList = je.without(this.extensionsList, t3), delete this.extensions[e9]);
  }, n2.getExtension = function(e9) {
    return this.extensions[e9];
  }, n2.hasExtension = function(e9) {
    return !!this.extensions[e9];
  }, n2.addGlobal = function(e9, t3) {
    return this.globals[e9] = t3, this;
  }, n2.getGlobal = function(e9) {
    if (void 0 === this.globals[e9]) throw new Error("global not found: " + e9);
    return this.globals[e9];
  }, n2.addFilter = function(e9, t3, n3) {
    var r2 = t3;
    return n3 && this.asyncFilters.push(e9), this.filters[e9] = r2, this;
  }, n2.getFilter = function(e9) {
    if (!this.filters[e9]) throw new Error("filter not found: " + e9);
    return this.filters[e9];
  }, n2.addTest = function(e9, t3) {
    return this.tests[e9] = t3, this;
  }, n2.getTest = function(e9) {
    if (!this.tests[e9]) throw new Error("test not found: " + e9);
    return this.tests[e9];
  }, n2.resolveTemplate = function(e9, t3, n3) {
    return !(!e9.isRelative || !t3) && e9.isRelative(n3) && e9.resolve ? e9.resolve(t3, n3) : n3;
  }, n2.getTemplate = function(e9, t3, n3, r2, i2) {
    var s2, a2 = this, o2 = this, c2 = null;
    if (e9 && e9.raw && (e9 = e9.raw), je.isFunction(n3) && (i2 = n3, n3 = null, t3 = t3 || false), je.isFunction(t3) && (i2 = t3, t3 = false), e9 instanceof _i) c2 = e9;
    else {
      if ("string" != typeof e9) throw new Error("template names must be a string: " + e9);
      for (var l2 = 0; l2 < this.loaders.length; l2++) {
        var h2 = this.loaders[l2];
        if (c2 = h2.cache[this.resolveTemplate(h2, n3, e9)]) break;
      }
    }
    if (c2) return t3 && c2.compile(), i2 ? void i2(null, c2) : c2;
    return je.asyncIter(this.loaders, (function(t4, r3, i3, s3) {
      function a3(e10, n4) {
        e10 ? s3(e10) : n4 ? (n4.loader = t4, s3(null, n4)) : i3();
      }
      e9 = o2.resolveTemplate(t4, n3, e9), t4.async ? t4.getSource(e9, a3) : a3(null, t4.getSource(e9));
    }), (function(n4, o3) {
      if (o3 || n4 || r2 || (n4 = new Error("template not found: " + e9)), n4) {
        if (i2) return void i2(n4);
        throw n4;
      }
      var c3;
      o3 ? (c3 = new _i(o3.src, a2, o3.path, t3), o3.noCache || (o3.loader.cache[e9] = c3)) : c3 = new _i(Ei, a2, "", t3), i2 ? i2(null, c3) : s2 = c3;
    })), s2;
  }, n2.express = function(e9) {
    return (function(e10, t3) {
      function n3(e11, t4) {
        if (this.name = e11, this.path = e11, this.defaultEngine = t4.defaultEngine, this.ext = Kr.extname(e11), !this.ext && !this.defaultEngine) throw new Error("No default engine was specified and no extension was provided.");
        this.ext || (this.name += this.ext = ("." !== this.defaultEngine[0] ? "." : "") + this.defaultEngine);
      }
      return n3.prototype.render = function(t4, n4) {
        e10.render(this.name, t4, n4);
      }, t3.set("view", n3), t3.set("jinja2Env", e10), e10;
    })(this, e9);
  }, n2.render = function(e9, t3, n3) {
    je.isFunction(t3) && (n3 = t3, t3 = null);
    var r2 = null;
    return this.getTemplate(e9, (function(e10, i2) {
      if (e10 && n3) di(n3, e10);
      else {
        if (e10) throw e10;
        r2 = i2.render(t3, n3);
      }
    })), r2;
  }, n2.renderString = function(e9, t3, n3, r2) {
    return je.isFunction(n3) && (r2 = n3, n3 = {}), new _i(e9, this, (n3 = n3 || {}).path).render(t3, r2);
  }, n2.waterfall = function(e9, t3, n3) {
    return qt(e9, t3, n3);
  }, t2;
})(ui), Ti = (function(e8) {
  function t2() {
    return e8.apply(this, arguments) || this;
  }
  si(t2, e8);
  var n2 = t2.prototype;
  return n2.init = function(e9, t3, n3) {
    var r2 = this;
    this.env = n3 || new mi(), this.ctx = je.extend({}, e9), this.blocks = {}, this.exported = [], je.keys(t3).forEach((function(e10) {
      r2.addBlock(e10, t3[e10]);
    }));
  }, n2.lookup = function(e9) {
    return e9 in this.env.globals && !(e9 in this.ctx) ? this.env.globals[e9] : this.ctx[e9];
  }, n2.setVariable = function(e9, t3) {
    this.ctx[e9] = t3;
  }, n2.getVariables = function() {
    return this.ctx;
  }, n2.addBlock = function(e9, t3) {
    return this.blocks[e9] = this.blocks[e9] || [], this.blocks[e9].push(t3), this;
  }, n2.getBlock = function(e9) {
    if (!this.blocks[e9]) throw new Error('unknown block "' + e9 + '"');
    return this.blocks[e9][0];
  }, n2.getSuper = function(e9, t3, n3, r2, i2, s2) {
    var a2 = je.indexOf(this.blocks[t3] || [], n3), o2 = this.blocks[t3][a2 + 1];
    if (-1 === a2 || !o2) throw new Error('no super block available for "' + t3 + '"');
    o2(e9, this, r2, i2, s2);
  }, n2.addExport = function(e9) {
    this.exported.push(e9);
  }, n2.getExported = function() {
    var e9 = this, t3 = {};
    return this.exported.forEach((function(n3) {
      t3[n3] = e9.ctx[n3];
    })), t3;
  }, t2;
})(hi), _i = (function(e8) {
  function t2() {
    return e8.apply(this, arguments) || this;
  }
  si(t2, e8);
  var n2 = t2.prototype;
  return n2.init = function(e9, t3, n3, r2) {
    if (this.env = t3 || new mi(), je.isObject(e9)) switch (e9.type) {
      case "code":
        this.tmplProps = e9.obj;
        break;
      case "string":
        this.tmplStr = e9.obj;
        break;
      default:
        throw new Error("Unexpected template object type " + e9.type + "; expected 'code', or 'string'");
    }
    else {
      if (!je.isString(e9)) throw new Error("src must be a string or an object describing the source");
      this.tmplStr = e9;
    }
    if (this.path = n3, r2) try {
      this._compile();
    } catch (e10) {
      throw je._prettifyError(this.path, this.env.opts.dev, e10);
    }
    else this.compiled = false;
  }, n2.render = function(e9, t3, n3) {
    var r2 = this;
    "function" == typeof e9 ? (n3 = e9, e9 = {}) : "function" == typeof t3 && (n3 = t3, t3 = null);
    var i2 = !t3;
    try {
      this.compile();
    } catch (e10) {
      var s2 = je._prettifyError(this.path, this.env.opts.dev, e10);
      if (n3) return di(n3, s2);
      throw s2;
    }
    var a2 = new Ti(e9 || {}, this.blocks, this.env), o2 = t3 ? t3.push(true) : new fi();
    o2.topLevel = true;
    var c2 = null, l2 = false;
    return this.rootRenderFunc(this.env, a2, o2, kr, (function(e10, t4) {
      if (!l2 || !n3 || void 0 === t4) if (e10 && (e10 = je._prettifyError(r2.path, r2.env.opts.dev, e10), l2 = true), n3) i2 ? di(n3, e10, t4) : n3(e10, t4);
      else {
        if (e10) throw e10;
        c2 = t4;
      }
    })), c2;
  }, n2.getExported = function(e9, t3, n3) {
    "function" == typeof e9 && (n3 = e9, e9 = {}), "function" == typeof t3 && (n3 = t3, t3 = null);
    try {
      this.compile();
    } catch (e10) {
      if (n3) return n3(e10);
      throw e10;
    }
    var r2 = t3 ? t3.push() : new fi();
    r2.topLevel = true;
    var i2 = new Ti(e9 || {}, this.blocks, this.env);
    this.rootRenderFunc(this.env, i2, r2, kr, (function(e10) {
      e10 ? n3(e10, null) : n3(null, i2.getExported());
    }));
  }, n2.compile = function() {
    this.compiled || this._compile();
  }, n2._compile = function() {
    var e9;
    if (this.tmplProps) e9 = this.tmplProps;
    else {
      var t3 = xr.compile(this.tmplStr, this.env.asyncFilters, this.env.extensionsList, this.path, this.env.opts);
      e9 = new Function(t3)();
    }
    this.blocks = this._getBlocks(e9), this.rootRenderFunc = e9.root, this.compiled = true;
  }, n2._getBlocks = function(e9) {
    var t3 = {};
    return je.keys(e9).forEach((function(n3) {
      "b_" === n3.slice(0, 2) && (t3[n3.slice(2)] = e9[n3]);
    })), t3;
  }, t2;
})(hi), Ai = { Environment: mi, Template: _i };
je._prettifyError;
var gi, Ni = Ai.Environment;
function Ci(e8, t2) {
  var n2;
  return t2 = t2 || {}, je.isObject(e8) && (t2 = e8, e8 = null), ti.FileSystemLoader ? n2 = new ti.FileSystemLoader(e8, { watch: t2.watch, noCache: t2.noCache }) : ti.WebLoader && (n2 = new ti.WebLoader(e8, { useCache: t2.web && t2.web.useCache, async: t2.web && t2.web.async })), gi = new Ni(n2, t2), t2 && t2.express && gi.express(t2.express), gi;
}
ti.FileSystemLoader, ti.NodeResolveLoader, ti.PrecompiledLoader, ti.WebLoader;
var Ii = function(e8, t2, n2) {
  return gi || Ci(), gi.renderString(e8, t2, n2);
}, Si = function() {
  return Si = Object.assign || function(e8) {
    for (var t2, n2 = 1, r2 = arguments.length; n2 < r2; n2++) for (var i2 in t2 = arguments[n2]) Object.prototype.hasOwnProperty.call(t2, i2) && (e8[i2] = t2[i2]);
    return e8;
  }, Si.apply(this, arguments);
};
function bi(e8, t2, n2) {
  return e8 ? e8(null != t2 ? t2 : e8._root.children, null, void 0, n2).toString() : "";
}
function Oi(e8, t2) {
  return bi(this, (function(e9, t3) {
    return !t3 && "object" == typeof e9 && null != e9 && !("length" in e9) && !("type" in e9);
  })(e8) ? void (t2 = e8) : e8, Si(Si(Si({}, n), null == this ? void 0 : this._options), i(null != t2 ? t2 : {})));
}
function yi(e8) {
  return bi(this, e8, Si(Si({}, this._options), { xmlMode: true }));
}
function Li(e8) {
  for (var t2 = e8 || (this ? this.root() : []), n2 = "", r2 = 0; r2 < t2.length; r2++) n2 += z(t2[r2]);
  return n2;
}
function ki(e8, t2, r2) {
  if (void 0 === r2 && (r2 = "boolean" == typeof t2 && t2), !e8 || "string" != typeof e8) return null;
  "boolean" == typeof t2 && (r2 = t2);
  var i2 = this.load(e8, n, false);
  return r2 || i2("script").remove(), i2.root()[0].children.slice();
}
function vi() {
  return this(this._root);
}
function Di(e8, t2) {
  if (t2 === e8) return false;
  for (var n2 = t2; n2 && n2 !== n2.parent; ) if ((n2 = n2.parent) === e8) return true;
  return false;
}
function Ri(e8, t2) {
  if (Mi(e8) && Mi(t2)) {
    for (var n2 = e8.length, r2 = +t2.length, i2 = 0; i2 < r2; i2++) e8[n2++] = t2[i2];
    return e8.length = n2, e8;
  }
}
function Mi(e8) {
  if (Array.isArray(e8)) return true;
  if ("object" != typeof e8 || !Object.prototype.hasOwnProperty.call(e8, "length") || "number" != typeof e8.length || e8.length < 0) return false;
  for (var t2 = 0; t2 < e8.length; t2++) if (!(t2 in e8)) return false;
  return true;
}
var Pi, xi = Object.freeze({ __proto__: null, html: Oi, xml: yi, text: Li, parseHTML: ki, root: vi, contains: Di, merge: Ri, jp: function(e8, t2) {
  return He({ path: e8, json: t2 });
}, jpo: function(e8) {
  return He(e8);
}, jinja2: function(e8, t2) {
  return Ii(e8, t2);
} });
function wi(e8) {
  return null != e8.cheerio;
}
function Bi(e8, t2) {
  for (var n2 = e8.length, r2 = 0; r2 < n2; r2++) t2(e8[r2], r2);
  return e8;
}
function Fi(e8) {
  var t2 = "length" in e8 ? Array.prototype.map.call(e8, (function(e9) {
    return v(e9, true);
  })) : [v(e8, true)], n2 = new N(t2);
  return t2.forEach((function(e9) {
    e9.parent = n2;
  })), t2;
}
function Ui(e8) {
  var t2 = e8.indexOf("<");
  if (t2 < 0 || t2 > e8.length - 3) return false;
  var n2 = e8.charCodeAt(t2 + 1);
  return (n2 >= Pi.LowerA && n2 <= Pi.LowerZ || n2 >= Pi.UpperA && n2 <= Pi.UpperZ || n2 === Pi.Exclamation) && e8.includes(">", t2 + 2);
}
!(function(e8) {
  e8[e8.LowerA = 97] = "LowerA", e8[e8.LowerZ = 122] = "LowerZ", e8[e8.UpperA = 65] = "UpperA", e8[e8.UpperZ = 90] = "UpperZ", e8[e8.Exclamation = 33] = "Exclamation";
})(Pi || (Pi = {}));
var Hi = Object.prototype.hasOwnProperty, Gi = /\s+/, Yi = { null: null, true: true, false: false }, qi = /^(?:autofocus|autoplay|async|checked|controls|defer|disabled|hidden|loop|multiple|open|readonly|required|scoped|selected)$/i, Ki = /^{[^]*}$|^\[[^]*]$/;
function ji(e8, t2, n2) {
  var r2;
  if (e8 && I(e8)) return null !== (r2 = e8.attribs) && void 0 !== r2 || (e8.attribs = {}), t2 ? Hi.call(e8.attribs, t2) ? !n2 && qi.test(t2) ? t2 : e8.attribs[t2] : "option" === e8.name && "value" === t2 ? Li(e8.children) : "input" !== e8.name || "radio" !== e8.attribs.type && "checkbox" !== e8.attribs.type || "value" !== t2 ? void 0 : "on" : e8.attribs;
}
function Vi(e8, t2, n2) {
  null === n2 ? zi(e8, t2) : e8.attribs[t2] = "".concat(n2);
}
function Wi(e8, t2, n2) {
  return t2 in e8 ? e8[t2] : !n2 && qi.test(t2) ? void 0 !== ji(e8, t2, false) : ji(e8, t2, n2);
}
function Qi(e8, t2, n2, r2) {
  t2 in e8 ? e8[t2] = n2 : Vi(e8, t2, !r2 && qi.test(t2) ? n2 ? "" : null : "".concat(n2));
}
function Xi(e8, t2, n2) {
  var r2, i2 = e8;
  null !== (r2 = i2.data) && void 0 !== r2 || (i2.data = {}), "object" == typeof t2 ? Object.assign(i2.data, t2) : "string" == typeof t2 && void 0 !== n2 && (i2.data[t2] = n2);
}
function $i(e8, t2) {
  var n2, r2, i2, s2;
  null == t2 ? r2 = (n2 = Object.keys(e8.attribs).filter((function(e9) {
    return e9.startsWith("data-");
  }))).map((function(e9) {
    return e9.slice("data-".length).replace(/[_.-](\w|$)/g, (function(e10, t3) {
      return t3.toUpperCase();
    }));
  })) : (n2 = ["data-" + (s2 = t2, s2.replace(/[A-Z]/g, "-$&").toLowerCase())], r2 = [t2]);
  for (var a2 = 0; a2 < n2.length; ++a2) {
    var o2 = n2[a2], c2 = r2[a2];
    if (Hi.call(e8.attribs, o2) && !Hi.call(e8.data, c2)) {
      if (i2 = e8.attribs[o2], Hi.call(Yi, i2)) i2 = Yi[i2];
      else if (i2 === String(Number(i2))) i2 = Number(i2);
      else if (Ki.test(i2)) try {
        i2 = JSON.parse(i2);
      } catch (e9) {
      }
      e8.data[c2] = i2;
    }
  }
  return null == t2 ? e8.data : i2;
}
function zi(e8, t2) {
  e8.attribs && Hi.call(e8.attribs, t2) && delete e8.attribs[t2];
}
function Ji(e8) {
  return e8 ? e8.trim().split(Gi) : [];
}
var Zi, es, ts = Object.freeze({ __proto__: null, attr: function(e8, t2) {
  if ("object" == typeof e8 || void 0 !== t2) {
    if ("function" == typeof t2) {
      if ("string" != typeof e8) throw new Error("Bad combination of arguments.");
      return Bi(this, (function(n2, r2) {
        I(n2) && Vi(n2, e8, t2.call(n2, r2, n2.attribs[e8]));
      }));
    }
    return Bi(this, (function(n2) {
      I(n2) && ("object" == typeof e8 ? Object.keys(e8).forEach((function(t3) {
        var r2 = e8[t3];
        Vi(n2, t3, r2);
      })) : Vi(n2, e8, t2));
    }));
  }
  return arguments.length > 1 ? this : ji(this[0], e8, this.options.xmlMode);
}, prop: function(e8, t2) {
  var n2, r2 = this;
  if ("string" == typeof e8 && void 0 === t2) {
    var i2 = this[0];
    if (!i2 || !I(i2)) return;
    switch (e8) {
      case "style":
        var s2 = this.css(), a2 = Object.keys(s2);
        return a2.forEach((function(e9, t3) {
          s2[t3] = e9;
        })), s2.length = a2.length, s2;
      case "tagName":
      case "nodeName":
        return i2.name.toUpperCase();
      case "href":
      case "src":
        var o2 = null === (n2 = i2.attribs) || void 0 === n2 ? void 0 : n2[e8];
        return "undefined" == typeof URL || ("href" !== e8 || "a" !== i2.tagName && "link" !== i2.name) && ("src" !== e8 || "img" !== i2.tagName && "iframe" !== i2.tagName && "audio" !== i2.tagName && "video" !== i2.tagName && "source" !== i2.tagName) || void 0 === o2 || !this.options.baseURI ? o2 : new URL(o2, this.options.baseURI).href;
      case "innerText":
        return J(i2);
      case "textContent":
        return z(i2);
      case "outerHTML":
        return this.clone().wrap("<container />").parent().html();
      case "innerHTML":
        return this.html();
      default:
        return Wi(i2, e8, this.options.xmlMode);
    }
  }
  if ("object" == typeof e8 || void 0 !== t2) {
    if ("function" == typeof t2) {
      if ("object" == typeof e8) throw new Error("Bad combination of arguments.");
      return Bi(this, (function(n3, i3) {
        I(n3) && Qi(n3, e8, t2.call(n3, i3, Wi(n3, e8, r2.options.xmlMode)), r2.options.xmlMode);
      }));
    }
    return Bi(this, (function(n3) {
      I(n3) && ("object" == typeof e8 ? Object.keys(e8).forEach((function(t3) {
        var i3 = e8[t3];
        Qi(n3, t3, i3, r2.options.xmlMode);
      })) : Qi(n3, e8, t2, r2.options.xmlMode));
    }));
  }
}, data: function(e8, t2) {
  var n2, r2 = this[0];
  if (r2 && I(r2)) {
    var i2 = r2;
    return null !== (n2 = i2.data) && void 0 !== n2 || (i2.data = {}), e8 ? "object" == typeof e8 || void 0 !== t2 ? (Bi(this, (function(n3) {
      I(n3) && ("object" == typeof e8 ? Xi(n3, e8) : Xi(n3, e8, t2));
    })), this) : Hi.call(i2.data, e8) ? i2.data[e8] : $i(i2, e8) : $i(i2);
  }
}, val: function(e8) {
  var t2 = 0 === arguments.length, n2 = this[0];
  if (!n2 || !I(n2)) return t2 ? void 0 : this;
  switch (n2.name) {
    case "textarea":
      return this.text(e8);
    case "select":
      var r2 = this.find("option:selected");
      if (!t2) {
        if (null == this.attr("multiple") && "object" == typeof e8) return this;
        this.find("option").removeAttr("selected");
        for (var i2 = "object" != typeof e8 ? [e8] : e8, s2 = 0; s2 < i2.length; s2++) this.find('option[value="'.concat(i2[s2], '"]')).attr("selected", "");
        return this;
      }
      return this.attr("multiple") ? r2.toArray().map((function(e9) {
        return Li(e9.children);
      })) : r2.attr("value");
    case "input":
    case "option":
      return t2 ? this.attr("value") : this.attr("value", e8);
  }
}, removeAttr: function(e8) {
  for (var t2 = Ji(e8), n2 = function(e9) {
    Bi(r2, (function(n3) {
      I(n3) && zi(n3, t2[e9]);
    }));
  }, r2 = this, i2 = 0; i2 < t2.length; i2++) n2(i2);
  return this;
}, hasClass: function(e8) {
  return this.toArray().some((function(t2) {
    var n2 = I(t2) && t2.attribs.class, r2 = -1;
    if (n2 && e8.length) for (; (r2 = n2.indexOf(e8, r2 + 1)) > -1; ) {
      var i2 = r2 + e8.length;
      if ((0 === r2 || Gi.test(n2[r2 - 1])) && (i2 === n2.length || Gi.test(n2[i2]))) return true;
    }
    return false;
  }));
}, addClass: function e5(t2) {
  if ("function" == typeof t2) return Bi(this, (function(n3, r3) {
    if (I(n3)) {
      var i3 = n3.attribs.class || "";
      e5.call([n3], t2.call(n3, r3, i3));
    }
  }));
  if (!t2 || "string" != typeof t2) return this;
  for (var n2 = t2.split(Gi), r2 = this.length, i2 = 0; i2 < r2; i2++) {
    var s2 = this[i2];
    if (I(s2)) {
      var a2 = ji(s2, "class", false);
      if (a2) {
        for (var o2 = " ".concat(a2, " "), c2 = 0; c2 < n2.length; c2++) {
          var l2 = "".concat(n2[c2], " ");
          o2.includes(" ".concat(l2)) || (o2 += l2);
        }
        Vi(s2, "class", o2.trim());
      } else Vi(s2, "class", n2.join(" ").trim());
    }
  }
  return this;
}, removeClass: function e6(t2) {
  if ("function" == typeof t2) return Bi(this, (function(n3, r3) {
    I(n3) && e6.call([n3], t2.call(n3, r3, n3.attribs.class || ""));
  }));
  var n2 = Ji(t2), r2 = n2.length, i2 = 0 === arguments.length;
  return Bi(this, (function(e8) {
    if (I(e8)) if (i2) e8.attribs.class = "";
    else {
      for (var t3 = Ji(e8.attribs.class), s2 = false, a2 = 0; a2 < r2; a2++) {
        var o2 = t3.indexOf(n2[a2]);
        o2 >= 0 && (t3.splice(o2, 1), s2 = true, a2--);
      }
      s2 && (e8.attribs.class = t3.join(" "));
    }
  }));
}, toggleClass: function e7(t2, n2) {
  if ("function" == typeof t2) return Bi(this, (function(r3, i3) {
    I(r3) && e7.call([r3], t2.call(r3, i3, r3.attribs.class || "", n2), n2);
  }));
  if (!t2 || "string" != typeof t2) return this;
  for (var r2 = t2.split(Gi), i2 = r2.length, s2 = "boolean" == typeof n2 ? n2 ? 1 : -1 : 0, a2 = this.length, o2 = 0; o2 < a2; o2++) {
    var c2 = this[o2];
    if (I(c2)) {
      for (var l2 = Ji(c2.attribs.class), h2 = 0; h2 < i2; h2++) {
        var u2 = l2.indexOf(r2[h2]);
        s2 >= 0 && u2 < 0 ? l2.push(r2[h2]) : s2 <= 0 && u2 >= 0 && l2.splice(u2, 1);
      }
      c2.attribs.class = l2.join(" ");
    }
  }
  return this;
} });
!(function(e8) {
  e8.Attribute = "attribute", e8.Pseudo = "pseudo", e8.PseudoElement = "pseudo-element", e8.Tag = "tag", e8.Universal = "universal", e8.Adjacent = "adjacent", e8.Child = "child", e8.Descendant = "descendant", e8.Parent = "parent", e8.Sibling = "sibling", e8.ColumnCombinator = "column-combinator";
})(Zi || (Zi = {})), (function(e8) {
  e8.Any = "any", e8.Element = "element", e8.End = "end", e8.Equals = "equals", e8.Exists = "exists", e8.Hyphen = "hyphen", e8.Not = "not", e8.Start = "start";
})(es || (es = {}));
const ns = /^[^\\#]?(?:\\(?:[\da-f]{1,6}\s?|.)|[\w\-\u00b0-\uFFFF])+/, rs = /\\([\da-f]{1,6}\s?|(\s)|.)/gi, is = /* @__PURE__ */ new Map([[126, es.Element], [94, es.Start], [36, es.End], [42, es.Any], [33, es.Not], [124, es.Hyphen]]), ss = /* @__PURE__ */ new Set(["has", "not", "matches", "is", "where", "host", "host-context"]);
function as(e8) {
  switch (e8.type) {
    case Zi.Adjacent:
    case Zi.Child:
    case Zi.Descendant:
    case Zi.Parent:
    case Zi.Sibling:
    case Zi.ColumnCombinator:
      return true;
    default:
      return false;
  }
}
const os = /* @__PURE__ */ new Set(["contains", "icontains"]);
function cs(e8, t2, n2) {
  const r2 = parseInt(t2, 16) - 65536;
  return r2 != r2 || n2 ? t2 : r2 < 0 ? String.fromCharCode(r2 + 65536) : String.fromCharCode(r2 >> 10 | 55296, 1023 & r2 | 56320);
}
function ls(e8) {
  return e8.replace(rs, cs);
}
function hs(e8) {
  return 39 === e8 || 34 === e8;
}
function us(e8) {
  return 32 === e8 || 9 === e8 || 10 === e8 || 12 === e8 || 13 === e8;
}
function ps(e8) {
  const t2 = [], n2 = fs(t2, `${e8}`, 0);
  if (n2 < e8.length) throw new Error(`Unmatched selector: ${e8.slice(n2)}`);
  return t2;
}
function fs(e8, t2, n2) {
  let r2 = [];
  function i2(e9) {
    const r3 = t2.slice(n2 + e9).match(ns);
    if (!r3) throw new Error(`Expected name, found ${t2.slice(n2)}`);
    const [i3] = r3;
    return n2 += e9 + i3.length, ls(i3);
  }
  function s2(e9) {
    for (n2 += e9; n2 < t2.length && us(t2.charCodeAt(n2)); ) n2++;
  }
  function a2() {
    const e9 = n2 += 1;
    let r3 = 1;
    for (; r3 > 0 && n2 < t2.length; n2++) 40 !== t2.charCodeAt(n2) || o2(n2) ? 41 !== t2.charCodeAt(n2) || o2(n2) || r3-- : r3++;
    if (r3) throw new Error("Parenthesis not matched");
    return ls(t2.slice(e9, n2 - 1));
  }
  function o2(e9) {
    let n3 = 0;
    for (; 92 === t2.charCodeAt(--e9); ) n3++;
    return 1 == (1 & n3);
  }
  function c2() {
    if (r2.length > 0 && as(r2[r2.length - 1])) throw new Error("Did not expect successive traversals.");
  }
  function l2(e9) {
    r2.length > 0 && r2[r2.length - 1].type === Zi.Descendant ? r2[r2.length - 1].type = e9 : (c2(), r2.push({ type: e9 }));
  }
  function h2(e9, t3) {
    r2.push({ type: Zi.Attribute, name: e9, action: t3, value: i2(1), namespace: null, ignoreCase: "quirks" });
  }
  function u2() {
    if (r2.length && r2[r2.length - 1].type === Zi.Descendant && r2.pop(), 0 === r2.length) throw new Error("Empty sub-selector");
    e8.push(r2);
  }
  if (s2(0), t2.length === n2) return n2;
  e: for (; n2 < t2.length; ) {
    const e9 = t2.charCodeAt(n2);
    switch (e9) {
      case 32:
      case 9:
      case 10:
      case 12:
      case 13:
        0 !== r2.length && r2[0].type === Zi.Descendant || (c2(), r2.push({ type: Zi.Descendant })), s2(1);
        break;
      case 62:
        l2(Zi.Child), s2(1);
        break;
      case 60:
        l2(Zi.Parent), s2(1);
        break;
      case 126:
        l2(Zi.Sibling), s2(1);
        break;
      case 43:
        l2(Zi.Adjacent), s2(1);
        break;
      case 46:
        h2("class", es.Element);
        break;
      case 35:
        h2("id", es.Equals);
        break;
      case 91: {
        let e10;
        s2(1);
        let a3 = null;
        124 === t2.charCodeAt(n2) ? e10 = i2(1) : t2.startsWith("*|", n2) ? (a3 = "*", e10 = i2(2)) : (e10 = i2(0), 124 === t2.charCodeAt(n2) && 61 !== t2.charCodeAt(n2 + 1) && (a3 = e10, e10 = i2(1))), s2(0);
        let c3 = es.Exists;
        const l3 = is.get(t2.charCodeAt(n2));
        if (l3) {
          if (c3 = l3, 61 !== t2.charCodeAt(n2 + 1)) throw new Error("Expected `=`");
          s2(2);
        } else 61 === t2.charCodeAt(n2) && (c3 = es.Equals, s2(1));
        let h3 = "", u3 = null;
        if ("exists" !== c3) {
          if (hs(t2.charCodeAt(n2))) {
            const e12 = t2.charCodeAt(n2);
            let r3 = n2 + 1;
            for (; r3 < t2.length && (t2.charCodeAt(r3) !== e12 || o2(r3)); ) r3 += 1;
            if (t2.charCodeAt(r3) !== e12) throw new Error("Attribute value didn't end");
            h3 = ls(t2.slice(n2 + 1, r3)), n2 = r3 + 1;
          } else {
            const e12 = n2;
            for (; n2 < t2.length && (!us(t2.charCodeAt(n2)) && 93 !== t2.charCodeAt(n2) || o2(n2)); ) n2 += 1;
            h3 = ls(t2.slice(e12, n2));
          }
          s2(0);
          const e11 = 32 | t2.charCodeAt(n2);
          115 === e11 ? (u3 = false, s2(1)) : 105 === e11 && (u3 = true, s2(1));
        }
        if (93 !== t2.charCodeAt(n2)) throw new Error("Attribute selector didn't terminate");
        n2 += 1;
        const p2 = { type: Zi.Attribute, name: e10, action: c3, value: h3, namespace: a3, ignoreCase: u3 };
        r2.push(p2);
        break;
      }
      case 58: {
        if (58 === t2.charCodeAt(n2 + 1)) {
          r2.push({ type: Zi.PseudoElement, name: i2(2).toLowerCase(), data: 40 === t2.charCodeAt(n2) ? a2() : null });
          continue;
        }
        const e10 = i2(1).toLowerCase();
        let s3 = null;
        if (40 === t2.charCodeAt(n2)) if (ss.has(e10)) {
          if (hs(t2.charCodeAt(n2 + 1))) throw new Error(`Pseudo-selector ${e10} cannot be quoted`);
          if (s3 = [], n2 = fs(s3, t2, n2 + 1), 41 !== t2.charCodeAt(n2)) throw new Error(`Missing closing parenthesis in :${e10} (${t2})`);
          n2 += 1;
        } else {
          if (s3 = a2(), os.has(e10)) {
            const e11 = s3.charCodeAt(0);
            e11 === s3.charCodeAt(s3.length - 1) && hs(e11) && (s3 = s3.slice(1, -1));
          }
          s3 = ls(s3);
        }
        r2.push({ type: Zi.Pseudo, name: e10, data: s3 });
        break;
      }
      case 44:
        u2(), r2 = [], s2(1);
        break;
      default: {
        if (t2.startsWith("/*", n2)) {
          const e10 = t2.indexOf("*/", n2 + 2);
          if (e10 < 0) throw new Error("Comment was not terminated");
          n2 = e10 + 2, 0 === r2.length && s2(0);
          break;
        }
        let a3, o3 = null;
        if (42 === e9) n2 += 1, a3 = "*";
        else if (124 === e9) {
          if (a3 = "", 124 === t2.charCodeAt(n2 + 1)) {
            l2(Zi.ColumnCombinator), s2(2);
            break;
          }
        } else {
          if (!ns.test(t2.slice(n2))) break e;
          a3 = i2(0);
        }
        124 === t2.charCodeAt(n2) && 124 !== t2.charCodeAt(n2 + 1) && (o3 = a3, 42 === t2.charCodeAt(n2 + 1) ? (a3 = "*", n2 += 2) : a3 = i2(1)), r2.push("*" === a3 ? { type: Zi.Universal, namespace: o3 } : { type: Zi.Tag, name: a3, namespace: o3 });
      }
    }
  }
  return u2(), n2;
}
var ds = { trueFunc: function() {
  return true;
}, falseFunc: function() {
  return false;
} }, Es = ds.trueFunc;
const ms = /* @__PURE__ */ new Map([[Zi.Universal, 50], [Zi.Tag, 30], [Zi.Attribute, 1], [Zi.Pseudo, 0]]);
function Ts(e8) {
  return !ms.has(e8.type);
}
const _s = /* @__PURE__ */ new Map([[es.Exists, 10], [es.Equals, 8], [es.Not, 7], [es.Start, 6], [es.End, 6], [es.Any, 5]]);
function As(e8) {
  const t2 = e8.map(gs);
  for (let n2 = 1; n2 < e8.length; n2++) {
    const r2 = t2[n2];
    if (!(r2 < 0)) for (let i2 = n2 - 1; i2 >= 0 && r2 < t2[i2]; i2--) {
      const n3 = e8[i2 + 1];
      e8[i2 + 1] = e8[i2], e8[i2] = n3, t2[i2 + 1] = t2[i2], t2[i2] = r2;
    }
  }
}
function gs(e8) {
  var t2, n2;
  let r2 = null !== (t2 = ms.get(e8.type)) && void 0 !== t2 ? t2 : -1;
  return e8.type === Zi.Attribute ? (r2 = null !== (n2 = _s.get(e8.action)) && void 0 !== n2 ? n2 : 4, e8.action === es.Equals && "id" === e8.name && (r2 = 9), e8.ignoreCase && (r2 >>= 1)) : e8.type === Zi.Pseudo && (e8.data ? "has" === e8.name || "contains" === e8.name ? r2 = 0 : Array.isArray(e8.data) ? (r2 = Math.min(...e8.data.map(((e9) => Math.min(...e9.map(gs))))), r2 < 0 && (r2 = 0)) : r2 = 2 : r2 = 3), r2;
}
const Ns = /[-[\]{}()*+?.,\\^$|#\s]/g;
function Cs(e8) {
  return e8.replace(Ns, "\\$&");
}
const Is = /* @__PURE__ */ new Set(["accept", "accept-charset", "align", "alink", "axis", "bgcolor", "charset", "checked", "clear", "codetype", "color", "compact", "declare", "defer", "dir", "direction", "disabled", "enctype", "face", "frame", "hreflang", "http-equiv", "lang", "language", "link", "media", "method", "multiple", "nohref", "noresize", "noshade", "nowrap", "readonly", "rel", "rev", "rules", "scope", "scrolling", "selected", "shape", "target", "text", "type", "valign", "valuetype", "vlink"]);
function Ss(e8, t2) {
  return "boolean" == typeof e8.ignoreCase ? e8.ignoreCase : "quirks" === e8.ignoreCase ? !!t2.quirksMode : !t2.xmlMode && Is.has(e8.name);
}
const bs = { equals(e8, t2, n2) {
  const { adapter: r2 } = n2, { name: i2 } = t2;
  let { value: s2 } = t2;
  return Ss(t2, n2) ? (s2 = s2.toLowerCase(), (t3) => {
    const n3 = r2.getAttributeValue(t3, i2);
    return null != n3 && n3.length === s2.length && n3.toLowerCase() === s2 && e8(t3);
  }) : (t3) => r2.getAttributeValue(t3, i2) === s2 && e8(t3);
}, hyphen(e8, t2, n2) {
  const { adapter: r2 } = n2, { name: i2 } = t2;
  let { value: s2 } = t2;
  const a2 = s2.length;
  return Ss(t2, n2) ? (s2 = s2.toLowerCase(), function(t3) {
    const n3 = r2.getAttributeValue(t3, i2);
    return null != n3 && (n3.length === a2 || "-" === n3.charAt(a2)) && n3.substr(0, a2).toLowerCase() === s2 && e8(t3);
  }) : function(t3) {
    const n3 = r2.getAttributeValue(t3, i2);
    return null != n3 && (n3.length === a2 || "-" === n3.charAt(a2)) && n3.substr(0, a2) === s2 && e8(t3);
  };
}, element(e8, t2, n2) {
  const { adapter: r2 } = n2, { name: i2, value: s2 } = t2;
  if (/\s/.test(s2)) return ds.falseFunc;
  const a2 = new RegExp(`(?:^|\\s)${Cs(s2)}(?:$|\\s)`, Ss(t2, n2) ? "i" : "");
  return function(t3) {
    const n3 = r2.getAttributeValue(t3, i2);
    return null != n3 && n3.length >= s2.length && a2.test(n3) && e8(t3);
  };
}, exists: (e8, { name: t2 }, { adapter: n2 }) => (r2) => n2.hasAttrib(r2, t2) && e8(r2), start(e8, t2, n2) {
  const { adapter: r2 } = n2, { name: i2 } = t2;
  let { value: s2 } = t2;
  const a2 = s2.length;
  return 0 === a2 ? ds.falseFunc : Ss(t2, n2) ? (s2 = s2.toLowerCase(), (t3) => {
    const n3 = r2.getAttributeValue(t3, i2);
    return null != n3 && n3.length >= a2 && n3.substr(0, a2).toLowerCase() === s2 && e8(t3);
  }) : (t3) => {
    var n3;
    return !!(null === (n3 = r2.getAttributeValue(t3, i2)) || void 0 === n3 ? void 0 : n3.startsWith(s2)) && e8(t3);
  };
}, end(e8, t2, n2) {
  const { adapter: r2 } = n2, { name: i2 } = t2;
  let { value: s2 } = t2;
  const a2 = -s2.length;
  return 0 === a2 ? ds.falseFunc : Ss(t2, n2) ? (s2 = s2.toLowerCase(), (t3) => {
    var n3;
    return (null === (n3 = r2.getAttributeValue(t3, i2)) || void 0 === n3 ? void 0 : n3.substr(a2).toLowerCase()) === s2 && e8(t3);
  }) : (t3) => {
    var n3;
    return !!(null === (n3 = r2.getAttributeValue(t3, i2)) || void 0 === n3 ? void 0 : n3.endsWith(s2)) && e8(t3);
  };
}, any(e8, t2, n2) {
  const { adapter: r2 } = n2, { name: i2, value: s2 } = t2;
  if ("" === s2) return ds.falseFunc;
  if (Ss(t2, n2)) {
    const t3 = new RegExp(Cs(s2), "i");
    return function(n3) {
      const a2 = r2.getAttributeValue(n3, i2);
      return null != a2 && a2.length >= s2.length && t3.test(a2) && e8(n3);
    };
  }
  return (t3) => {
    var n3;
    return !!(null === (n3 = r2.getAttributeValue(t3, i2)) || void 0 === n3 ? void 0 : n3.includes(s2)) && e8(t3);
  };
}, not(e8, t2, n2) {
  const { adapter: r2 } = n2, { name: i2 } = t2;
  let { value: s2 } = t2;
  return "" === s2 ? (t3) => !!r2.getAttributeValue(t3, i2) && e8(t3) : Ss(t2, n2) ? (s2 = s2.toLowerCase(), (t3) => {
    const n3 = r2.getAttributeValue(t3, i2);
    return (null == n3 || n3.length !== s2.length || n3.toLowerCase() !== s2) && e8(t3);
  }) : (t3) => r2.getAttributeValue(t3, i2) !== s2 && e8(t3);
} };
var Os = Ke((function(e8, t2) {
  Object.defineProperty(t2, "__esModule", { value: true }), t2.parse = void 0;
  var n2 = /* @__PURE__ */ new Set([9, 10, 12, 13, 32]), r2 = "0".charCodeAt(0), i2 = "9".charCodeAt(0);
  t2.parse = function(e9) {
    if ("even" === (e9 = e9.trim().toLowerCase())) return [2, 0];
    if ("odd" === e9) return [2, 1];
    var t3 = 0, s2 = 0, a2 = c2(), o2 = l2();
    if (t3 < e9.length && "n" === e9.charAt(t3) && (t3++, s2 = a2 * (null != o2 ? o2 : 1), h2(), t3 < e9.length ? (a2 = c2(), h2(), o2 = l2()) : a2 = o2 = 0), null === o2 || t3 < e9.length) throw new Error("n-th rule couldn't be parsed ('" + e9 + "')");
    return [s2, a2 * o2];
    function c2() {
      return "-" === e9.charAt(t3) ? (t3++, -1) : ("+" === e9.charAt(t3) && t3++, 1);
    }
    function l2() {
      for (var n3 = t3, s3 = 0; t3 < e9.length && e9.charCodeAt(t3) >= r2 && e9.charCodeAt(t3) <= i2; ) s3 = 10 * s3 + (e9.charCodeAt(t3) - r2), t3++;
      return t3 === n3 ? null : s3;
    }
    function h2() {
      for (; t3 < e9.length && n2.has(e9.charCodeAt(t3)); ) t3++;
    }
  };
}));
qe(Os), Os.parse;
var ys = Ke((function(e8, t2) {
  Object.defineProperty(t2, "__esModule", { value: true }), t2.compile = void 0, t2.compile = function(e9) {
    var t3 = e9[0], n2 = e9[1] - 1;
    if (n2 < 0 && t3 <= 0) return ds.falseFunc;
    if (-1 === t3) return function(e10) {
      return e10 <= n2;
    };
    if (0 === t3) return function(e10) {
      return e10 === n2;
    };
    if (1 === t3) return n2 < 0 ? ds.trueFunc : function(e10) {
      return e10 >= n2;
    };
    var r2 = Math.abs(t3), i2 = (n2 % r2 + r2) % r2;
    return t3 > 1 ? function(e10) {
      return e10 >= n2 && e10 % r2 === i2;
    } : function(e10) {
      return e10 <= n2 && e10 % r2 === i2;
    };
  };
}));
qe(ys), ys.compile;
var Ls = Os, ks = ys, vs = Ke((function(e8, t2) {
  Object.defineProperty(t2, "__esModule", { value: true }), t2.compile = t2.parse = void 0, Object.defineProperty(t2, "parse", { enumerable: true, get: function() {
    return Ls.parse;
  } }), Object.defineProperty(t2, "compile", { enumerable: true, get: function() {
    return ks.compile;
  } }), t2.default = function(e9) {
    return (0, ks.compile)((0, Ls.parse)(e9));
  };
})), Ds = qe(vs);
function Rs(e8, t2) {
  return (n2) => {
    const r2 = t2.getParent(n2);
    return null != r2 && t2.isTag(r2) && e8(n2);
  };
}
vs.compile, vs.parse;
const Ms = { contains: (e8, t2, { adapter: n2 }) => function(r2) {
  return e8(r2) && n2.getText(r2).includes(t2);
}, icontains(e8, t2, { adapter: n2 }) {
  const r2 = t2.toLowerCase();
  return function(t3) {
    return e8(t3) && n2.getText(t3).toLowerCase().includes(r2);
  };
}, "nth-child"(e8, t2, { adapter: n2, equals: r2 }) {
  const i2 = Ds(t2);
  return i2 === ds.falseFunc ? ds.falseFunc : i2 === ds.trueFunc ? Rs(e8, n2) : function(t3) {
    const s2 = n2.getSiblings(t3);
    let a2 = 0;
    for (let e9 = 0; e9 < s2.length && !r2(t3, s2[e9]); e9++) n2.isTag(s2[e9]) && a2++;
    return i2(a2) && e8(t3);
  };
}, "nth-last-child"(e8, t2, { adapter: n2, equals: r2 }) {
  const i2 = Ds(t2);
  return i2 === ds.falseFunc ? ds.falseFunc : i2 === ds.trueFunc ? Rs(e8, n2) : function(t3) {
    const s2 = n2.getSiblings(t3);
    let a2 = 0;
    for (let e9 = s2.length - 1; e9 >= 0 && !r2(t3, s2[e9]); e9--) n2.isTag(s2[e9]) && a2++;
    return i2(a2) && e8(t3);
  };
}, "nth-of-type"(e8, t2, { adapter: n2, equals: r2 }) {
  const i2 = Ds(t2);
  return i2 === ds.falseFunc ? ds.falseFunc : i2 === ds.trueFunc ? Rs(e8, n2) : function(t3) {
    const s2 = n2.getSiblings(t3);
    let a2 = 0;
    for (let e9 = 0; e9 < s2.length; e9++) {
      const i3 = s2[e9];
      if (r2(t3, i3)) break;
      n2.isTag(i3) && n2.getName(i3) === n2.getName(t3) && a2++;
    }
    return i2(a2) && e8(t3);
  };
}, "nth-last-of-type"(e8, t2, { adapter: n2, equals: r2 }) {
  const i2 = Ds(t2);
  return i2 === ds.falseFunc ? ds.falseFunc : i2 === ds.trueFunc ? Rs(e8, n2) : function(t3) {
    const s2 = n2.getSiblings(t3);
    let a2 = 0;
    for (let e9 = s2.length - 1; e9 >= 0; e9--) {
      const i3 = s2[e9];
      if (r2(t3, i3)) break;
      n2.isTag(i3) && n2.getName(i3) === n2.getName(t3) && a2++;
    }
    return i2(a2) && e8(t3);
  };
}, root: (e8, t2, { adapter: n2 }) => (t3) => {
  const r2 = n2.getParent(t3);
  return (null == r2 || !n2.isTag(r2)) && e8(t3);
}, scope(e8, t2, n2, r2) {
  const { equals: i2 } = n2;
  return r2 && 0 !== r2.length ? 1 === r2.length ? (t3) => i2(r2[0], t3) && e8(t3) : (t3) => r2.includes(t3) && e8(t3) : Ms.root(e8, t2, n2);
}, hover: Ps("isHovered"), visited: Ps("isVisited"), active: Ps("isActive") };
function Ps(e8) {
  return function(t2, n2, { adapter: r2 }) {
    const i2 = r2[e8];
    return "function" != typeof i2 ? ds.falseFunc : function(e9) {
      return i2(e9) && t2(e9);
    };
  };
}
const xs = { empty: (e8, { adapter: t2 }) => !t2.getChildren(e8).some(((e9) => t2.isTag(e9) || "" !== t2.getText(e9))), "first-child"(e8, { adapter: t2, equals: n2 }) {
  if (t2.prevElementSibling) return null == t2.prevElementSibling(e8);
  const r2 = t2.getSiblings(e8).find(((e9) => t2.isTag(e9)));
  return null != r2 && n2(e8, r2);
}, "last-child"(e8, { adapter: t2, equals: n2 }) {
  const r2 = t2.getSiblings(e8);
  for (let i2 = r2.length - 1; i2 >= 0; i2--) {
    if (n2(e8, r2[i2])) return true;
    if (t2.isTag(r2[i2])) break;
  }
  return false;
}, "first-of-type"(e8, { adapter: t2, equals: n2 }) {
  const r2 = t2.getSiblings(e8), i2 = t2.getName(e8);
  for (let s2 = 0; s2 < r2.length; s2++) {
    const a2 = r2[s2];
    if (n2(e8, a2)) return true;
    if (t2.isTag(a2) && t2.getName(a2) === i2) break;
  }
  return false;
}, "last-of-type"(e8, { adapter: t2, equals: n2 }) {
  const r2 = t2.getSiblings(e8), i2 = t2.getName(e8);
  for (let s2 = r2.length - 1; s2 >= 0; s2--) {
    const a2 = r2[s2];
    if (n2(e8, a2)) return true;
    if (t2.isTag(a2) && t2.getName(a2) === i2) break;
  }
  return false;
}, "only-of-type"(e8, { adapter: t2, equals: n2 }) {
  const r2 = t2.getName(e8);
  return t2.getSiblings(e8).every(((i2) => n2(e8, i2) || !t2.isTag(i2) || t2.getName(i2) !== r2));
}, "only-child": (e8, { adapter: t2, equals: n2 }) => t2.getSiblings(e8).every(((r2) => n2(e8, r2) || !t2.isTag(r2))) };
function ws(e8, t2, n2, r2) {
  if (null === n2) {
    if (e8.length > r2) throw new Error(`Pseudo-class :${t2} requires an argument`);
  } else if (e8.length === r2) throw new Error(`Pseudo-class :${t2} doesn't have any arguments`);
}
const Bs = { "any-link": ":is(a, area, link)[href]", link: ":any-link:not(:visited)", disabled: ":is(\n        :is(button, input, select, textarea, optgroup, option)[disabled],\n        optgroup[disabled] > option,\n        fieldset[disabled]:not(fieldset[disabled] legend:first-of-type *)\n    )", enabled: ":not(:disabled)", checked: ":is(:is(input[type=radio], input[type=checkbox])[checked], option:selected)", required: ":is(input, select, textarea)[required]", optional: ":is(input, select, textarea):not([required])", selected: "option:is([selected], select:not([multiple]):not(:has(> option[selected])) > :first-of-type)", checkbox: "[type=checkbox]", file: "[type=file]", password: "[type=password]", radio: "[type=radio]", reset: "[type=reset]", image: "[type=image]", submit: "[type=submit]", parent: ":not(:empty)", header: ":is(h1, h2, h3, h4, h5, h6)", button: ":is(button, input[type=button])", input: ":is(input, textarea, select, button)", text: "input:is(:not([type!='']), [type=text])" }, Fs = {};
function Us(e8, t2) {
  const n2 = t2.getSiblings(e8);
  if (n2.length <= 1) return [];
  const r2 = n2.indexOf(e8);
  return r2 < 0 || r2 === n2.length - 1 ? [] : n2.slice(r2 + 1).filter(t2.isTag);
}
function Hs(e8) {
  return { xmlMode: !!e8.xmlMode, lowerCaseAttributeNames: !!e8.lowerCaseAttributeNames, lowerCaseTags: !!e8.lowerCaseTags, quirksMode: !!e8.quirksMode, cacheResults: !!e8.cacheResults, pseudos: e8.pseudos, adapter: e8.adapter, equals: e8.equals };
}
const Gs = (e8, t2, n2, r2, i2) => {
  const s2 = i2(t2, Hs(n2), r2);
  return s2 === ds.trueFunc ? e8 : s2 === ds.falseFunc ? ds.falseFunc : (t3) => s2(t3) && e8(t3);
}, Ys = { is: Gs, matches: Gs, where: Gs, not(e8, t2, n2, r2, i2) {
  const s2 = i2(t2, Hs(n2), r2);
  return s2 === ds.falseFunc ? e8 : s2 === ds.trueFunc ? ds.falseFunc : (t3) => !s2(t3) && e8(t3);
}, has(e8, t2, n2, r2, i2) {
  const { adapter: s2 } = n2, a2 = Hs(n2);
  a2.relativeSelector = true;
  const o2 = t2.some(((e9) => e9.some(Ts))) ? [Fs] : void 0, c2 = i2(t2, a2, o2);
  if (c2 === ds.falseFunc) return ds.falseFunc;
  const l2 = (function(e9, t3) {
    return e9 === ds.falseFunc ? ds.falseFunc : (n3) => t3.isTag(n3) && e9(n3);
  })(c2, s2);
  if (o2 && c2 !== ds.trueFunc) {
    const { shouldTestNextSiblings: t3 = false } = c2;
    return (n3) => {
      if (!e8(n3)) return false;
      o2[0] = n3;
      const r3 = s2.getChildren(n3), i3 = t3 ? [...r3, ...Us(n3, s2)] : r3;
      return s2.existsOne(l2, i3);
    };
  }
  return (t3) => e8(t3) && s2.existsOne(l2, s2.getChildren(t3));
} };
function qs(e8, t2) {
  const n2 = t2.getParent(e8);
  return n2 && t2.isTag(n2) ? n2 : null;
}
function Ks(e8, t2, n2, r2, i2) {
  const { adapter: s2, equals: a2 } = n2;
  switch (t2.type) {
    case Zi.PseudoElement:
      throw new Error("Pseudo-elements are not supported by css-select");
    case Zi.ColumnCombinator:
      throw new Error("Column combinators are not yet supported by css-select");
    case Zi.Attribute:
      if (null != t2.namespace) throw new Error("Namespaced attributes are not yet supported by css-select");
      return n2.xmlMode && !n2.lowerCaseAttributeNames || (t2.name = t2.name.toLowerCase()), bs[t2.action](e8, t2, n2);
    case Zi.Pseudo:
      return (function(e9, t3, n3, r3, i3) {
        var s3;
        const { name: a3, data: o2 } = t3;
        if (Array.isArray(o2)) {
          if (!(a3 in Ys)) throw new Error(`Unknown pseudo-class :${a3}(${o2})`);
          return Ys[a3](e9, o2, n3, r3, i3);
        }
        const c2 = null === (s3 = n3.pseudos) || void 0 === s3 ? void 0 : s3[a3], l2 = "string" == typeof c2 ? c2 : Bs[a3];
        if ("string" == typeof l2) {
          if (null != o2) throw new Error(`Pseudo ${a3} doesn't have any arguments`);
          const t4 = ps(l2);
          return Ys.is(e9, t4, n3, r3, i3);
        }
        if ("function" == typeof c2) return ws(c2, a3, o2, 1), (t4) => c2(t4, o2) && e9(t4);
        if (a3 in Ms) return Ms[a3](e9, o2, n3, r3);
        if (a3 in xs) {
          const t4 = xs[a3];
          return ws(t4, a3, o2, 2), (r4) => t4(r4, n3, o2) && e9(r4);
        }
        throw new Error(`Unknown pseudo-class :${a3}`);
      })(e8, t2, n2, r2, i2);
    case Zi.Tag: {
      if (null != t2.namespace) throw new Error("Namespaced tag names are not yet supported by css-select");
      let { name: r3 } = t2;
      return n2.xmlMode && !n2.lowerCaseTags || (r3 = r3.toLowerCase()), function(t3) {
        return s2.getName(t3) === r3 && e8(t3);
      };
    }
    case Zi.Descendant: {
      if (false === n2.cacheResults || "undefined" == typeof WeakSet) return function(t4) {
        let n3 = t4;
        for (; n3 = qs(n3, s2); ) if (e8(n3)) return true;
        return false;
      };
      const t3 = /* @__PURE__ */ new WeakSet();
      return function(n3) {
        let r3 = n3;
        for (; r3 = qs(r3, s2); ) if (!t3.has(r3)) {
          if (s2.isTag(r3) && e8(r3)) return true;
          t3.add(r3);
        }
        return false;
      };
    }
    case "_flexibleDescendant":
      return function(t3) {
        let n3 = t3;
        do {
          if (e8(n3)) return true;
        } while (n3 = qs(n3, s2));
        return false;
      };
    case Zi.Parent:
      return function(t3) {
        return s2.getChildren(t3).some(((t4) => s2.isTag(t4) && e8(t4)));
      };
    case Zi.Child:
      return function(t3) {
        const n3 = s2.getParent(t3);
        return null != n3 && s2.isTag(n3) && e8(n3);
      };
    case Zi.Sibling:
      return function(t3) {
        const n3 = s2.getSiblings(t3);
        for (let r3 = 0; r3 < n3.length; r3++) {
          const i3 = n3[r3];
          if (a2(t3, i3)) break;
          if (s2.isTag(i3) && e8(i3)) return true;
        }
        return false;
      };
    case Zi.Adjacent:
      return s2.prevElementSibling ? function(t3) {
        const n3 = s2.prevElementSibling(t3);
        return null != n3 && e8(n3);
      } : function(t3) {
        const n3 = s2.getSiblings(t3);
        let r3;
        for (let e9 = 0; e9 < n3.length; e9++) {
          const i3 = n3[e9];
          if (a2(t3, i3)) break;
          s2.isTag(i3) && (r3 = i3);
        }
        return !!r3 && e8(r3);
      };
    case Zi.Universal:
      if (null != t2.namespace && "*" !== t2.namespace) throw new Error("Namespaced universal selectors are not yet supported by css-select");
      return e8;
  }
}
function js(e8) {
  return e8.type === Zi.Pseudo && ("scope" === e8.name || Array.isArray(e8.data) && e8.data.some(((e9) => e9.some(js))));
}
const Vs = { type: Zi.Descendant }, Ws = { type: "_flexibleDescendant" }, Qs = { type: Zi.Pseudo, name: "scope", data: null };
function Xs(e8, t2, n2) {
  var r2;
  e8.forEach(As), n2 = null !== (r2 = t2.context) && void 0 !== r2 ? r2 : n2;
  const i2 = Array.isArray(n2), s2 = n2 && (Array.isArray(n2) ? n2 : [n2]);
  if (false !== t2.relativeSelector) !(function(e9, { adapter: t3 }, n3) {
    const r3 = !!(null == n3 ? void 0 : n3.every(((e10) => {
      const n4 = t3.isTag(e10) && t3.getParent(e10);
      return e10 === Fs || n4 && t3.isTag(n4);
    })));
    for (const t4 of e9) {
      if (t4.length > 0 && Ts(t4[0]) && t4[0].type !== Zi.Descendant) ;
      else {
        if (!r3 || t4.some(js)) continue;
        t4.unshift(Vs);
      }
      t4.unshift(Qs);
    }
  })(e8, t2, s2);
  else if (e8.some(((e9) => e9.length > 0 && Ts(e9[0])))) throw new Error("Relative selectors are not allowed when the `relativeSelector` option is disabled");
  let a2 = false;
  const o2 = e8.map(((e9) => {
    if (e9.length >= 2) {
      const [t3, n3] = e9;
      t3.type !== Zi.Pseudo || "scope" !== t3.name || (i2 && n3.type === Zi.Descendant ? e9[1] = Ws : n3.type !== Zi.Adjacent && n3.type !== Zi.Sibling || (a2 = true));
    }
    return (function(e10, t3, n3) {
      var r3;
      return e10.reduce(((e11, r4) => e11 === ds.falseFunc ? ds.falseFunc : Ks(e11, r4, t3, n3, Xs)), null !== (r3 = t3.rootFunc) && void 0 !== r3 ? r3 : ds.trueFunc);
    })(e9, t2, s2);
  })).reduce($s, ds.falseFunc);
  return o2.shouldTestNextSiblings = a2, o2;
}
function $s(e8, t2) {
  return t2 === ds.falseFunc || e8 === ds.trueFunc ? e8 : e8 === ds.falseFunc || t2 === ds.trueFunc ? t2 : function(n2) {
    return e8(n2) || t2(n2);
  };
}
const zs = (e8, t2) => e8 === t2, Js = { adapter: Se, equals: zs };
const Zs = (ea = Xs, function(e8, t2, n2) {
  const r2 = (function(e9) {
    var t3, n3, r3, i2;
    const s2 = null != e9 ? e9 : Js;
    return null !== (t3 = s2.adapter) && void 0 !== t3 || (s2.adapter = Se), null !== (n3 = s2.equals) && void 0 !== n3 || (s2.equals = null !== (i2 = null === (r3 = s2.adapter) || void 0 === r3 ? void 0 : r3.equals) && void 0 !== i2 ? i2 : zs), s2;
  })(t2);
  return ea(e8, r2, n2);
});
var ea;
function ta(e8, t2, n2 = false) {
  return n2 && (e8 = (function(e9, t3) {
    const n3 = Array.isArray(e9) ? e9.slice(0) : [e9], r2 = n3.length;
    for (let e10 = 0; e10 < r2; e10++) {
      const r3 = Us(n3[e10], t3);
      n3.push(...r3);
    }
    return n3;
  })(e8, t2)), Array.isArray(e8) ? t2.removeSubsets(e8) : t2.getChildren(e8);
}
const na = /* @__PURE__ */ new Set(["first", "last", "eq", "gt", "nth", "lt", "even", "odd"]);
function ra(e8) {
  return "pseudo" === e8.type && (!!na.has(e8.name) || !("not" !== e8.name || !Array.isArray(e8.data)) && e8.data.some(((e9) => e9.some(ra))));
}
function ia(e8) {
  const t2 = [], n2 = [];
  for (const r2 of e8) r2.some(ra) ? t2.push(r2) : n2.push(r2);
  return [n2, t2];
}
const sa = { type: Zi.Universal, namespace: null }, aa = { type: Zi.Pseudo, name: "scope", data: null };
function oa(e8, t2, n2 = {}) {
  return ca([e8], t2, n2);
}
function ca(e8, t2, n2 = {}) {
  if ("function" == typeof t2) return e8.some(t2);
  const [r2, i2] = ia(ps(t2));
  return r2.length > 0 && e8.some(Zs(r2, n2)) || i2.some(((t3) => ua(t3, e8, n2).length > 0));
}
function la(e8, t2, n2 = {}) {
  return ha(ps(e8), t2, n2);
}
function ha(e8, t2, n2) {
  if (0 === t2.length) return [];
  const [r2, i2] = ia(e8);
  let s2;
  if (r2.length) {
    const e9 = Ea(t2, r2, n2);
    if (0 === i2.length) return e9;
    e9.length && (s2 = new Set(e9));
  }
  for (let e9 = 0; e9 < i2.length && (null == s2 ? void 0 : s2.size) !== t2.length; e9++) {
    const r3 = i2[e9], a2 = s2 ? t2.filter(((e10) => I(e10) && !s2.has(e10))) : t2;
    if (0 === a2.length) break;
    const o2 = ua(r3, t2, n2);
    if (o2.length) if (s2) o2.forEach(((e10) => s2.add(e10)));
    else {
      if (e9 === i2.length - 1) return o2;
      s2 = new Set(o2);
    }
  }
  return void 0 !== s2 ? s2.size === t2.length ? t2 : t2.filter(((e9) => s2.has(e9))) : [];
}
function ua(e8, t2, n2) {
  var r2;
  if (e8.some(as)) {
    const i2 = null !== (r2 = n2.root) && void 0 !== r2 ? r2 : (function(e9) {
      for (; e9.parent; ) e9 = e9.parent;
      return e9;
    })(t2[0]), s2 = { ...n2, context: t2, relativeSelector: false };
    return e8.push(aa), pa(i2, e8, s2, true, t2.length);
  }
  return pa(t2, e8, n2, false, t2.length);
}
function pa(e8, t2, n2, r2, i2) {
  const s2 = t2.findIndex(ra), a2 = t2.slice(0, s2), o2 = t2[s2], c2 = t2.length - 1 === s2 ? i2 : 1 / 0, l2 = (function(e9, t3, n3) {
    const r3 = null != t3 ? parseInt(t3, 10) : NaN;
    switch (e9) {
      case "first":
        return 1;
      case "nth":
      case "eq":
        return isFinite(r3) ? r3 >= 0 ? r3 + 1 : 1 / 0 : 0;
      case "lt":
        return isFinite(r3) ? r3 >= 0 ? Math.min(r3, n3) : 1 / 0 : 0;
      case "gt":
        return isFinite(r3) ? 1 / 0 : 0;
      case "odd":
        return 2 * n3;
      case "even":
        return 2 * n3 - 1;
      case "last":
      case "not":
        return 1 / 0;
    }
  })(o2.name, o2.data, c2);
  if (0 === l2) return [];
  const h2 = (0 !== a2.length || Array.isArray(e8) ? 0 === a2.length ? (Array.isArray(e8) ? e8 : [e8]).filter(I) : r2 || a2.some(as) ? fa(e8, [a2], n2, l2) : Ea(e8, [a2], n2) : Z(e8).filter(I)).slice(0, l2);
  let u2 = (function(e9, t3, n3, r3) {
    const i3 = "string" == typeof n3 ? parseInt(n3, 10) : NaN;
    switch (e9) {
      case "first":
      case "lt":
        return t3;
      case "last":
        return t3.length > 0 ? [t3[t3.length - 1]] : t3;
      case "nth":
      case "eq":
        return isFinite(i3) && Math.abs(i3) < t3.length ? [i3 < 0 ? t3[t3.length + i3] : t3[i3]] : [];
      case "gt":
        return isFinite(i3) ? t3.slice(i3 + 1) : [];
      case "even":
        return t3.filter(((e10, t4) => t4 % 2 == 0));
      case "odd":
        return t3.filter(((e10, t4) => t4 % 2 == 1));
      case "not": {
        const e10 = new Set(ha(n3, t3, r3));
        return t3.filter(((t4) => !e10.has(t4)));
      }
    }
  })(o2.name, h2, o2.data, n2);
  if (0 === u2.length || t2.length === s2 + 1) return u2;
  const p2 = t2.slice(s2 + 1), f2 = p2.some(as);
  if (f2) {
    if (as(p2[0])) {
      const { type: e9 } = p2[0];
      e9 !== Zi.Sibling && e9 !== Zi.Adjacent || (u2 = ta(u2, Se, true)), p2.unshift(sa);
    }
    n2 = { ...n2, relativeSelector: false, rootFunc: (e9) => u2.includes(e9) };
  } else n2.rootFunc && n2.rootFunc !== Es && (n2 = { ...n2, rootFunc: Es });
  return p2.some(ra) ? pa(u2, p2, n2, false, i2) : f2 ? fa(u2, [p2], n2, i2) : Ea(u2, [p2], n2);
}
function fa(e8, t2, n2, r2) {
  return da(e8, Zs(t2, n2, e8), r2);
}
function da(e8, t2, n2 = 1 / 0) {
  return ae(((e9) => I(e9) && t2(e9)), ta(e8, Se, t2.shouldTestNextSiblings), true, n2);
}
function Ea(e8, t2, n2) {
  const r2 = (Array.isArray(e8) ? e8 : [e8]).filter(I);
  if (0 === r2.length) return r2;
  const i2 = Zs(t2, n2);
  return i2 === Es ? r2 : r2.filter(i2);
}
var ma = function(e8, t2, n2) {
  if (n2 || 2 === arguments.length) for (var r2, i2 = 0, s2 = t2.length; i2 < s2; i2++) !r2 && i2 in t2 || (r2 || (r2 = Array.prototype.slice.call(t2, 0, i2)), r2[i2] = t2[i2]);
  return e8.concat(r2 || Array.prototype.slice.call(t2));
}, Ta = /^\s*[~+]/;
function _a(e8) {
  return function(t2) {
    for (var n2 = [], r2 = 1; r2 < arguments.length; r2++) n2[r2 - 1] = arguments[r2];
    return function(r3) {
      var i2, s2 = e8(t2, this);
      return r3 && (s2 = xa(s2, r3, this.options.xmlMode, null === (i2 = this._root) || void 0 === i2 ? void 0 : i2[0])), this._make(this.length > 1 && s2.length > 1 ? n2.reduce((function(e9, t3) {
        return t3(e9);
      }), s2) : s2);
    };
  };
}
var Aa = _a((function(e8, t2) {
  for (var n2, r2 = [], i2 = 0; i2 < t2.length; i2++) {
    var s2 = e8(t2[i2]);
    r2.push(s2);
  }
  return (n2 = new Array()).concat.apply(n2, r2);
})), ga = _a((function(e8, t2) {
  for (var n2 = [], r2 = 0; r2 < t2.length; r2++) {
    var i2 = e8(t2[r2]);
    null !== i2 && n2.push(i2);
  }
  return n2;
}));
function Na(e8) {
  for (var t2 = [], n2 = 1; n2 < arguments.length; n2++) t2[n2 - 1] = arguments[n2];
  var r2 = null, i2 = _a((function(e9, t3) {
    var n3 = [];
    return Bi(t3, (function(t4) {
      for (var i3; (i3 = e9(t4)) && !(null == r2 ? void 0 : r2(i3, n3.length)); t4 = i3) n3.push(i3);
    })), n3;
  })).apply(void 0, ma([e8], t2, false));
  return function(e9, t3) {
    var n3 = this;
    r2 = "string" == typeof e9 ? function(t4) {
      return oa(t4, e9, n3.options);
    } : e9 ? Pa(e9) : null;
    var s2 = i2.call(this, t3);
    return r2 = null, s2;
  };
}
function Ca(e8) {
  return Array.from(new Set(e8));
}
var Ia = ga((function(e8) {
  var t2 = e8.parent;
  return t2 && !L(t2) ? t2 : null;
}), Ca), Sa = Aa((function(e8) {
  for (var t2 = []; e8.parent && !L(e8.parent); ) t2.push(e8.parent), e8 = e8.parent;
  return t2;
}), Ee, (function(e8) {
  return e8.reverse();
})), ba = Na((function(e8) {
  var t2 = e8.parent;
  return t2 && !L(t2) ? t2 : null;
}), Ee, (function(e8) {
  return e8.reverse();
}));
var Oa = ga((function(e8) {
  return ne(e8);
})), ya = Aa((function(e8) {
  for (var t2 = []; e8.next; ) I(e8 = e8.next) && t2.push(e8);
  return t2;
}), Ca), La = Na((function(e8) {
  return ne(e8);
}), Ca), ka = ga((function(e8) {
  return re(e8);
})), va = Aa((function(e8) {
  for (var t2 = []; e8.prev; ) I(e8 = e8.prev) && t2.push(e8);
  return t2;
}), Ca), Da = Na((function(e8) {
  return re(e8);
}), Ca), Ra = Aa((function(e8) {
  return te(e8).filter((function(t2) {
    return I(t2) && t2 !== e8;
  }));
}), Ee), Ma = Aa((function(e8) {
  return Z(e8).filter(I);
}), Ca);
function Pa(e8) {
  return "function" == typeof e8 ? function(t2, n2) {
    return e8.call(t2, n2, t2);
  } : wi(e8) ? function(t2) {
    return Array.prototype.includes.call(e8, t2);
  } : function(t2) {
    return e8 === t2;
  };
}
function xa(e8, t2, n2, r2) {
  return "string" == typeof t2 ? la(t2, e8, { xmlMode: n2, root: r2 }) : e8.filter(Pa(t2));
}
var wa = Object.freeze({ __proto__: null, find: function(e8) {
  var t2;
  if (!e8) return this._make([]);
  var n2 = this.toArray();
  if ("string" != typeof e8) {
    var r2 = wi(e8) ? e8.toArray() : [e8];
    return this._make(r2.filter((function(e9) {
      return n2.some((function(t3) {
        return Di(t3, e9);
      }));
    })));
  }
  var i2 = Ta.test(e8) ? n2 : this.children().toArray(), s2 = { context: n2, root: null === (t2 = this._root) || void 0 === t2 ? void 0 : t2[0], xmlMode: this.options.xmlMode, lowerCaseTags: this.options.lowerCaseTags, lowerCaseAttributeNames: this.options.lowerCaseAttributeNames, pseudos: this.options.pseudos, quirksMode: this.options.quirksMode };
  return this._make((function(e9, t3, n3 = {}, r3 = 1 / 0) {
    if ("function" == typeof e9) return da(t3, e9);
    const [i3, s3] = ia(ps(e9)), a2 = s3.map(((e10) => pa(t3, e10, n3, true, r3)));
    return i3.length && a2.push(fa(t3, i3, n3, r3)), 0 === a2.length ? [] : 1 === a2.length ? a2[0] : Ee(a2.reduce(((e10, t4) => [...e10, ...t4])));
  })(e8, i2, s2));
}, parent: Ia, parents: Sa, parentsUntil: ba, closest: function(e8) {
  var t2, n2 = [];
  if (!e8) return this._make(n2);
  var r2 = { xmlMode: this.options.xmlMode, root: null === (t2 = this._root) || void 0 === t2 ? void 0 : t2[0] }, i2 = "string" == typeof e8 ? function(t3) {
    return oa(t3, e8, r2);
  } : Pa(e8);
  return Bi(this, (function(e9) {
    for (; e9 && I(e9); ) {
      if (i2(e9, 0)) {
        n2.includes(e9) || n2.push(e9);
        break;
      }
      e9 = e9.parent;
    }
  })), this._make(n2);
}, next: Oa, nextAll: ya, nextUntil: La, prev: ka, prevAll: va, prevUntil: Da, siblings: Ra, children: Ma, contents: function() {
  var e8 = this.toArray().reduce((function(e9, t2) {
    return k(t2) ? e9.concat(t2.children) : e9;
  }), []);
  return this._make(e8);
}, each: function(e8) {
  for (var t2 = 0, n2 = this.length; t2 < n2 && false !== e8.call(this[t2], t2, this[t2]); ) ++t2;
  return this;
}, map: function(e8) {
  for (var t2 = [], n2 = 0; n2 < this.length; n2++) {
    var r2 = this[n2], i2 = e8.call(r2, n2, r2);
    null != i2 && (t2 = t2.concat(i2));
  }
  return this._make(t2);
}, filter: function(e8) {
  var t2;
  return this._make(xa(this.toArray(), e8, this.options.xmlMode, null === (t2 = this._root) || void 0 === t2 ? void 0 : t2[0]));
}, filterArray: xa, is: function(e8) {
  var t2 = this.toArray();
  return "string" == typeof e8 ? ca(t2.filter(I), e8, this.options) : !!e8 && t2.some(Pa(e8));
}, not: function(e8) {
  var t2 = this.toArray();
  if ("string" == typeof e8) {
    var n2 = new Set(la(e8, t2, this.options));
    t2 = t2.filter((function(e9) {
      return !n2.has(e9);
    }));
  } else {
    var r2 = Pa(e8);
    t2 = t2.filter((function(e9, t3) {
      return !r2(e9, t3);
    }));
  }
  return this._make(t2);
}, has: function(e8) {
  var t2 = this;
  return this.filter("string" == typeof e8 ? ":has(".concat(e8, ")") : function(n2, r2) {
    return t2._make(r2).find(e8).length > 0;
  });
}, first: function() {
  return this.length > 1 ? this._make(this[0]) : this;
}, last: function() {
  return this.length > 0 ? this._make(this[this.length - 1]) : this;
}, eq: function(e8) {
  var t2;
  return 0 === (e8 = +e8) && this.length <= 1 ? this : (e8 < 0 && (e8 = this.length + e8), this._make(null !== (t2 = this[e8]) && void 0 !== t2 ? t2 : []));
}, get: function(e8) {
  return null == e8 ? this.toArray() : this[e8 < 0 ? this.length + e8 : e8];
}, toArray: function() {
  return Array.prototype.slice.call(this);
}, index: function(e8) {
  var t2, n2;
  return null == e8 ? (t2 = this.parent().children(), n2 = this[0]) : "string" == typeof e8 ? (t2 = this._make(e8), n2 = this[0]) : (t2 = this, n2 = wi(e8) ? e8[0] : e8), Array.prototype.indexOf.call(t2, n2);
}, slice: function(e8, t2) {
  return this._make(Array.prototype.slice.call(this, e8, t2));
}, end: function() {
  var e8;
  return null !== (e8 = this.prevObject) && void 0 !== e8 ? e8 : this._make([]);
}, add: function(e8, t2) {
  var n2 = this._make(e8, t2), r2 = Ee(ma(ma([], this.get(), true), n2.get(), true));
  return this._make(r2);
}, addBack: function(e8) {
  return this.prevObject ? this.add(e8 ? this.prevObject.filter(e8) : this.prevObject) : this;
} });
function Ba(e8, t2) {
  var n2 = Array.isArray(e8) ? e8 : [e8];
  t2 ? t2.children = n2 : t2 = null;
  for (var r2 = 0; r2 < n2.length; r2++) {
    var i2 = n2[r2];
    i2.parent && i2.parent.children !== n2 && ie(i2), t2 ? (i2.prev = n2[r2 - 1] || null, i2.next = n2[r2 + 1] || null) : i2.prev = i2.next = null, i2.parent = t2;
  }
  return t2;
}
var Fa = function(e8, t2, n2) {
  if (n2 || 2 === arguments.length) for (var r2, i2 = 0, s2 = t2.length; i2 < s2; i2++) !r2 && i2 in t2 || (r2 || (r2 = Array.prototype.slice.call(t2, 0, i2)), r2[i2] = t2[i2]);
  return e8.concat(r2 || Array.prototype.slice.call(t2));
};
function Ua(e8) {
  return function() {
    for (var t2 = this, n2 = [], r2 = 0; r2 < arguments.length; r2++) n2[r2] = arguments[r2];
    var i2 = this.length - 1;
    return Bi(this, (function(r3, s2) {
      if (k(r3)) {
        var a2 = "function" == typeof n2[0] ? n2[0].call(r3, s2, t2._render(r3.children)) : n2, o2 = t2._makeDomArray(a2, s2 < i2);
        e8(o2, r3.children, r3);
      }
    }));
  };
}
function Ha(e8, t2, n2, r2, i2) {
  for (var s2, a2, o2 = Fa([t2, n2], r2, true), c2 = 0 === t2 ? null : e8[t2 - 1], l2 = t2 + n2 >= e8.length ? null : e8[t2 + n2], h2 = 0; h2 < r2.length; ++h2) {
    var u2 = r2[h2], p2 = u2.parent;
    if (p2) {
      var f2 = p2.children.indexOf(u2);
      f2 > -1 && (p2.children.splice(f2, 1), i2 === p2 && t2 > f2 && o2[0]--);
    }
    u2.parent = i2, u2.prev && (u2.prev.next = null !== (s2 = u2.next) && void 0 !== s2 ? s2 : null), u2.next && (u2.next.prev = null !== (a2 = u2.prev) && void 0 !== a2 ? a2 : null), u2.prev = 0 === h2 ? c2 : r2[h2 - 1], u2.next = h2 === r2.length - 1 ? l2 : r2[h2 + 1];
  }
  return c2 && (c2.next = r2[0]), l2 && (l2.prev = r2[r2.length - 1]), e8.splice.apply(e8, o2);
}
var Ga = Ua((function(e8, t2, n2) {
  Ha(t2, t2.length, 0, e8, n2);
})), Ya = Ua((function(e8, t2, n2) {
  Ha(t2, 0, 0, e8, n2);
}));
function qa(e8) {
  return function(t2) {
    for (var n2 = this.length - 1, r2 = this.parents().last(), i2 = 0; i2 < this.length; i2++) {
      var s2 = this[i2], a2 = "function" == typeof t2 ? t2.call(s2, i2, s2) : "string" != typeof t2 || Ui(t2) ? t2 : r2.find(t2).clone(), o2 = this._makeDomArray(a2, i2 < n2)[0];
      if (o2 && k(o2)) {
        for (var c2 = o2, l2 = 0; l2 < c2.children.length; ) {
          var h2 = c2.children[l2];
          I(h2) ? (c2 = h2, l2 = 0) : l2++;
        }
        e8(s2, c2, [o2]);
      }
    }
    return this;
  };
}
var Ka = qa((function(e8, t2, n2) {
  var r2 = e8.parent;
  if (r2) {
    var i2 = r2.children, s2 = i2.indexOf(e8);
    Ba([e8], t2), Ha(i2, s2, 0, n2, r2);
  }
})), ja = qa((function(e8, t2, n2) {
  k(e8) && (Ba(e8.children, t2), Ba(n2, e8));
}));
var Va = Object.freeze({ __proto__: null, _makeDomArray: function(e8, t2) {
  var n2 = this;
  return null == e8 ? [] : wi(e8) ? t2 ? Fi(e8.get()) : e8.get() : Array.isArray(e8) ? e8.reduce((function(e9, r2) {
    return e9.concat(n2._makeDomArray(r2, t2));
  }), []) : "string" == typeof e8 ? this._parse(e8, this.options, false, null).children : t2 ? Fi([e8]) : [e8];
}, appendTo: function(e8) {
  return (wi(e8) ? e8 : this._make(e8)).append(this), this;
}, prependTo: function(e8) {
  return (wi(e8) ? e8 : this._make(e8)).prepend(this), this;
}, append: Ga, prepend: Ya, wrap: Ka, wrapInner: ja, unwrap: function(e8) {
  var t2 = this;
  return this.parent(e8).not("body").each((function(e9, n2) {
    t2._make(n2).replaceWith(n2.children);
  })), this;
}, wrapAll: function(e8) {
  var t2 = this[0];
  if (t2) {
    for (var n2 = this._make("function" == typeof e8 ? e8.call(t2, 0, t2) : e8).insertBefore(t2), r2 = void 0, i2 = 0; i2 < n2.length; i2++) "tag" === n2[i2].type && (r2 = n2[i2]);
    for (var s2 = 0; r2 && s2 < r2.children.length; ) {
      var a2 = r2.children[s2];
      "tag" === a2.type ? (r2 = a2, s2 = 0) : s2++;
    }
    r2 && this._make(r2).append(this);
  }
  return this;
}, after: function() {
  for (var e8 = this, t2 = [], n2 = 0; n2 < arguments.length; n2++) t2[n2] = arguments[n2];
  var r2 = this.length - 1;
  return Bi(this, (function(n3, i2) {
    var s2 = n3.parent;
    if (k(n3) && s2) {
      var a2 = s2.children, o2 = a2.indexOf(n3);
      if (!(o2 < 0)) {
        var c2 = "function" == typeof t2[0] ? t2[0].call(n3, i2, e8._render(n3.children)) : t2;
        Ha(a2, o2 + 1, 0, e8._makeDomArray(c2, i2 < r2), s2);
      }
    }
  }));
}, insertAfter: function(e8) {
  var t2 = this;
  "string" == typeof e8 && (e8 = this._make(e8)), this.remove();
  var n2 = [];
  return this._makeDomArray(e8).forEach((function(e9) {
    var r2 = t2.clone().toArray(), i2 = e9.parent;
    if (i2) {
      var s2 = i2.children, a2 = s2.indexOf(e9);
      a2 < 0 || (Ha(s2, a2 + 1, 0, r2, i2), n2.push.apply(n2, r2));
    }
  })), this._make(n2);
}, before: function() {
  for (var e8 = this, t2 = [], n2 = 0; n2 < arguments.length; n2++) t2[n2] = arguments[n2];
  var r2 = this.length - 1;
  return Bi(this, (function(n3, i2) {
    var s2 = n3.parent;
    if (k(n3) && s2) {
      var a2 = s2.children, o2 = a2.indexOf(n3);
      if (!(o2 < 0)) {
        var c2 = "function" == typeof t2[0] ? t2[0].call(n3, i2, e8._render(n3.children)) : t2;
        Ha(a2, o2, 0, e8._makeDomArray(c2, i2 < r2), s2);
      }
    }
  }));
}, insertBefore: function(e8) {
  var t2 = this, n2 = this._make(e8);
  this.remove();
  var r2 = [];
  return Bi(n2, (function(e9) {
    var n3 = t2.clone().toArray(), i2 = e9.parent;
    if (i2) {
      var s2 = i2.children, a2 = s2.indexOf(e9);
      a2 < 0 || (Ha(s2, a2, 0, n3, i2), r2.push.apply(r2, n3));
    }
  })), this._make(r2);
}, remove: function(e8) {
  return Bi(e8 ? this.filter(e8) : this, (function(e9) {
    ie(e9), e9.prev = e9.next = e9.parent = null;
  })), this;
}, replaceWith: function(e8) {
  var t2 = this;
  return Bi(this, (function(n2, r2) {
    var i2 = n2.parent;
    if (i2) {
      var s2 = i2.children, a2 = "function" == typeof e8 ? e8.call(n2, r2, n2) : e8, o2 = t2._makeDomArray(a2);
      Ba(o2, null);
      var c2 = s2.indexOf(n2);
      Ha(s2, c2, 1, o2, i2), o2.includes(n2) || (n2.parent = n2.prev = n2.next = null);
    }
  }));
}, empty: function() {
  return Bi(this, (function(e8) {
    k(e8) && (e8.children.forEach((function(e9) {
      e9.next = e9.prev = e9.parent = null;
    })), e8.children.length = 0);
  }));
}, html: function(e8) {
  var t2 = this;
  if (void 0 === e8) {
    var n2 = this[0];
    return n2 && k(n2) ? this._render(n2.children) : null;
  }
  return Bi(this, (function(n3) {
    k(n3) && (n3.children.forEach((function(e9) {
      e9.next = e9.prev = e9.parent = null;
    })), Ba(wi(e8) ? e8.toArray() : t2._parse("".concat(e8), t2.options, false, n3).children, n3));
  }));
}, toString: function() {
  return this._render(this);
}, text: function(e8) {
  var t2 = this;
  return void 0 === e8 ? Li(this) : Bi(this, "function" == typeof e8 ? function(n2, r2) {
    return t2._make(n2).text(e8.call(n2, r2, Li([n2])));
  } : function(t3) {
    k(t3) && (t3.children.forEach((function(e9) {
      e9.next = e9.prev = e9.parent = null;
    })), Ba(new m("".concat(e8)), t3));
  });
}, clone: function() {
  return this._make(Fi(this.get()));
} });
function Wa(e8, t2, n2, r2) {
  if ("string" == typeof t2) {
    var i2 = Qa(e8), s2 = "function" == typeof n2 ? n2.call(e8, r2, i2[t2]) : n2;
    "" === s2 ? delete i2[t2] : null != s2 && (i2[t2] = s2), e8.attribs.style = (a2 = i2, Object.keys(a2).reduce((function(e9, t3) {
      return "".concat(e9).concat(e9 ? " " : "").concat(t3, ": ").concat(a2[t3], ";");
    }), ""));
  } else "object" == typeof t2 && Object.keys(t2).forEach((function(n3, r3) {
    Wa(e8, n3, t2[n3], r3);
  }));
  var a2;
}
function Qa(e8, t2) {
  if (e8 && I(e8)) {
    var n2 = (function(e9) {
      if (!(e9 = (e9 || "").trim())) return {};
      for (var t3, n3 = {}, r3 = 0, i2 = e9.split(";"); r3 < i2.length; r3++) {
        var s2 = i2[r3], a2 = s2.indexOf(":");
        if (a2 < 1 || a2 === s2.length - 1) {
          var o2 = s2.trimEnd();
          o2.length > 0 && void 0 !== t3 && (n3[t3] += ";".concat(o2));
        } else n3[t3 = s2.slice(0, a2).trim()] = s2.slice(a2 + 1).trim();
      }
      return n3;
    })(e8.attribs.style);
    if ("string" == typeof t2) return n2[t2];
    if (Array.isArray(t2)) {
      var r2 = {};
      return t2.forEach((function(e9) {
        null != n2[e9] && (r2[e9] = n2[e9]);
      })), r2;
    }
    return n2;
  }
}
var Xa = Object.freeze({ __proto__: null, css: function(e8, t2) {
  return null != e8 && null != t2 || "object" == typeof e8 && !Array.isArray(e8) ? Bi(this, (function(n2, r2) {
    I(n2) && Wa(n2, e8, t2, r2);
  })) : 0 !== this.length ? Qa(this[0], e8) : void 0;
} }), $a = /%20/g, za = /\r?\n/g;
var Ja = Object.freeze({ __proto__: null, serialize: function() {
  var e8 = this.serializeArray().map((function(e9) {
    return "".concat(encodeURIComponent(e9.name), "=").concat(encodeURIComponent(e9.value));
  }));
  return e8.join("&").replace($a, "+");
}, serializeArray: function() {
  var e8 = this;
  return this.map((function(t2, n2) {
    var r2 = e8._make(n2);
    return I(n2) && "form" === n2.name ? r2.find("input,select,textarea,keygen").toArray() : r2.filter("input,select,textarea,keygen").toArray();
  })).filter('[name!=""]:enabled:not(:submit, :button, :image, :reset, :file):matches([checked], :not(:checkbox, :radio))').map((function(t2, n2) {
    var r2, i2 = e8._make(n2), s2 = i2.attr("name"), a2 = null !== (r2 = i2.val()) && void 0 !== r2 ? r2 : "";
    return Array.isArray(a2) ? a2.map((function(e9) {
      return { name: s2, value: e9.replace(za, "\r\n") };
    })) : { name: s2, value: a2.replace(za, "\r\n") };
  })).toArray();
} }), Za = function(e8, t2, n2) {
  if (this.length = 0, this.options = n2, this._root = t2, e8) {
    for (var r2 = 0; r2 < e8.length; r2++) this[r2] = e8[r2];
    this.length = e8.length;
  }
};
Za.prototype.cheerio = "[cheerio object]", Za.prototype.splice = Array.prototype.splice, Za.prototype[Symbol.iterator] = Array.prototype[Symbol.iterator], Object.assign(Za.prototype, ts, wa, Va, Xa, Ja);
var eo, to = (eo = function(e8, t2) {
  return eo = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(e9, t3) {
    e9.__proto__ = t3;
  } || function(e9, t3) {
    for (var n2 in t3) Object.prototype.hasOwnProperty.call(t3, n2) && (e9[n2] = t3[n2]);
  }, eo(e8, t2);
}, function(e8, t2) {
  if ("function" != typeof t2 && null !== t2) throw new TypeError("Class extends value " + String(t2) + " is not a constructor or null");
  function n2() {
    this.constructor = e8;
  }
  eo(e8, t2), e8.prototype = null === t2 ? Object.create(t2) : (n2.prototype = t2.prototype, new n2());
}), no = function() {
  return no = Object.assign || function(e8) {
    for (var t2, n2 = 1, r2 = arguments.length; n2 < r2; n2++) for (var i2 in t2 = arguments[n2]) Object.prototype.hasOwnProperty.call(t2, i2) && (e8[i2] = t2[i2]);
    return e8;
  }, no.apply(this, arguments);
};
const ro = /* @__PURE__ */ new Set([65534, 65535, 131070, 131071, 196606, 196607, 262142, 262143, 327678, 327679, 393214, 393215, 458750, 458751, 524286, 524287, 589822, 589823, 655358, 655359, 720894, 720895, 786430, 786431, 851966, 851967, 917502, 917503, 983038, 983039, 1048574, 1048575, 1114110, 1114111]);
var io;
!(function(e8) {
  e8[e8.EOF = -1] = "EOF", e8[e8.NULL = 0] = "NULL", e8[e8.TABULATION = 9] = "TABULATION", e8[e8.CARRIAGE_RETURN = 13] = "CARRIAGE_RETURN", e8[e8.LINE_FEED = 10] = "LINE_FEED", e8[e8.FORM_FEED = 12] = "FORM_FEED", e8[e8.SPACE = 32] = "SPACE", e8[e8.EXCLAMATION_MARK = 33] = "EXCLAMATION_MARK", e8[e8.QUOTATION_MARK = 34] = "QUOTATION_MARK", e8[e8.NUMBER_SIGN = 35] = "NUMBER_SIGN", e8[e8.AMPERSAND = 38] = "AMPERSAND", e8[e8.APOSTROPHE = 39] = "APOSTROPHE", e8[e8.HYPHEN_MINUS = 45] = "HYPHEN_MINUS", e8[e8.SOLIDUS = 47] = "SOLIDUS", e8[e8.DIGIT_0 = 48] = "DIGIT_0", e8[e8.DIGIT_9 = 57] = "DIGIT_9", e8[e8.SEMICOLON = 59] = "SEMICOLON", e8[e8.LESS_THAN_SIGN = 60] = "LESS_THAN_SIGN", e8[e8.EQUALS_SIGN = 61] = "EQUALS_SIGN", e8[e8.GREATER_THAN_SIGN = 62] = "GREATER_THAN_SIGN", e8[e8.QUESTION_MARK = 63] = "QUESTION_MARK", e8[e8.LATIN_CAPITAL_A = 65] = "LATIN_CAPITAL_A", e8[e8.LATIN_CAPITAL_F = 70] = "LATIN_CAPITAL_F", e8[e8.LATIN_CAPITAL_X = 88] = "LATIN_CAPITAL_X", e8[e8.LATIN_CAPITAL_Z = 90] = "LATIN_CAPITAL_Z", e8[e8.RIGHT_SQUARE_BRACKET = 93] = "RIGHT_SQUARE_BRACKET", e8[e8.GRAVE_ACCENT = 96] = "GRAVE_ACCENT", e8[e8.LATIN_SMALL_A = 97] = "LATIN_SMALL_A", e8[e8.LATIN_SMALL_F = 102] = "LATIN_SMALL_F", e8[e8.LATIN_SMALL_X = 120] = "LATIN_SMALL_X", e8[e8.LATIN_SMALL_Z = 122] = "LATIN_SMALL_Z", e8[e8.REPLACEMENT_CHARACTER = 65533] = "REPLACEMENT_CHARACTER";
})(io = io || (io = {}));
const so = "--", ao = "[CDATA[", oo = "doctype", co = "script", lo = "public", ho = "system";
function uo(e8) {
  return e8 >= 55296 && e8 <= 57343;
}
function po(e8) {
  return 32 !== e8 && 10 !== e8 && 13 !== e8 && 9 !== e8 && 12 !== e8 && e8 >= 1 && e8 <= 31 || e8 >= 127 && e8 <= 159;
}
function fo(e8) {
  return e8 >= 64976 && e8 <= 65007 || ro.has(e8);
}
var Eo;
!(function(e8) {
  e8.controlCharacterInInputStream = "control-character-in-input-stream", e8.noncharacterInInputStream = "noncharacter-in-input-stream", e8.surrogateInInputStream = "surrogate-in-input-stream", e8.nonVoidHtmlElementStartTagWithTrailingSolidus = "non-void-html-element-start-tag-with-trailing-solidus", e8.endTagWithAttributes = "end-tag-with-attributes", e8.endTagWithTrailingSolidus = "end-tag-with-trailing-solidus", e8.unexpectedSolidusInTag = "unexpected-solidus-in-tag", e8.unexpectedNullCharacter = "unexpected-null-character", e8.unexpectedQuestionMarkInsteadOfTagName = "unexpected-question-mark-instead-of-tag-name", e8.invalidFirstCharacterOfTagName = "invalid-first-character-of-tag-name", e8.unexpectedEqualsSignBeforeAttributeName = "unexpected-equals-sign-before-attribute-name", e8.missingEndTagName = "missing-end-tag-name", e8.unexpectedCharacterInAttributeName = "unexpected-character-in-attribute-name", e8.unknownNamedCharacterReference = "unknown-named-character-reference", e8.missingSemicolonAfterCharacterReference = "missing-semicolon-after-character-reference", e8.unexpectedCharacterAfterDoctypeSystemIdentifier = "unexpected-character-after-doctype-system-identifier", e8.unexpectedCharacterInUnquotedAttributeValue = "unexpected-character-in-unquoted-attribute-value", e8.eofBeforeTagName = "eof-before-tag-name", e8.eofInTag = "eof-in-tag", e8.missingAttributeValue = "missing-attribute-value", e8.missingWhitespaceBetweenAttributes = "missing-whitespace-between-attributes", e8.missingWhitespaceAfterDoctypePublicKeyword = "missing-whitespace-after-doctype-public-keyword", e8.missingWhitespaceBetweenDoctypePublicAndSystemIdentifiers = "missing-whitespace-between-doctype-public-and-system-identifiers", e8.missingWhitespaceAfterDoctypeSystemKeyword = "missing-whitespace-after-doctype-system-keyword", e8.missingQuoteBeforeDoctypePublicIdentifier = "missing-quote-before-doctype-public-identifier", e8.missingQuoteBeforeDoctypeSystemIdentifier = "missing-quote-before-doctype-system-identifier", e8.missingDoctypePublicIdentifier = "missing-doctype-public-identifier", e8.missingDoctypeSystemIdentifier = "missing-doctype-system-identifier", e8.abruptDoctypePublicIdentifier = "abrupt-doctype-public-identifier", e8.abruptDoctypeSystemIdentifier = "abrupt-doctype-system-identifier", e8.cdataInHtmlContent = "cdata-in-html-content", e8.incorrectlyOpenedComment = "incorrectly-opened-comment", e8.eofInScriptHtmlCommentLikeText = "eof-in-script-html-comment-like-text", e8.eofInDoctype = "eof-in-doctype", e8.nestedComment = "nested-comment", e8.abruptClosingOfEmptyComment = "abrupt-closing-of-empty-comment", e8.eofInComment = "eof-in-comment", e8.incorrectlyClosedComment = "incorrectly-closed-comment", e8.eofInCdata = "eof-in-cdata", e8.absenceOfDigitsInNumericCharacterReference = "absence-of-digits-in-numeric-character-reference", e8.nullCharacterReference = "null-character-reference", e8.surrogateCharacterReference = "surrogate-character-reference", e8.characterReferenceOutsideUnicodeRange = "character-reference-outside-unicode-range", e8.controlCharacterReference = "control-character-reference", e8.noncharacterCharacterReference = "noncharacter-character-reference", e8.missingWhitespaceBeforeDoctypeName = "missing-whitespace-before-doctype-name", e8.missingDoctypeName = "missing-doctype-name", e8.invalidCharacterSequenceAfterDoctypeName = "invalid-character-sequence-after-doctype-name", e8.duplicateAttribute = "duplicate-attribute", e8.nonConformingDoctype = "non-conforming-doctype", e8.missingDoctype = "missing-doctype", e8.misplacedDoctype = "misplaced-doctype", e8.endTagWithoutMatchingOpenElement = "end-tag-without-matching-open-element", e8.closingOfElementWithOpenChildElements = "closing-of-element-with-open-child-elements", e8.disallowedContentInNoscriptInHead = "disallowed-content-in-noscript-in-head", e8.openElementsLeftAfterEof = "open-elements-left-after-eof", e8.abandonedHeadElementChild = "abandoned-head-element-child", e8.misplacedStartTagForHeadElement = "misplaced-start-tag-for-head-element", e8.nestedNoscriptInHead = "nested-noscript-in-head", e8.eofInElementThatCanContainOnlyText = "eof-in-element-that-can-contain-only-text";
})(Eo = Eo || (Eo = {}));
class mo {
  constructor(e8) {
    this.handler = e8, this.html = "", this.pos = -1, this.lastGapPos = -2, this.gapStack = [], this.skipNextNewLine = false, this.lastChunkWritten = false, this.endOfChunkHit = false, this.bufferWaterline = 65536, this.isEol = false, this.lineStartPos = 0, this.droppedBufferSize = 0, this.line = 1, this.lastErrOffset = -1;
  }
  get col() {
    return this.pos - this.lineStartPos + Number(this.lastGapPos !== this.pos);
  }
  get offset() {
    return this.droppedBufferSize + this.pos;
  }
  getError(e8) {
    const { line: t2, col: n2, offset: r2 } = this;
    return { code: e8, startLine: t2, endLine: t2, startCol: n2, endCol: n2, startOffset: r2, endOffset: r2 };
  }
  _err(e8) {
    this.handler.onParseError && this.lastErrOffset !== this.offset && (this.lastErrOffset = this.offset, this.handler.onParseError(this.getError(e8)));
  }
  _addGap() {
    this.gapStack.push(this.lastGapPos), this.lastGapPos = this.pos;
  }
  _processSurrogate(e8) {
    if (this.pos !== this.html.length - 1) {
      const t2 = this.html.charCodeAt(this.pos + 1);
      if ((function(e9) {
        return e9 >= 56320 && e9 <= 57343;
      })(t2)) return this.pos++, this._addGap(), 1024 * (e8 - 55296) + 9216 + t2;
    } else if (!this.lastChunkWritten) return this.endOfChunkHit = true, io.EOF;
    return this._err(Eo.surrogateInInputStream), e8;
  }
  willDropParsedChunk() {
    return this.pos > this.bufferWaterline;
  }
  dropParsedChunk() {
    this.willDropParsedChunk() && (this.html = this.html.substring(this.pos), this.lineStartPos -= this.pos, this.droppedBufferSize += this.pos, this.pos = 0, this.lastGapPos = -2, this.gapStack.length = 0);
  }
  write(e8, t2) {
    this.html.length > 0 ? this.html += e8 : this.html = e8, this.endOfChunkHit = false, this.lastChunkWritten = t2;
  }
  insertHtmlAtCurrentPos(e8) {
    this.html = this.html.substring(0, this.pos + 1) + e8 + this.html.substring(this.pos + 1), this.endOfChunkHit = false;
  }
  startsWith(e8, t2) {
    if (this.pos + e8.length > this.html.length) return this.endOfChunkHit = !this.lastChunkWritten, false;
    if (t2) return this.html.startsWith(e8, this.pos);
    for (let t3 = 0; t3 < e8.length; t3++) {
      if ((32 | this.html.charCodeAt(this.pos + t3)) !== e8.charCodeAt(t3)) return false;
    }
    return true;
  }
  peek(e8) {
    const t2 = this.pos + e8;
    return t2 >= this.html.length ? (this.endOfChunkHit = !this.lastChunkWritten, io.EOF) : this.html.charCodeAt(t2);
  }
  advance() {
    if (this.pos++, this.isEol && (this.isEol = false, this.line++, this.lineStartPos = this.pos), this.pos >= this.html.length) return this.endOfChunkHit = !this.lastChunkWritten, io.EOF;
    let e8 = this.html.charCodeAt(this.pos);
    if (e8 === io.CARRIAGE_RETURN) return this.isEol = true, this.skipNextNewLine = true, io.LINE_FEED;
    if (e8 === io.LINE_FEED && (this.isEol = true, this.skipNextNewLine)) return this.line--, this.skipNextNewLine = false, this._addGap(), this.advance();
    this.skipNextNewLine = false, uo(e8) && (e8 = this._processSurrogate(e8));
    return null === this.handler.onParseError || e8 > 31 && e8 < 127 || e8 === io.LINE_FEED || e8 === io.CARRIAGE_RETURN || e8 > 159 && e8 < 64976 || this._checkForProblematicCharacters(e8), e8;
  }
  _checkForProblematicCharacters(e8) {
    po(e8) ? this._err(Eo.controlCharacterInInputStream) : fo(e8) && this._err(Eo.noncharacterInInputStream);
  }
  retreat(e8) {
    for (this.pos -= e8; this.pos < this.lastGapPos; ) this.lastGapPos = this.gapStack.pop(), this.pos--;
    this.isEol = false;
  }
}
var To;
function _o(e8, t2) {
  for (let n2 = e8.attrs.length - 1; n2 >= 0; n2--) if (e8.attrs[n2].name === t2) return e8.attrs[n2].value;
  return null;
}
!(function(e8) {
  e8[e8.CHARACTER = 0] = "CHARACTER", e8[e8.NULL_CHARACTER = 1] = "NULL_CHARACTER", e8[e8.WHITESPACE_CHARACTER = 2] = "WHITESPACE_CHARACTER", e8[e8.START_TAG = 3] = "START_TAG", e8[e8.END_TAG = 4] = "END_TAG", e8[e8.COMMENT = 5] = "COMMENT", e8[e8.DOCTYPE = 6] = "DOCTYPE", e8[e8.EOF = 7] = "EOF", e8[e8.HIBERNATION = 8] = "HIBERNATION";
})(To = To || (To = {}));
var Ao = Ke((function(e8, t2) {
  Object.defineProperty(t2, "__esModule", { value: true }), t2.default = new Uint16Array('\u1D41<\xD5\u0131\u028A\u049D\u057B\u05D0\u0675\u06DE\u07A2\u07D6\u080F\u0A4A\u0A91\u0DA1\u0E6D\u0F09\u0F26\u10CA\u1228\u12E1\u1415\u149D\u14C3\u14DF\u1525\0\0\0\0\0\0\u156B\u16CD\u198D\u1C12\u1DDD\u1F7E\u2060\u21B0\u228D\u23C0\u23FB\u2442\u2824\u2912\u2D08\u2E48\u2FCE\u3016\u32BA\u3639\u37AC\u38FE\u3A28\u3A71\u3AE0\u3B2E\u0800EMabcfglmnoprstu\\bfms\x7F\x84\x8B\x90\x95\x98\xA6\xB3\xB9\xC8\xCFlig\u803B\xC6\u40C6P\u803B&\u4026cute\u803B\xC1\u40C1reve;\u4102\u0100iyx}rc\u803B\xC2\u40C2;\u4410r;\uC000\u{1D504}rave\u803B\xC0\u40C0pha;\u4391acr;\u4100d;\u6A53\u0100gp\x9D\xA1on;\u4104f;\uC000\u{1D538}plyFunction;\u6061ing\u803B\xC5\u40C5\u0100cs\xBE\xC3r;\uC000\u{1D49C}ign;\u6254ilde\u803B\xC3\u40C3ml\u803B\xC4\u40C4\u0400aceforsu\xE5\xFB\xFE\u0117\u011C\u0122\u0127\u012A\u0100cr\xEA\xF2kslash;\u6216\u0176\xF6\xF8;\u6AE7ed;\u6306y;\u4411\u0180crt\u0105\u010B\u0114ause;\u6235noullis;\u612Ca;\u4392r;\uC000\u{1D505}pf;\uC000\u{1D539}eve;\u42D8c\xF2\u0113mpeq;\u624E\u0700HOacdefhilorsu\u014D\u0151\u0156\u0180\u019E\u01A2\u01B5\u01B7\u01BA\u01DC\u0215\u0273\u0278\u027Ecy;\u4427PY\u803B\xA9\u40A9\u0180cpy\u015D\u0162\u017Aute;\u4106\u0100;i\u0167\u0168\u62D2talDifferentialD;\u6145leys;\u612D\u0200aeio\u0189\u018E\u0194\u0198ron;\u410Cdil\u803B\xC7\u40C7rc;\u4108nint;\u6230ot;\u410A\u0100dn\u01A7\u01ADilla;\u40B8terDot;\u40B7\xF2\u017Fi;\u43A7rcle\u0200DMPT\u01C7\u01CB\u01D1\u01D6ot;\u6299inus;\u6296lus;\u6295imes;\u6297o\u0100cs\u01E2\u01F8kwiseContourIntegral;\u6232eCurly\u0100DQ\u0203\u020FoubleQuote;\u601Duote;\u6019\u0200lnpu\u021E\u0228\u0247\u0255on\u0100;e\u0225\u0226\u6237;\u6A74\u0180git\u022F\u0236\u023Aruent;\u6261nt;\u622FourIntegral;\u622E\u0100fr\u024C\u024E;\u6102oduct;\u6210nterClockwiseContourIntegral;\u6233oss;\u6A2Fcr;\uC000\u{1D49E}p\u0100;C\u0284\u0285\u62D3ap;\u624D\u0580DJSZacefios\u02A0\u02AC\u02B0\u02B4\u02B8\u02CB\u02D7\u02E1\u02E6\u0333\u048D\u0100;o\u0179\u02A5trahd;\u6911cy;\u4402cy;\u4405cy;\u440F\u0180grs\u02BF\u02C4\u02C7ger;\u6021r;\u61A1hv;\u6AE4\u0100ay\u02D0\u02D5ron;\u410E;\u4414l\u0100;t\u02DD\u02DE\u6207a;\u4394r;\uC000\u{1D507}\u0100af\u02EB\u0327\u0100cm\u02F0\u0322ritical\u0200ADGT\u0300\u0306\u0316\u031Ccute;\u40B4o\u0174\u030B\u030D;\u42D9bleAcute;\u42DDrave;\u4060ilde;\u42DCond;\u62C4ferentialD;\u6146\u0470\u033D\0\0\0\u0342\u0354\0\u0405f;\uC000\u{1D53B}\u0180;DE\u0348\u0349\u034D\u40A8ot;\u60DCqual;\u6250ble\u0300CDLRUV\u0363\u0372\u0382\u03CF\u03E2\u03F8ontourIntegra\xEC\u0239o\u0274\u0379\0\0\u037B\xBB\u0349nArrow;\u61D3\u0100eo\u0387\u03A4ft\u0180ART\u0390\u0396\u03A1rrow;\u61D0ightArrow;\u61D4e\xE5\u02CAng\u0100LR\u03AB\u03C4eft\u0100AR\u03B3\u03B9rrow;\u67F8ightArrow;\u67FAightArrow;\u67F9ight\u0100AT\u03D8\u03DErrow;\u61D2ee;\u62A8p\u0241\u03E9\0\0\u03EFrrow;\u61D1ownArrow;\u61D5erticalBar;\u6225n\u0300ABLRTa\u0412\u042A\u0430\u045E\u047F\u037Crrow\u0180;BU\u041D\u041E\u0422\u6193ar;\u6913pArrow;\u61F5reve;\u4311eft\u02D2\u043A\0\u0446\0\u0450ightVector;\u6950eeVector;\u695Eector\u0100;B\u0459\u045A\u61BDar;\u6956ight\u01D4\u0467\0\u0471eeVector;\u695Fector\u0100;B\u047A\u047B\u61C1ar;\u6957ee\u0100;A\u0486\u0487\u62A4rrow;\u61A7\u0100ct\u0492\u0497r;\uC000\u{1D49F}rok;\u4110\u0800NTacdfglmopqstux\u04BD\u04C0\u04C4\u04CB\u04DE\u04E2\u04E7\u04EE\u04F5\u0521\u052F\u0536\u0552\u055D\u0560\u0565G;\u414AH\u803B\xD0\u40D0cute\u803B\xC9\u40C9\u0180aiy\u04D2\u04D7\u04DCron;\u411Arc\u803B\xCA\u40CA;\u442Dot;\u4116r;\uC000\u{1D508}rave\u803B\xC8\u40C8ement;\u6208\u0100ap\u04FA\u04FEcr;\u4112ty\u0253\u0506\0\0\u0512mallSquare;\u65FBerySmallSquare;\u65AB\u0100gp\u0526\u052Aon;\u4118f;\uC000\u{1D53C}silon;\u4395u\u0100ai\u053C\u0549l\u0100;T\u0542\u0543\u6A75ilde;\u6242librium;\u61CC\u0100ci\u0557\u055Ar;\u6130m;\u6A73a;\u4397ml\u803B\xCB\u40CB\u0100ip\u056A\u056Fsts;\u6203onentialE;\u6147\u0280cfios\u0585\u0588\u058D\u05B2\u05CCy;\u4424r;\uC000\u{1D509}lled\u0253\u0597\0\0\u05A3mallSquare;\u65FCerySmallSquare;\u65AA\u0370\u05BA\0\u05BF\0\0\u05C4f;\uC000\u{1D53D}All;\u6200riertrf;\u6131c\xF2\u05CB\u0600JTabcdfgorst\u05E8\u05EC\u05EF\u05FA\u0600\u0612\u0616\u061B\u061D\u0623\u066C\u0672cy;\u4403\u803B>\u403Emma\u0100;d\u05F7\u05F8\u4393;\u43DCreve;\u411E\u0180eiy\u0607\u060C\u0610dil;\u4122rc;\u411C;\u4413ot;\u4120r;\uC000\u{1D50A};\u62D9pf;\uC000\u{1D53E}eater\u0300EFGLST\u0635\u0644\u064E\u0656\u065B\u0666qual\u0100;L\u063E\u063F\u6265ess;\u62DBullEqual;\u6267reater;\u6AA2ess;\u6277lantEqual;\u6A7Eilde;\u6273cr;\uC000\u{1D4A2};\u626B\u0400Aacfiosu\u0685\u068B\u0696\u069B\u069E\u06AA\u06BE\u06CARDcy;\u442A\u0100ct\u0690\u0694ek;\u42C7;\u405Eirc;\u4124r;\u610ClbertSpace;\u610B\u01F0\u06AF\0\u06B2f;\u610DizontalLine;\u6500\u0100ct\u06C3\u06C5\xF2\u06A9rok;\u4126mp\u0144\u06D0\u06D8ownHum\xF0\u012Fqual;\u624F\u0700EJOacdfgmnostu\u06FA\u06FE\u0703\u0707\u070E\u071A\u071E\u0721\u0728\u0744\u0778\u078B\u078F\u0795cy;\u4415lig;\u4132cy;\u4401cute\u803B\xCD\u40CD\u0100iy\u0713\u0718rc\u803B\xCE\u40CE;\u4418ot;\u4130r;\u6111rave\u803B\xCC\u40CC\u0180;ap\u0720\u072F\u073F\u0100cg\u0734\u0737r;\u412AinaryI;\u6148lie\xF3\u03DD\u01F4\u0749\0\u0762\u0100;e\u074D\u074E\u622C\u0100gr\u0753\u0758ral;\u622Bsection;\u62C2isible\u0100CT\u076C\u0772omma;\u6063imes;\u6062\u0180gpt\u077F\u0783\u0788on;\u412Ef;\uC000\u{1D540}a;\u4399cr;\u6110ilde;\u4128\u01EB\u079A\0\u079Ecy;\u4406l\u803B\xCF\u40CF\u0280cfosu\u07AC\u07B7\u07BC\u07C2\u07D0\u0100iy\u07B1\u07B5rc;\u4134;\u4419r;\uC000\u{1D50D}pf;\uC000\u{1D541}\u01E3\u07C7\0\u07CCr;\uC000\u{1D4A5}rcy;\u4408kcy;\u4404\u0380HJacfos\u07E4\u07E8\u07EC\u07F1\u07FD\u0802\u0808cy;\u4425cy;\u440Cppa;\u439A\u0100ey\u07F6\u07FBdil;\u4136;\u441Ar;\uC000\u{1D50E}pf;\uC000\u{1D542}cr;\uC000\u{1D4A6}\u0580JTaceflmost\u0825\u0829\u082C\u0850\u0863\u09B3\u09B8\u09C7\u09CD\u0A37\u0A47cy;\u4409\u803B<\u403C\u0280cmnpr\u0837\u083C\u0841\u0844\u084Dute;\u4139bda;\u439Bg;\u67EAlacetrf;\u6112r;\u619E\u0180aey\u0857\u085C\u0861ron;\u413Ddil;\u413B;\u441B\u0100fs\u0868\u0970t\u0500ACDFRTUVar\u087E\u08A9\u08B1\u08E0\u08E6\u08FC\u092F\u095B\u0390\u096A\u0100nr\u0883\u088FgleBracket;\u67E8row\u0180;BR\u0899\u089A\u089E\u6190ar;\u61E4ightArrow;\u61C6eiling;\u6308o\u01F5\u08B7\0\u08C3bleBracket;\u67E6n\u01D4\u08C8\0\u08D2eeVector;\u6961ector\u0100;B\u08DB\u08DC\u61C3ar;\u6959loor;\u630Aight\u0100AV\u08EF\u08F5rrow;\u6194ector;\u694E\u0100er\u0901\u0917e\u0180;AV\u0909\u090A\u0910\u62A3rrow;\u61A4ector;\u695Aiangle\u0180;BE\u0924\u0925\u0929\u62B2ar;\u69CFqual;\u62B4p\u0180DTV\u0937\u0942\u094CownVector;\u6951eeVector;\u6960ector\u0100;B\u0956\u0957\u61BFar;\u6958ector\u0100;B\u0965\u0966\u61BCar;\u6952ight\xE1\u039Cs\u0300EFGLST\u097E\u098B\u0995\u099D\u09A2\u09ADqualGreater;\u62DAullEqual;\u6266reater;\u6276ess;\u6AA1lantEqual;\u6A7Dilde;\u6272r;\uC000\u{1D50F}\u0100;e\u09BD\u09BE\u62D8ftarrow;\u61DAidot;\u413F\u0180npw\u09D4\u0A16\u0A1Bg\u0200LRlr\u09DE\u09F7\u0A02\u0A10eft\u0100AR\u09E6\u09ECrrow;\u67F5ightArrow;\u67F7ightArrow;\u67F6eft\u0100ar\u03B3\u0A0Aight\xE1\u03BFight\xE1\u03CAf;\uC000\u{1D543}er\u0100LR\u0A22\u0A2CeftArrow;\u6199ightArrow;\u6198\u0180cht\u0A3E\u0A40\u0A42\xF2\u084C;\u61B0rok;\u4141;\u626A\u0400acefiosu\u0A5A\u0A5D\u0A60\u0A77\u0A7C\u0A85\u0A8B\u0A8Ep;\u6905y;\u441C\u0100dl\u0A65\u0A6FiumSpace;\u605Flintrf;\u6133r;\uC000\u{1D510}nusPlus;\u6213pf;\uC000\u{1D544}c\xF2\u0A76;\u439C\u0480Jacefostu\u0AA3\u0AA7\u0AAD\u0AC0\u0B14\u0B19\u0D91\u0D97\u0D9Ecy;\u440Acute;\u4143\u0180aey\u0AB4\u0AB9\u0ABEron;\u4147dil;\u4145;\u441D\u0180gsw\u0AC7\u0AF0\u0B0Eative\u0180MTV\u0AD3\u0ADF\u0AE8ediumSpace;\u600Bhi\u0100cn\u0AE6\u0AD8\xEB\u0AD9eryThi\xEE\u0AD9ted\u0100GL\u0AF8\u0B06reaterGreate\xF2\u0673essLes\xF3\u0A48Line;\u400Ar;\uC000\u{1D511}\u0200Bnpt\u0B22\u0B28\u0B37\u0B3Areak;\u6060BreakingSpace;\u40A0f;\u6115\u0680;CDEGHLNPRSTV\u0B55\u0B56\u0B6A\u0B7C\u0BA1\u0BEB\u0C04\u0C5E\u0C84\u0CA6\u0CD8\u0D61\u0D85\u6AEC\u0100ou\u0B5B\u0B64ngruent;\u6262pCap;\u626DoubleVerticalBar;\u6226\u0180lqx\u0B83\u0B8A\u0B9Bement;\u6209ual\u0100;T\u0B92\u0B93\u6260ilde;\uC000\u2242\u0338ists;\u6204reater\u0380;EFGLST\u0BB6\u0BB7\u0BBD\u0BC9\u0BD3\u0BD8\u0BE5\u626Fqual;\u6271ullEqual;\uC000\u2267\u0338reater;\uC000\u226B\u0338ess;\u6279lantEqual;\uC000\u2A7E\u0338ilde;\u6275ump\u0144\u0BF2\u0BFDownHump;\uC000\u224E\u0338qual;\uC000\u224F\u0338e\u0100fs\u0C0A\u0C27tTriangle\u0180;BE\u0C1A\u0C1B\u0C21\u62EAar;\uC000\u29CF\u0338qual;\u62ECs\u0300;EGLST\u0C35\u0C36\u0C3C\u0C44\u0C4B\u0C58\u626Equal;\u6270reater;\u6278ess;\uC000\u226A\u0338lantEqual;\uC000\u2A7D\u0338ilde;\u6274ested\u0100GL\u0C68\u0C79reaterGreater;\uC000\u2AA2\u0338essLess;\uC000\u2AA1\u0338recedes\u0180;ES\u0C92\u0C93\u0C9B\u6280qual;\uC000\u2AAF\u0338lantEqual;\u62E0\u0100ei\u0CAB\u0CB9verseElement;\u620CghtTriangle\u0180;BE\u0CCB\u0CCC\u0CD2\u62EBar;\uC000\u29D0\u0338qual;\u62ED\u0100qu\u0CDD\u0D0CuareSu\u0100bp\u0CE8\u0CF9set\u0100;E\u0CF0\u0CF3\uC000\u228F\u0338qual;\u62E2erset\u0100;E\u0D03\u0D06\uC000\u2290\u0338qual;\u62E3\u0180bcp\u0D13\u0D24\u0D4Eset\u0100;E\u0D1B\u0D1E\uC000\u2282\u20D2qual;\u6288ceeds\u0200;EST\u0D32\u0D33\u0D3B\u0D46\u6281qual;\uC000\u2AB0\u0338lantEqual;\u62E1ilde;\uC000\u227F\u0338erset\u0100;E\u0D58\u0D5B\uC000\u2283\u20D2qual;\u6289ilde\u0200;EFT\u0D6E\u0D6F\u0D75\u0D7F\u6241qual;\u6244ullEqual;\u6247ilde;\u6249erticalBar;\u6224cr;\uC000\u{1D4A9}ilde\u803B\xD1\u40D1;\u439D\u0700Eacdfgmoprstuv\u0DBD\u0DC2\u0DC9\u0DD5\u0DDB\u0DE0\u0DE7\u0DFC\u0E02\u0E20\u0E22\u0E32\u0E3F\u0E44lig;\u4152cute\u803B\xD3\u40D3\u0100iy\u0DCE\u0DD3rc\u803B\xD4\u40D4;\u441Eblac;\u4150r;\uC000\u{1D512}rave\u803B\xD2\u40D2\u0180aei\u0DEE\u0DF2\u0DF6cr;\u414Cga;\u43A9cron;\u439Fpf;\uC000\u{1D546}enCurly\u0100DQ\u0E0E\u0E1AoubleQuote;\u601Cuote;\u6018;\u6A54\u0100cl\u0E27\u0E2Cr;\uC000\u{1D4AA}ash\u803B\xD8\u40D8i\u016C\u0E37\u0E3Cde\u803B\xD5\u40D5es;\u6A37ml\u803B\xD6\u40D6er\u0100BP\u0E4B\u0E60\u0100ar\u0E50\u0E53r;\u603Eac\u0100ek\u0E5A\u0E5C;\u63DEet;\u63B4arenthesis;\u63DC\u0480acfhilors\u0E7F\u0E87\u0E8A\u0E8F\u0E92\u0E94\u0E9D\u0EB0\u0EFCrtialD;\u6202y;\u441Fr;\uC000\u{1D513}i;\u43A6;\u43A0usMinus;\u40B1\u0100ip\u0EA2\u0EADncareplan\xE5\u069Df;\u6119\u0200;eio\u0EB9\u0EBA\u0EE0\u0EE4\u6ABBcedes\u0200;EST\u0EC8\u0EC9\u0ECF\u0EDA\u627Aqual;\u6AAFlantEqual;\u627Cilde;\u627Eme;\u6033\u0100dp\u0EE9\u0EEEuct;\u620Fortion\u0100;a\u0225\u0EF9l;\u621D\u0100ci\u0F01\u0F06r;\uC000\u{1D4AB};\u43A8\u0200Ufos\u0F11\u0F16\u0F1B\u0F1FOT\u803B"\u4022r;\uC000\u{1D514}pf;\u611Acr;\uC000\u{1D4AC}\u0600BEacefhiorsu\u0F3E\u0F43\u0F47\u0F60\u0F73\u0FA7\u0FAA\u0FAD\u1096\u10A9\u10B4\u10BEarr;\u6910G\u803B\xAE\u40AE\u0180cnr\u0F4E\u0F53\u0F56ute;\u4154g;\u67EBr\u0100;t\u0F5C\u0F5D\u61A0l;\u6916\u0180aey\u0F67\u0F6C\u0F71ron;\u4158dil;\u4156;\u4420\u0100;v\u0F78\u0F79\u611Cerse\u0100EU\u0F82\u0F99\u0100lq\u0F87\u0F8Eement;\u620Builibrium;\u61CBpEquilibrium;\u696Fr\xBB\u0F79o;\u43A1ght\u0400ACDFTUVa\u0FC1\u0FEB\u0FF3\u1022\u1028\u105B\u1087\u03D8\u0100nr\u0FC6\u0FD2gleBracket;\u67E9row\u0180;BL\u0FDC\u0FDD\u0FE1\u6192ar;\u61E5eftArrow;\u61C4eiling;\u6309o\u01F5\u0FF9\0\u1005bleBracket;\u67E7n\u01D4\u100A\0\u1014eeVector;\u695Dector\u0100;B\u101D\u101E\u61C2ar;\u6955loor;\u630B\u0100er\u102D\u1043e\u0180;AV\u1035\u1036\u103C\u62A2rrow;\u61A6ector;\u695Biangle\u0180;BE\u1050\u1051\u1055\u62B3ar;\u69D0qual;\u62B5p\u0180DTV\u1063\u106E\u1078ownVector;\u694FeeVector;\u695Cector\u0100;B\u1082\u1083\u61BEar;\u6954ector\u0100;B\u1091\u1092\u61C0ar;\u6953\u0100pu\u109B\u109Ef;\u611DndImplies;\u6970ightarrow;\u61DB\u0100ch\u10B9\u10BCr;\u611B;\u61B1leDelayed;\u69F4\u0680HOacfhimoqstu\u10E4\u10F1\u10F7\u10FD\u1119\u111E\u1151\u1156\u1161\u1167\u11B5\u11BB\u11BF\u0100Cc\u10E9\u10EEHcy;\u4429y;\u4428FTcy;\u442Ccute;\u415A\u0280;aeiy\u1108\u1109\u110E\u1113\u1117\u6ABCron;\u4160dil;\u415Erc;\u415C;\u4421r;\uC000\u{1D516}ort\u0200DLRU\u112A\u1134\u113E\u1149ownArrow\xBB\u041EeftArrow\xBB\u089AightArrow\xBB\u0FDDpArrow;\u6191gma;\u43A3allCircle;\u6218pf;\uC000\u{1D54A}\u0272\u116D\0\0\u1170t;\u621Aare\u0200;ISU\u117B\u117C\u1189\u11AF\u65A1ntersection;\u6293u\u0100bp\u118F\u119Eset\u0100;E\u1197\u1198\u628Fqual;\u6291erset\u0100;E\u11A8\u11A9\u6290qual;\u6292nion;\u6294cr;\uC000\u{1D4AE}ar;\u62C6\u0200bcmp\u11C8\u11DB\u1209\u120B\u0100;s\u11CD\u11CE\u62D0et\u0100;E\u11CD\u11D5qual;\u6286\u0100ch\u11E0\u1205eeds\u0200;EST\u11ED\u11EE\u11F4\u11FF\u627Bqual;\u6AB0lantEqual;\u627Dilde;\u627FTh\xE1\u0F8C;\u6211\u0180;es\u1212\u1213\u1223\u62D1rset\u0100;E\u121C\u121D\u6283qual;\u6287et\xBB\u1213\u0580HRSacfhiors\u123E\u1244\u1249\u1255\u125E\u1271\u1276\u129F\u12C2\u12C8\u12D1ORN\u803B\xDE\u40DEADE;\u6122\u0100Hc\u124E\u1252cy;\u440By;\u4426\u0100bu\u125A\u125C;\u4009;\u43A4\u0180aey\u1265\u126A\u126Fron;\u4164dil;\u4162;\u4422r;\uC000\u{1D517}\u0100ei\u127B\u1289\u01F2\u1280\0\u1287efore;\u6234a;\u4398\u0100cn\u128E\u1298kSpace;\uC000\u205F\u200ASpace;\u6009lde\u0200;EFT\u12AB\u12AC\u12B2\u12BC\u623Cqual;\u6243ullEqual;\u6245ilde;\u6248pf;\uC000\u{1D54B}ipleDot;\u60DB\u0100ct\u12D6\u12DBr;\uC000\u{1D4AF}rok;\u4166\u0AE1\u12F7\u130E\u131A\u1326\0\u132C\u1331\0\0\0\0\0\u1338\u133D\u1377\u1385\0\u13FF\u1404\u140A\u1410\u0100cr\u12FB\u1301ute\u803B\xDA\u40DAr\u0100;o\u1307\u1308\u619Fcir;\u6949r\u01E3\u1313\0\u1316y;\u440Eve;\u416C\u0100iy\u131E\u1323rc\u803B\xDB\u40DB;\u4423blac;\u4170r;\uC000\u{1D518}rave\u803B\xD9\u40D9acr;\u416A\u0100di\u1341\u1369er\u0100BP\u1348\u135D\u0100ar\u134D\u1350r;\u405Fac\u0100ek\u1357\u1359;\u63DFet;\u63B5arenthesis;\u63DDon\u0100;P\u1370\u1371\u62C3lus;\u628E\u0100gp\u137B\u137Fon;\u4172f;\uC000\u{1D54C}\u0400ADETadps\u1395\u13AE\u13B8\u13C4\u03E8\u13D2\u13D7\u13F3rrow\u0180;BD\u1150\u13A0\u13A4ar;\u6912ownArrow;\u61C5ownArrow;\u6195quilibrium;\u696Eee\u0100;A\u13CB\u13CC\u62A5rrow;\u61A5own\xE1\u03F3er\u0100LR\u13DE\u13E8eftArrow;\u6196ightArrow;\u6197i\u0100;l\u13F9\u13FA\u43D2on;\u43A5ing;\u416Ecr;\uC000\u{1D4B0}ilde;\u4168ml\u803B\xDC\u40DC\u0480Dbcdefosv\u1427\u142C\u1430\u1433\u143E\u1485\u148A\u1490\u1496ash;\u62ABar;\u6AEBy;\u4412ash\u0100;l\u143B\u143C\u62A9;\u6AE6\u0100er\u1443\u1445;\u62C1\u0180bty\u144C\u1450\u147Aar;\u6016\u0100;i\u144F\u1455cal\u0200BLST\u1461\u1465\u146A\u1474ar;\u6223ine;\u407Ceparator;\u6758ilde;\u6240ThinSpace;\u600Ar;\uC000\u{1D519}pf;\uC000\u{1D54D}cr;\uC000\u{1D4B1}dash;\u62AA\u0280cefos\u14A7\u14AC\u14B1\u14B6\u14BCirc;\u4174dge;\u62C0r;\uC000\u{1D51A}pf;\uC000\u{1D54E}cr;\uC000\u{1D4B2}\u0200fios\u14CB\u14D0\u14D2\u14D8r;\uC000\u{1D51B};\u439Epf;\uC000\u{1D54F}cr;\uC000\u{1D4B3}\u0480AIUacfosu\u14F1\u14F5\u14F9\u14FD\u1504\u150F\u1514\u151A\u1520cy;\u442Fcy;\u4407cy;\u442Ecute\u803B\xDD\u40DD\u0100iy\u1509\u150Drc;\u4176;\u442Br;\uC000\u{1D51C}pf;\uC000\u{1D550}cr;\uC000\u{1D4B4}ml;\u4178\u0400Hacdefos\u1535\u1539\u153F\u154B\u154F\u155D\u1560\u1564cy;\u4416cute;\u4179\u0100ay\u1544\u1549ron;\u417D;\u4417ot;\u417B\u01F2\u1554\0\u155BoWidt\xE8\u0AD9a;\u4396r;\u6128pf;\u6124cr;\uC000\u{1D4B5}\u0BE1\u1583\u158A\u1590\0\u15B0\u15B6\u15BF\0\0\0\0\u15C6\u15DB\u15EB\u165F\u166D\0\u1695\u169B\u16B2\u16B9\0\u16BEcute\u803B\xE1\u40E1reve;\u4103\u0300;Ediuy\u159C\u159D\u15A1\u15A3\u15A8\u15AD\u623E;\uC000\u223E\u0333;\u623Frc\u803B\xE2\u40E2te\u80BB\xB4\u0306;\u4430lig\u803B\xE6\u40E6\u0100;r\xB2\u15BA;\uC000\u{1D51E}rave\u803B\xE0\u40E0\u0100ep\u15CA\u15D6\u0100fp\u15CF\u15D4sym;\u6135\xE8\u15D3ha;\u43B1\u0100ap\u15DFc\u0100cl\u15E4\u15E7r;\u4101g;\u6A3F\u0264\u15F0\0\0\u160A\u0280;adsv\u15FA\u15FB\u15FF\u1601\u1607\u6227nd;\u6A55;\u6A5Clope;\u6A58;\u6A5A\u0380;elmrsz\u1618\u1619\u161B\u161E\u163F\u164F\u1659\u6220;\u69A4e\xBB\u1619sd\u0100;a\u1625\u1626\u6221\u0461\u1630\u1632\u1634\u1636\u1638\u163A\u163C\u163E;\u69A8;\u69A9;\u69AA;\u69AB;\u69AC;\u69AD;\u69AE;\u69AFt\u0100;v\u1645\u1646\u621Fb\u0100;d\u164C\u164D\u62BE;\u699D\u0100pt\u1654\u1657h;\u6222\xBB\xB9arr;\u637C\u0100gp\u1663\u1667on;\u4105f;\uC000\u{1D552}\u0380;Eaeiop\u12C1\u167B\u167D\u1682\u1684\u1687\u168A;\u6A70cir;\u6A6F;\u624Ad;\u624Bs;\u4027rox\u0100;e\u12C1\u1692\xF1\u1683ing\u803B\xE5\u40E5\u0180cty\u16A1\u16A6\u16A8r;\uC000\u{1D4B6};\u402Amp\u0100;e\u12C1\u16AF\xF1\u0288ilde\u803B\xE3\u40E3ml\u803B\xE4\u40E4\u0100ci\u16C2\u16C8onin\xF4\u0272nt;\u6A11\u0800Nabcdefiklnoprsu\u16ED\u16F1\u1730\u173C\u1743\u1748\u1778\u177D\u17E0\u17E6\u1839\u1850\u170D\u193D\u1948\u1970ot;\u6AED\u0100cr\u16F6\u171Ek\u0200ceps\u1700\u1705\u170D\u1713ong;\u624Cpsilon;\u43F6rime;\u6035im\u0100;e\u171A\u171B\u623Dq;\u62CD\u0176\u1722\u1726ee;\u62BDed\u0100;g\u172C\u172D\u6305e\xBB\u172Drk\u0100;t\u135C\u1737brk;\u63B6\u0100oy\u1701\u1741;\u4431quo;\u601E\u0280cmprt\u1753\u175B\u1761\u1764\u1768aus\u0100;e\u010A\u0109ptyv;\u69B0s\xE9\u170Cno\xF5\u0113\u0180ahw\u176F\u1771\u1773;\u43B2;\u6136een;\u626Cr;\uC000\u{1D51F}g\u0380costuvw\u178D\u179D\u17B3\u17C1\u17D5\u17DB\u17DE\u0180aiu\u1794\u1796\u179A\xF0\u0760rc;\u65EFp\xBB\u1371\u0180dpt\u17A4\u17A8\u17ADot;\u6A00lus;\u6A01imes;\u6A02\u0271\u17B9\0\0\u17BEcup;\u6A06ar;\u6605riangle\u0100du\u17CD\u17D2own;\u65BDp;\u65B3plus;\u6A04e\xE5\u1444\xE5\u14ADarow;\u690D\u0180ako\u17ED\u1826\u1835\u0100cn\u17F2\u1823k\u0180lst\u17FA\u05AB\u1802ozenge;\u69EBriangle\u0200;dlr\u1812\u1813\u1818\u181D\u65B4own;\u65BEeft;\u65C2ight;\u65B8k;\u6423\u01B1\u182B\0\u1833\u01B2\u182F\0\u1831;\u6592;\u65914;\u6593ck;\u6588\u0100eo\u183E\u184D\u0100;q\u1843\u1846\uC000=\u20E5uiv;\uC000\u2261\u20E5t;\u6310\u0200ptwx\u1859\u185E\u1867\u186Cf;\uC000\u{1D553}\u0100;t\u13CB\u1863om\xBB\u13CCtie;\u62C8\u0600DHUVbdhmptuv\u1885\u1896\u18AA\u18BB\u18D7\u18DB\u18EC\u18FF\u1905\u190A\u1910\u1921\u0200LRlr\u188E\u1890\u1892\u1894;\u6557;\u6554;\u6556;\u6553\u0280;DUdu\u18A1\u18A2\u18A4\u18A6\u18A8\u6550;\u6566;\u6569;\u6564;\u6567\u0200LRlr\u18B3\u18B5\u18B7\u18B9;\u655D;\u655A;\u655C;\u6559\u0380;HLRhlr\u18CA\u18CB\u18CD\u18CF\u18D1\u18D3\u18D5\u6551;\u656C;\u6563;\u6560;\u656B;\u6562;\u655Fox;\u69C9\u0200LRlr\u18E4\u18E6\u18E8\u18EA;\u6555;\u6552;\u6510;\u650C\u0280;DUdu\u06BD\u18F7\u18F9\u18FB\u18FD;\u6565;\u6568;\u652C;\u6534inus;\u629Flus;\u629Eimes;\u62A0\u0200LRlr\u1919\u191B\u191D\u191F;\u655B;\u6558;\u6518;\u6514\u0380;HLRhlr\u1930\u1931\u1933\u1935\u1937\u1939\u193B\u6502;\u656A;\u6561;\u655E;\u653C;\u6524;\u651C\u0100ev\u0123\u1942bar\u803B\xA6\u40A6\u0200ceio\u1951\u1956\u195A\u1960r;\uC000\u{1D4B7}mi;\u604Fm\u0100;e\u171A\u171Cl\u0180;bh\u1968\u1969\u196B\u405C;\u69C5sub;\u67C8\u016C\u1974\u197El\u0100;e\u1979\u197A\u6022t\xBB\u197Ap\u0180;Ee\u012F\u1985\u1987;\u6AAE\u0100;q\u06DC\u06DB\u0CE1\u19A7\0\u19E8\u1A11\u1A15\u1A32\0\u1A37\u1A50\0\0\u1AB4\0\0\u1AC1\0\0\u1B21\u1B2E\u1B4D\u1B52\0\u1BFD\0\u1C0C\u0180cpr\u19AD\u19B2\u19DDute;\u4107\u0300;abcds\u19BF\u19C0\u19C4\u19CA\u19D5\u19D9\u6229nd;\u6A44rcup;\u6A49\u0100au\u19CF\u19D2p;\u6A4Bp;\u6A47ot;\u6A40;\uC000\u2229\uFE00\u0100eo\u19E2\u19E5t;\u6041\xEE\u0693\u0200aeiu\u19F0\u19FB\u1A01\u1A05\u01F0\u19F5\0\u19F8s;\u6A4Don;\u410Ddil\u803B\xE7\u40E7rc;\u4109ps\u0100;s\u1A0C\u1A0D\u6A4Cm;\u6A50ot;\u410B\u0180dmn\u1A1B\u1A20\u1A26il\u80BB\xB8\u01ADptyv;\u69B2t\u8100\xA2;e\u1A2D\u1A2E\u40A2r\xE4\u01B2r;\uC000\u{1D520}\u0180cei\u1A3D\u1A40\u1A4Dy;\u4447ck\u0100;m\u1A47\u1A48\u6713ark\xBB\u1A48;\u43C7r\u0380;Ecefms\u1A5F\u1A60\u1A62\u1A6B\u1AA4\u1AAA\u1AAE\u65CB;\u69C3\u0180;el\u1A69\u1A6A\u1A6D\u42C6q;\u6257e\u0261\u1A74\0\0\u1A88rrow\u0100lr\u1A7C\u1A81eft;\u61BAight;\u61BB\u0280RSacd\u1A92\u1A94\u1A96\u1A9A\u1A9F\xBB\u0F47;\u64C8st;\u629Birc;\u629Aash;\u629Dnint;\u6A10id;\u6AEFcir;\u69C2ubs\u0100;u\u1ABB\u1ABC\u6663it\xBB\u1ABC\u02EC\u1AC7\u1AD4\u1AFA\0\u1B0Aon\u0100;e\u1ACD\u1ACE\u403A\u0100;q\xC7\xC6\u026D\u1AD9\0\0\u1AE2a\u0100;t\u1ADE\u1ADF\u402C;\u4040\u0180;fl\u1AE8\u1AE9\u1AEB\u6201\xEE\u1160e\u0100mx\u1AF1\u1AF6ent\xBB\u1AE9e\xF3\u024D\u01E7\u1AFE\0\u1B07\u0100;d\u12BB\u1B02ot;\u6A6Dn\xF4\u0246\u0180fry\u1B10\u1B14\u1B17;\uC000\u{1D554}o\xE4\u0254\u8100\xA9;s\u0155\u1B1Dr;\u6117\u0100ao\u1B25\u1B29rr;\u61B5ss;\u6717\u0100cu\u1B32\u1B37r;\uC000\u{1D4B8}\u0100bp\u1B3C\u1B44\u0100;e\u1B41\u1B42\u6ACF;\u6AD1\u0100;e\u1B49\u1B4A\u6AD0;\u6AD2dot;\u62EF\u0380delprvw\u1B60\u1B6C\u1B77\u1B82\u1BAC\u1BD4\u1BF9arr\u0100lr\u1B68\u1B6A;\u6938;\u6935\u0270\u1B72\0\0\u1B75r;\u62DEc;\u62DFarr\u0100;p\u1B7F\u1B80\u61B6;\u693D\u0300;bcdos\u1B8F\u1B90\u1B96\u1BA1\u1BA5\u1BA8\u622Arcap;\u6A48\u0100au\u1B9B\u1B9Ep;\u6A46p;\u6A4Aot;\u628Dr;\u6A45;\uC000\u222A\uFE00\u0200alrv\u1BB5\u1BBF\u1BDE\u1BE3rr\u0100;m\u1BBC\u1BBD\u61B7;\u693Cy\u0180evw\u1BC7\u1BD4\u1BD8q\u0270\u1BCE\0\0\u1BD2re\xE3\u1B73u\xE3\u1B75ee;\u62CEedge;\u62CFen\u803B\xA4\u40A4earrow\u0100lr\u1BEE\u1BF3eft\xBB\u1B80ight\xBB\u1BBDe\xE4\u1BDD\u0100ci\u1C01\u1C07onin\xF4\u01F7nt;\u6231lcty;\u632D\u0980AHabcdefhijlorstuwz\u1C38\u1C3B\u1C3F\u1C5D\u1C69\u1C75\u1C8A\u1C9E\u1CAC\u1CB7\u1CFB\u1CFF\u1D0D\u1D7B\u1D91\u1DAB\u1DBB\u1DC6\u1DCDr\xF2\u0381ar;\u6965\u0200glrs\u1C48\u1C4D\u1C52\u1C54ger;\u6020eth;\u6138\xF2\u1133h\u0100;v\u1C5A\u1C5B\u6010\xBB\u090A\u016B\u1C61\u1C67arow;\u690Fa\xE3\u0315\u0100ay\u1C6E\u1C73ron;\u410F;\u4434\u0180;ao\u0332\u1C7C\u1C84\u0100gr\u02BF\u1C81r;\u61CAtseq;\u6A77\u0180glm\u1C91\u1C94\u1C98\u803B\xB0\u40B0ta;\u43B4ptyv;\u69B1\u0100ir\u1CA3\u1CA8sht;\u697F;\uC000\u{1D521}ar\u0100lr\u1CB3\u1CB5\xBB\u08DC\xBB\u101E\u0280aegsv\u1CC2\u0378\u1CD6\u1CDC\u1CE0m\u0180;os\u0326\u1CCA\u1CD4nd\u0100;s\u0326\u1CD1uit;\u6666amma;\u43DDin;\u62F2\u0180;io\u1CE7\u1CE8\u1CF8\u40F7de\u8100\xF7;o\u1CE7\u1CF0ntimes;\u62C7n\xF8\u1CF7cy;\u4452c\u026F\u1D06\0\0\u1D0Arn;\u631Eop;\u630D\u0280lptuw\u1D18\u1D1D\u1D22\u1D49\u1D55lar;\u4024f;\uC000\u{1D555}\u0280;emps\u030B\u1D2D\u1D37\u1D3D\u1D42q\u0100;d\u0352\u1D33ot;\u6251inus;\u6238lus;\u6214quare;\u62A1blebarwedg\xE5\xFAn\u0180adh\u112E\u1D5D\u1D67ownarrow\xF3\u1C83arpoon\u0100lr\u1D72\u1D76ef\xF4\u1CB4igh\xF4\u1CB6\u0162\u1D7F\u1D85karo\xF7\u0F42\u026F\u1D8A\0\0\u1D8Ern;\u631Fop;\u630C\u0180cot\u1D98\u1DA3\u1DA6\u0100ry\u1D9D\u1DA1;\uC000\u{1D4B9};\u4455l;\u69F6rok;\u4111\u0100dr\u1DB0\u1DB4ot;\u62F1i\u0100;f\u1DBA\u1816\u65BF\u0100ah\u1DC0\u1DC3r\xF2\u0429a\xF2\u0FA6angle;\u69A6\u0100ci\u1DD2\u1DD5y;\u445Fgrarr;\u67FF\u0900Dacdefglmnopqrstux\u1E01\u1E09\u1E19\u1E38\u0578\u1E3C\u1E49\u1E61\u1E7E\u1EA5\u1EAF\u1EBD\u1EE1\u1F2A\u1F37\u1F44\u1F4E\u1F5A\u0100Do\u1E06\u1D34o\xF4\u1C89\u0100cs\u1E0E\u1E14ute\u803B\xE9\u40E9ter;\u6A6E\u0200aioy\u1E22\u1E27\u1E31\u1E36ron;\u411Br\u0100;c\u1E2D\u1E2E\u6256\u803B\xEA\u40EAlon;\u6255;\u444Dot;\u4117\u0100Dr\u1E41\u1E45ot;\u6252;\uC000\u{1D522}\u0180;rs\u1E50\u1E51\u1E57\u6A9Aave\u803B\xE8\u40E8\u0100;d\u1E5C\u1E5D\u6A96ot;\u6A98\u0200;ils\u1E6A\u1E6B\u1E72\u1E74\u6A99nters;\u63E7;\u6113\u0100;d\u1E79\u1E7A\u6A95ot;\u6A97\u0180aps\u1E85\u1E89\u1E97cr;\u4113ty\u0180;sv\u1E92\u1E93\u1E95\u6205et\xBB\u1E93p\u01001;\u1E9D\u1EA4\u0133\u1EA1\u1EA3;\u6004;\u6005\u6003\u0100gs\u1EAA\u1EAC;\u414Bp;\u6002\u0100gp\u1EB4\u1EB8on;\u4119f;\uC000\u{1D556}\u0180als\u1EC4\u1ECE\u1ED2r\u0100;s\u1ECA\u1ECB\u62D5l;\u69E3us;\u6A71i\u0180;lv\u1EDA\u1EDB\u1EDF\u43B5on\xBB\u1EDB;\u43F5\u0200csuv\u1EEA\u1EF3\u1F0B\u1F23\u0100io\u1EEF\u1E31rc\xBB\u1E2E\u0269\u1EF9\0\0\u1EFB\xED\u0548ant\u0100gl\u1F02\u1F06tr\xBB\u1E5Dess\xBB\u1E7A\u0180aei\u1F12\u1F16\u1F1Als;\u403Dst;\u625Fv\u0100;D\u0235\u1F20D;\u6A78parsl;\u69E5\u0100Da\u1F2F\u1F33ot;\u6253rr;\u6971\u0180cdi\u1F3E\u1F41\u1EF8r;\u612Fo\xF4\u0352\u0100ah\u1F49\u1F4B;\u43B7\u803B\xF0\u40F0\u0100mr\u1F53\u1F57l\u803B\xEB\u40EBo;\u60AC\u0180cip\u1F61\u1F64\u1F67l;\u4021s\xF4\u056E\u0100eo\u1F6C\u1F74ctatio\xEE\u0559nential\xE5\u0579\u09E1\u1F92\0\u1F9E\0\u1FA1\u1FA7\0\0\u1FC6\u1FCC\0\u1FD3\0\u1FE6\u1FEA\u2000\0\u2008\u205Allingdotse\xF1\u1E44y;\u4444male;\u6640\u0180ilr\u1FAD\u1FB3\u1FC1lig;\u8000\uFB03\u0269\u1FB9\0\0\u1FBDg;\u8000\uFB00ig;\u8000\uFB04;\uC000\u{1D523}lig;\u8000\uFB01lig;\uC000fj\u0180alt\u1FD9\u1FDC\u1FE1t;\u666Dig;\u8000\uFB02ns;\u65B1of;\u4192\u01F0\u1FEE\0\u1FF3f;\uC000\u{1D557}\u0100ak\u05BF\u1FF7\u0100;v\u1FFC\u1FFD\u62D4;\u6AD9artint;\u6A0D\u0100ao\u200C\u2055\u0100cs\u2011\u2052\u03B1\u201A\u2030\u2038\u2045\u2048\0\u2050\u03B2\u2022\u2025\u2027\u202A\u202C\0\u202E\u803B\xBD\u40BD;\u6153\u803B\xBC\u40BC;\u6155;\u6159;\u615B\u01B3\u2034\0\u2036;\u6154;\u6156\u02B4\u203E\u2041\0\0\u2043\u803B\xBE\u40BE;\u6157;\u615C5;\u6158\u01B6\u204C\0\u204E;\u615A;\u615D8;\u615El;\u6044wn;\u6322cr;\uC000\u{1D4BB}\u0880Eabcdefgijlnorstv\u2082\u2089\u209F\u20A5\u20B0\u20B4\u20F0\u20F5\u20FA\u20FF\u2103\u2112\u2138\u0317\u213E\u2152\u219E\u0100;l\u064D\u2087;\u6A8C\u0180cmp\u2090\u2095\u209Dute;\u41F5ma\u0100;d\u209C\u1CDA\u43B3;\u6A86reve;\u411F\u0100iy\u20AA\u20AErc;\u411D;\u4433ot;\u4121\u0200;lqs\u063E\u0642\u20BD\u20C9\u0180;qs\u063E\u064C\u20C4lan\xF4\u0665\u0200;cdl\u0665\u20D2\u20D5\u20E5c;\u6AA9ot\u0100;o\u20DC\u20DD\u6A80\u0100;l\u20E2\u20E3\u6A82;\u6A84\u0100;e\u20EA\u20ED\uC000\u22DB\uFE00s;\u6A94r;\uC000\u{1D524}\u0100;g\u0673\u061Bmel;\u6137cy;\u4453\u0200;Eaj\u065A\u210C\u210E\u2110;\u6A92;\u6AA5;\u6AA4\u0200Eaes\u211B\u211D\u2129\u2134;\u6269p\u0100;p\u2123\u2124\u6A8Arox\xBB\u2124\u0100;q\u212E\u212F\u6A88\u0100;q\u212E\u211Bim;\u62E7pf;\uC000\u{1D558}\u0100ci\u2143\u2146r;\u610Am\u0180;el\u066B\u214E\u2150;\u6A8E;\u6A90\u8300>;cdlqr\u05EE\u2160\u216A\u216E\u2173\u2179\u0100ci\u2165\u2167;\u6AA7r;\u6A7Aot;\u62D7Par;\u6995uest;\u6A7C\u0280adels\u2184\u216A\u2190\u0656\u219B\u01F0\u2189\0\u218Epro\xF8\u209Er;\u6978q\u0100lq\u063F\u2196les\xF3\u2088i\xED\u066B\u0100en\u21A3\u21ADrtneqq;\uC000\u2269\uFE00\xC5\u21AA\u0500Aabcefkosy\u21C4\u21C7\u21F1\u21F5\u21FA\u2218\u221D\u222F\u2268\u227Dr\xF2\u03A0\u0200ilmr\u21D0\u21D4\u21D7\u21DBrs\xF0\u1484f\xBB\u2024il\xF4\u06A9\u0100dr\u21E0\u21E4cy;\u444A\u0180;cw\u08F4\u21EB\u21EFir;\u6948;\u61ADar;\u610Firc;\u4125\u0180alr\u2201\u220E\u2213rts\u0100;u\u2209\u220A\u6665it\xBB\u220Alip;\u6026con;\u62B9r;\uC000\u{1D525}s\u0100ew\u2223\u2229arow;\u6925arow;\u6926\u0280amopr\u223A\u223E\u2243\u225E\u2263rr;\u61FFtht;\u623Bk\u0100lr\u2249\u2253eftarrow;\u61A9ightarrow;\u61AAf;\uC000\u{1D559}bar;\u6015\u0180clt\u226F\u2274\u2278r;\uC000\u{1D4BD}as\xE8\u21F4rok;\u4127\u0100bp\u2282\u2287ull;\u6043hen\xBB\u1C5B\u0AE1\u22A3\0\u22AA\0\u22B8\u22C5\u22CE\0\u22D5\u22F3\0\0\u22F8\u2322\u2367\u2362\u237F\0\u2386\u23AA\u23B4cute\u803B\xED\u40ED\u0180;iy\u0771\u22B0\u22B5rc\u803B\xEE\u40EE;\u4438\u0100cx\u22BC\u22BFy;\u4435cl\u803B\xA1\u40A1\u0100fr\u039F\u22C9;\uC000\u{1D526}rave\u803B\xEC\u40EC\u0200;ino\u073E\u22DD\u22E9\u22EE\u0100in\u22E2\u22E6nt;\u6A0Ct;\u622Dfin;\u69DCta;\u6129lig;\u4133\u0180aop\u22FE\u231A\u231D\u0180cgt\u2305\u2308\u2317r;\u412B\u0180elp\u071F\u230F\u2313in\xE5\u078Ear\xF4\u0720h;\u4131f;\u62B7ed;\u41B5\u0280;cfot\u04F4\u232C\u2331\u233D\u2341are;\u6105in\u0100;t\u2338\u2339\u621Eie;\u69DDdo\xF4\u2319\u0280;celp\u0757\u234C\u2350\u235B\u2361al;\u62BA\u0100gr\u2355\u2359er\xF3\u1563\xE3\u234Darhk;\u6A17rod;\u6A3C\u0200cgpt\u236F\u2372\u2376\u237By;\u4451on;\u412Ff;\uC000\u{1D55A}a;\u43B9uest\u803B\xBF\u40BF\u0100ci\u238A\u238Fr;\uC000\u{1D4BE}n\u0280;Edsv\u04F4\u239B\u239D\u23A1\u04F3;\u62F9ot;\u62F5\u0100;v\u23A6\u23A7\u62F4;\u62F3\u0100;i\u0777\u23AElde;\u4129\u01EB\u23B8\0\u23BCcy;\u4456l\u803B\xEF\u40EF\u0300cfmosu\u23CC\u23D7\u23DC\u23E1\u23E7\u23F5\u0100iy\u23D1\u23D5rc;\u4135;\u4439r;\uC000\u{1D527}ath;\u4237pf;\uC000\u{1D55B}\u01E3\u23EC\0\u23F1r;\uC000\u{1D4BF}rcy;\u4458kcy;\u4454\u0400acfghjos\u240B\u2416\u2422\u2427\u242D\u2431\u2435\u243Bppa\u0100;v\u2413\u2414\u43BA;\u43F0\u0100ey\u241B\u2420dil;\u4137;\u443Ar;\uC000\u{1D528}reen;\u4138cy;\u4445cy;\u445Cpf;\uC000\u{1D55C}cr;\uC000\u{1D4C0}\u0B80ABEHabcdefghjlmnoprstuv\u2470\u2481\u2486\u248D\u2491\u250E\u253D\u255A\u2580\u264E\u265E\u2665\u2679\u267D\u269A\u26B2\u26D8\u275D\u2768\u278B\u27C0\u2801\u2812\u0180art\u2477\u247A\u247Cr\xF2\u09C6\xF2\u0395ail;\u691Barr;\u690E\u0100;g\u0994\u248B;\u6A8Bar;\u6962\u0963\u24A5\0\u24AA\0\u24B1\0\0\0\0\0\u24B5\u24BA\0\u24C6\u24C8\u24CD\0\u24F9ute;\u413Amptyv;\u69B4ra\xEE\u084Cbda;\u43BBg\u0180;dl\u088E\u24C1\u24C3;\u6991\xE5\u088E;\u6A85uo\u803B\xAB\u40ABr\u0400;bfhlpst\u0899\u24DE\u24E6\u24E9\u24EB\u24EE\u24F1\u24F5\u0100;f\u089D\u24E3s;\u691Fs;\u691D\xEB\u2252p;\u61ABl;\u6939im;\u6973l;\u61A2\u0180;ae\u24FF\u2500\u2504\u6AABil;\u6919\u0100;s\u2509\u250A\u6AAD;\uC000\u2AAD\uFE00\u0180abr\u2515\u2519\u251Drr;\u690Crk;\u6772\u0100ak\u2522\u252Cc\u0100ek\u2528\u252A;\u407B;\u405B\u0100es\u2531\u2533;\u698Bl\u0100du\u2539\u253B;\u698F;\u698D\u0200aeuy\u2546\u254B\u2556\u2558ron;\u413E\u0100di\u2550\u2554il;\u413C\xEC\u08B0\xE2\u2529;\u443B\u0200cqrs\u2563\u2566\u256D\u257Da;\u6936uo\u0100;r\u0E19\u1746\u0100du\u2572\u2577har;\u6967shar;\u694Bh;\u61B2\u0280;fgqs\u258B\u258C\u0989\u25F3\u25FF\u6264t\u0280ahlrt\u2598\u25A4\u25B7\u25C2\u25E8rrow\u0100;t\u0899\u25A1a\xE9\u24F6arpoon\u0100du\u25AF\u25B4own\xBB\u045Ap\xBB\u0966eftarrows;\u61C7ight\u0180ahs\u25CD\u25D6\u25DErrow\u0100;s\u08F4\u08A7arpoon\xF3\u0F98quigarro\xF7\u21F0hreetimes;\u62CB\u0180;qs\u258B\u0993\u25FAlan\xF4\u09AC\u0280;cdgs\u09AC\u260A\u260D\u261D\u2628c;\u6AA8ot\u0100;o\u2614\u2615\u6A7F\u0100;r\u261A\u261B\u6A81;\u6A83\u0100;e\u2622\u2625\uC000\u22DA\uFE00s;\u6A93\u0280adegs\u2633\u2639\u263D\u2649\u264Bppro\xF8\u24C6ot;\u62D6q\u0100gq\u2643\u2645\xF4\u0989gt\xF2\u248C\xF4\u099Bi\xED\u09B2\u0180ilr\u2655\u08E1\u265Asht;\u697C;\uC000\u{1D529}\u0100;E\u099C\u2663;\u6A91\u0161\u2669\u2676r\u0100du\u25B2\u266E\u0100;l\u0965\u2673;\u696Alk;\u6584cy;\u4459\u0280;acht\u0A48\u2688\u268B\u2691\u2696r\xF2\u25C1orne\xF2\u1D08ard;\u696Bri;\u65FA\u0100io\u269F\u26A4dot;\u4140ust\u0100;a\u26AC\u26AD\u63B0che\xBB\u26AD\u0200Eaes\u26BB\u26BD\u26C9\u26D4;\u6268p\u0100;p\u26C3\u26C4\u6A89rox\xBB\u26C4\u0100;q\u26CE\u26CF\u6A87\u0100;q\u26CE\u26BBim;\u62E6\u0400abnoptwz\u26E9\u26F4\u26F7\u271A\u272F\u2741\u2747\u2750\u0100nr\u26EE\u26F1g;\u67ECr;\u61FDr\xEB\u08C1g\u0180lmr\u26FF\u270D\u2714eft\u0100ar\u09E6\u2707ight\xE1\u09F2apsto;\u67FCight\xE1\u09FDparrow\u0100lr\u2725\u2729ef\xF4\u24EDight;\u61AC\u0180afl\u2736\u2739\u273Dr;\u6985;\uC000\u{1D55D}us;\u6A2Dimes;\u6A34\u0161\u274B\u274Fst;\u6217\xE1\u134E\u0180;ef\u2757\u2758\u1800\u65CAnge\xBB\u2758ar\u0100;l\u2764\u2765\u4028t;\u6993\u0280achmt\u2773\u2776\u277C\u2785\u2787r\xF2\u08A8orne\xF2\u1D8Car\u0100;d\u0F98\u2783;\u696D;\u600Eri;\u62BF\u0300achiqt\u2798\u279D\u0A40\u27A2\u27AE\u27BBquo;\u6039r;\uC000\u{1D4C1}m\u0180;eg\u09B2\u27AA\u27AC;\u6A8D;\u6A8F\u0100bu\u252A\u27B3o\u0100;r\u0E1F\u27B9;\u601Arok;\u4142\u8400<;cdhilqr\u082B\u27D2\u2639\u27DC\u27E0\u27E5\u27EA\u27F0\u0100ci\u27D7\u27D9;\u6AA6r;\u6A79re\xE5\u25F2mes;\u62C9arr;\u6976uest;\u6A7B\u0100Pi\u27F5\u27F9ar;\u6996\u0180;ef\u2800\u092D\u181B\u65C3r\u0100du\u2807\u280Dshar;\u694Ahar;\u6966\u0100en\u2817\u2821rtneqq;\uC000\u2268\uFE00\xC5\u281E\u0700Dacdefhilnopsu\u2840\u2845\u2882\u288E\u2893\u28A0\u28A5\u28A8\u28DA\u28E2\u28E4\u0A83\u28F3\u2902Dot;\u623A\u0200clpr\u284E\u2852\u2863\u287Dr\u803B\xAF\u40AF\u0100et\u2857\u2859;\u6642\u0100;e\u285E\u285F\u6720se\xBB\u285F\u0100;s\u103B\u2868to\u0200;dlu\u103B\u2873\u2877\u287Bow\xEE\u048Cef\xF4\u090F\xF0\u13D1ker;\u65AE\u0100oy\u2887\u288Cmma;\u6A29;\u443Cash;\u6014asuredangle\xBB\u1626r;\uC000\u{1D52A}o;\u6127\u0180cdn\u28AF\u28B4\u28C9ro\u803B\xB5\u40B5\u0200;acd\u1464\u28BD\u28C0\u28C4s\xF4\u16A7ir;\u6AF0ot\u80BB\xB7\u01B5us\u0180;bd\u28D2\u1903\u28D3\u6212\u0100;u\u1D3C\u28D8;\u6A2A\u0163\u28DE\u28E1p;\u6ADB\xF2\u2212\xF0\u0A81\u0100dp\u28E9\u28EEels;\u62A7f;\uC000\u{1D55E}\u0100ct\u28F8\u28FDr;\uC000\u{1D4C2}pos\xBB\u159D\u0180;lm\u2909\u290A\u290D\u43BCtimap;\u62B8\u0C00GLRVabcdefghijlmoprstuvw\u2942\u2953\u297E\u2989\u2998\u29DA\u29E9\u2A15\u2A1A\u2A58\u2A5D\u2A83\u2A95\u2AA4\u2AA8\u2B04\u2B07\u2B44\u2B7F\u2BAE\u2C34\u2C67\u2C7C\u2CE9\u0100gt\u2947\u294B;\uC000\u22D9\u0338\u0100;v\u2950\u0BCF\uC000\u226B\u20D2\u0180elt\u295A\u2972\u2976ft\u0100ar\u2961\u2967rrow;\u61CDightarrow;\u61CE;\uC000\u22D8\u0338\u0100;v\u297B\u0C47\uC000\u226A\u20D2ightarrow;\u61CF\u0100Dd\u298E\u2993ash;\u62AFash;\u62AE\u0280bcnpt\u29A3\u29A7\u29AC\u29B1\u29CCla\xBB\u02DEute;\u4144g;\uC000\u2220\u20D2\u0280;Eiop\u0D84\u29BC\u29C0\u29C5\u29C8;\uC000\u2A70\u0338d;\uC000\u224B\u0338s;\u4149ro\xF8\u0D84ur\u0100;a\u29D3\u29D4\u666El\u0100;s\u29D3\u0B38\u01F3\u29DF\0\u29E3p\u80BB\xA0\u0B37mp\u0100;e\u0BF9\u0C00\u0280aeouy\u29F4\u29FE\u2A03\u2A10\u2A13\u01F0\u29F9\0\u29FB;\u6A43on;\u4148dil;\u4146ng\u0100;d\u0D7E\u2A0Aot;\uC000\u2A6D\u0338p;\u6A42;\u443Dash;\u6013\u0380;Aadqsx\u0B92\u2A29\u2A2D\u2A3B\u2A41\u2A45\u2A50rr;\u61D7r\u0100hr\u2A33\u2A36k;\u6924\u0100;o\u13F2\u13F0ot;\uC000\u2250\u0338ui\xF6\u0B63\u0100ei\u2A4A\u2A4Ear;\u6928\xED\u0B98ist\u0100;s\u0BA0\u0B9Fr;\uC000\u{1D52B}\u0200Eest\u0BC5\u2A66\u2A79\u2A7C\u0180;qs\u0BBC\u2A6D\u0BE1\u0180;qs\u0BBC\u0BC5\u2A74lan\xF4\u0BE2i\xED\u0BEA\u0100;r\u0BB6\u2A81\xBB\u0BB7\u0180Aap\u2A8A\u2A8D\u2A91r\xF2\u2971rr;\u61AEar;\u6AF2\u0180;sv\u0F8D\u2A9C\u0F8C\u0100;d\u2AA1\u2AA2\u62FC;\u62FAcy;\u445A\u0380AEadest\u2AB7\u2ABA\u2ABE\u2AC2\u2AC5\u2AF6\u2AF9r\xF2\u2966;\uC000\u2266\u0338rr;\u619Ar;\u6025\u0200;fqs\u0C3B\u2ACE\u2AE3\u2AEFt\u0100ar\u2AD4\u2AD9rro\xF7\u2AC1ightarro\xF7\u2A90\u0180;qs\u0C3B\u2ABA\u2AEAlan\xF4\u0C55\u0100;s\u0C55\u2AF4\xBB\u0C36i\xED\u0C5D\u0100;r\u0C35\u2AFEi\u0100;e\u0C1A\u0C25i\xE4\u0D90\u0100pt\u2B0C\u2B11f;\uC000\u{1D55F}\u8180\xAC;in\u2B19\u2B1A\u2B36\u40ACn\u0200;Edv\u0B89\u2B24\u2B28\u2B2E;\uC000\u22F9\u0338ot;\uC000\u22F5\u0338\u01E1\u0B89\u2B33\u2B35;\u62F7;\u62F6i\u0100;v\u0CB8\u2B3C\u01E1\u0CB8\u2B41\u2B43;\u62FE;\u62FD\u0180aor\u2B4B\u2B63\u2B69r\u0200;ast\u0B7B\u2B55\u2B5A\u2B5Flle\xEC\u0B7Bl;\uC000\u2AFD\u20E5;\uC000\u2202\u0338lint;\u6A14\u0180;ce\u0C92\u2B70\u2B73u\xE5\u0CA5\u0100;c\u0C98\u2B78\u0100;e\u0C92\u2B7D\xF1\u0C98\u0200Aait\u2B88\u2B8B\u2B9D\u2BA7r\xF2\u2988rr\u0180;cw\u2B94\u2B95\u2B99\u619B;\uC000\u2933\u0338;\uC000\u219D\u0338ghtarrow\xBB\u2B95ri\u0100;e\u0CCB\u0CD6\u0380chimpqu\u2BBD\u2BCD\u2BD9\u2B04\u0B78\u2BE4\u2BEF\u0200;cer\u0D32\u2BC6\u0D37\u2BC9u\xE5\u0D45;\uC000\u{1D4C3}ort\u026D\u2B05\0\0\u2BD6ar\xE1\u2B56m\u0100;e\u0D6E\u2BDF\u0100;q\u0D74\u0D73su\u0100bp\u2BEB\u2BED\xE5\u0CF8\xE5\u0D0B\u0180bcp\u2BF6\u2C11\u2C19\u0200;Ees\u2BFF\u2C00\u0D22\u2C04\u6284;\uC000\u2AC5\u0338et\u0100;e\u0D1B\u2C0Bq\u0100;q\u0D23\u2C00c\u0100;e\u0D32\u2C17\xF1\u0D38\u0200;Ees\u2C22\u2C23\u0D5F\u2C27\u6285;\uC000\u2AC6\u0338et\u0100;e\u0D58\u2C2Eq\u0100;q\u0D60\u2C23\u0200gilr\u2C3D\u2C3F\u2C45\u2C47\xEC\u0BD7lde\u803B\xF1\u40F1\xE7\u0C43iangle\u0100lr\u2C52\u2C5Ceft\u0100;e\u0C1A\u2C5A\xF1\u0C26ight\u0100;e\u0CCB\u2C65\xF1\u0CD7\u0100;m\u2C6C\u2C6D\u43BD\u0180;es\u2C74\u2C75\u2C79\u4023ro;\u6116p;\u6007\u0480DHadgilrs\u2C8F\u2C94\u2C99\u2C9E\u2CA3\u2CB0\u2CB6\u2CD3\u2CE3ash;\u62ADarr;\u6904p;\uC000\u224D\u20D2ash;\u62AC\u0100et\u2CA8\u2CAC;\uC000\u2265\u20D2;\uC000>\u20D2nfin;\u69DE\u0180Aet\u2CBD\u2CC1\u2CC5rr;\u6902;\uC000\u2264\u20D2\u0100;r\u2CCA\u2CCD\uC000<\u20D2ie;\uC000\u22B4\u20D2\u0100At\u2CD8\u2CDCrr;\u6903rie;\uC000\u22B5\u20D2im;\uC000\u223C\u20D2\u0180Aan\u2CF0\u2CF4\u2D02rr;\u61D6r\u0100hr\u2CFA\u2CFDk;\u6923\u0100;o\u13E7\u13E5ear;\u6927\u1253\u1A95\0\0\0\0\0\0\0\0\0\0\0\0\0\u2D2D\0\u2D38\u2D48\u2D60\u2D65\u2D72\u2D84\u1B07\0\0\u2D8D\u2DAB\0\u2DC8\u2DCE\0\u2DDC\u2E19\u2E2B\u2E3E\u2E43\u0100cs\u2D31\u1A97ute\u803B\xF3\u40F3\u0100iy\u2D3C\u2D45r\u0100;c\u1A9E\u2D42\u803B\xF4\u40F4;\u443E\u0280abios\u1AA0\u2D52\u2D57\u01C8\u2D5Alac;\u4151v;\u6A38old;\u69BClig;\u4153\u0100cr\u2D69\u2D6Dir;\u69BF;\uC000\u{1D52C}\u036F\u2D79\0\0\u2D7C\0\u2D82n;\u42DBave\u803B\xF2\u40F2;\u69C1\u0100bm\u2D88\u0DF4ar;\u69B5\u0200acit\u2D95\u2D98\u2DA5\u2DA8r\xF2\u1A80\u0100ir\u2D9D\u2DA0r;\u69BEoss;\u69BBn\xE5\u0E52;\u69C0\u0180aei\u2DB1\u2DB5\u2DB9cr;\u414Dga;\u43C9\u0180cdn\u2DC0\u2DC5\u01CDron;\u43BF;\u69B6pf;\uC000\u{1D560}\u0180ael\u2DD4\u2DD7\u01D2r;\u69B7rp;\u69B9\u0380;adiosv\u2DEA\u2DEB\u2DEE\u2E08\u2E0D\u2E10\u2E16\u6228r\xF2\u1A86\u0200;efm\u2DF7\u2DF8\u2E02\u2E05\u6A5Dr\u0100;o\u2DFE\u2DFF\u6134f\xBB\u2DFF\u803B\xAA\u40AA\u803B\xBA\u40BAgof;\u62B6r;\u6A56lope;\u6A57;\u6A5B\u0180clo\u2E1F\u2E21\u2E27\xF2\u2E01ash\u803B\xF8\u40F8l;\u6298i\u016C\u2E2F\u2E34de\u803B\xF5\u40F5es\u0100;a\u01DB\u2E3As;\u6A36ml\u803B\xF6\u40F6bar;\u633D\u0AE1\u2E5E\0\u2E7D\0\u2E80\u2E9D\0\u2EA2\u2EB9\0\0\u2ECB\u0E9C\0\u2F13\0\0\u2F2B\u2FBC\0\u2FC8r\u0200;ast\u0403\u2E67\u2E72\u0E85\u8100\xB6;l\u2E6D\u2E6E\u40B6le\xEC\u0403\u0269\u2E78\0\0\u2E7Bm;\u6AF3;\u6AFDy;\u443Fr\u0280cimpt\u2E8B\u2E8F\u2E93\u1865\u2E97nt;\u4025od;\u402Eil;\u6030enk;\u6031r;\uC000\u{1D52D}\u0180imo\u2EA8\u2EB0\u2EB4\u0100;v\u2EAD\u2EAE\u43C6;\u43D5ma\xF4\u0A76ne;\u660E\u0180;tv\u2EBF\u2EC0\u2EC8\u43C0chfork\xBB\u1FFD;\u43D6\u0100au\u2ECF\u2EDFn\u0100ck\u2ED5\u2EDDk\u0100;h\u21F4\u2EDB;\u610E\xF6\u21F4s\u0480;abcdemst\u2EF3\u2EF4\u1908\u2EF9\u2EFD\u2F04\u2F06\u2F0A\u2F0E\u402Bcir;\u6A23ir;\u6A22\u0100ou\u1D40\u2F02;\u6A25;\u6A72n\u80BB\xB1\u0E9Dim;\u6A26wo;\u6A27\u0180ipu\u2F19\u2F20\u2F25ntint;\u6A15f;\uC000\u{1D561}nd\u803B\xA3\u40A3\u0500;Eaceinosu\u0EC8\u2F3F\u2F41\u2F44\u2F47\u2F81\u2F89\u2F92\u2F7E\u2FB6;\u6AB3p;\u6AB7u\xE5\u0ED9\u0100;c\u0ECE\u2F4C\u0300;acens\u0EC8\u2F59\u2F5F\u2F66\u2F68\u2F7Eppro\xF8\u2F43urlye\xF1\u0ED9\xF1\u0ECE\u0180aes\u2F6F\u2F76\u2F7Approx;\u6AB9qq;\u6AB5im;\u62E8i\xED\u0EDFme\u0100;s\u2F88\u0EAE\u6032\u0180Eas\u2F78\u2F90\u2F7A\xF0\u2F75\u0180dfp\u0EEC\u2F99\u2FAF\u0180als\u2FA0\u2FA5\u2FAAlar;\u632Eine;\u6312urf;\u6313\u0100;t\u0EFB\u2FB4\xEF\u0EFBrel;\u62B0\u0100ci\u2FC0\u2FC5r;\uC000\u{1D4C5};\u43C8ncsp;\u6008\u0300fiopsu\u2FDA\u22E2\u2FDF\u2FE5\u2FEB\u2FF1r;\uC000\u{1D52E}pf;\uC000\u{1D562}rime;\u6057cr;\uC000\u{1D4C6}\u0180aeo\u2FF8\u3009\u3013t\u0100ei\u2FFE\u3005rnion\xF3\u06B0nt;\u6A16st\u0100;e\u3010\u3011\u403F\xF1\u1F19\xF4\u0F14\u0A80ABHabcdefhilmnoprstux\u3040\u3051\u3055\u3059\u30E0\u310E\u312B\u3147\u3162\u3172\u318E\u3206\u3215\u3224\u3229\u3258\u326E\u3272\u3290\u32B0\u32B7\u0180art\u3047\u304A\u304Cr\xF2\u10B3\xF2\u03DDail;\u691Car\xF2\u1C65ar;\u6964\u0380cdenqrt\u3068\u3075\u3078\u307F\u308F\u3094\u30CC\u0100eu\u306D\u3071;\uC000\u223D\u0331te;\u4155i\xE3\u116Emptyv;\u69B3g\u0200;del\u0FD1\u3089\u308B\u308D;\u6992;\u69A5\xE5\u0FD1uo\u803B\xBB\u40BBr\u0580;abcfhlpstw\u0FDC\u30AC\u30AF\u30B7\u30B9\u30BC\u30BE\u30C0\u30C3\u30C7\u30CAp;\u6975\u0100;f\u0FE0\u30B4s;\u6920;\u6933s;\u691E\xEB\u225D\xF0\u272El;\u6945im;\u6974l;\u61A3;\u619D\u0100ai\u30D1\u30D5il;\u691Ao\u0100;n\u30DB\u30DC\u6236al\xF3\u0F1E\u0180abr\u30E7\u30EA\u30EEr\xF2\u17E5rk;\u6773\u0100ak\u30F3\u30FDc\u0100ek\u30F9\u30FB;\u407D;\u405D\u0100es\u3102\u3104;\u698Cl\u0100du\u310A\u310C;\u698E;\u6990\u0200aeuy\u3117\u311C\u3127\u3129ron;\u4159\u0100di\u3121\u3125il;\u4157\xEC\u0FF2\xE2\u30FA;\u4440\u0200clqs\u3134\u3137\u313D\u3144a;\u6937dhar;\u6969uo\u0100;r\u020E\u020Dh;\u61B3\u0180acg\u314E\u315F\u0F44l\u0200;ips\u0F78\u3158\u315B\u109Cn\xE5\u10BBar\xF4\u0FA9t;\u65AD\u0180ilr\u3169\u1023\u316Esht;\u697D;\uC000\u{1D52F}\u0100ao\u3177\u3186r\u0100du\u317D\u317F\xBB\u047B\u0100;l\u1091\u3184;\u696C\u0100;v\u318B\u318C\u43C1;\u43F1\u0180gns\u3195\u31F9\u31FCht\u0300ahlrst\u31A4\u31B0\u31C2\u31D8\u31E4\u31EErrow\u0100;t\u0FDC\u31ADa\xE9\u30C8arpoon\u0100du\u31BB\u31BFow\xEE\u317Ep\xBB\u1092eft\u0100ah\u31CA\u31D0rrow\xF3\u0FEAarpoon\xF3\u0551ightarrows;\u61C9quigarro\xF7\u30CBhreetimes;\u62CCg;\u42DAingdotse\xF1\u1F32\u0180ahm\u320D\u3210\u3213r\xF2\u0FEAa\xF2\u0551;\u600Foust\u0100;a\u321E\u321F\u63B1che\xBB\u321Fmid;\u6AEE\u0200abpt\u3232\u323D\u3240\u3252\u0100nr\u3237\u323Ag;\u67EDr;\u61FEr\xEB\u1003\u0180afl\u3247\u324A\u324Er;\u6986;\uC000\u{1D563}us;\u6A2Eimes;\u6A35\u0100ap\u325D\u3267r\u0100;g\u3263\u3264\u4029t;\u6994olint;\u6A12ar\xF2\u31E3\u0200achq\u327B\u3280\u10BC\u3285quo;\u603Ar;\uC000\u{1D4C7}\u0100bu\u30FB\u328Ao\u0100;r\u0214\u0213\u0180hir\u3297\u329B\u32A0re\xE5\u31F8mes;\u62CAi\u0200;efl\u32AA\u1059\u1821\u32AB\u65B9tri;\u69CEluhar;\u6968;\u611E\u0D61\u32D5\u32DB\u32DF\u332C\u3338\u3371\0\u337A\u33A4\0\0\u33EC\u33F0\0\u3428\u3448\u345A\u34AD\u34B1\u34CA\u34F1\0\u3616\0\0\u3633cute;\u415Bqu\xEF\u27BA\u0500;Eaceinpsy\u11ED\u32F3\u32F5\u32FF\u3302\u330B\u330F\u331F\u3326\u3329;\u6AB4\u01F0\u32FA\0\u32FC;\u6AB8on;\u4161u\xE5\u11FE\u0100;d\u11F3\u3307il;\u415Frc;\u415D\u0180Eas\u3316\u3318\u331B;\u6AB6p;\u6ABAim;\u62E9olint;\u6A13i\xED\u1204;\u4441ot\u0180;be\u3334\u1D47\u3335\u62C5;\u6A66\u0380Aacmstx\u3346\u334A\u3357\u335B\u335E\u3363\u336Drr;\u61D8r\u0100hr\u3350\u3352\xEB\u2228\u0100;o\u0A36\u0A34t\u803B\xA7\u40A7i;\u403Bwar;\u6929m\u0100in\u3369\xF0nu\xF3\xF1t;\u6736r\u0100;o\u3376\u2055\uC000\u{1D530}\u0200acoy\u3382\u3386\u3391\u33A0rp;\u666F\u0100hy\u338B\u338Fcy;\u4449;\u4448rt\u026D\u3399\0\0\u339Ci\xE4\u1464ara\xEC\u2E6F\u803B\xAD\u40AD\u0100gm\u33A8\u33B4ma\u0180;fv\u33B1\u33B2\u33B2\u43C3;\u43C2\u0400;deglnpr\u12AB\u33C5\u33C9\u33CE\u33D6\u33DE\u33E1\u33E6ot;\u6A6A\u0100;q\u12B1\u12B0\u0100;E\u33D3\u33D4\u6A9E;\u6AA0\u0100;E\u33DB\u33DC\u6A9D;\u6A9Fe;\u6246lus;\u6A24arr;\u6972ar\xF2\u113D\u0200aeit\u33F8\u3408\u340F\u3417\u0100ls\u33FD\u3404lsetm\xE9\u336Ahp;\u6A33parsl;\u69E4\u0100dl\u1463\u3414e;\u6323\u0100;e\u341C\u341D\u6AAA\u0100;s\u3422\u3423\u6AAC;\uC000\u2AAC\uFE00\u0180flp\u342E\u3433\u3442tcy;\u444C\u0100;b\u3438\u3439\u402F\u0100;a\u343E\u343F\u69C4r;\u633Ff;\uC000\u{1D564}a\u0100dr\u344D\u0402es\u0100;u\u3454\u3455\u6660it\xBB\u3455\u0180csu\u3460\u3479\u349F\u0100au\u3465\u346Fp\u0100;s\u1188\u346B;\uC000\u2293\uFE00p\u0100;s\u11B4\u3475;\uC000\u2294\uFE00u\u0100bp\u347F\u348F\u0180;es\u1197\u119C\u3486et\u0100;e\u1197\u348D\xF1\u119D\u0180;es\u11A8\u11AD\u3496et\u0100;e\u11A8\u349D\xF1\u11AE\u0180;af\u117B\u34A6\u05B0r\u0165\u34AB\u05B1\xBB\u117Car\xF2\u1148\u0200cemt\u34B9\u34BE\u34C2\u34C5r;\uC000\u{1D4C8}tm\xEE\xF1i\xEC\u3415ar\xE6\u11BE\u0100ar\u34CE\u34D5r\u0100;f\u34D4\u17BF\u6606\u0100an\u34DA\u34EDight\u0100ep\u34E3\u34EApsilo\xEE\u1EE0h\xE9\u2EAFs\xBB\u2852\u0280bcmnp\u34FB\u355E\u1209\u358B\u358E\u0480;Edemnprs\u350E\u350F\u3511\u3515\u351E\u3523\u352C\u3531\u3536\u6282;\u6AC5ot;\u6ABD\u0100;d\u11DA\u351Aot;\u6AC3ult;\u6AC1\u0100Ee\u3528\u352A;\u6ACB;\u628Alus;\u6ABFarr;\u6979\u0180eiu\u353D\u3552\u3555t\u0180;en\u350E\u3545\u354Bq\u0100;q\u11DA\u350Feq\u0100;q\u352B\u3528m;\u6AC7\u0100bp\u355A\u355C;\u6AD5;\u6AD3c\u0300;acens\u11ED\u356C\u3572\u3579\u357B\u3326ppro\xF8\u32FAurlye\xF1\u11FE\xF1\u11F3\u0180aes\u3582\u3588\u331Bppro\xF8\u331Aq\xF1\u3317g;\u666A\u0680123;Edehlmnps\u35A9\u35AC\u35AF\u121C\u35B2\u35B4\u35C0\u35C9\u35D5\u35DA\u35DF\u35E8\u35ED\u803B\xB9\u40B9\u803B\xB2\u40B2\u803B\xB3\u40B3;\u6AC6\u0100os\u35B9\u35BCt;\u6ABEub;\u6AD8\u0100;d\u1222\u35C5ot;\u6AC4s\u0100ou\u35CF\u35D2l;\u67C9b;\u6AD7arr;\u697Bult;\u6AC2\u0100Ee\u35E4\u35E6;\u6ACC;\u628Blus;\u6AC0\u0180eiu\u35F4\u3609\u360Ct\u0180;en\u121C\u35FC\u3602q\u0100;q\u1222\u35B2eq\u0100;q\u35E7\u35E4m;\u6AC8\u0100bp\u3611\u3613;\u6AD4;\u6AD6\u0180Aan\u361C\u3620\u362Drr;\u61D9r\u0100hr\u3626\u3628\xEB\u222E\u0100;o\u0A2B\u0A29war;\u692Alig\u803B\xDF\u40DF\u0BE1\u3651\u365D\u3660\u12CE\u3673\u3679\0\u367E\u36C2\0\0\0\0\0\u36DB\u3703\0\u3709\u376C\0\0\0\u3787\u0272\u3656\0\0\u365Bget;\u6316;\u43C4r\xEB\u0E5F\u0180aey\u3666\u366B\u3670ron;\u4165dil;\u4163;\u4442lrec;\u6315r;\uC000\u{1D531}\u0200eiko\u3686\u369D\u36B5\u36BC\u01F2\u368B\0\u3691e\u01004f\u1284\u1281a\u0180;sv\u3698\u3699\u369B\u43B8ym;\u43D1\u0100cn\u36A2\u36B2k\u0100as\u36A8\u36AEppro\xF8\u12C1im\xBB\u12ACs\xF0\u129E\u0100as\u36BA\u36AE\xF0\u12C1rn\u803B\xFE\u40FE\u01EC\u031F\u36C6\u22E7es\u8180\xD7;bd\u36CF\u36D0\u36D8\u40D7\u0100;a\u190F\u36D5r;\u6A31;\u6A30\u0180eps\u36E1\u36E3\u3700\xE1\u2A4D\u0200;bcf\u0486\u36EC\u36F0\u36F4ot;\u6336ir;\u6AF1\u0100;o\u36F9\u36FC\uC000\u{1D565}rk;\u6ADA\xE1\u3362rime;\u6034\u0180aip\u370F\u3712\u3764d\xE5\u1248\u0380adempst\u3721\u374D\u3740\u3751\u3757\u375C\u375Fngle\u0280;dlqr\u3730\u3731\u3736\u3740\u3742\u65B5own\xBB\u1DBBeft\u0100;e\u2800\u373E\xF1\u092E;\u625Cight\u0100;e\u32AA\u374B\xF1\u105Aot;\u65ECinus;\u6A3Alus;\u6A39b;\u69CDime;\u6A3Bezium;\u63E2\u0180cht\u3772\u377D\u3781\u0100ry\u3777\u377B;\uC000\u{1D4C9};\u4446cy;\u445Brok;\u4167\u0100io\u378B\u378Ex\xF4\u1777head\u0100lr\u3797\u37A0eftarro\xF7\u084Fightarrow\xBB\u0F5D\u0900AHabcdfghlmoprstuw\u37D0\u37D3\u37D7\u37E4\u37F0\u37FC\u380E\u381C\u3823\u3834\u3851\u385D\u386B\u38A9\u38CC\u38D2\u38EA\u38F6r\xF2\u03EDar;\u6963\u0100cr\u37DC\u37E2ute\u803B\xFA\u40FA\xF2\u1150r\u01E3\u37EA\0\u37EDy;\u445Eve;\u416D\u0100iy\u37F5\u37FArc\u803B\xFB\u40FB;\u4443\u0180abh\u3803\u3806\u380Br\xF2\u13ADlac;\u4171a\xF2\u13C3\u0100ir\u3813\u3818sht;\u697E;\uC000\u{1D532}rave\u803B\xF9\u40F9\u0161\u3827\u3831r\u0100lr\u382C\u382E\xBB\u0957\xBB\u1083lk;\u6580\u0100ct\u3839\u384D\u026F\u383F\0\0\u384Arn\u0100;e\u3845\u3846\u631Cr\xBB\u3846op;\u630Fri;\u65F8\u0100al\u3856\u385Acr;\u416B\u80BB\xA8\u0349\u0100gp\u3862\u3866on;\u4173f;\uC000\u{1D566}\u0300adhlsu\u114B\u3878\u387D\u1372\u3891\u38A0own\xE1\u13B3arpoon\u0100lr\u3888\u388Cef\xF4\u382Digh\xF4\u382Fi\u0180;hl\u3899\u389A\u389C\u43C5\xBB\u13FAon\xBB\u389Aparrows;\u61C8\u0180cit\u38B0\u38C4\u38C8\u026F\u38B6\0\0\u38C1rn\u0100;e\u38BC\u38BD\u631Dr\xBB\u38BDop;\u630Eng;\u416Fri;\u65F9cr;\uC000\u{1D4CA}\u0180dir\u38D9\u38DD\u38E2ot;\u62F0lde;\u4169i\u0100;f\u3730\u38E8\xBB\u1813\u0100am\u38EF\u38F2r\xF2\u38A8l\u803B\xFC\u40FCangle;\u69A7\u0780ABDacdeflnoprsz\u391C\u391F\u3929\u392D\u39B5\u39B8\u39BD\u39DF\u39E4\u39E8\u39F3\u39F9\u39FD\u3A01\u3A20r\xF2\u03F7ar\u0100;v\u3926\u3927\u6AE8;\u6AE9as\xE8\u03E1\u0100nr\u3932\u3937grt;\u699C\u0380eknprst\u34E3\u3946\u394B\u3952\u395D\u3964\u3996app\xE1\u2415othin\xE7\u1E96\u0180hir\u34EB\u2EC8\u3959op\xF4\u2FB5\u0100;h\u13B7\u3962\xEF\u318D\u0100iu\u3969\u396Dgm\xE1\u33B3\u0100bp\u3972\u3984setneq\u0100;q\u397D\u3980\uC000\u228A\uFE00;\uC000\u2ACB\uFE00setneq\u0100;q\u398F\u3992\uC000\u228B\uFE00;\uC000\u2ACC\uFE00\u0100hr\u399B\u399Fet\xE1\u369Ciangle\u0100lr\u39AA\u39AFeft\xBB\u0925ight\xBB\u1051y;\u4432ash\xBB\u1036\u0180elr\u39C4\u39D2\u39D7\u0180;be\u2DEA\u39CB\u39CFar;\u62BBq;\u625Alip;\u62EE\u0100bt\u39DC\u1468a\xF2\u1469r;\uC000\u{1D533}tr\xE9\u39AEsu\u0100bp\u39EF\u39F1\xBB\u0D1C\xBB\u0D59pf;\uC000\u{1D567}ro\xF0\u0EFBtr\xE9\u39B4\u0100cu\u3A06\u3A0Br;\uC000\u{1D4CB}\u0100bp\u3A10\u3A18n\u0100Ee\u3980\u3A16\xBB\u397En\u0100Ee\u3992\u3A1E\xBB\u3990igzag;\u699A\u0380cefoprs\u3A36\u3A3B\u3A56\u3A5B\u3A54\u3A61\u3A6Airc;\u4175\u0100di\u3A40\u3A51\u0100bg\u3A45\u3A49ar;\u6A5Fe\u0100;q\u15FA\u3A4F;\u6259erp;\u6118r;\uC000\u{1D534}pf;\uC000\u{1D568}\u0100;e\u1479\u3A66at\xE8\u1479cr;\uC000\u{1D4CC}\u0AE3\u178E\u3A87\0\u3A8B\0\u3A90\u3A9B\0\0\u3A9D\u3AA8\u3AAB\u3AAF\0\0\u3AC3\u3ACE\0\u3AD8\u17DC\u17DFtr\xE9\u17D1r;\uC000\u{1D535}\u0100Aa\u3A94\u3A97r\xF2\u03C3r\xF2\u09F6;\u43BE\u0100Aa\u3AA1\u3AA4r\xF2\u03B8r\xF2\u09EBa\xF0\u2713is;\u62FB\u0180dpt\u17A4\u3AB5\u3ABE\u0100fl\u3ABA\u17A9;\uC000\u{1D569}im\xE5\u17B2\u0100Aa\u3AC7\u3ACAr\xF2\u03CEr\xF2\u0A01\u0100cq\u3AD2\u17B8r;\uC000\u{1D4CD}\u0100pt\u17D6\u3ADCr\xE9\u17D4\u0400acefiosu\u3AF0\u3AFD\u3B08\u3B0C\u3B11\u3B15\u3B1B\u3B21c\u0100uy\u3AF6\u3AFBte\u803B\xFD\u40FD;\u444F\u0100iy\u3B02\u3B06rc;\u4177;\u444Bn\u803B\xA5\u40A5r;\uC000\u{1D536}cy;\u4457pf;\uC000\u{1D56A}cr;\uC000\u{1D4CE}\u0100cm\u3B26\u3B29y;\u444El\u803B\xFF\u40FF\u0500acdefhiosw\u3B42\u3B48\u3B54\u3B58\u3B64\u3B69\u3B6D\u3B74\u3B7A\u3B80cute;\u417A\u0100ay\u3B4D\u3B52ron;\u417E;\u4437ot;\u417C\u0100et\u3B5D\u3B61tr\xE6\u155Fa;\u43B6r;\uC000\u{1D537}cy;\u4436grarr;\u61DDpf;\uC000\u{1D56B}cr;\uC000\u{1D4CF}\u0100jn\u3B85\u3B87;\u600Dj;\u600C'.split("").map((function(e9) {
    return e9.charCodeAt(0);
  })));
}));
qe(Ao);
var go = Ke((function(e8, t2) {
  Object.defineProperty(t2, "__esModule", { value: true }), t2.default = new Uint16Array("\u0200aglq	\x1B\u026D\0\0p;\u4026os;\u4027t;\u403Et;\u403Cuot;\u4022".split("").map((function(e9) {
    return e9.charCodeAt(0);
  })));
}));
qe(go);
var No = Ke((function(e8, t2) {
  var n2;
  Object.defineProperty(t2, "__esModule", { value: true }), t2.replaceCodePoint = t2.fromCodePoint = void 0;
  var r2 = /* @__PURE__ */ new Map([[0, 65533], [128, 8364], [130, 8218], [131, 402], [132, 8222], [133, 8230], [134, 8224], [135, 8225], [136, 710], [137, 8240], [138, 352], [139, 8249], [140, 338], [142, 381], [145, 8216], [146, 8217], [147, 8220], [148, 8221], [149, 8226], [150, 8211], [151, 8212], [152, 732], [153, 8482], [154, 353], [155, 8250], [156, 339], [158, 382], [159, 376]]);
  function i2(e9) {
    var t3;
    return e9 >= 55296 && e9 <= 57343 || e9 > 1114111 ? 65533 : null !== (t3 = r2.get(e9)) && void 0 !== t3 ? t3 : e9;
  }
  t2.fromCodePoint = null !== (n2 = String.fromCodePoint) && void 0 !== n2 ? n2 : function(e9) {
    var t3 = "";
    return e9 > 65535 && (e9 -= 65536, t3 += String.fromCharCode(e9 >>> 10 & 1023 | 55296), e9 = 56320 | 1023 & e9), t3 += String.fromCharCode(e9);
  }, t2.replaceCodePoint = i2, t2.default = function(e9) {
    return (0, t2.fromCodePoint)(i2(e9));
  };
}));
qe(No), No.replaceCodePoint, No.fromCodePoint;
var Co = Ao, Io = go, So = No, bo = Ke((function(e8, t2) {
  var n2 = Ge && Ge.__importDefault || function(e9) {
    return e9 && e9.__esModule ? e9 : { default: e9 };
  };
  Object.defineProperty(t2, "__esModule", { value: true }), t2.decodeXML = t2.decodeHTMLStrict = t2.decodeHTML = t2.determineBranch = t2.BinTrieFlags = t2.fromCodePoint = t2.replaceCodePoint = t2.decodeCodePoint = t2.xmlDecodeTree = t2.htmlDecodeTree = void 0;
  var r2 = n2(Co);
  t2.htmlDecodeTree = r2.default;
  var i2 = n2(Io);
  t2.xmlDecodeTree = i2.default;
  var s2 = n2(So);
  t2.decodeCodePoint = s2.default;
  var a2, o2, c2 = So;
  function l2(e9) {
    return function(t3, n3) {
      for (var r3 = "", i3 = 0, c3 = 0; (c3 = t3.indexOf("&", c3)) >= 0; ) if (r3 += t3.slice(i3, c3), i3 = c3, c3 += 1, t3.charCodeAt(c3) !== a2.NUM) {
        for (var l3 = 0, u3 = 1, p3 = 0, f2 = e9[p3]; c3 < t3.length && !((p3 = h2(e9, f2, p3 + 1, t3.charCodeAt(c3))) < 0); c3++, u3++) {
          var d2 = (f2 = e9[p3]) & o2.VALUE_LENGTH;
          if (d2) {
            var E2;
            if (n3 && t3.charCodeAt(c3) !== a2.SEMI || (l3 = p3, u3 = 0), 0 === (E2 = (d2 >> 14) - 1)) break;
            p3 += E2;
          }
        }
        if (0 !== l3) r3 += 1 === (E2 = (e9[l3] & o2.VALUE_LENGTH) >> 14) ? String.fromCharCode(e9[l3] & ~o2.VALUE_LENGTH) : 2 === E2 ? String.fromCharCode(e9[l3 + 1]) : String.fromCharCode(e9[l3 + 1], e9[l3 + 2]), i3 = c3 - u3 + 1;
      } else {
        var m2 = c3 + 1, T2 = 10, _2 = t3.charCodeAt(m2);
        (_2 | a2.To_LOWER_BIT) === a2.LOWER_X && (T2 = 16, c3 += 1, m2 += 1);
        do {
          _2 = t3.charCodeAt(++c3);
        } while (_2 >= a2.ZERO && _2 <= a2.NINE || 16 === T2 && (_2 | a2.To_LOWER_BIT) >= a2.LOWER_A && (_2 | a2.To_LOWER_BIT) <= a2.LOWER_F);
        if (m2 !== c3) {
          var A2 = t3.substring(m2, c3), g2 = parseInt(A2, T2);
          if (t3.charCodeAt(c3) === a2.SEMI) c3 += 1;
          else if (n3) continue;
          r3 += (0, s2.default)(g2), i3 = c3;
        }
      }
      return r3 + t3.slice(i3);
    };
  }
  function h2(e9, t3, n3, r3) {
    var i3 = (t3 & o2.BRANCH_LENGTH) >> 7, s3 = t3 & o2.JUMP_TABLE;
    if (0 === i3) return 0 !== s3 && r3 === s3 ? n3 : -1;
    if (s3) {
      var a3 = r3 - s3;
      return a3 < 0 || a3 >= i3 ? -1 : e9[n3 + a3] - 1;
    }
    for (var c3 = n3, l3 = c3 + i3 - 1; c3 <= l3; ) {
      var h3 = c3 + l3 >>> 1, u3 = e9[h3];
      if (u3 < r3) c3 = h3 + 1;
      else {
        if (!(u3 > r3)) return e9[h3 + i3];
        l3 = h3 - 1;
      }
    }
    return -1;
  }
  Object.defineProperty(t2, "replaceCodePoint", { enumerable: true, get: function() {
    return c2.replaceCodePoint;
  } }), Object.defineProperty(t2, "fromCodePoint", { enumerable: true, get: function() {
    return c2.fromCodePoint;
  } }), (function(e9) {
    e9[e9.NUM = 35] = "NUM", e9[e9.SEMI = 59] = "SEMI", e9[e9.ZERO = 48] = "ZERO", e9[e9.NINE = 57] = "NINE", e9[e9.LOWER_A = 97] = "LOWER_A", e9[e9.LOWER_F = 102] = "LOWER_F", e9[e9.LOWER_X = 120] = "LOWER_X", e9[e9.To_LOWER_BIT = 32] = "To_LOWER_BIT";
  })(a2 || (a2 = {})), (function(e9) {
    e9[e9.VALUE_LENGTH = 49152] = "VALUE_LENGTH", e9[e9.BRANCH_LENGTH = 16256] = "BRANCH_LENGTH", e9[e9.JUMP_TABLE = 127] = "JUMP_TABLE";
  })(o2 = t2.BinTrieFlags || (t2.BinTrieFlags = {})), t2.determineBranch = h2;
  var u2 = l2(r2.default), p2 = l2(i2.default);
  t2.decodeHTML = function(e9) {
    return u2(e9, false);
  }, t2.decodeHTMLStrict = function(e9) {
    return u2(e9, true);
  }, t2.decodeXML = function(e9) {
    return p2(e9, true);
  };
}));
qe(bo), bo.decodeXML, bo.decodeHTMLStrict, bo.decodeHTML;
var Oo = bo.determineBranch, yo = bo.BinTrieFlags, Lo = bo.fromCodePoint, ko = bo.replaceCodePoint;
bo.decodeCodePoint;
var vo, Do, Ro, Mo, Po, xo = bo.xmlDecodeTree, wo = bo.htmlDecodeTree;
!(function(e8) {
  e8.HTML = "http://www.w3.org/1999/xhtml", e8.MATHML = "http://www.w3.org/1998/Math/MathML", e8.SVG = "http://www.w3.org/2000/svg", e8.XLINK = "http://www.w3.org/1999/xlink", e8.XML = "http://www.w3.org/XML/1998/namespace", e8.XMLNS = "http://www.w3.org/2000/xmlns/";
})(vo = vo || (vo = {})), (function(e8) {
  e8.TYPE = "type", e8.ACTION = "action", e8.ENCODING = "encoding", e8.PROMPT = "prompt", e8.NAME = "name", e8.COLOR = "color", e8.FACE = "face", e8.SIZE = "size";
})(Do = Do || (Do = {})), (function(e8) {
  e8.NO_QUIRKS = "no-quirks", e8.QUIRKS = "quirks", e8.LIMITED_QUIRKS = "limited-quirks";
})(Ro = Ro || (Ro = {})), (function(e8) {
  e8.A = "a", e8.ADDRESS = "address", e8.ANNOTATION_XML = "annotation-xml", e8.APPLET = "applet", e8.AREA = "area", e8.ARTICLE = "article", e8.ASIDE = "aside", e8.B = "b", e8.BASE = "base", e8.BASEFONT = "basefont", e8.BGSOUND = "bgsound", e8.BIG = "big", e8.BLOCKQUOTE = "blockquote", e8.BODY = "body", e8.BR = "br", e8.BUTTON = "button", e8.CAPTION = "caption", e8.CENTER = "center", e8.CODE = "code", e8.COL = "col", e8.COLGROUP = "colgroup", e8.DD = "dd", e8.DESC = "desc", e8.DETAILS = "details", e8.DIALOG = "dialog", e8.DIR = "dir", e8.DIV = "div", e8.DL = "dl", e8.DT = "dt", e8.EM = "em", e8.EMBED = "embed", e8.FIELDSET = "fieldset", e8.FIGCAPTION = "figcaption", e8.FIGURE = "figure", e8.FONT = "font", e8.FOOTER = "footer", e8.FOREIGN_OBJECT = "foreignObject", e8.FORM = "form", e8.FRAME = "frame", e8.FRAMESET = "frameset", e8.H1 = "h1", e8.H2 = "h2", e8.H3 = "h3", e8.H4 = "h4", e8.H5 = "h5", e8.H6 = "h6", e8.HEAD = "head", e8.HEADER = "header", e8.HGROUP = "hgroup", e8.HR = "hr", e8.HTML = "html", e8.I = "i", e8.IMG = "img", e8.IMAGE = "image", e8.INPUT = "input", e8.IFRAME = "iframe", e8.KEYGEN = "keygen", e8.LABEL = "label", e8.LI = "li", e8.LINK = "link", e8.LISTING = "listing", e8.MAIN = "main", e8.MALIGNMARK = "malignmark", e8.MARQUEE = "marquee", e8.MATH = "math", e8.MENU = "menu", e8.META = "meta", e8.MGLYPH = "mglyph", e8.MI = "mi", e8.MO = "mo", e8.MN = "mn", e8.MS = "ms", e8.MTEXT = "mtext", e8.NAV = "nav", e8.NOBR = "nobr", e8.NOFRAMES = "noframes", e8.NOEMBED = "noembed", e8.NOSCRIPT = "noscript", e8.OBJECT = "object", e8.OL = "ol", e8.OPTGROUP = "optgroup", e8.OPTION = "option", e8.P = "p", e8.PARAM = "param", e8.PLAINTEXT = "plaintext", e8.PRE = "pre", e8.RB = "rb", e8.RP = "rp", e8.RT = "rt", e8.RTC = "rtc", e8.RUBY = "ruby", e8.S = "s", e8.SCRIPT = "script", e8.SECTION = "section", e8.SELECT = "select", e8.SOURCE = "source", e8.SMALL = "small", e8.SPAN = "span", e8.STRIKE = "strike", e8.STRONG = "strong", e8.STYLE = "style", e8.SUB = "sub", e8.SUMMARY = "summary", e8.SUP = "sup", e8.TABLE = "table", e8.TBODY = "tbody", e8.TEMPLATE = "template", e8.TEXTAREA = "textarea", e8.TFOOT = "tfoot", e8.TD = "td", e8.TH = "th", e8.THEAD = "thead", e8.TITLE = "title", e8.TR = "tr", e8.TRACK = "track", e8.TT = "tt", e8.U = "u", e8.UL = "ul", e8.SVG = "svg", e8.VAR = "var", e8.WBR = "wbr", e8.XMP = "xmp";
})(Mo = Mo || (Mo = {})), (function(e8) {
  e8[e8.UNKNOWN = 0] = "UNKNOWN", e8[e8.A = 1] = "A", e8[e8.ADDRESS = 2] = "ADDRESS", e8[e8.ANNOTATION_XML = 3] = "ANNOTATION_XML", e8[e8.APPLET = 4] = "APPLET", e8[e8.AREA = 5] = "AREA", e8[e8.ARTICLE = 6] = "ARTICLE", e8[e8.ASIDE = 7] = "ASIDE", e8[e8.B = 8] = "B", e8[e8.BASE = 9] = "BASE", e8[e8.BASEFONT = 10] = "BASEFONT", e8[e8.BGSOUND = 11] = "BGSOUND", e8[e8.BIG = 12] = "BIG", e8[e8.BLOCKQUOTE = 13] = "BLOCKQUOTE", e8[e8.BODY = 14] = "BODY", e8[e8.BR = 15] = "BR", e8[e8.BUTTON = 16] = "BUTTON", e8[e8.CAPTION = 17] = "CAPTION", e8[e8.CENTER = 18] = "CENTER", e8[e8.CODE = 19] = "CODE", e8[e8.COL = 20] = "COL", e8[e8.COLGROUP = 21] = "COLGROUP", e8[e8.DD = 22] = "DD", e8[e8.DESC = 23] = "DESC", e8[e8.DETAILS = 24] = "DETAILS", e8[e8.DIALOG = 25] = "DIALOG", e8[e8.DIR = 26] = "DIR", e8[e8.DIV = 27] = "DIV", e8[e8.DL = 28] = "DL", e8[e8.DT = 29] = "DT", e8[e8.EM = 30] = "EM", e8[e8.EMBED = 31] = "EMBED", e8[e8.FIELDSET = 32] = "FIELDSET", e8[e8.FIGCAPTION = 33] = "FIGCAPTION", e8[e8.FIGURE = 34] = "FIGURE", e8[e8.FONT = 35] = "FONT", e8[e8.FOOTER = 36] = "FOOTER", e8[e8.FOREIGN_OBJECT = 37] = "FOREIGN_OBJECT", e8[e8.FORM = 38] = "FORM", e8[e8.FRAME = 39] = "FRAME", e8[e8.FRAMESET = 40] = "FRAMESET", e8[e8.H1 = 41] = "H1", e8[e8.H2 = 42] = "H2", e8[e8.H3 = 43] = "H3", e8[e8.H4 = 44] = "H4", e8[e8.H5 = 45] = "H5", e8[e8.H6 = 46] = "H6", e8[e8.HEAD = 47] = "HEAD", e8[e8.HEADER = 48] = "HEADER", e8[e8.HGROUP = 49] = "HGROUP", e8[e8.HR = 50] = "HR", e8[e8.HTML = 51] = "HTML", e8[e8.I = 52] = "I", e8[e8.IMG = 53] = "IMG", e8[e8.IMAGE = 54] = "IMAGE", e8[e8.INPUT = 55] = "INPUT", e8[e8.IFRAME = 56] = "IFRAME", e8[e8.KEYGEN = 57] = "KEYGEN", e8[e8.LABEL = 58] = "LABEL", e8[e8.LI = 59] = "LI", e8[e8.LINK = 60] = "LINK", e8[e8.LISTING = 61] = "LISTING", e8[e8.MAIN = 62] = "MAIN", e8[e8.MALIGNMARK = 63] = "MALIGNMARK", e8[e8.MARQUEE = 64] = "MARQUEE", e8[e8.MATH = 65] = "MATH", e8[e8.MENU = 66] = "MENU", e8[e8.META = 67] = "META", e8[e8.MGLYPH = 68] = "MGLYPH", e8[e8.MI = 69] = "MI", e8[e8.MO = 70] = "MO", e8[e8.MN = 71] = "MN", e8[e8.MS = 72] = "MS", e8[e8.MTEXT = 73] = "MTEXT", e8[e8.NAV = 74] = "NAV", e8[e8.NOBR = 75] = "NOBR", e8[e8.NOFRAMES = 76] = "NOFRAMES", e8[e8.NOEMBED = 77] = "NOEMBED", e8[e8.NOSCRIPT = 78] = "NOSCRIPT", e8[e8.OBJECT = 79] = "OBJECT", e8[e8.OL = 80] = "OL", e8[e8.OPTGROUP = 81] = "OPTGROUP", e8[e8.OPTION = 82] = "OPTION", e8[e8.P = 83] = "P", e8[e8.PARAM = 84] = "PARAM", e8[e8.PLAINTEXT = 85] = "PLAINTEXT", e8[e8.PRE = 86] = "PRE", e8[e8.RB = 87] = "RB", e8[e8.RP = 88] = "RP", e8[e8.RT = 89] = "RT", e8[e8.RTC = 90] = "RTC", e8[e8.RUBY = 91] = "RUBY", e8[e8.S = 92] = "S", e8[e8.SCRIPT = 93] = "SCRIPT", e8[e8.SECTION = 94] = "SECTION", e8[e8.SELECT = 95] = "SELECT", e8[e8.SOURCE = 96] = "SOURCE", e8[e8.SMALL = 97] = "SMALL", e8[e8.SPAN = 98] = "SPAN", e8[e8.STRIKE = 99] = "STRIKE", e8[e8.STRONG = 100] = "STRONG", e8[e8.STYLE = 101] = "STYLE", e8[e8.SUB = 102] = "SUB", e8[e8.SUMMARY = 103] = "SUMMARY", e8[e8.SUP = 104] = "SUP", e8[e8.TABLE = 105] = "TABLE", e8[e8.TBODY = 106] = "TBODY", e8[e8.TEMPLATE = 107] = "TEMPLATE", e8[e8.TEXTAREA = 108] = "TEXTAREA", e8[e8.TFOOT = 109] = "TFOOT", e8[e8.TD = 110] = "TD", e8[e8.TH = 111] = "TH", e8[e8.THEAD = 112] = "THEAD", e8[e8.TITLE = 113] = "TITLE", e8[e8.TR = 114] = "TR", e8[e8.TRACK = 115] = "TRACK", e8[e8.TT = 116] = "TT", e8[e8.U = 117] = "U", e8[e8.UL = 118] = "UL", e8[e8.SVG = 119] = "SVG", e8[e8.VAR = 120] = "VAR", e8[e8.WBR = 121] = "WBR", e8[e8.XMP = 122] = "XMP";
})(Po = Po || (Po = {}));
const Bo = /* @__PURE__ */ new Map([[Mo.A, Po.A], [Mo.ADDRESS, Po.ADDRESS], [Mo.ANNOTATION_XML, Po.ANNOTATION_XML], [Mo.APPLET, Po.APPLET], [Mo.AREA, Po.AREA], [Mo.ARTICLE, Po.ARTICLE], [Mo.ASIDE, Po.ASIDE], [Mo.B, Po.B], [Mo.BASE, Po.BASE], [Mo.BASEFONT, Po.BASEFONT], [Mo.BGSOUND, Po.BGSOUND], [Mo.BIG, Po.BIG], [Mo.BLOCKQUOTE, Po.BLOCKQUOTE], [Mo.BODY, Po.BODY], [Mo.BR, Po.BR], [Mo.BUTTON, Po.BUTTON], [Mo.CAPTION, Po.CAPTION], [Mo.CENTER, Po.CENTER], [Mo.CODE, Po.CODE], [Mo.COL, Po.COL], [Mo.COLGROUP, Po.COLGROUP], [Mo.DD, Po.DD], [Mo.DESC, Po.DESC], [Mo.DETAILS, Po.DETAILS], [Mo.DIALOG, Po.DIALOG], [Mo.DIR, Po.DIR], [Mo.DIV, Po.DIV], [Mo.DL, Po.DL], [Mo.DT, Po.DT], [Mo.EM, Po.EM], [Mo.EMBED, Po.EMBED], [Mo.FIELDSET, Po.FIELDSET], [Mo.FIGCAPTION, Po.FIGCAPTION], [Mo.FIGURE, Po.FIGURE], [Mo.FONT, Po.FONT], [Mo.FOOTER, Po.FOOTER], [Mo.FOREIGN_OBJECT, Po.FOREIGN_OBJECT], [Mo.FORM, Po.FORM], [Mo.FRAME, Po.FRAME], [Mo.FRAMESET, Po.FRAMESET], [Mo.H1, Po.H1], [Mo.H2, Po.H2], [Mo.H3, Po.H3], [Mo.H4, Po.H4], [Mo.H5, Po.H5], [Mo.H6, Po.H6], [Mo.HEAD, Po.HEAD], [Mo.HEADER, Po.HEADER], [Mo.HGROUP, Po.HGROUP], [Mo.HR, Po.HR], [Mo.HTML, Po.HTML], [Mo.I, Po.I], [Mo.IMG, Po.IMG], [Mo.IMAGE, Po.IMAGE], [Mo.INPUT, Po.INPUT], [Mo.IFRAME, Po.IFRAME], [Mo.KEYGEN, Po.KEYGEN], [Mo.LABEL, Po.LABEL], [Mo.LI, Po.LI], [Mo.LINK, Po.LINK], [Mo.LISTING, Po.LISTING], [Mo.MAIN, Po.MAIN], [Mo.MALIGNMARK, Po.MALIGNMARK], [Mo.MARQUEE, Po.MARQUEE], [Mo.MATH, Po.MATH], [Mo.MENU, Po.MENU], [Mo.META, Po.META], [Mo.MGLYPH, Po.MGLYPH], [Mo.MI, Po.MI], [Mo.MO, Po.MO], [Mo.MN, Po.MN], [Mo.MS, Po.MS], [Mo.MTEXT, Po.MTEXT], [Mo.NAV, Po.NAV], [Mo.NOBR, Po.NOBR], [Mo.NOFRAMES, Po.NOFRAMES], [Mo.NOEMBED, Po.NOEMBED], [Mo.NOSCRIPT, Po.NOSCRIPT], [Mo.OBJECT, Po.OBJECT], [Mo.OL, Po.OL], [Mo.OPTGROUP, Po.OPTGROUP], [Mo.OPTION, Po.OPTION], [Mo.P, Po.P], [Mo.PARAM, Po.PARAM], [Mo.PLAINTEXT, Po.PLAINTEXT], [Mo.PRE, Po.PRE], [Mo.RB, Po.RB], [Mo.RP, Po.RP], [Mo.RT, Po.RT], [Mo.RTC, Po.RTC], [Mo.RUBY, Po.RUBY], [Mo.S, Po.S], [Mo.SCRIPT, Po.SCRIPT], [Mo.SECTION, Po.SECTION], [Mo.SELECT, Po.SELECT], [Mo.SOURCE, Po.SOURCE], [Mo.SMALL, Po.SMALL], [Mo.SPAN, Po.SPAN], [Mo.STRIKE, Po.STRIKE], [Mo.STRONG, Po.STRONG], [Mo.STYLE, Po.STYLE], [Mo.SUB, Po.SUB], [Mo.SUMMARY, Po.SUMMARY], [Mo.SUP, Po.SUP], [Mo.TABLE, Po.TABLE], [Mo.TBODY, Po.TBODY], [Mo.TEMPLATE, Po.TEMPLATE], [Mo.TEXTAREA, Po.TEXTAREA], [Mo.TFOOT, Po.TFOOT], [Mo.TD, Po.TD], [Mo.TH, Po.TH], [Mo.THEAD, Po.THEAD], [Mo.TITLE, Po.TITLE], [Mo.TR, Po.TR], [Mo.TRACK, Po.TRACK], [Mo.TT, Po.TT], [Mo.U, Po.U], [Mo.UL, Po.UL], [Mo.SVG, Po.SVG], [Mo.VAR, Po.VAR], [Mo.WBR, Po.WBR], [Mo.XMP, Po.XMP]]);
function Fo(e8) {
  var t2;
  return null !== (t2 = Bo.get(e8)) && void 0 !== t2 ? t2 : Po.UNKNOWN;
}
const Uo = Po, Ho = { [vo.HTML]: /* @__PURE__ */ new Set([Uo.ADDRESS, Uo.APPLET, Uo.AREA, Uo.ARTICLE, Uo.ASIDE, Uo.BASE, Uo.BASEFONT, Uo.BGSOUND, Uo.BLOCKQUOTE, Uo.BODY, Uo.BR, Uo.BUTTON, Uo.CAPTION, Uo.CENTER, Uo.COL, Uo.COLGROUP, Uo.DD, Uo.DETAILS, Uo.DIR, Uo.DIV, Uo.DL, Uo.DT, Uo.EMBED, Uo.FIELDSET, Uo.FIGCAPTION, Uo.FIGURE, Uo.FOOTER, Uo.FORM, Uo.FRAME, Uo.FRAMESET, Uo.H1, Uo.H2, Uo.H3, Uo.H4, Uo.H5, Uo.H6, Uo.HEAD, Uo.HEADER, Uo.HGROUP, Uo.HR, Uo.HTML, Uo.IFRAME, Uo.IMG, Uo.INPUT, Uo.LI, Uo.LINK, Uo.LISTING, Uo.MAIN, Uo.MARQUEE, Uo.MENU, Uo.META, Uo.NAV, Uo.NOEMBED, Uo.NOFRAMES, Uo.NOSCRIPT, Uo.OBJECT, Uo.OL, Uo.P, Uo.PARAM, Uo.PLAINTEXT, Uo.PRE, Uo.SCRIPT, Uo.SECTION, Uo.SELECT, Uo.SOURCE, Uo.STYLE, Uo.SUMMARY, Uo.TABLE, Uo.TBODY, Uo.TD, Uo.TEMPLATE, Uo.TEXTAREA, Uo.TFOOT, Uo.TH, Uo.THEAD, Uo.TITLE, Uo.TR, Uo.TRACK, Uo.UL, Uo.WBR, Uo.XMP]), [vo.MATHML]: /* @__PURE__ */ new Set([Uo.MI, Uo.MO, Uo.MN, Uo.MS, Uo.MTEXT, Uo.ANNOTATION_XML]), [vo.SVG]: /* @__PURE__ */ new Set([Uo.TITLE, Uo.FOREIGN_OBJECT, Uo.DESC]), [vo.XLINK]: /* @__PURE__ */ new Set(), [vo.XML]: /* @__PURE__ */ new Set(), [vo.XMLNS]: /* @__PURE__ */ new Set() };
function Go(e8) {
  return e8 === Uo.H1 || e8 === Uo.H2 || e8 === Uo.H3 || e8 === Uo.H4 || e8 === Uo.H5 || e8 === Uo.H6;
}
const Yo = /* @__PURE__ */ new Set([Mo.STYLE, Mo.SCRIPT, Mo.XMP, Mo.IFRAME, Mo.NOEMBED, Mo.NOFRAMES, Mo.PLAINTEXT]);
const qo = /* @__PURE__ */ new Map([[128, 8364], [130, 8218], [131, 402], [132, 8222], [133, 8230], [134, 8224], [135, 8225], [136, 710], [137, 8240], [138, 352], [139, 8249], [140, 338], [142, 381], [145, 8216], [146, 8217], [147, 8220], [148, 8221], [149, 8226], [150, 8211], [151, 8212], [152, 732], [153, 8482], [154, 353], [155, 8250], [156, 339], [158, 382], [159, 376]]);
var Ko;
!(function(e8) {
  e8[e8.DATA = 0] = "DATA", e8[e8.RCDATA = 1] = "RCDATA", e8[e8.RAWTEXT = 2] = "RAWTEXT", e8[e8.SCRIPT_DATA = 3] = "SCRIPT_DATA", e8[e8.PLAINTEXT = 4] = "PLAINTEXT", e8[e8.TAG_OPEN = 5] = "TAG_OPEN", e8[e8.END_TAG_OPEN = 6] = "END_TAG_OPEN", e8[e8.TAG_NAME = 7] = "TAG_NAME", e8[e8.RCDATA_LESS_THAN_SIGN = 8] = "RCDATA_LESS_THAN_SIGN", e8[e8.RCDATA_END_TAG_OPEN = 9] = "RCDATA_END_TAG_OPEN", e8[e8.RCDATA_END_TAG_NAME = 10] = "RCDATA_END_TAG_NAME", e8[e8.RAWTEXT_LESS_THAN_SIGN = 11] = "RAWTEXT_LESS_THAN_SIGN", e8[e8.RAWTEXT_END_TAG_OPEN = 12] = "RAWTEXT_END_TAG_OPEN", e8[e8.RAWTEXT_END_TAG_NAME = 13] = "RAWTEXT_END_TAG_NAME", e8[e8.SCRIPT_DATA_LESS_THAN_SIGN = 14] = "SCRIPT_DATA_LESS_THAN_SIGN", e8[e8.SCRIPT_DATA_END_TAG_OPEN = 15] = "SCRIPT_DATA_END_TAG_OPEN", e8[e8.SCRIPT_DATA_END_TAG_NAME = 16] = "SCRIPT_DATA_END_TAG_NAME", e8[e8.SCRIPT_DATA_ESCAPE_START = 17] = "SCRIPT_DATA_ESCAPE_START", e8[e8.SCRIPT_DATA_ESCAPE_START_DASH = 18] = "SCRIPT_DATA_ESCAPE_START_DASH", e8[e8.SCRIPT_DATA_ESCAPED = 19] = "SCRIPT_DATA_ESCAPED", e8[e8.SCRIPT_DATA_ESCAPED_DASH = 20] = "SCRIPT_DATA_ESCAPED_DASH", e8[e8.SCRIPT_DATA_ESCAPED_DASH_DASH = 21] = "SCRIPT_DATA_ESCAPED_DASH_DASH", e8[e8.SCRIPT_DATA_ESCAPED_LESS_THAN_SIGN = 22] = "SCRIPT_DATA_ESCAPED_LESS_THAN_SIGN", e8[e8.SCRIPT_DATA_ESCAPED_END_TAG_OPEN = 23] = "SCRIPT_DATA_ESCAPED_END_TAG_OPEN", e8[e8.SCRIPT_DATA_ESCAPED_END_TAG_NAME = 24] = "SCRIPT_DATA_ESCAPED_END_TAG_NAME", e8[e8.SCRIPT_DATA_DOUBLE_ESCAPE_START = 25] = "SCRIPT_DATA_DOUBLE_ESCAPE_START", e8[e8.SCRIPT_DATA_DOUBLE_ESCAPED = 26] = "SCRIPT_DATA_DOUBLE_ESCAPED", e8[e8.SCRIPT_DATA_DOUBLE_ESCAPED_DASH = 27] = "SCRIPT_DATA_DOUBLE_ESCAPED_DASH", e8[e8.SCRIPT_DATA_DOUBLE_ESCAPED_DASH_DASH = 28] = "SCRIPT_DATA_DOUBLE_ESCAPED_DASH_DASH", e8[e8.SCRIPT_DATA_DOUBLE_ESCAPED_LESS_THAN_SIGN = 29] = "SCRIPT_DATA_DOUBLE_ESCAPED_LESS_THAN_SIGN", e8[e8.SCRIPT_DATA_DOUBLE_ESCAPE_END = 30] = "SCRIPT_DATA_DOUBLE_ESCAPE_END", e8[e8.BEFORE_ATTRIBUTE_NAME = 31] = "BEFORE_ATTRIBUTE_NAME", e8[e8.ATTRIBUTE_NAME = 32] = "ATTRIBUTE_NAME", e8[e8.AFTER_ATTRIBUTE_NAME = 33] = "AFTER_ATTRIBUTE_NAME", e8[e8.BEFORE_ATTRIBUTE_VALUE = 34] = "BEFORE_ATTRIBUTE_VALUE", e8[e8.ATTRIBUTE_VALUE_DOUBLE_QUOTED = 35] = "ATTRIBUTE_VALUE_DOUBLE_QUOTED", e8[e8.ATTRIBUTE_VALUE_SINGLE_QUOTED = 36] = "ATTRIBUTE_VALUE_SINGLE_QUOTED", e8[e8.ATTRIBUTE_VALUE_UNQUOTED = 37] = "ATTRIBUTE_VALUE_UNQUOTED", e8[e8.AFTER_ATTRIBUTE_VALUE_QUOTED = 38] = "AFTER_ATTRIBUTE_VALUE_QUOTED", e8[e8.SELF_CLOSING_START_TAG = 39] = "SELF_CLOSING_START_TAG", e8[e8.BOGUS_COMMENT = 40] = "BOGUS_COMMENT", e8[e8.MARKUP_DECLARATION_OPEN = 41] = "MARKUP_DECLARATION_OPEN", e8[e8.COMMENT_START = 42] = "COMMENT_START", e8[e8.COMMENT_START_DASH = 43] = "COMMENT_START_DASH", e8[e8.COMMENT = 44] = "COMMENT", e8[e8.COMMENT_LESS_THAN_SIGN = 45] = "COMMENT_LESS_THAN_SIGN", e8[e8.COMMENT_LESS_THAN_SIGN_BANG = 46] = "COMMENT_LESS_THAN_SIGN_BANG", e8[e8.COMMENT_LESS_THAN_SIGN_BANG_DASH = 47] = "COMMENT_LESS_THAN_SIGN_BANG_DASH", e8[e8.COMMENT_LESS_THAN_SIGN_BANG_DASH_DASH = 48] = "COMMENT_LESS_THAN_SIGN_BANG_DASH_DASH", e8[e8.COMMENT_END_DASH = 49] = "COMMENT_END_DASH", e8[e8.COMMENT_END = 50] = "COMMENT_END", e8[e8.COMMENT_END_BANG = 51] = "COMMENT_END_BANG", e8[e8.DOCTYPE = 52] = "DOCTYPE", e8[e8.BEFORE_DOCTYPE_NAME = 53] = "BEFORE_DOCTYPE_NAME", e8[e8.DOCTYPE_NAME = 54] = "DOCTYPE_NAME", e8[e8.AFTER_DOCTYPE_NAME = 55] = "AFTER_DOCTYPE_NAME", e8[e8.AFTER_DOCTYPE_PUBLIC_KEYWORD = 56] = "AFTER_DOCTYPE_PUBLIC_KEYWORD", e8[e8.BEFORE_DOCTYPE_PUBLIC_IDENTIFIER = 57] = "BEFORE_DOCTYPE_PUBLIC_IDENTIFIER", e8[e8.DOCTYPE_PUBLIC_IDENTIFIER_DOUBLE_QUOTED = 58] = "DOCTYPE_PUBLIC_IDENTIFIER_DOUBLE_QUOTED", e8[e8.DOCTYPE_PUBLIC_IDENTIFIER_SINGLE_QUOTED = 59] = "DOCTYPE_PUBLIC_IDENTIFIER_SINGLE_QUOTED", e8[e8.AFTER_DOCTYPE_PUBLIC_IDENTIFIER = 60] = "AFTER_DOCTYPE_PUBLIC_IDENTIFIER", e8[e8.BETWEEN_DOCTYPE_PUBLIC_AND_SYSTEM_IDENTIFIERS = 61] = "BETWEEN_DOCTYPE_PUBLIC_AND_SYSTEM_IDENTIFIERS", e8[e8.AFTER_DOCTYPE_SYSTEM_KEYWORD = 62] = "AFTER_DOCTYPE_SYSTEM_KEYWORD", e8[e8.BEFORE_DOCTYPE_SYSTEM_IDENTIFIER = 63] = "BEFORE_DOCTYPE_SYSTEM_IDENTIFIER", e8[e8.DOCTYPE_SYSTEM_IDENTIFIER_DOUBLE_QUOTED = 64] = "DOCTYPE_SYSTEM_IDENTIFIER_DOUBLE_QUOTED", e8[e8.DOCTYPE_SYSTEM_IDENTIFIER_SINGLE_QUOTED = 65] = "DOCTYPE_SYSTEM_IDENTIFIER_SINGLE_QUOTED", e8[e8.AFTER_DOCTYPE_SYSTEM_IDENTIFIER = 66] = "AFTER_DOCTYPE_SYSTEM_IDENTIFIER", e8[e8.BOGUS_DOCTYPE = 67] = "BOGUS_DOCTYPE", e8[e8.CDATA_SECTION = 68] = "CDATA_SECTION", e8[e8.CDATA_SECTION_BRACKET = 69] = "CDATA_SECTION_BRACKET", e8[e8.CDATA_SECTION_END = 70] = "CDATA_SECTION_END", e8[e8.CHARACTER_REFERENCE = 71] = "CHARACTER_REFERENCE", e8[e8.NAMED_CHARACTER_REFERENCE = 72] = "NAMED_CHARACTER_REFERENCE", e8[e8.AMBIGUOUS_AMPERSAND = 73] = "AMBIGUOUS_AMPERSAND", e8[e8.NUMERIC_CHARACTER_REFERENCE = 74] = "NUMERIC_CHARACTER_REFERENCE", e8[e8.HEXADEMICAL_CHARACTER_REFERENCE_START = 75] = "HEXADEMICAL_CHARACTER_REFERENCE_START", e8[e8.HEXADEMICAL_CHARACTER_REFERENCE = 76] = "HEXADEMICAL_CHARACTER_REFERENCE", e8[e8.DECIMAL_CHARACTER_REFERENCE = 77] = "DECIMAL_CHARACTER_REFERENCE", e8[e8.NUMERIC_CHARACTER_REFERENCE_END = 78] = "NUMERIC_CHARACTER_REFERENCE_END";
})(Ko || (Ko = {}));
const jo = { DATA: Ko.DATA, RCDATA: Ko.RCDATA, RAWTEXT: Ko.RAWTEXT, SCRIPT_DATA: Ko.SCRIPT_DATA, PLAINTEXT: Ko.PLAINTEXT, CDATA_SECTION: Ko.CDATA_SECTION };
function Vo(e8) {
  return e8 >= io.DIGIT_0 && e8 <= io.DIGIT_9;
}
function Wo(e8) {
  return e8 >= io.LATIN_CAPITAL_A && e8 <= io.LATIN_CAPITAL_Z;
}
function Qo(e8) {
  return (function(e9) {
    return e9 >= io.LATIN_SMALL_A && e9 <= io.LATIN_SMALL_Z;
  })(e8) || Wo(e8);
}
function Xo(e8) {
  return Qo(e8) || Vo(e8);
}
function $o(e8) {
  return e8 >= io.LATIN_CAPITAL_A && e8 <= io.LATIN_CAPITAL_F;
}
function zo(e8) {
  return e8 >= io.LATIN_SMALL_A && e8 <= io.LATIN_SMALL_F;
}
function Jo(e8) {
  return e8 + 32;
}
function Zo(e8) {
  return e8 === io.SPACE || e8 === io.LINE_FEED || e8 === io.TABULATION || e8 === io.FORM_FEED;
}
function ec(e8) {
  return Zo(e8) || e8 === io.SOLIDUS || e8 === io.GREATER_THAN_SIGN;
}
class tc {
  constructor(e8, t2) {
    this.options = e8, this.handler = t2, this.paused = false, this.inLoop = false, this.inForeignNode = false, this.lastStartTagName = "", this.active = false, this.state = Ko.DATA, this.returnState = Ko.DATA, this.charRefCode = -1, this.consumedAfterSnapshot = -1, this.currentCharacterToken = null, this.currentToken = null, this.currentAttr = { name: "", value: "" }, this.preprocessor = new mo(t2), this.currentLocation = this.getCurrentLocation(-1);
  }
  _err(e8) {
    var t2, n2;
    null === (n2 = (t2 = this.handler).onParseError) || void 0 === n2 || n2.call(t2, this.preprocessor.getError(e8));
  }
  getCurrentLocation(e8) {
    return this.options.sourceCodeLocationInfo ? { startLine: this.preprocessor.line, startCol: this.preprocessor.col - e8, startOffset: this.preprocessor.offset - e8, endLine: -1, endCol: -1, endOffset: -1 } : null;
  }
  _runParsingLoop() {
    if (!this.inLoop) {
      for (this.inLoop = true; this.active && !this.paused; ) {
        this.consumedAfterSnapshot = 0;
        const e8 = this._consume();
        this._ensureHibernation() || this._callState(e8);
      }
      this.inLoop = false;
    }
  }
  pause() {
    this.paused = true;
  }
  resume(e8) {
    if (!this.paused) throw new Error("Parser was already resumed");
    this.paused = false, this.inLoop || (this._runParsingLoop(), this.paused || null == e8 || e8());
  }
  write(e8, t2, n2) {
    this.active = true, this.preprocessor.write(e8, t2), this._runParsingLoop(), this.paused || null == n2 || n2();
  }
  insertHtmlAtCurrentPos(e8) {
    this.active = true, this.preprocessor.insertHtmlAtCurrentPos(e8), this._runParsingLoop();
  }
  _ensureHibernation() {
    return !!this.preprocessor.endOfChunkHit && (this._unconsume(this.consumedAfterSnapshot), this.active = false, true);
  }
  _consume() {
    return this.consumedAfterSnapshot++, this.preprocessor.advance();
  }
  _unconsume(e8) {
    this.consumedAfterSnapshot -= e8, this.preprocessor.retreat(e8);
  }
  _reconsumeInState(e8, t2) {
    this.state = e8, this._callState(t2);
  }
  _advanceBy(e8) {
    this.consumedAfterSnapshot += e8;
    for (let t2 = 0; t2 < e8; t2++) this.preprocessor.advance();
  }
  _consumeSequenceIfMatch(e8, t2) {
    return !!this.preprocessor.startsWith(e8, t2) && (this._advanceBy(e8.length - 1), true);
  }
  _createStartTagToken() {
    this.currentToken = { type: To.START_TAG, tagName: "", tagID: Po.UNKNOWN, selfClosing: false, ackSelfClosing: false, attrs: [], location: this.getCurrentLocation(1) };
  }
  _createEndTagToken() {
    this.currentToken = { type: To.END_TAG, tagName: "", tagID: Po.UNKNOWN, selfClosing: false, ackSelfClosing: false, attrs: [], location: this.getCurrentLocation(2) };
  }
  _createCommentToken(e8) {
    this.currentToken = { type: To.COMMENT, data: "", location: this.getCurrentLocation(e8) };
  }
  _createDoctypeToken(e8) {
    this.currentToken = { type: To.DOCTYPE, name: e8, forceQuirks: false, publicId: null, systemId: null, location: this.currentLocation };
  }
  _createCharacterToken(e8, t2) {
    this.currentCharacterToken = { type: e8, chars: t2, location: this.currentLocation };
  }
  _createAttr(e8) {
    this.currentAttr = { name: e8, value: "" }, this.currentLocation = this.getCurrentLocation(0);
  }
  _leaveAttrName() {
    var e8, t2;
    const n2 = this.currentToken;
    if (null === _o(n2, this.currentAttr.name)) {
      if (n2.attrs.push(this.currentAttr), n2.location && this.currentLocation) {
        (null !== (e8 = (t2 = n2.location).attrs) && void 0 !== e8 ? e8 : t2.attrs = /* @__PURE__ */ Object.create(null))[this.currentAttr.name] = this.currentLocation, this._leaveAttrValue();
      }
    } else this._err(Eo.duplicateAttribute);
  }
  _leaveAttrValue() {
    this.currentLocation && (this.currentLocation.endLine = this.preprocessor.line, this.currentLocation.endCol = this.preprocessor.col, this.currentLocation.endOffset = this.preprocessor.offset);
  }
  prepareToken(e8) {
    this._emitCurrentCharacterToken(e8.location), this.currentToken = null, e8.location && (e8.location.endLine = this.preprocessor.line, e8.location.endCol = this.preprocessor.col + 1, e8.location.endOffset = this.preprocessor.offset + 1), this.currentLocation = this.getCurrentLocation(-1);
  }
  emitCurrentTagToken() {
    const e8 = this.currentToken;
    this.prepareToken(e8), e8.tagID = Fo(e8.tagName), e8.type === To.START_TAG ? (this.lastStartTagName = e8.tagName, this.handler.onStartTag(e8)) : (e8.attrs.length > 0 && this._err(Eo.endTagWithAttributes), e8.selfClosing && this._err(Eo.endTagWithTrailingSolidus), this.handler.onEndTag(e8)), this.preprocessor.dropParsedChunk();
  }
  emitCurrentComment(e8) {
    this.prepareToken(e8), this.handler.onComment(e8), this.preprocessor.dropParsedChunk();
  }
  emitCurrentDoctype(e8) {
    this.prepareToken(e8), this.handler.onDoctype(e8), this.preprocessor.dropParsedChunk();
  }
  _emitCurrentCharacterToken(e8) {
    if (this.currentCharacterToken) {
      switch (e8 && this.currentCharacterToken.location && (this.currentCharacterToken.location.endLine = e8.startLine, this.currentCharacterToken.location.endCol = e8.startCol, this.currentCharacterToken.location.endOffset = e8.startOffset), this.currentCharacterToken.type) {
        case To.CHARACTER:
          this.handler.onCharacter(this.currentCharacterToken);
          break;
        case To.NULL_CHARACTER:
          this.handler.onNullCharacter(this.currentCharacterToken);
          break;
        case To.WHITESPACE_CHARACTER:
          this.handler.onWhitespaceCharacter(this.currentCharacterToken);
      }
      this.currentCharacterToken = null;
    }
  }
  _emitEOFToken() {
    const e8 = this.getCurrentLocation(0);
    e8 && (e8.endLine = e8.startLine, e8.endCol = e8.startCol, e8.endOffset = e8.startOffset), this._emitCurrentCharacterToken(e8), this.handler.onEof({ type: To.EOF, location: e8 }), this.active = false;
  }
  _appendCharToCurrentCharacterToken(e8, t2) {
    if (this.currentCharacterToken) {
      if (this.currentCharacterToken.type === e8) return void (this.currentCharacterToken.chars += t2);
      this.currentLocation = this.getCurrentLocation(0), this._emitCurrentCharacterToken(this.currentLocation), this.preprocessor.dropParsedChunk();
    }
    this._createCharacterToken(e8, t2);
  }
  _emitCodePoint(e8) {
    const t2 = Zo(e8) ? To.WHITESPACE_CHARACTER : e8 === io.NULL ? To.NULL_CHARACTER : To.CHARACTER;
    this._appendCharToCurrentCharacterToken(t2, String.fromCodePoint(e8));
  }
  _emitChars(e8) {
    this._appendCharToCurrentCharacterToken(To.CHARACTER, e8);
  }
  _matchNamedCharacterReference(e8) {
    let t2 = null, n2 = 0, r2 = false;
    for (let s2 = 0, a2 = wo[0]; s2 >= 0 && (s2 = Oo(wo, a2, s2 + 1, e8), !(s2 < 0)); e8 = this._consume()) {
      n2 += 1, a2 = wo[s2];
      const o2 = a2 & yo.VALUE_LENGTH;
      if (o2) {
        const a3 = (o2 >> 14) - 1;
        if (e8 !== io.SEMICOLON && this._isCharacterReferenceInAttribute() && ((i2 = this.preprocessor.peek(1)) === io.EQUALS_SIGN || Xo(i2)) ? (t2 = [io.AMPERSAND], s2 += a3) : (t2 = 0 === a3 ? [wo[s2] & ~yo.VALUE_LENGTH] : 1 === a3 ? [wo[++s2]] : [wo[++s2], wo[++s2]], n2 = 0, r2 = e8 !== io.SEMICOLON), 0 === a3) {
          this._consume();
          break;
        }
      }
    }
    var i2;
    return this._unconsume(n2), r2 && !this.preprocessor.endOfChunkHit && this._err(Eo.missingSemicolonAfterCharacterReference), this._unconsume(1), t2;
  }
  _isCharacterReferenceInAttribute() {
    return this.returnState === Ko.ATTRIBUTE_VALUE_DOUBLE_QUOTED || this.returnState === Ko.ATTRIBUTE_VALUE_SINGLE_QUOTED || this.returnState === Ko.ATTRIBUTE_VALUE_UNQUOTED;
  }
  _flushCodePointConsumedAsCharacterReference(e8) {
    this._isCharacterReferenceInAttribute() ? this.currentAttr.value += String.fromCodePoint(e8) : this._emitCodePoint(e8);
  }
  _callState(e8) {
    switch (this.state) {
      case Ko.DATA:
        this._stateData(e8);
        break;
      case Ko.RCDATA:
        this._stateRcdata(e8);
        break;
      case Ko.RAWTEXT:
        this._stateRawtext(e8);
        break;
      case Ko.SCRIPT_DATA:
        this._stateScriptData(e8);
        break;
      case Ko.PLAINTEXT:
        this._statePlaintext(e8);
        break;
      case Ko.TAG_OPEN:
        this._stateTagOpen(e8);
        break;
      case Ko.END_TAG_OPEN:
        this._stateEndTagOpen(e8);
        break;
      case Ko.TAG_NAME:
        this._stateTagName(e8);
        break;
      case Ko.RCDATA_LESS_THAN_SIGN:
        this._stateRcdataLessThanSign(e8);
        break;
      case Ko.RCDATA_END_TAG_OPEN:
        this._stateRcdataEndTagOpen(e8);
        break;
      case Ko.RCDATA_END_TAG_NAME:
        this._stateRcdataEndTagName(e8);
        break;
      case Ko.RAWTEXT_LESS_THAN_SIGN:
        this._stateRawtextLessThanSign(e8);
        break;
      case Ko.RAWTEXT_END_TAG_OPEN:
        this._stateRawtextEndTagOpen(e8);
        break;
      case Ko.RAWTEXT_END_TAG_NAME:
        this._stateRawtextEndTagName(e8);
        break;
      case Ko.SCRIPT_DATA_LESS_THAN_SIGN:
        this._stateScriptDataLessThanSign(e8);
        break;
      case Ko.SCRIPT_DATA_END_TAG_OPEN:
        this._stateScriptDataEndTagOpen(e8);
        break;
      case Ko.SCRIPT_DATA_END_TAG_NAME:
        this._stateScriptDataEndTagName(e8);
        break;
      case Ko.SCRIPT_DATA_ESCAPE_START:
        this._stateScriptDataEscapeStart(e8);
        break;
      case Ko.SCRIPT_DATA_ESCAPE_START_DASH:
        this._stateScriptDataEscapeStartDash(e8);
        break;
      case Ko.SCRIPT_DATA_ESCAPED:
        this._stateScriptDataEscaped(e8);
        break;
      case Ko.SCRIPT_DATA_ESCAPED_DASH:
        this._stateScriptDataEscapedDash(e8);
        break;
      case Ko.SCRIPT_DATA_ESCAPED_DASH_DASH:
        this._stateScriptDataEscapedDashDash(e8);
        break;
      case Ko.SCRIPT_DATA_ESCAPED_LESS_THAN_SIGN:
        this._stateScriptDataEscapedLessThanSign(e8);
        break;
      case Ko.SCRIPT_DATA_ESCAPED_END_TAG_OPEN:
        this._stateScriptDataEscapedEndTagOpen(e8);
        break;
      case Ko.SCRIPT_DATA_ESCAPED_END_TAG_NAME:
        this._stateScriptDataEscapedEndTagName(e8);
        break;
      case Ko.SCRIPT_DATA_DOUBLE_ESCAPE_START:
        this._stateScriptDataDoubleEscapeStart(e8);
        break;
      case Ko.SCRIPT_DATA_DOUBLE_ESCAPED:
        this._stateScriptDataDoubleEscaped(e8);
        break;
      case Ko.SCRIPT_DATA_DOUBLE_ESCAPED_DASH:
        this._stateScriptDataDoubleEscapedDash(e8);
        break;
      case Ko.SCRIPT_DATA_DOUBLE_ESCAPED_DASH_DASH:
        this._stateScriptDataDoubleEscapedDashDash(e8);
        break;
      case Ko.SCRIPT_DATA_DOUBLE_ESCAPED_LESS_THAN_SIGN:
        this._stateScriptDataDoubleEscapedLessThanSign(e8);
        break;
      case Ko.SCRIPT_DATA_DOUBLE_ESCAPE_END:
        this._stateScriptDataDoubleEscapeEnd(e8);
        break;
      case Ko.BEFORE_ATTRIBUTE_NAME:
        this._stateBeforeAttributeName(e8);
        break;
      case Ko.ATTRIBUTE_NAME:
        this._stateAttributeName(e8);
        break;
      case Ko.AFTER_ATTRIBUTE_NAME:
        this._stateAfterAttributeName(e8);
        break;
      case Ko.BEFORE_ATTRIBUTE_VALUE:
        this._stateBeforeAttributeValue(e8);
        break;
      case Ko.ATTRIBUTE_VALUE_DOUBLE_QUOTED:
        this._stateAttributeValueDoubleQuoted(e8);
        break;
      case Ko.ATTRIBUTE_VALUE_SINGLE_QUOTED:
        this._stateAttributeValueSingleQuoted(e8);
        break;
      case Ko.ATTRIBUTE_VALUE_UNQUOTED:
        this._stateAttributeValueUnquoted(e8);
        break;
      case Ko.AFTER_ATTRIBUTE_VALUE_QUOTED:
        this._stateAfterAttributeValueQuoted(e8);
        break;
      case Ko.SELF_CLOSING_START_TAG:
        this._stateSelfClosingStartTag(e8);
        break;
      case Ko.BOGUS_COMMENT:
        this._stateBogusComment(e8);
        break;
      case Ko.MARKUP_DECLARATION_OPEN:
        this._stateMarkupDeclarationOpen(e8);
        break;
      case Ko.COMMENT_START:
        this._stateCommentStart(e8);
        break;
      case Ko.COMMENT_START_DASH:
        this._stateCommentStartDash(e8);
        break;
      case Ko.COMMENT:
        this._stateComment(e8);
        break;
      case Ko.COMMENT_LESS_THAN_SIGN:
        this._stateCommentLessThanSign(e8);
        break;
      case Ko.COMMENT_LESS_THAN_SIGN_BANG:
        this._stateCommentLessThanSignBang(e8);
        break;
      case Ko.COMMENT_LESS_THAN_SIGN_BANG_DASH:
        this._stateCommentLessThanSignBangDash(e8);
        break;
      case Ko.COMMENT_LESS_THAN_SIGN_BANG_DASH_DASH:
        this._stateCommentLessThanSignBangDashDash(e8);
        break;
      case Ko.COMMENT_END_DASH:
        this._stateCommentEndDash(e8);
        break;
      case Ko.COMMENT_END:
        this._stateCommentEnd(e8);
        break;
      case Ko.COMMENT_END_BANG:
        this._stateCommentEndBang(e8);
        break;
      case Ko.DOCTYPE:
        this._stateDoctype(e8);
        break;
      case Ko.BEFORE_DOCTYPE_NAME:
        this._stateBeforeDoctypeName(e8);
        break;
      case Ko.DOCTYPE_NAME:
        this._stateDoctypeName(e8);
        break;
      case Ko.AFTER_DOCTYPE_NAME:
        this._stateAfterDoctypeName(e8);
        break;
      case Ko.AFTER_DOCTYPE_PUBLIC_KEYWORD:
        this._stateAfterDoctypePublicKeyword(e8);
        break;
      case Ko.BEFORE_DOCTYPE_PUBLIC_IDENTIFIER:
        this._stateBeforeDoctypePublicIdentifier(e8);
        break;
      case Ko.DOCTYPE_PUBLIC_IDENTIFIER_DOUBLE_QUOTED:
        this._stateDoctypePublicIdentifierDoubleQuoted(e8);
        break;
      case Ko.DOCTYPE_PUBLIC_IDENTIFIER_SINGLE_QUOTED:
        this._stateDoctypePublicIdentifierSingleQuoted(e8);
        break;
      case Ko.AFTER_DOCTYPE_PUBLIC_IDENTIFIER:
        this._stateAfterDoctypePublicIdentifier(e8);
        break;
      case Ko.BETWEEN_DOCTYPE_PUBLIC_AND_SYSTEM_IDENTIFIERS:
        this._stateBetweenDoctypePublicAndSystemIdentifiers(e8);
        break;
      case Ko.AFTER_DOCTYPE_SYSTEM_KEYWORD:
        this._stateAfterDoctypeSystemKeyword(e8);
        break;
      case Ko.BEFORE_DOCTYPE_SYSTEM_IDENTIFIER:
        this._stateBeforeDoctypeSystemIdentifier(e8);
        break;
      case Ko.DOCTYPE_SYSTEM_IDENTIFIER_DOUBLE_QUOTED:
        this._stateDoctypeSystemIdentifierDoubleQuoted(e8);
        break;
      case Ko.DOCTYPE_SYSTEM_IDENTIFIER_SINGLE_QUOTED:
        this._stateDoctypeSystemIdentifierSingleQuoted(e8);
        break;
      case Ko.AFTER_DOCTYPE_SYSTEM_IDENTIFIER:
        this._stateAfterDoctypeSystemIdentifier(e8);
        break;
      case Ko.BOGUS_DOCTYPE:
        this._stateBogusDoctype(e8);
        break;
      case Ko.CDATA_SECTION:
        this._stateCdataSection(e8);
        break;
      case Ko.CDATA_SECTION_BRACKET:
        this._stateCdataSectionBracket(e8);
        break;
      case Ko.CDATA_SECTION_END:
        this._stateCdataSectionEnd(e8);
        break;
      case Ko.CHARACTER_REFERENCE:
        this._stateCharacterReference(e8);
        break;
      case Ko.NAMED_CHARACTER_REFERENCE:
        this._stateNamedCharacterReference(e8);
        break;
      case Ko.AMBIGUOUS_AMPERSAND:
        this._stateAmbiguousAmpersand(e8);
        break;
      case Ko.NUMERIC_CHARACTER_REFERENCE:
        this._stateNumericCharacterReference(e8);
        break;
      case Ko.HEXADEMICAL_CHARACTER_REFERENCE_START:
        this._stateHexademicalCharacterReferenceStart(e8);
        break;
      case Ko.HEXADEMICAL_CHARACTER_REFERENCE:
        this._stateHexademicalCharacterReference(e8);
        break;
      case Ko.DECIMAL_CHARACTER_REFERENCE:
        this._stateDecimalCharacterReference(e8);
        break;
      case Ko.NUMERIC_CHARACTER_REFERENCE_END:
        this._stateNumericCharacterReferenceEnd(e8);
        break;
      default:
        throw new Error("Unknown state");
    }
  }
  _stateData(e8) {
    switch (e8) {
      case io.LESS_THAN_SIGN:
        this.state = Ko.TAG_OPEN;
        break;
      case io.AMPERSAND:
        this.returnState = Ko.DATA, this.state = Ko.CHARACTER_REFERENCE;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this._emitCodePoint(e8);
        break;
      case io.EOF:
        this._emitEOFToken();
        break;
      default:
        this._emitCodePoint(e8);
    }
  }
  _stateRcdata(e8) {
    switch (e8) {
      case io.AMPERSAND:
        this.returnState = Ko.RCDATA, this.state = Ko.CHARACTER_REFERENCE;
        break;
      case io.LESS_THAN_SIGN:
        this.state = Ko.RCDATA_LESS_THAN_SIGN;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this._emitChars("\uFFFD");
        break;
      case io.EOF:
        this._emitEOFToken();
        break;
      default:
        this._emitCodePoint(e8);
    }
  }
  _stateRawtext(e8) {
    switch (e8) {
      case io.LESS_THAN_SIGN:
        this.state = Ko.RAWTEXT_LESS_THAN_SIGN;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this._emitChars("\uFFFD");
        break;
      case io.EOF:
        this._emitEOFToken();
        break;
      default:
        this._emitCodePoint(e8);
    }
  }
  _stateScriptData(e8) {
    switch (e8) {
      case io.LESS_THAN_SIGN:
        this.state = Ko.SCRIPT_DATA_LESS_THAN_SIGN;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this._emitChars("\uFFFD");
        break;
      case io.EOF:
        this._emitEOFToken();
        break;
      default:
        this._emitCodePoint(e8);
    }
  }
  _statePlaintext(e8) {
    switch (e8) {
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this._emitChars("\uFFFD");
        break;
      case io.EOF:
        this._emitEOFToken();
        break;
      default:
        this._emitCodePoint(e8);
    }
  }
  _stateTagOpen(e8) {
    if (Qo(e8)) this._createStartTagToken(), this.state = Ko.TAG_NAME, this._stateTagName(e8);
    else switch (e8) {
      case io.EXCLAMATION_MARK:
        this.state = Ko.MARKUP_DECLARATION_OPEN;
        break;
      case io.SOLIDUS:
        this.state = Ko.END_TAG_OPEN;
        break;
      case io.QUESTION_MARK:
        this._err(Eo.unexpectedQuestionMarkInsteadOfTagName), this._createCommentToken(1), this.state = Ko.BOGUS_COMMENT, this._stateBogusComment(e8);
        break;
      case io.EOF:
        this._err(Eo.eofBeforeTagName), this._emitChars("<"), this._emitEOFToken();
        break;
      default:
        this._err(Eo.invalidFirstCharacterOfTagName), this._emitChars("<"), this.state = Ko.DATA, this._stateData(e8);
    }
  }
  _stateEndTagOpen(e8) {
    if (Qo(e8)) this._createEndTagToken(), this.state = Ko.TAG_NAME, this._stateTagName(e8);
    else switch (e8) {
      case io.GREATER_THAN_SIGN:
        this._err(Eo.missingEndTagName), this.state = Ko.DATA;
        break;
      case io.EOF:
        this._err(Eo.eofBeforeTagName), this._emitChars("</"), this._emitEOFToken();
        break;
      default:
        this._err(Eo.invalidFirstCharacterOfTagName), this._createCommentToken(2), this.state = Ko.BOGUS_COMMENT, this._stateBogusComment(e8);
    }
  }
  _stateTagName(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        this.state = Ko.BEFORE_ATTRIBUTE_NAME;
        break;
      case io.SOLIDUS:
        this.state = Ko.SELF_CLOSING_START_TAG;
        break;
      case io.GREATER_THAN_SIGN:
        this.state = Ko.DATA, this.emitCurrentTagToken();
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), t2.tagName += "\uFFFD";
        break;
      case io.EOF:
        this._err(Eo.eofInTag), this._emitEOFToken();
        break;
      default:
        t2.tagName += String.fromCodePoint(Wo(e8) ? Jo(e8) : e8);
    }
  }
  _stateRcdataLessThanSign(e8) {
    e8 === io.SOLIDUS ? this.state = Ko.RCDATA_END_TAG_OPEN : (this._emitChars("<"), this.state = Ko.RCDATA, this._stateRcdata(e8));
  }
  _stateRcdataEndTagOpen(e8) {
    Qo(e8) ? (this.state = Ko.RCDATA_END_TAG_NAME, this._stateRcdataEndTagName(e8)) : (this._emitChars("</"), this.state = Ko.RCDATA, this._stateRcdata(e8));
  }
  handleSpecialEndTag(e8) {
    if (!this.preprocessor.startsWith(this.lastStartTagName, false)) return !this._ensureHibernation();
    this._createEndTagToken();
    this.currentToken.tagName = this.lastStartTagName;
    switch (this.preprocessor.peek(this.lastStartTagName.length)) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        return this._advanceBy(this.lastStartTagName.length), this.state = Ko.BEFORE_ATTRIBUTE_NAME, false;
      case io.SOLIDUS:
        return this._advanceBy(this.lastStartTagName.length), this.state = Ko.SELF_CLOSING_START_TAG, false;
      case io.GREATER_THAN_SIGN:
        return this._advanceBy(this.lastStartTagName.length), this.emitCurrentTagToken(), this.state = Ko.DATA, false;
      default:
        return !this._ensureHibernation();
    }
  }
  _stateRcdataEndTagName(e8) {
    this.handleSpecialEndTag(e8) && (this._emitChars("</"), this.state = Ko.RCDATA, this._stateRcdata(e8));
  }
  _stateRawtextLessThanSign(e8) {
    e8 === io.SOLIDUS ? this.state = Ko.RAWTEXT_END_TAG_OPEN : (this._emitChars("<"), this.state = Ko.RAWTEXT, this._stateRawtext(e8));
  }
  _stateRawtextEndTagOpen(e8) {
    Qo(e8) ? (this.state = Ko.RAWTEXT_END_TAG_NAME, this._stateRawtextEndTagName(e8)) : (this._emitChars("</"), this.state = Ko.RAWTEXT, this._stateRawtext(e8));
  }
  _stateRawtextEndTagName(e8) {
    this.handleSpecialEndTag(e8) && (this._emitChars("</"), this.state = Ko.RAWTEXT, this._stateRawtext(e8));
  }
  _stateScriptDataLessThanSign(e8) {
    switch (e8) {
      case io.SOLIDUS:
        this.state = Ko.SCRIPT_DATA_END_TAG_OPEN;
        break;
      case io.EXCLAMATION_MARK:
        this.state = Ko.SCRIPT_DATA_ESCAPE_START, this._emitChars("<!");
        break;
      default:
        this._emitChars("<"), this.state = Ko.SCRIPT_DATA, this._stateScriptData(e8);
    }
  }
  _stateScriptDataEndTagOpen(e8) {
    Qo(e8) ? (this.state = Ko.SCRIPT_DATA_END_TAG_NAME, this._stateScriptDataEndTagName(e8)) : (this._emitChars("</"), this.state = Ko.SCRIPT_DATA, this._stateScriptData(e8));
  }
  _stateScriptDataEndTagName(e8) {
    this.handleSpecialEndTag(e8) && (this._emitChars("</"), this.state = Ko.SCRIPT_DATA, this._stateScriptData(e8));
  }
  _stateScriptDataEscapeStart(e8) {
    e8 === io.HYPHEN_MINUS ? (this.state = Ko.SCRIPT_DATA_ESCAPE_START_DASH, this._emitChars("-")) : (this.state = Ko.SCRIPT_DATA, this._stateScriptData(e8));
  }
  _stateScriptDataEscapeStartDash(e8) {
    e8 === io.HYPHEN_MINUS ? (this.state = Ko.SCRIPT_DATA_ESCAPED_DASH_DASH, this._emitChars("-")) : (this.state = Ko.SCRIPT_DATA, this._stateScriptData(e8));
  }
  _stateScriptDataEscaped(e8) {
    switch (e8) {
      case io.HYPHEN_MINUS:
        this.state = Ko.SCRIPT_DATA_ESCAPED_DASH, this._emitChars("-");
        break;
      case io.LESS_THAN_SIGN:
        this.state = Ko.SCRIPT_DATA_ESCAPED_LESS_THAN_SIGN;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this._emitChars("\uFFFD");
        break;
      case io.EOF:
        this._err(Eo.eofInScriptHtmlCommentLikeText), this._emitEOFToken();
        break;
      default:
        this._emitCodePoint(e8);
    }
  }
  _stateScriptDataEscapedDash(e8) {
    switch (e8) {
      case io.HYPHEN_MINUS:
        this.state = Ko.SCRIPT_DATA_ESCAPED_DASH_DASH, this._emitChars("-");
        break;
      case io.LESS_THAN_SIGN:
        this.state = Ko.SCRIPT_DATA_ESCAPED_LESS_THAN_SIGN;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this.state = Ko.SCRIPT_DATA_ESCAPED, this._emitChars("\uFFFD");
        break;
      case io.EOF:
        this._err(Eo.eofInScriptHtmlCommentLikeText), this._emitEOFToken();
        break;
      default:
        this.state = Ko.SCRIPT_DATA_ESCAPED, this._emitCodePoint(e8);
    }
  }
  _stateScriptDataEscapedDashDash(e8) {
    switch (e8) {
      case io.HYPHEN_MINUS:
        this._emitChars("-");
        break;
      case io.LESS_THAN_SIGN:
        this.state = Ko.SCRIPT_DATA_ESCAPED_LESS_THAN_SIGN;
        break;
      case io.GREATER_THAN_SIGN:
        this.state = Ko.SCRIPT_DATA, this._emitChars(">");
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this.state = Ko.SCRIPT_DATA_ESCAPED, this._emitChars("\uFFFD");
        break;
      case io.EOF:
        this._err(Eo.eofInScriptHtmlCommentLikeText), this._emitEOFToken();
        break;
      default:
        this.state = Ko.SCRIPT_DATA_ESCAPED, this._emitCodePoint(e8);
    }
  }
  _stateScriptDataEscapedLessThanSign(e8) {
    e8 === io.SOLIDUS ? this.state = Ko.SCRIPT_DATA_ESCAPED_END_TAG_OPEN : Qo(e8) ? (this._emitChars("<"), this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPE_START, this._stateScriptDataDoubleEscapeStart(e8)) : (this._emitChars("<"), this.state = Ko.SCRIPT_DATA_ESCAPED, this._stateScriptDataEscaped(e8));
  }
  _stateScriptDataEscapedEndTagOpen(e8) {
    Qo(e8) ? (this.state = Ko.SCRIPT_DATA_ESCAPED_END_TAG_NAME, this._stateScriptDataEscapedEndTagName(e8)) : (this._emitChars("</"), this.state = Ko.SCRIPT_DATA_ESCAPED, this._stateScriptDataEscaped(e8));
  }
  _stateScriptDataEscapedEndTagName(e8) {
    this.handleSpecialEndTag(e8) && (this._emitChars("</"), this.state = Ko.SCRIPT_DATA_ESCAPED, this._stateScriptDataEscaped(e8));
  }
  _stateScriptDataDoubleEscapeStart(e8) {
    if (this.preprocessor.startsWith(co, false) && ec(this.preprocessor.peek(co.length))) {
      this._emitCodePoint(e8);
      for (let e9 = 0; e9 < co.length; e9++) this._emitCodePoint(this._consume());
      this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED;
    } else this._ensureHibernation() || (this.state = Ko.SCRIPT_DATA_ESCAPED, this._stateScriptDataEscaped(e8));
  }
  _stateScriptDataDoubleEscaped(e8) {
    switch (e8) {
      case io.HYPHEN_MINUS:
        this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED_DASH, this._emitChars("-");
        break;
      case io.LESS_THAN_SIGN:
        this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED_LESS_THAN_SIGN, this._emitChars("<");
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this._emitChars("\uFFFD");
        break;
      case io.EOF:
        this._err(Eo.eofInScriptHtmlCommentLikeText), this._emitEOFToken();
        break;
      default:
        this._emitCodePoint(e8);
    }
  }
  _stateScriptDataDoubleEscapedDash(e8) {
    switch (e8) {
      case io.HYPHEN_MINUS:
        this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED_DASH_DASH, this._emitChars("-");
        break;
      case io.LESS_THAN_SIGN:
        this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED_LESS_THAN_SIGN, this._emitChars("<");
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED, this._emitChars("\uFFFD");
        break;
      case io.EOF:
        this._err(Eo.eofInScriptHtmlCommentLikeText), this._emitEOFToken();
        break;
      default:
        this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED, this._emitCodePoint(e8);
    }
  }
  _stateScriptDataDoubleEscapedDashDash(e8) {
    switch (e8) {
      case io.HYPHEN_MINUS:
        this._emitChars("-");
        break;
      case io.LESS_THAN_SIGN:
        this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED_LESS_THAN_SIGN, this._emitChars("<");
        break;
      case io.GREATER_THAN_SIGN:
        this.state = Ko.SCRIPT_DATA, this._emitChars(">");
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED, this._emitChars("\uFFFD");
        break;
      case io.EOF:
        this._err(Eo.eofInScriptHtmlCommentLikeText), this._emitEOFToken();
        break;
      default:
        this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED, this._emitCodePoint(e8);
    }
  }
  _stateScriptDataDoubleEscapedLessThanSign(e8) {
    e8 === io.SOLIDUS ? (this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPE_END, this._emitChars("/")) : (this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED, this._stateScriptDataDoubleEscaped(e8));
  }
  _stateScriptDataDoubleEscapeEnd(e8) {
    if (this.preprocessor.startsWith(co, false) && ec(this.preprocessor.peek(co.length))) {
      this._emitCodePoint(e8);
      for (let e9 = 0; e9 < co.length; e9++) this._emitCodePoint(this._consume());
      this.state = Ko.SCRIPT_DATA_ESCAPED;
    } else this._ensureHibernation() || (this.state = Ko.SCRIPT_DATA_DOUBLE_ESCAPED, this._stateScriptDataDoubleEscaped(e8));
  }
  _stateBeforeAttributeName(e8) {
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        break;
      case io.SOLIDUS:
      case io.GREATER_THAN_SIGN:
      case io.EOF:
        this.state = Ko.AFTER_ATTRIBUTE_NAME, this._stateAfterAttributeName(e8);
        break;
      case io.EQUALS_SIGN:
        this._err(Eo.unexpectedEqualsSignBeforeAttributeName), this._createAttr("="), this.state = Ko.ATTRIBUTE_NAME;
        break;
      default:
        this._createAttr(""), this.state = Ko.ATTRIBUTE_NAME, this._stateAttributeName(e8);
    }
  }
  _stateAttributeName(e8) {
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
      case io.SOLIDUS:
      case io.GREATER_THAN_SIGN:
      case io.EOF:
        this._leaveAttrName(), this.state = Ko.AFTER_ATTRIBUTE_NAME, this._stateAfterAttributeName(e8);
        break;
      case io.EQUALS_SIGN:
        this._leaveAttrName(), this.state = Ko.BEFORE_ATTRIBUTE_VALUE;
        break;
      case io.QUOTATION_MARK:
      case io.APOSTROPHE:
      case io.LESS_THAN_SIGN:
        this._err(Eo.unexpectedCharacterInAttributeName), this.currentAttr.name += String.fromCodePoint(e8);
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this.currentAttr.name += "\uFFFD";
        break;
      default:
        this.currentAttr.name += String.fromCodePoint(Wo(e8) ? Jo(e8) : e8);
    }
  }
  _stateAfterAttributeName(e8) {
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        break;
      case io.SOLIDUS:
        this.state = Ko.SELF_CLOSING_START_TAG;
        break;
      case io.EQUALS_SIGN:
        this.state = Ko.BEFORE_ATTRIBUTE_VALUE;
        break;
      case io.GREATER_THAN_SIGN:
        this.state = Ko.DATA, this.emitCurrentTagToken();
        break;
      case io.EOF:
        this._err(Eo.eofInTag), this._emitEOFToken();
        break;
      default:
        this._createAttr(""), this.state = Ko.ATTRIBUTE_NAME, this._stateAttributeName(e8);
    }
  }
  _stateBeforeAttributeValue(e8) {
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        break;
      case io.QUOTATION_MARK:
        this.state = Ko.ATTRIBUTE_VALUE_DOUBLE_QUOTED;
        break;
      case io.APOSTROPHE:
        this.state = Ko.ATTRIBUTE_VALUE_SINGLE_QUOTED;
        break;
      case io.GREATER_THAN_SIGN:
        this._err(Eo.missingAttributeValue), this.state = Ko.DATA, this.emitCurrentTagToken();
        break;
      default:
        this.state = Ko.ATTRIBUTE_VALUE_UNQUOTED, this._stateAttributeValueUnquoted(e8);
    }
  }
  _stateAttributeValueDoubleQuoted(e8) {
    switch (e8) {
      case io.QUOTATION_MARK:
        this.state = Ko.AFTER_ATTRIBUTE_VALUE_QUOTED;
        break;
      case io.AMPERSAND:
        this.returnState = Ko.ATTRIBUTE_VALUE_DOUBLE_QUOTED, this.state = Ko.CHARACTER_REFERENCE;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this.currentAttr.value += "\uFFFD";
        break;
      case io.EOF:
        this._err(Eo.eofInTag), this._emitEOFToken();
        break;
      default:
        this.currentAttr.value += String.fromCodePoint(e8);
    }
  }
  _stateAttributeValueSingleQuoted(e8) {
    switch (e8) {
      case io.APOSTROPHE:
        this.state = Ko.AFTER_ATTRIBUTE_VALUE_QUOTED;
        break;
      case io.AMPERSAND:
        this.returnState = Ko.ATTRIBUTE_VALUE_SINGLE_QUOTED, this.state = Ko.CHARACTER_REFERENCE;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this.currentAttr.value += "\uFFFD";
        break;
      case io.EOF:
        this._err(Eo.eofInTag), this._emitEOFToken();
        break;
      default:
        this.currentAttr.value += String.fromCodePoint(e8);
    }
  }
  _stateAttributeValueUnquoted(e8) {
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        this._leaveAttrValue(), this.state = Ko.BEFORE_ATTRIBUTE_NAME;
        break;
      case io.AMPERSAND:
        this.returnState = Ko.ATTRIBUTE_VALUE_UNQUOTED, this.state = Ko.CHARACTER_REFERENCE;
        break;
      case io.GREATER_THAN_SIGN:
        this._leaveAttrValue(), this.state = Ko.DATA, this.emitCurrentTagToken();
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this.currentAttr.value += "\uFFFD";
        break;
      case io.QUOTATION_MARK:
      case io.APOSTROPHE:
      case io.LESS_THAN_SIGN:
      case io.EQUALS_SIGN:
      case io.GRAVE_ACCENT:
        this._err(Eo.unexpectedCharacterInUnquotedAttributeValue), this.currentAttr.value += String.fromCodePoint(e8);
        break;
      case io.EOF:
        this._err(Eo.eofInTag), this._emitEOFToken();
        break;
      default:
        this.currentAttr.value += String.fromCodePoint(e8);
    }
  }
  _stateAfterAttributeValueQuoted(e8) {
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        this._leaveAttrValue(), this.state = Ko.BEFORE_ATTRIBUTE_NAME;
        break;
      case io.SOLIDUS:
        this._leaveAttrValue(), this.state = Ko.SELF_CLOSING_START_TAG;
        break;
      case io.GREATER_THAN_SIGN:
        this._leaveAttrValue(), this.state = Ko.DATA, this.emitCurrentTagToken();
        break;
      case io.EOF:
        this._err(Eo.eofInTag), this._emitEOFToken();
        break;
      default:
        this._err(Eo.missingWhitespaceBetweenAttributes), this.state = Ko.BEFORE_ATTRIBUTE_NAME, this._stateBeforeAttributeName(e8);
    }
  }
  _stateSelfClosingStartTag(e8) {
    switch (e8) {
      case io.GREATER_THAN_SIGN:
        this.currentToken.selfClosing = true, this.state = Ko.DATA, this.emitCurrentTagToken();
        break;
      case io.EOF:
        this._err(Eo.eofInTag), this._emitEOFToken();
        break;
      default:
        this._err(Eo.unexpectedSolidusInTag), this.state = Ko.BEFORE_ATTRIBUTE_NAME, this._stateBeforeAttributeName(e8);
    }
  }
  _stateBogusComment(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.GREATER_THAN_SIGN:
        this.state = Ko.DATA, this.emitCurrentComment(t2);
        break;
      case io.EOF:
        this.emitCurrentComment(t2), this._emitEOFToken();
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), t2.data += "\uFFFD";
        break;
      default:
        t2.data += String.fromCodePoint(e8);
    }
  }
  _stateMarkupDeclarationOpen(e8) {
    this._consumeSequenceIfMatch(so, true) ? (this._createCommentToken(so.length + 1), this.state = Ko.COMMENT_START) : this._consumeSequenceIfMatch(oo, false) ? (this.currentLocation = this.getCurrentLocation(oo.length + 1), this.state = Ko.DOCTYPE) : this._consumeSequenceIfMatch(ao, true) ? this.inForeignNode ? this.state = Ko.CDATA_SECTION : (this._err(Eo.cdataInHtmlContent), this._createCommentToken(ao.length + 1), this.currentToken.data = "[CDATA[", this.state = Ko.BOGUS_COMMENT) : this._ensureHibernation() || (this._err(Eo.incorrectlyOpenedComment), this._createCommentToken(2), this.state = Ko.BOGUS_COMMENT, this._stateBogusComment(e8));
  }
  _stateCommentStart(e8) {
    switch (e8) {
      case io.HYPHEN_MINUS:
        this.state = Ko.COMMENT_START_DASH;
        break;
      case io.GREATER_THAN_SIGN: {
        this._err(Eo.abruptClosingOfEmptyComment), this.state = Ko.DATA;
        const e9 = this.currentToken;
        this.emitCurrentComment(e9);
        break;
      }
      default:
        this.state = Ko.COMMENT, this._stateComment(e8);
    }
  }
  _stateCommentStartDash(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.HYPHEN_MINUS:
        this.state = Ko.COMMENT_END;
        break;
      case io.GREATER_THAN_SIGN:
        this._err(Eo.abruptClosingOfEmptyComment), this.state = Ko.DATA, this.emitCurrentComment(t2);
        break;
      case io.EOF:
        this._err(Eo.eofInComment), this.emitCurrentComment(t2), this._emitEOFToken();
        break;
      default:
        t2.data += "-", this.state = Ko.COMMENT, this._stateComment(e8);
    }
  }
  _stateComment(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.HYPHEN_MINUS:
        this.state = Ko.COMMENT_END_DASH;
        break;
      case io.LESS_THAN_SIGN:
        t2.data += "<", this.state = Ko.COMMENT_LESS_THAN_SIGN;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), t2.data += "\uFFFD";
        break;
      case io.EOF:
        this._err(Eo.eofInComment), this.emitCurrentComment(t2), this._emitEOFToken();
        break;
      default:
        t2.data += String.fromCodePoint(e8);
    }
  }
  _stateCommentLessThanSign(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.EXCLAMATION_MARK:
        t2.data += "!", this.state = Ko.COMMENT_LESS_THAN_SIGN_BANG;
        break;
      case io.LESS_THAN_SIGN:
        t2.data += "<";
        break;
      default:
        this.state = Ko.COMMENT, this._stateComment(e8);
    }
  }
  _stateCommentLessThanSignBang(e8) {
    e8 === io.HYPHEN_MINUS ? this.state = Ko.COMMENT_LESS_THAN_SIGN_BANG_DASH : (this.state = Ko.COMMENT, this._stateComment(e8));
  }
  _stateCommentLessThanSignBangDash(e8) {
    e8 === io.HYPHEN_MINUS ? this.state = Ko.COMMENT_LESS_THAN_SIGN_BANG_DASH_DASH : (this.state = Ko.COMMENT_END_DASH, this._stateCommentEndDash(e8));
  }
  _stateCommentLessThanSignBangDashDash(e8) {
    e8 !== io.GREATER_THAN_SIGN && e8 !== io.EOF && this._err(Eo.nestedComment), this.state = Ko.COMMENT_END, this._stateCommentEnd(e8);
  }
  _stateCommentEndDash(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.HYPHEN_MINUS:
        this.state = Ko.COMMENT_END;
        break;
      case io.EOF:
        this._err(Eo.eofInComment), this.emitCurrentComment(t2), this._emitEOFToken();
        break;
      default:
        t2.data += "-", this.state = Ko.COMMENT, this._stateComment(e8);
    }
  }
  _stateCommentEnd(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.GREATER_THAN_SIGN:
        this.state = Ko.DATA, this.emitCurrentComment(t2);
        break;
      case io.EXCLAMATION_MARK:
        this.state = Ko.COMMENT_END_BANG;
        break;
      case io.HYPHEN_MINUS:
        t2.data += "-";
        break;
      case io.EOF:
        this._err(Eo.eofInComment), this.emitCurrentComment(t2), this._emitEOFToken();
        break;
      default:
        t2.data += "--", this.state = Ko.COMMENT, this._stateComment(e8);
    }
  }
  _stateCommentEndBang(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.HYPHEN_MINUS:
        t2.data += "--!", this.state = Ko.COMMENT_END_DASH;
        break;
      case io.GREATER_THAN_SIGN:
        this._err(Eo.incorrectlyClosedComment), this.state = Ko.DATA, this.emitCurrentComment(t2);
        break;
      case io.EOF:
        this._err(Eo.eofInComment), this.emitCurrentComment(t2), this._emitEOFToken();
        break;
      default:
        t2.data += "--!", this.state = Ko.COMMENT, this._stateComment(e8);
    }
  }
  _stateDoctype(e8) {
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        this.state = Ko.BEFORE_DOCTYPE_NAME;
        break;
      case io.GREATER_THAN_SIGN:
        this.state = Ko.BEFORE_DOCTYPE_NAME, this._stateBeforeDoctypeName(e8);
        break;
      case io.EOF: {
        this._err(Eo.eofInDoctype), this._createDoctypeToken(null);
        const e9 = this.currentToken;
        e9.forceQuirks = true, this.emitCurrentDoctype(e9), this._emitEOFToken();
        break;
      }
      default:
        this._err(Eo.missingWhitespaceBeforeDoctypeName), this.state = Ko.BEFORE_DOCTYPE_NAME, this._stateBeforeDoctypeName(e8);
    }
  }
  _stateBeforeDoctypeName(e8) {
    if (Wo(e8)) this._createDoctypeToken(String.fromCharCode(Jo(e8))), this.state = Ko.DOCTYPE_NAME;
    else switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), this._createDoctypeToken("\uFFFD"), this.state = Ko.DOCTYPE_NAME;
        break;
      case io.GREATER_THAN_SIGN: {
        this._err(Eo.missingDoctypeName), this._createDoctypeToken(null);
        const e9 = this.currentToken;
        e9.forceQuirks = true, this.emitCurrentDoctype(e9), this.state = Ko.DATA;
        break;
      }
      case io.EOF: {
        this._err(Eo.eofInDoctype), this._createDoctypeToken(null);
        const e9 = this.currentToken;
        e9.forceQuirks = true, this.emitCurrentDoctype(e9), this._emitEOFToken();
        break;
      }
      default:
        this._createDoctypeToken(String.fromCodePoint(e8)), this.state = Ko.DOCTYPE_NAME;
    }
  }
  _stateDoctypeName(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        this.state = Ko.AFTER_DOCTYPE_NAME;
        break;
      case io.GREATER_THAN_SIGN:
        this.state = Ko.DATA, this.emitCurrentDoctype(t2);
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), t2.name += "\uFFFD";
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        t2.name += String.fromCodePoint(Wo(e8) ? Jo(e8) : e8);
    }
  }
  _stateAfterDoctypeName(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        break;
      case io.GREATER_THAN_SIGN:
        this.state = Ko.DATA, this.emitCurrentDoctype(t2);
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        this._consumeSequenceIfMatch(lo, false) ? this.state = Ko.AFTER_DOCTYPE_PUBLIC_KEYWORD : this._consumeSequenceIfMatch(ho, false) ? this.state = Ko.AFTER_DOCTYPE_SYSTEM_KEYWORD : this._ensureHibernation() || (this._err(Eo.invalidCharacterSequenceAfterDoctypeName), t2.forceQuirks = true, this.state = Ko.BOGUS_DOCTYPE, this._stateBogusDoctype(e8));
    }
  }
  _stateAfterDoctypePublicKeyword(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        this.state = Ko.BEFORE_DOCTYPE_PUBLIC_IDENTIFIER;
        break;
      case io.QUOTATION_MARK:
        this._err(Eo.missingWhitespaceAfterDoctypePublicKeyword), t2.publicId = "", this.state = Ko.DOCTYPE_PUBLIC_IDENTIFIER_DOUBLE_QUOTED;
        break;
      case io.APOSTROPHE:
        this._err(Eo.missingWhitespaceAfterDoctypePublicKeyword), t2.publicId = "", this.state = Ko.DOCTYPE_PUBLIC_IDENTIFIER_SINGLE_QUOTED;
        break;
      case io.GREATER_THAN_SIGN:
        this._err(Eo.missingDoctypePublicIdentifier), t2.forceQuirks = true, this.state = Ko.DATA, this.emitCurrentDoctype(t2);
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        this._err(Eo.missingQuoteBeforeDoctypePublicIdentifier), t2.forceQuirks = true, this.state = Ko.BOGUS_DOCTYPE, this._stateBogusDoctype(e8);
    }
  }
  _stateBeforeDoctypePublicIdentifier(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        break;
      case io.QUOTATION_MARK:
        t2.publicId = "", this.state = Ko.DOCTYPE_PUBLIC_IDENTIFIER_DOUBLE_QUOTED;
        break;
      case io.APOSTROPHE:
        t2.publicId = "", this.state = Ko.DOCTYPE_PUBLIC_IDENTIFIER_SINGLE_QUOTED;
        break;
      case io.GREATER_THAN_SIGN:
        this._err(Eo.missingDoctypePublicIdentifier), t2.forceQuirks = true, this.state = Ko.DATA, this.emitCurrentDoctype(t2);
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        this._err(Eo.missingQuoteBeforeDoctypePublicIdentifier), t2.forceQuirks = true, this.state = Ko.BOGUS_DOCTYPE, this._stateBogusDoctype(e8);
    }
  }
  _stateDoctypePublicIdentifierDoubleQuoted(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.QUOTATION_MARK:
        this.state = Ko.AFTER_DOCTYPE_PUBLIC_IDENTIFIER;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), t2.publicId += "\uFFFD";
        break;
      case io.GREATER_THAN_SIGN:
        this._err(Eo.abruptDoctypePublicIdentifier), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this.state = Ko.DATA;
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        t2.publicId += String.fromCodePoint(e8);
    }
  }
  _stateDoctypePublicIdentifierSingleQuoted(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.APOSTROPHE:
        this.state = Ko.AFTER_DOCTYPE_PUBLIC_IDENTIFIER;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), t2.publicId += "\uFFFD";
        break;
      case io.GREATER_THAN_SIGN:
        this._err(Eo.abruptDoctypePublicIdentifier), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this.state = Ko.DATA;
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        t2.publicId += String.fromCodePoint(e8);
    }
  }
  _stateAfterDoctypePublicIdentifier(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        this.state = Ko.BETWEEN_DOCTYPE_PUBLIC_AND_SYSTEM_IDENTIFIERS;
        break;
      case io.GREATER_THAN_SIGN:
        this.state = Ko.DATA, this.emitCurrentDoctype(t2);
        break;
      case io.QUOTATION_MARK:
        this._err(Eo.missingWhitespaceBetweenDoctypePublicAndSystemIdentifiers), t2.systemId = "", this.state = Ko.DOCTYPE_SYSTEM_IDENTIFIER_DOUBLE_QUOTED;
        break;
      case io.APOSTROPHE:
        this._err(Eo.missingWhitespaceBetweenDoctypePublicAndSystemIdentifiers), t2.systemId = "", this.state = Ko.DOCTYPE_SYSTEM_IDENTIFIER_SINGLE_QUOTED;
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        this._err(Eo.missingQuoteBeforeDoctypeSystemIdentifier), t2.forceQuirks = true, this.state = Ko.BOGUS_DOCTYPE, this._stateBogusDoctype(e8);
    }
  }
  _stateBetweenDoctypePublicAndSystemIdentifiers(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        break;
      case io.GREATER_THAN_SIGN:
        this.emitCurrentDoctype(t2), this.state = Ko.DATA;
        break;
      case io.QUOTATION_MARK:
        t2.systemId = "", this.state = Ko.DOCTYPE_SYSTEM_IDENTIFIER_DOUBLE_QUOTED;
        break;
      case io.APOSTROPHE:
        t2.systemId = "", this.state = Ko.DOCTYPE_SYSTEM_IDENTIFIER_SINGLE_QUOTED;
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        this._err(Eo.missingQuoteBeforeDoctypeSystemIdentifier), t2.forceQuirks = true, this.state = Ko.BOGUS_DOCTYPE, this._stateBogusDoctype(e8);
    }
  }
  _stateAfterDoctypeSystemKeyword(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        this.state = Ko.BEFORE_DOCTYPE_SYSTEM_IDENTIFIER;
        break;
      case io.QUOTATION_MARK:
        this._err(Eo.missingWhitespaceAfterDoctypeSystemKeyword), t2.systemId = "", this.state = Ko.DOCTYPE_SYSTEM_IDENTIFIER_DOUBLE_QUOTED;
        break;
      case io.APOSTROPHE:
        this._err(Eo.missingWhitespaceAfterDoctypeSystemKeyword), t2.systemId = "", this.state = Ko.DOCTYPE_SYSTEM_IDENTIFIER_SINGLE_QUOTED;
        break;
      case io.GREATER_THAN_SIGN:
        this._err(Eo.missingDoctypeSystemIdentifier), t2.forceQuirks = true, this.state = Ko.DATA, this.emitCurrentDoctype(t2);
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        this._err(Eo.missingQuoteBeforeDoctypeSystemIdentifier), t2.forceQuirks = true, this.state = Ko.BOGUS_DOCTYPE, this._stateBogusDoctype(e8);
    }
  }
  _stateBeforeDoctypeSystemIdentifier(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        break;
      case io.QUOTATION_MARK:
        t2.systemId = "", this.state = Ko.DOCTYPE_SYSTEM_IDENTIFIER_DOUBLE_QUOTED;
        break;
      case io.APOSTROPHE:
        t2.systemId = "", this.state = Ko.DOCTYPE_SYSTEM_IDENTIFIER_SINGLE_QUOTED;
        break;
      case io.GREATER_THAN_SIGN:
        this._err(Eo.missingDoctypeSystemIdentifier), t2.forceQuirks = true, this.state = Ko.DATA, this.emitCurrentDoctype(t2);
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        this._err(Eo.missingQuoteBeforeDoctypeSystemIdentifier), t2.forceQuirks = true, this.state = Ko.BOGUS_DOCTYPE, this._stateBogusDoctype(e8);
    }
  }
  _stateDoctypeSystemIdentifierDoubleQuoted(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.QUOTATION_MARK:
        this.state = Ko.AFTER_DOCTYPE_SYSTEM_IDENTIFIER;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), t2.systemId += "\uFFFD";
        break;
      case io.GREATER_THAN_SIGN:
        this._err(Eo.abruptDoctypeSystemIdentifier), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this.state = Ko.DATA;
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        t2.systemId += String.fromCodePoint(e8);
    }
  }
  _stateDoctypeSystemIdentifierSingleQuoted(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.APOSTROPHE:
        this.state = Ko.AFTER_DOCTYPE_SYSTEM_IDENTIFIER;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter), t2.systemId += "\uFFFD";
        break;
      case io.GREATER_THAN_SIGN:
        this._err(Eo.abruptDoctypeSystemIdentifier), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this.state = Ko.DATA;
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        t2.systemId += String.fromCodePoint(e8);
    }
  }
  _stateAfterDoctypeSystemIdentifier(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.SPACE:
      case io.LINE_FEED:
      case io.TABULATION:
      case io.FORM_FEED:
        break;
      case io.GREATER_THAN_SIGN:
        this.emitCurrentDoctype(t2), this.state = Ko.DATA;
        break;
      case io.EOF:
        this._err(Eo.eofInDoctype), t2.forceQuirks = true, this.emitCurrentDoctype(t2), this._emitEOFToken();
        break;
      default:
        this._err(Eo.unexpectedCharacterAfterDoctypeSystemIdentifier), this.state = Ko.BOGUS_DOCTYPE, this._stateBogusDoctype(e8);
    }
  }
  _stateBogusDoctype(e8) {
    const t2 = this.currentToken;
    switch (e8) {
      case io.GREATER_THAN_SIGN:
        this.emitCurrentDoctype(t2), this.state = Ko.DATA;
        break;
      case io.NULL:
        this._err(Eo.unexpectedNullCharacter);
        break;
      case io.EOF:
        this.emitCurrentDoctype(t2), this._emitEOFToken();
    }
  }
  _stateCdataSection(e8) {
    switch (e8) {
      case io.RIGHT_SQUARE_BRACKET:
        this.state = Ko.CDATA_SECTION_BRACKET;
        break;
      case io.EOF:
        this._err(Eo.eofInCdata), this._emitEOFToken();
        break;
      default:
        this._emitCodePoint(e8);
    }
  }
  _stateCdataSectionBracket(e8) {
    e8 === io.RIGHT_SQUARE_BRACKET ? this.state = Ko.CDATA_SECTION_END : (this._emitChars("]"), this.state = Ko.CDATA_SECTION, this._stateCdataSection(e8));
  }
  _stateCdataSectionEnd(e8) {
    switch (e8) {
      case io.GREATER_THAN_SIGN:
        this.state = Ko.DATA;
        break;
      case io.RIGHT_SQUARE_BRACKET:
        this._emitChars("]");
        break;
      default:
        this._emitChars("]]"), this.state = Ko.CDATA_SECTION, this._stateCdataSection(e8);
    }
  }
  _stateCharacterReference(e8) {
    e8 === io.NUMBER_SIGN ? this.state = Ko.NUMERIC_CHARACTER_REFERENCE : Xo(e8) ? (this.state = Ko.NAMED_CHARACTER_REFERENCE, this._stateNamedCharacterReference(e8)) : (this._flushCodePointConsumedAsCharacterReference(io.AMPERSAND), this._reconsumeInState(this.returnState, e8));
  }
  _stateNamedCharacterReference(e8) {
    const t2 = this._matchNamedCharacterReference(e8);
    if (this._ensureHibernation()) ;
    else if (t2) {
      for (let e9 = 0; e9 < t2.length; e9++) this._flushCodePointConsumedAsCharacterReference(t2[e9]);
      this.state = this.returnState;
    } else this._flushCodePointConsumedAsCharacterReference(io.AMPERSAND), this.state = Ko.AMBIGUOUS_AMPERSAND;
  }
  _stateAmbiguousAmpersand(e8) {
    Xo(e8) ? this._flushCodePointConsumedAsCharacterReference(e8) : (e8 === io.SEMICOLON && this._err(Eo.unknownNamedCharacterReference), this._reconsumeInState(this.returnState, e8));
  }
  _stateNumericCharacterReference(e8) {
    this.charRefCode = 0, e8 === io.LATIN_SMALL_X || e8 === io.LATIN_CAPITAL_X ? this.state = Ko.HEXADEMICAL_CHARACTER_REFERENCE_START : Vo(e8) ? (this.state = Ko.DECIMAL_CHARACTER_REFERENCE, this._stateDecimalCharacterReference(e8)) : (this._err(Eo.absenceOfDigitsInNumericCharacterReference), this._flushCodePointConsumedAsCharacterReference(io.AMPERSAND), this._flushCodePointConsumedAsCharacterReference(io.NUMBER_SIGN), this._reconsumeInState(this.returnState, e8));
  }
  _stateHexademicalCharacterReferenceStart(e8) {
    !(function(e9) {
      return Vo(e9) || $o(e9) || zo(e9);
    })(e8) ? (this._err(Eo.absenceOfDigitsInNumericCharacterReference), this._flushCodePointConsumedAsCharacterReference(io.AMPERSAND), this._flushCodePointConsumedAsCharacterReference(io.NUMBER_SIGN), this._unconsume(2), this.state = this.returnState) : (this.state = Ko.HEXADEMICAL_CHARACTER_REFERENCE, this._stateHexademicalCharacterReference(e8));
  }
  _stateHexademicalCharacterReference(e8) {
    $o(e8) ? this.charRefCode = 16 * this.charRefCode + e8 - 55 : zo(e8) ? this.charRefCode = 16 * this.charRefCode + e8 - 87 : Vo(e8) ? this.charRefCode = 16 * this.charRefCode + e8 - 48 : e8 === io.SEMICOLON ? this.state = Ko.NUMERIC_CHARACTER_REFERENCE_END : (this._err(Eo.missingSemicolonAfterCharacterReference), this.state = Ko.NUMERIC_CHARACTER_REFERENCE_END, this._stateNumericCharacterReferenceEnd(e8));
  }
  _stateDecimalCharacterReference(e8) {
    Vo(e8) ? this.charRefCode = 10 * this.charRefCode + e8 - 48 : e8 === io.SEMICOLON ? this.state = Ko.NUMERIC_CHARACTER_REFERENCE_END : (this._err(Eo.missingSemicolonAfterCharacterReference), this.state = Ko.NUMERIC_CHARACTER_REFERENCE_END, this._stateNumericCharacterReferenceEnd(e8));
  }
  _stateNumericCharacterReferenceEnd(e8) {
    if (this.charRefCode === io.NULL) this._err(Eo.nullCharacterReference), this.charRefCode = io.REPLACEMENT_CHARACTER;
    else if (this.charRefCode > 1114111) this._err(Eo.characterReferenceOutsideUnicodeRange), this.charRefCode = io.REPLACEMENT_CHARACTER;
    else if (uo(this.charRefCode)) this._err(Eo.surrogateCharacterReference), this.charRefCode = io.REPLACEMENT_CHARACTER;
    else if (fo(this.charRefCode)) this._err(Eo.noncharacterCharacterReference);
    else if (po(this.charRefCode) || this.charRefCode === io.CARRIAGE_RETURN) {
      this._err(Eo.controlCharacterReference);
      const e9 = qo.get(this.charRefCode);
      void 0 !== e9 && (this.charRefCode = e9);
    }
    this._flushCodePointConsumedAsCharacterReference(this.charRefCode), this._reconsumeInState(this.returnState, e8);
  }
}
const nc = /* @__PURE__ */ new Set([Po.DD, Po.DT, Po.LI, Po.OPTGROUP, Po.OPTION, Po.P, Po.RB, Po.RP, Po.RT, Po.RTC]), rc = /* @__PURE__ */ new Set([...nc, Po.CAPTION, Po.COLGROUP, Po.TBODY, Po.TD, Po.TFOOT, Po.TH, Po.THEAD, Po.TR]), ic = /* @__PURE__ */ new Map([[Po.APPLET, vo.HTML], [Po.CAPTION, vo.HTML], [Po.HTML, vo.HTML], [Po.MARQUEE, vo.HTML], [Po.OBJECT, vo.HTML], [Po.TABLE, vo.HTML], [Po.TD, vo.HTML], [Po.TEMPLATE, vo.HTML], [Po.TH, vo.HTML], [Po.ANNOTATION_XML, vo.MATHML], [Po.MI, vo.MATHML], [Po.MN, vo.MATHML], [Po.MO, vo.MATHML], [Po.MS, vo.MATHML], [Po.MTEXT, vo.MATHML], [Po.DESC, vo.SVG], [Po.FOREIGN_OBJECT, vo.SVG], [Po.TITLE, vo.SVG]]), sc = [Po.H1, Po.H2, Po.H3, Po.H4, Po.H5, Po.H6], ac = [Po.TR, Po.TEMPLATE, Po.HTML], oc = [Po.TBODY, Po.TFOOT, Po.THEAD, Po.TEMPLATE, Po.HTML], cc = [Po.TABLE, Po.TEMPLATE, Po.HTML], lc = [Po.TD, Po.TH];
class hc {
  constructor(e8, t2, n2) {
    this.treeAdapter = t2, this.handler = n2, this.items = [], this.tagIDs = [], this.stackTop = -1, this.tmplCount = 0, this.currentTagId = Po.UNKNOWN, this.current = e8;
  }
  get currentTmplContentOrNode() {
    return this._isInTemplate() ? this.treeAdapter.getTemplateContent(this.current) : this.current;
  }
  _indexOf(e8) {
    return this.items.lastIndexOf(e8, this.stackTop);
  }
  _isInTemplate() {
    return this.currentTagId === Po.TEMPLATE && this.treeAdapter.getNamespaceURI(this.current) === vo.HTML;
  }
  _updateCurrentElement() {
    this.current = this.items[this.stackTop], this.currentTagId = this.tagIDs[this.stackTop];
  }
  push(e8, t2) {
    this.stackTop++, this.items[this.stackTop] = e8, this.current = e8, this.tagIDs[this.stackTop] = t2, this.currentTagId = t2, this._isInTemplate() && this.tmplCount++, this.handler.onItemPush(e8, t2, true);
  }
  pop() {
    const e8 = this.current;
    this.tmplCount > 0 && this._isInTemplate() && this.tmplCount--, this.stackTop--, this._updateCurrentElement(), this.handler.onItemPop(e8, true);
  }
  replace(e8, t2) {
    const n2 = this._indexOf(e8);
    this.items[n2] = t2, n2 === this.stackTop && (this.current = t2);
  }
  insertAfter(e8, t2, n2) {
    const r2 = this._indexOf(e8) + 1;
    this.items.splice(r2, 0, t2), this.tagIDs.splice(r2, 0, n2), this.stackTop++, r2 === this.stackTop && this._updateCurrentElement(), this.handler.onItemPush(this.current, this.currentTagId, r2 === this.stackTop);
  }
  popUntilTagNamePopped(e8) {
    let t2 = this.stackTop + 1;
    do {
      t2 = this.tagIDs.lastIndexOf(e8, t2 - 1);
    } while (t2 > 0 && this.treeAdapter.getNamespaceURI(this.items[t2]) !== vo.HTML);
    this.shortenToLength(t2 < 0 ? 0 : t2);
  }
  shortenToLength(e8) {
    for (; this.stackTop >= e8; ) {
      const t2 = this.current;
      this.tmplCount > 0 && this._isInTemplate() && (this.tmplCount -= 1), this.stackTop--, this._updateCurrentElement(), this.handler.onItemPop(t2, this.stackTop < e8);
    }
  }
  popUntilElementPopped(e8) {
    const t2 = this._indexOf(e8);
    this.shortenToLength(t2 < 0 ? 0 : t2);
  }
  popUntilPopped(e8, t2) {
    const n2 = this._indexOfTagNames(e8, t2);
    this.shortenToLength(n2 < 0 ? 0 : n2);
  }
  popUntilNumberedHeaderPopped() {
    this.popUntilPopped(sc, vo.HTML);
  }
  popUntilTableCellPopped() {
    this.popUntilPopped(lc, vo.HTML);
  }
  popAllUpToHtmlElement() {
    this.tmplCount = 0, this.shortenToLength(1);
  }
  _indexOfTagNames(e8, t2) {
    for (let n2 = this.stackTop; n2 >= 0; n2--) if (e8.includes(this.tagIDs[n2]) && this.treeAdapter.getNamespaceURI(this.items[n2]) === t2) return n2;
    return -1;
  }
  clearBackTo(e8, t2) {
    const n2 = this._indexOfTagNames(e8, t2);
    this.shortenToLength(n2 + 1);
  }
  clearBackToTableContext() {
    this.clearBackTo(cc, vo.HTML);
  }
  clearBackToTableBodyContext() {
    this.clearBackTo(oc, vo.HTML);
  }
  clearBackToTableRowContext() {
    this.clearBackTo(ac, vo.HTML);
  }
  remove(e8) {
    const t2 = this._indexOf(e8);
    t2 >= 0 && (t2 === this.stackTop ? this.pop() : (this.items.splice(t2, 1), this.tagIDs.splice(t2, 1), this.stackTop--, this._updateCurrentElement(), this.handler.onItemPop(e8, false)));
  }
  tryPeekProperlyNestedBodyElement() {
    return this.stackTop >= 1 && this.tagIDs[1] === Po.BODY ? this.items[1] : null;
  }
  contains(e8) {
    return this._indexOf(e8) > -1;
  }
  getCommonAncestor(e8) {
    const t2 = this._indexOf(e8) - 1;
    return t2 >= 0 ? this.items[t2] : null;
  }
  isRootHtmlElementCurrent() {
    return 0 === this.stackTop && this.tagIDs[0] === Po.HTML;
  }
  hasInScope(e8) {
    for (let t2 = this.stackTop; t2 >= 0; t2--) {
      const n2 = this.tagIDs[t2], r2 = this.treeAdapter.getNamespaceURI(this.items[t2]);
      if (n2 === e8 && r2 === vo.HTML) return true;
      if (ic.get(n2) === r2) return false;
    }
    return true;
  }
  hasNumberedHeaderInScope() {
    for (let e8 = this.stackTop; e8 >= 0; e8--) {
      const t2 = this.tagIDs[e8], n2 = this.treeAdapter.getNamespaceURI(this.items[e8]);
      if (Go(t2) && n2 === vo.HTML) return true;
      if (ic.get(t2) === n2) return false;
    }
    return true;
  }
  hasInListItemScope(e8) {
    for (let t2 = this.stackTop; t2 >= 0; t2--) {
      const n2 = this.tagIDs[t2], r2 = this.treeAdapter.getNamespaceURI(this.items[t2]);
      if (n2 === e8 && r2 === vo.HTML) return true;
      if ((n2 === Po.UL || n2 === Po.OL) && r2 === vo.HTML || ic.get(n2) === r2) return false;
    }
    return true;
  }
  hasInButtonScope(e8) {
    for (let t2 = this.stackTop; t2 >= 0; t2--) {
      const n2 = this.tagIDs[t2], r2 = this.treeAdapter.getNamespaceURI(this.items[t2]);
      if (n2 === e8 && r2 === vo.HTML) return true;
      if (n2 === Po.BUTTON && r2 === vo.HTML || ic.get(n2) === r2) return false;
    }
    return true;
  }
  hasInTableScope(e8) {
    for (let t2 = this.stackTop; t2 >= 0; t2--) {
      const n2 = this.tagIDs[t2];
      if (this.treeAdapter.getNamespaceURI(this.items[t2]) === vo.HTML) {
        if (n2 === e8) return true;
        if (n2 === Po.TABLE || n2 === Po.TEMPLATE || n2 === Po.HTML) return false;
      }
    }
    return true;
  }
  hasTableBodyContextInTableScope() {
    for (let e8 = this.stackTop; e8 >= 0; e8--) {
      const t2 = this.tagIDs[e8];
      if (this.treeAdapter.getNamespaceURI(this.items[e8]) === vo.HTML) {
        if (t2 === Po.TBODY || t2 === Po.THEAD || t2 === Po.TFOOT) return true;
        if (t2 === Po.TABLE || t2 === Po.HTML) return false;
      }
    }
    return true;
  }
  hasInSelectScope(e8) {
    for (let t2 = this.stackTop; t2 >= 0; t2--) {
      const n2 = this.tagIDs[t2];
      if (this.treeAdapter.getNamespaceURI(this.items[t2]) === vo.HTML) {
        if (n2 === e8) return true;
        if (n2 !== Po.OPTION && n2 !== Po.OPTGROUP) return false;
      }
    }
    return true;
  }
  generateImpliedEndTags() {
    for (; nc.has(this.currentTagId); ) this.pop();
  }
  generateImpliedEndTagsThoroughly() {
    for (; rc.has(this.currentTagId); ) this.pop();
  }
  generateImpliedEndTagsWithExclusion(e8) {
    for (; this.currentTagId !== e8 && rc.has(this.currentTagId); ) this.pop();
  }
}
var uc;
!(function(e8) {
  e8[e8.Marker = 0] = "Marker", e8[e8.Element = 1] = "Element";
})(uc = uc || (uc = {}));
const pc = { type: uc.Marker };
class fc {
  constructor(e8) {
    this.treeAdapter = e8, this.entries = [], this.bookmark = null;
  }
  _getNoahArkConditionCandidates(e8, t2) {
    const n2 = [], r2 = t2.length, i2 = this.treeAdapter.getTagName(e8), s2 = this.treeAdapter.getNamespaceURI(e8);
    for (let e9 = 0; e9 < this.entries.length; e9++) {
      const t3 = this.entries[e9];
      if (t3.type === uc.Marker) break;
      const { element: a2 } = t3;
      if (this.treeAdapter.getTagName(a2) === i2 && this.treeAdapter.getNamespaceURI(a2) === s2) {
        const t4 = this.treeAdapter.getAttrList(a2);
        t4.length === r2 && n2.push({ idx: e9, attrs: t4 });
      }
    }
    return n2;
  }
  _ensureNoahArkCondition(e8) {
    if (this.entries.length < 3) return;
    const t2 = this.treeAdapter.getAttrList(e8), n2 = this._getNoahArkConditionCandidates(e8, t2);
    if (n2.length < 3) return;
    const r2 = new Map(t2.map(((e9) => [e9.name, e9.value])));
    let i2 = 0;
    for (let e9 = 0; e9 < n2.length; e9++) {
      const t3 = n2[e9];
      t3.attrs.every(((e10) => r2.get(e10.name) === e10.value)) && (i2 += 1, i2 >= 3 && this.entries.splice(t3.idx, 1));
    }
  }
  insertMarker() {
    this.entries.unshift(pc);
  }
  pushElement(e8, t2) {
    this._ensureNoahArkCondition(e8), this.entries.unshift({ type: uc.Element, element: e8, token: t2 });
  }
  insertElementAfterBookmark(e8, t2) {
    const n2 = this.entries.indexOf(this.bookmark);
    this.entries.splice(n2, 0, { type: uc.Element, element: e8, token: t2 });
  }
  removeEntry(e8) {
    const t2 = this.entries.indexOf(e8);
    t2 >= 0 && this.entries.splice(t2, 1);
  }
  clearToLastMarker() {
    const e8 = this.entries.indexOf(pc);
    e8 >= 0 ? this.entries.splice(0, e8 + 1) : this.entries.length = 0;
  }
  getElementEntryInScopeWithTagName(e8) {
    const t2 = this.entries.find(((t3) => t3.type === uc.Marker || this.treeAdapter.getTagName(t3.element) === e8));
    return t2 && t2.type === uc.Element ? t2 : null;
  }
  getElementEntry(e8) {
    return this.entries.find(((t2) => t2.type === uc.Element && t2.element === e8));
  }
}
function dc(e8) {
  return { nodeName: "#text", value: e8, parentNode: null };
}
const Ec = { createDocument: () => ({ nodeName: "#document", mode: Ro.NO_QUIRKS, childNodes: [] }), createDocumentFragment: () => ({ nodeName: "#document-fragment", childNodes: [] }), createElement: (e8, t2, n2) => ({ nodeName: e8, tagName: e8, attrs: n2, namespaceURI: t2, childNodes: [], parentNode: null }), createCommentNode: (e8) => ({ nodeName: "#comment", data: e8, parentNode: null }), appendChild(e8, t2) {
  e8.childNodes.push(t2), t2.parentNode = e8;
}, insertBefore(e8, t2, n2) {
  const r2 = e8.childNodes.indexOf(n2);
  e8.childNodes.splice(r2, 0, t2), t2.parentNode = e8;
}, setTemplateContent(e8, t2) {
  e8.content = t2;
}, getTemplateContent: (e8) => e8.content, setDocumentType(e8, t2, n2, r2) {
  const i2 = e8.childNodes.find(((e9) => "#documentType" === e9.nodeName));
  if (i2) i2.name = t2, i2.publicId = n2, i2.systemId = r2;
  else {
    const i3 = { nodeName: "#documentType", name: t2, publicId: n2, systemId: r2, parentNode: null };
    Ec.appendChild(e8, i3);
  }
}, setDocumentMode(e8, t2) {
  e8.mode = t2;
}, getDocumentMode: (e8) => e8.mode, detachNode(e8) {
  if (e8.parentNode) {
    const t2 = e8.parentNode.childNodes.indexOf(e8);
    e8.parentNode.childNodes.splice(t2, 1), e8.parentNode = null;
  }
}, insertText(e8, t2) {
  if (e8.childNodes.length > 0) {
    const n2 = e8.childNodes[e8.childNodes.length - 1];
    if (Ec.isTextNode(n2)) return void (n2.value += t2);
  }
  Ec.appendChild(e8, dc(t2));
}, insertTextBefore(e8, t2, n2) {
  const r2 = e8.childNodes[e8.childNodes.indexOf(n2) - 1];
  r2 && Ec.isTextNode(r2) ? r2.value += t2 : Ec.insertBefore(e8, dc(t2), n2);
}, adoptAttributes(e8, t2) {
  const n2 = new Set(e8.attrs.map(((e9) => e9.name)));
  for (let r2 = 0; r2 < t2.length; r2++) n2.has(t2[r2].name) || e8.attrs.push(t2[r2]);
}, getFirstChild: (e8) => e8.childNodes[0], getChildNodes: (e8) => e8.childNodes, getParentNode: (e8) => e8.parentNode, getAttrList: (e8) => e8.attrs, getTagName: (e8) => e8.tagName, getNamespaceURI: (e8) => e8.namespaceURI, getTextNodeContent: (e8) => e8.value, getCommentNodeContent: (e8) => e8.data, getDocumentTypeNodeName: (e8) => e8.name, getDocumentTypeNodePublicId: (e8) => e8.publicId, getDocumentTypeNodeSystemId: (e8) => e8.systemId, isTextNode: (e8) => "#text" === e8.nodeName, isCommentNode: (e8) => "#comment" === e8.nodeName, isDocumentTypeNode: (e8) => "#documentType" === e8.nodeName, isElementNode: (e8) => Object.prototype.hasOwnProperty.call(e8, "tagName"), setNodeSourceCodeLocation(e8, t2) {
  e8.sourceCodeLocation = t2;
}, getNodeSourceCodeLocation: (e8) => e8.sourceCodeLocation, updateNodeSourceCodeLocation(e8, t2) {
  e8.sourceCodeLocation = { ...e8.sourceCodeLocation, ...t2 };
} }, mc = ["+//silmaril//dtd html pro v0r11 19970101//", "-//as//dtd html 3.0 aswedit + extensions//", "-//advasoft ltd//dtd html 3.0 aswedit + extensions//", "-//ietf//dtd html 2.0 level 1//", "-//ietf//dtd html 2.0 level 2//", "-//ietf//dtd html 2.0 strict level 1//", "-//ietf//dtd html 2.0 strict level 2//", "-//ietf//dtd html 2.0 strict//", "-//ietf//dtd html 2.0//", "-//ietf//dtd html 2.1e//", "-//ietf//dtd html 3.0//", "-//ietf//dtd html 3.2 final//", "-//ietf//dtd html 3.2//", "-//ietf//dtd html 3//", "-//ietf//dtd html level 0//", "-//ietf//dtd html level 1//", "-//ietf//dtd html level 2//", "-//ietf//dtd html level 3//", "-//ietf//dtd html strict level 0//", "-//ietf//dtd html strict level 1//", "-//ietf//dtd html strict level 2//", "-//ietf//dtd html strict level 3//", "-//ietf//dtd html strict//", "-//ietf//dtd html//", "-//metrius//dtd metrius presentational//", "-//microsoft//dtd internet explorer 2.0 html strict//", "-//microsoft//dtd internet explorer 2.0 html//", "-//microsoft//dtd internet explorer 2.0 tables//", "-//microsoft//dtd internet explorer 3.0 html strict//", "-//microsoft//dtd internet explorer 3.0 html//", "-//microsoft//dtd internet explorer 3.0 tables//", "-//netscape comm. corp.//dtd html//", "-//netscape comm. corp.//dtd strict html//", "-//o'reilly and associates//dtd html 2.0//", "-//o'reilly and associates//dtd html extended 1.0//", "-//o'reilly and associates//dtd html extended relaxed 1.0//", "-//sq//dtd html 2.0 hotmetal + extensions//", "-//softquad software//dtd hotmetal pro 6.0::19990601::extensions to html 4.0//", "-//softquad//dtd hotmetal pro 4.0::19971010::extensions to html 4.0//", "-//spyglass//dtd html 2.0 extended//", "-//sun microsystems corp.//dtd hotjava html//", "-//sun microsystems corp.//dtd hotjava strict html//", "-//w3c//dtd html 3 1995-03-24//", "-//w3c//dtd html 3.2 draft//", "-//w3c//dtd html 3.2 final//", "-//w3c//dtd html 3.2//", "-//w3c//dtd html 3.2s draft//", "-//w3c//dtd html 4.0 frameset//", "-//w3c//dtd html 4.0 transitional//", "-//w3c//dtd html experimental 19960712//", "-//w3c//dtd html experimental 970421//", "-//w3c//dtd w3 html//", "-//w3o//dtd w3 html 3.0//", "-//webtechs//dtd mozilla html 2.0//", "-//webtechs//dtd mozilla html//"], Tc = [...mc, "-//w3c//dtd html 4.01 frameset//", "-//w3c//dtd html 4.01 transitional//"], _c = /* @__PURE__ */ new Set(["-//w3o//dtd w3 html strict 3.0//en//", "-/w3c/dtd html 4.0 transitional/en", "html"]), Ac = ["-//w3c//dtd xhtml 1.0 frameset//", "-//w3c//dtd xhtml 1.0 transitional//"], gc = [...Ac, "-//w3c//dtd html 4.01 frameset//", "-//w3c//dtd html 4.01 transitional//"];
function Nc(e8, t2) {
  return t2.some(((t3) => e8.startsWith(t3)));
}
const Cc = "text/html", Ic = "application/xhtml+xml", Sc = new Map(["attributeName", "attributeType", "baseFrequency", "baseProfile", "calcMode", "clipPathUnits", "diffuseConstant", "edgeMode", "filterUnits", "glyphRef", "gradientTransform", "gradientUnits", "kernelMatrix", "kernelUnitLength", "keyPoints", "keySplines", "keyTimes", "lengthAdjust", "limitingConeAngle", "markerHeight", "markerUnits", "markerWidth", "maskContentUnits", "maskUnits", "numOctaves", "pathLength", "patternContentUnits", "patternTransform", "patternUnits", "pointsAtX", "pointsAtY", "pointsAtZ", "preserveAlpha", "preserveAspectRatio", "primitiveUnits", "refX", "refY", "repeatCount", "repeatDur", "requiredExtensions", "requiredFeatures", "specularConstant", "specularExponent", "spreadMethod", "startOffset", "stdDeviation", "stitchTiles", "surfaceScale", "systemLanguage", "tableValues", "targetX", "targetY", "textLength", "viewBox", "viewTarget", "xChannelSelector", "yChannelSelector", "zoomAndPan"].map(((e8) => [e8.toLowerCase(), e8]))), bc = /* @__PURE__ */ new Map([["xlink:actuate", { prefix: "xlink", name: "actuate", namespace: vo.XLINK }], ["xlink:arcrole", { prefix: "xlink", name: "arcrole", namespace: vo.XLINK }], ["xlink:href", { prefix: "xlink", name: "href", namespace: vo.XLINK }], ["xlink:role", { prefix: "xlink", name: "role", namespace: vo.XLINK }], ["xlink:show", { prefix: "xlink", name: "show", namespace: vo.XLINK }], ["xlink:title", { prefix: "xlink", name: "title", namespace: vo.XLINK }], ["xlink:type", { prefix: "xlink", name: "type", namespace: vo.XLINK }], ["xml:base", { prefix: "xml", name: "base", namespace: vo.XML }], ["xml:lang", { prefix: "xml", name: "lang", namespace: vo.XML }], ["xml:space", { prefix: "xml", name: "space", namespace: vo.XML }], ["xmlns", { prefix: "", name: "xmlns", namespace: vo.XMLNS }], ["xmlns:xlink", { prefix: "xmlns", name: "xlink", namespace: vo.XMLNS }]]), Oc = new Map(["altGlyph", "altGlyphDef", "altGlyphItem", "animateColor", "animateMotion", "animateTransform", "clipPath", "feBlend", "feColorMatrix", "feComponentTransfer", "feComposite", "feConvolveMatrix", "feDiffuseLighting", "feDisplacementMap", "feDistantLight", "feFlood", "feFuncA", "feFuncB", "feFuncG", "feFuncR", "feGaussianBlur", "feImage", "feMerge", "feMergeNode", "feMorphology", "feOffset", "fePointLight", "feSpecularLighting", "feSpotLight", "feTile", "feTurbulence", "foreignObject", "glyphRef", "linearGradient", "radialGradient", "textPath"].map(((e8) => [e8.toLowerCase(), e8]))), yc = /* @__PURE__ */ new Set([Po.B, Po.BIG, Po.BLOCKQUOTE, Po.BODY, Po.BR, Po.CENTER, Po.CODE, Po.DD, Po.DIV, Po.DL, Po.DT, Po.EM, Po.EMBED, Po.H1, Po.H2, Po.H3, Po.H4, Po.H5, Po.H6, Po.HEAD, Po.HR, Po.I, Po.IMG, Po.LI, Po.LISTING, Po.MENU, Po.META, Po.NOBR, Po.OL, Po.P, Po.PRE, Po.RUBY, Po.S, Po.SMALL, Po.SPAN, Po.STRONG, Po.STRIKE, Po.SUB, Po.SUP, Po.TABLE, Po.TT, Po.U, Po.UL, Po.VAR]);
function Lc(e8) {
  for (let t2 = 0; t2 < e8.attrs.length; t2++) if ("definitionurl" === e8.attrs[t2].name) {
    e8.attrs[t2].name = "definitionURL";
    break;
  }
}
function kc(e8) {
  for (let t2 = 0; t2 < e8.attrs.length; t2++) {
    const n2 = Sc.get(e8.attrs[t2].name);
    null != n2 && (e8.attrs[t2].name = n2);
  }
}
function vc(e8) {
  for (let t2 = 0; t2 < e8.attrs.length; t2++) {
    const n2 = bc.get(e8.attrs[t2].name);
    n2 && (e8.attrs[t2].prefix = n2.prefix, e8.attrs[t2].name = n2.name, e8.attrs[t2].namespace = n2.namespace);
  }
}
function Dc(e8, t2, n2, r2) {
  return (!r2 || r2 === vo.HTML) && (function(e9, t3, n3) {
    if (t3 === vo.MATHML && e9 === Po.ANNOTATION_XML) {
      for (let e10 = 0; e10 < n3.length; e10++) if (n3[e10].name === Do.ENCODING) {
        const t4 = n3[e10].value.toLowerCase();
        return t4 === Cc || t4 === Ic;
      }
    }
    return t3 === vo.SVG && (e9 === Po.FOREIGN_OBJECT || e9 === Po.DESC || e9 === Po.TITLE);
  })(e8, t2, n2) || (!r2 || r2 === vo.MATHML) && (function(e9, t3) {
    return t3 === vo.MATHML && (e9 === Po.MI || e9 === Po.MO || e9 === Po.MN || e9 === Po.MS || e9 === Po.MTEXT);
  })(e8, t2);
}
var Rc;
!(function(e8) {
  e8[e8.INITIAL = 0] = "INITIAL", e8[e8.BEFORE_HTML = 1] = "BEFORE_HTML", e8[e8.BEFORE_HEAD = 2] = "BEFORE_HEAD", e8[e8.IN_HEAD = 3] = "IN_HEAD", e8[e8.IN_HEAD_NO_SCRIPT = 4] = "IN_HEAD_NO_SCRIPT", e8[e8.AFTER_HEAD = 5] = "AFTER_HEAD", e8[e8.IN_BODY = 6] = "IN_BODY", e8[e8.TEXT = 7] = "TEXT", e8[e8.IN_TABLE = 8] = "IN_TABLE", e8[e8.IN_TABLE_TEXT = 9] = "IN_TABLE_TEXT", e8[e8.IN_CAPTION = 10] = "IN_CAPTION", e8[e8.IN_COLUMN_GROUP = 11] = "IN_COLUMN_GROUP", e8[e8.IN_TABLE_BODY = 12] = "IN_TABLE_BODY", e8[e8.IN_ROW = 13] = "IN_ROW", e8[e8.IN_CELL = 14] = "IN_CELL", e8[e8.IN_SELECT = 15] = "IN_SELECT", e8[e8.IN_SELECT_IN_TABLE = 16] = "IN_SELECT_IN_TABLE", e8[e8.IN_TEMPLATE = 17] = "IN_TEMPLATE", e8[e8.AFTER_BODY = 18] = "AFTER_BODY", e8[e8.IN_FRAMESET = 19] = "IN_FRAMESET", e8[e8.AFTER_FRAMESET = 20] = "AFTER_FRAMESET", e8[e8.AFTER_AFTER_BODY = 21] = "AFTER_AFTER_BODY", e8[e8.AFTER_AFTER_FRAMESET = 22] = "AFTER_AFTER_FRAMESET";
})(Rc || (Rc = {}));
const Mc = { startLine: -1, startCol: -1, startOffset: -1, endLine: -1, endCol: -1, endOffset: -1 }, Pc = /* @__PURE__ */ new Set([Po.TABLE, Po.TBODY, Po.TFOOT, Po.THEAD, Po.TR]), xc = { scriptingEnabled: true, sourceCodeLocationInfo: false, treeAdapter: Ec, onParseError: null };
class wc {
  constructor(e8, t2, n2 = null, r2 = null) {
    this.fragmentContext = n2, this.scriptHandler = r2, this.currentToken = null, this.stopped = false, this.insertionMode = Rc.INITIAL, this.originalInsertionMode = Rc.INITIAL, this.headElement = null, this.formElement = null, this.currentNotInHTML = false, this.tmplInsertionModeStack = [], this.pendingCharacterTokens = [], this.hasNonWhitespacePendingCharacterToken = false, this.framesetOk = true, this.skipNextNewLine = false, this.fosterParentingEnabled = false, this.options = { ...xc, ...e8 }, this.treeAdapter = this.options.treeAdapter, this.onParseError = this.options.onParseError, this.onParseError && (this.options.sourceCodeLocationInfo = true), this.document = null != t2 ? t2 : this.treeAdapter.createDocument(), this.tokenizer = new tc(this.options, this), this.activeFormattingElements = new fc(this.treeAdapter), this.fragmentContextID = n2 ? Fo(this.treeAdapter.getTagName(n2)) : Po.UNKNOWN, this._setContextModes(null != n2 ? n2 : this.document, this.fragmentContextID), this.openElements = new hc(this.document, this.treeAdapter, this);
  }
  static parse(e8, t2) {
    const n2 = new this(t2);
    return n2.tokenizer.write(e8, true), n2.document;
  }
  static getFragmentParser(e8, t2) {
    const n2 = { ...xc, ...t2 };
    null != e8 || (e8 = n2.treeAdapter.createElement(Mo.TEMPLATE, vo.HTML, []));
    const r2 = n2.treeAdapter.createElement("documentmock", vo.HTML, []), i2 = new this(n2, r2, e8);
    return i2.fragmentContextID === Po.TEMPLATE && i2.tmplInsertionModeStack.unshift(Rc.IN_TEMPLATE), i2._initTokenizerForFragmentParsing(), i2._insertFakeRootElement(), i2._resetInsertionMode(), i2._findFormInFragmentContext(), i2;
  }
  getFragment() {
    const e8 = this.treeAdapter.getFirstChild(this.document), t2 = this.treeAdapter.createDocumentFragment();
    return this._adoptNodes(e8, t2), t2;
  }
  _err(e8, t2, n2) {
    var r2;
    if (!this.onParseError) return;
    const i2 = null !== (r2 = e8.location) && void 0 !== r2 ? r2 : Mc, s2 = { code: t2, startLine: i2.startLine, startCol: i2.startCol, startOffset: i2.startOffset, endLine: n2 ? i2.startLine : i2.endLine, endCol: n2 ? i2.startCol : i2.endCol, endOffset: n2 ? i2.startOffset : i2.endOffset };
    this.onParseError(s2);
  }
  onItemPush(e8, t2, n2) {
    var r2, i2;
    null === (i2 = (r2 = this.treeAdapter).onItemPush) || void 0 === i2 || i2.call(r2, e8), n2 && this.openElements.stackTop > 0 && this._setContextModes(e8, t2);
  }
  onItemPop(e8, t2) {
    var n2, r2;
    if (this.options.sourceCodeLocationInfo && this._setEndLocation(e8, this.currentToken), null === (r2 = (n2 = this.treeAdapter).onItemPop) || void 0 === r2 || r2.call(n2, e8, this.openElements.current), t2) {
      let e9, t3;
      0 === this.openElements.stackTop && this.fragmentContext ? (e9 = this.fragmentContext, t3 = this.fragmentContextID) : { current: e9, currentTagId: t3 } = this.openElements, this._setContextModes(e9, t3);
    }
  }
  _setContextModes(e8, t2) {
    const n2 = e8 === this.document || this.treeAdapter.getNamespaceURI(e8) === vo.HTML;
    this.currentNotInHTML = !n2, this.tokenizer.inForeignNode = !n2 && !this._isIntegrationPoint(t2, e8);
  }
  _switchToTextParsing(e8, t2) {
    this._insertElement(e8, vo.HTML), this.tokenizer.state = t2, this.originalInsertionMode = this.insertionMode, this.insertionMode = Rc.TEXT;
  }
  switchToPlaintextParsing() {
    this.insertionMode = Rc.TEXT, this.originalInsertionMode = Rc.IN_BODY, this.tokenizer.state = jo.PLAINTEXT;
  }
  _getAdjustedCurrentElement() {
    return 0 === this.openElements.stackTop && this.fragmentContext ? this.fragmentContext : this.openElements.current;
  }
  _findFormInFragmentContext() {
    let e8 = this.fragmentContext;
    for (; e8; ) {
      if (this.treeAdapter.getTagName(e8) === Mo.FORM) {
        this.formElement = e8;
        break;
      }
      e8 = this.treeAdapter.getParentNode(e8);
    }
  }
  _initTokenizerForFragmentParsing() {
    if (this.fragmentContext && this.treeAdapter.getNamespaceURI(this.fragmentContext) === vo.HTML) switch (this.fragmentContextID) {
      case Po.TITLE:
      case Po.TEXTAREA:
        this.tokenizer.state = jo.RCDATA;
        break;
      case Po.STYLE:
      case Po.XMP:
      case Po.IFRAME:
      case Po.NOEMBED:
      case Po.NOFRAMES:
      case Po.NOSCRIPT:
        this.tokenizer.state = jo.RAWTEXT;
        break;
      case Po.SCRIPT:
        this.tokenizer.state = jo.SCRIPT_DATA;
        break;
      case Po.PLAINTEXT:
        this.tokenizer.state = jo.PLAINTEXT;
    }
  }
  _setDocumentType(e8) {
    const t2 = e8.name || "", n2 = e8.publicId || "", r2 = e8.systemId || "";
    if (this.treeAdapter.setDocumentType(this.document, t2, n2, r2), e8.location) {
      const t3 = this.treeAdapter.getChildNodes(this.document).find(((e9) => this.treeAdapter.isDocumentTypeNode(e9)));
      t3 && this.treeAdapter.setNodeSourceCodeLocation(t3, e8.location);
    }
  }
  _attachElementToTree(e8, t2) {
    if (this.options.sourceCodeLocationInfo) {
      const n2 = t2 && { ...t2, startTag: t2 };
      this.treeAdapter.setNodeSourceCodeLocation(e8, n2);
    }
    if (this._shouldFosterParentOnInsertion()) this._fosterParentElement(e8);
    else {
      const t3 = this.openElements.currentTmplContentOrNode;
      this.treeAdapter.appendChild(t3, e8);
    }
  }
  _appendElement(e8, t2) {
    const n2 = this.treeAdapter.createElement(e8.tagName, t2, e8.attrs);
    this._attachElementToTree(n2, e8.location);
  }
  _insertElement(e8, t2) {
    const n2 = this.treeAdapter.createElement(e8.tagName, t2, e8.attrs);
    this._attachElementToTree(n2, e8.location), this.openElements.push(n2, e8.tagID);
  }
  _insertFakeElement(e8, t2) {
    const n2 = this.treeAdapter.createElement(e8, vo.HTML, []);
    this._attachElementToTree(n2, null), this.openElements.push(n2, t2);
  }
  _insertTemplate(e8) {
    const t2 = this.treeAdapter.createElement(e8.tagName, vo.HTML, e8.attrs), n2 = this.treeAdapter.createDocumentFragment();
    this.treeAdapter.setTemplateContent(t2, n2), this._attachElementToTree(t2, e8.location), this.openElements.push(t2, e8.tagID), this.options.sourceCodeLocationInfo && this.treeAdapter.setNodeSourceCodeLocation(n2, null);
  }
  _insertFakeRootElement() {
    const e8 = this.treeAdapter.createElement(Mo.HTML, vo.HTML, []);
    this.options.sourceCodeLocationInfo && this.treeAdapter.setNodeSourceCodeLocation(e8, null), this.treeAdapter.appendChild(this.openElements.current, e8), this.openElements.push(e8, Po.HTML);
  }
  _appendCommentNode(e8, t2) {
    const n2 = this.treeAdapter.createCommentNode(e8.data);
    this.treeAdapter.appendChild(t2, n2), this.options.sourceCodeLocationInfo && this.treeAdapter.setNodeSourceCodeLocation(n2, e8.location);
  }
  _insertCharacters(e8) {
    let t2, n2;
    if (this._shouldFosterParentOnInsertion() ? ({ parent: t2, beforeElement: n2 } = this._findFosterParentingLocation(), n2 ? this.treeAdapter.insertTextBefore(t2, e8.chars, n2) : this.treeAdapter.insertText(t2, e8.chars)) : (t2 = this.openElements.currentTmplContentOrNode, this.treeAdapter.insertText(t2, e8.chars)), !e8.location) return;
    const r2 = this.treeAdapter.getChildNodes(t2), i2 = n2 ? r2.lastIndexOf(n2) : r2.length, s2 = r2[i2 - 1];
    if (this.treeAdapter.getNodeSourceCodeLocation(s2)) {
      const { endLine: t3, endCol: n3, endOffset: r3 } = e8.location;
      this.treeAdapter.updateNodeSourceCodeLocation(s2, { endLine: t3, endCol: n3, endOffset: r3 });
    } else this.options.sourceCodeLocationInfo && this.treeAdapter.setNodeSourceCodeLocation(s2, e8.location);
  }
  _adoptNodes(e8, t2) {
    for (let n2 = this.treeAdapter.getFirstChild(e8); n2; n2 = this.treeAdapter.getFirstChild(e8)) this.treeAdapter.detachNode(n2), this.treeAdapter.appendChild(t2, n2);
  }
  _setEndLocation(e8, t2) {
    if (this.treeAdapter.getNodeSourceCodeLocation(e8) && t2.location) {
      const n2 = t2.location, r2 = this.treeAdapter.getTagName(e8), i2 = t2.type === To.END_TAG && r2 === t2.tagName ? { endTag: { ...n2 }, endLine: n2.endLine, endCol: n2.endCol, endOffset: n2.endOffset } : { endLine: n2.startLine, endCol: n2.startCol, endOffset: n2.startOffset };
      this.treeAdapter.updateNodeSourceCodeLocation(e8, i2);
    }
  }
  shouldProcessStartTagTokenInForeignContent(e8) {
    if (!this.currentNotInHTML) return false;
    let t2, n2;
    return 0 === this.openElements.stackTop && this.fragmentContext ? (t2 = this.fragmentContext, n2 = this.fragmentContextID) : { current: t2, currentTagId: n2 } = this.openElements, (e8.tagID !== Po.SVG || this.treeAdapter.getTagName(t2) !== Mo.ANNOTATION_XML || this.treeAdapter.getNamespaceURI(t2) !== vo.MATHML) && (this.tokenizer.inForeignNode || (e8.tagID === Po.MGLYPH || e8.tagID === Po.MALIGNMARK) && !this._isIntegrationPoint(n2, t2, vo.HTML));
  }
  _processToken(e8) {
    switch (e8.type) {
      case To.CHARACTER:
        this.onCharacter(e8);
        break;
      case To.NULL_CHARACTER:
        this.onNullCharacter(e8);
        break;
      case To.COMMENT:
        this.onComment(e8);
        break;
      case To.DOCTYPE:
        this.onDoctype(e8);
        break;
      case To.START_TAG:
        this._processStartTag(e8);
        break;
      case To.END_TAG:
        this.onEndTag(e8);
        break;
      case To.EOF:
        this.onEof(e8);
        break;
      case To.WHITESPACE_CHARACTER:
        this.onWhitespaceCharacter(e8);
    }
  }
  _isIntegrationPoint(e8, t2, n2) {
    return Dc(e8, this.treeAdapter.getNamespaceURI(t2), this.treeAdapter.getAttrList(t2), n2);
  }
  _reconstructActiveFormattingElements() {
    const e8 = this.activeFormattingElements.entries.length;
    if (e8) {
      const t2 = this.activeFormattingElements.entries.findIndex(((e9) => e9.type === uc.Marker || this.openElements.contains(e9.element)));
      for (let n2 = t2 < 0 ? e8 - 1 : t2 - 1; n2 >= 0; n2--) {
        const e9 = this.activeFormattingElements.entries[n2];
        this._insertElement(e9.token, this.treeAdapter.getNamespaceURI(e9.element)), e9.element = this.openElements.current;
      }
    }
  }
  _closeTableCell() {
    this.openElements.generateImpliedEndTags(), this.openElements.popUntilTableCellPopped(), this.activeFormattingElements.clearToLastMarker(), this.insertionMode = Rc.IN_ROW;
  }
  _closePElement() {
    this.openElements.generateImpliedEndTagsWithExclusion(Po.P), this.openElements.popUntilTagNamePopped(Po.P);
  }
  _resetInsertionMode() {
    for (let e8 = this.openElements.stackTop; e8 >= 0; e8--) switch (0 === e8 && this.fragmentContext ? this.fragmentContextID : this.openElements.tagIDs[e8]) {
      case Po.TR:
        return void (this.insertionMode = Rc.IN_ROW);
      case Po.TBODY:
      case Po.THEAD:
      case Po.TFOOT:
        return void (this.insertionMode = Rc.IN_TABLE_BODY);
      case Po.CAPTION:
        return void (this.insertionMode = Rc.IN_CAPTION);
      case Po.COLGROUP:
        return void (this.insertionMode = Rc.IN_COLUMN_GROUP);
      case Po.TABLE:
        return void (this.insertionMode = Rc.IN_TABLE);
      case Po.BODY:
        return void (this.insertionMode = Rc.IN_BODY);
      case Po.FRAMESET:
        return void (this.insertionMode = Rc.IN_FRAMESET);
      case Po.SELECT:
        return void this._resetInsertionModeForSelect(e8);
      case Po.TEMPLATE:
        return void (this.insertionMode = this.tmplInsertionModeStack[0]);
      case Po.HTML:
        return void (this.insertionMode = this.headElement ? Rc.AFTER_HEAD : Rc.BEFORE_HEAD);
      case Po.TD:
      case Po.TH:
        if (e8 > 0) return void (this.insertionMode = Rc.IN_CELL);
        break;
      case Po.HEAD:
        if (e8 > 0) return void (this.insertionMode = Rc.IN_HEAD);
    }
    this.insertionMode = Rc.IN_BODY;
  }
  _resetInsertionModeForSelect(e8) {
    if (e8 > 0) for (let t2 = e8 - 1; t2 > 0; t2--) {
      const e9 = this.openElements.tagIDs[t2];
      if (e9 === Po.TEMPLATE) break;
      if (e9 === Po.TABLE) return void (this.insertionMode = Rc.IN_SELECT_IN_TABLE);
    }
    this.insertionMode = Rc.IN_SELECT;
  }
  _isElementCausesFosterParenting(e8) {
    return Pc.has(e8);
  }
  _shouldFosterParentOnInsertion() {
    return this.fosterParentingEnabled && this._isElementCausesFosterParenting(this.openElements.currentTagId);
  }
  _findFosterParentingLocation() {
    for (let e8 = this.openElements.stackTop; e8 >= 0; e8--) {
      const t2 = this.openElements.items[e8];
      switch (this.openElements.tagIDs[e8]) {
        case Po.TEMPLATE:
          if (this.treeAdapter.getNamespaceURI(t2) === vo.HTML) return { parent: this.treeAdapter.getTemplateContent(t2), beforeElement: null };
          break;
        case Po.TABLE: {
          const n2 = this.treeAdapter.getParentNode(t2);
          return n2 ? { parent: n2, beforeElement: t2 } : { parent: this.openElements.items[e8 - 1], beforeElement: null };
        }
      }
    }
    return { parent: this.openElements.items[0], beforeElement: null };
  }
  _fosterParentElement(e8) {
    const t2 = this._findFosterParentingLocation();
    t2.beforeElement ? this.treeAdapter.insertBefore(t2.parent, e8, t2.beforeElement) : this.treeAdapter.appendChild(t2.parent, e8);
  }
  _isSpecialElement(e8, t2) {
    const n2 = this.treeAdapter.getNamespaceURI(e8);
    return Ho[n2].has(t2);
  }
  onCharacter(e8) {
    if (this.skipNextNewLine = false, this.tokenizer.inForeignNode) !(function(e9, t2) {
      e9._insertCharacters(t2), e9.framesetOk = false;
    })(this, e8);
    else switch (this.insertionMode) {
      case Rc.INITIAL:
        Vc(this, e8);
        break;
      case Rc.BEFORE_HTML:
        Wc(this, e8);
        break;
      case Rc.BEFORE_HEAD:
        Qc(this, e8);
        break;
      case Rc.IN_HEAD:
        zc(this, e8);
        break;
      case Rc.IN_HEAD_NO_SCRIPT:
        Jc(this, e8);
        break;
      case Rc.AFTER_HEAD:
        Zc(this, e8);
        break;
      case Rc.IN_BODY:
      case Rc.IN_CAPTION:
      case Rc.IN_CELL:
      case Rc.IN_TEMPLATE:
        nl(this, e8);
        break;
      case Rc.TEXT:
      case Rc.IN_SELECT:
      case Rc.IN_SELECT_IN_TABLE:
        this._insertCharacters(e8);
        break;
      case Rc.IN_TABLE:
      case Rc.IN_TABLE_BODY:
      case Rc.IN_ROW:
        ul(this, e8);
        break;
      case Rc.IN_TABLE_TEXT:
        ml(this, e8);
        break;
      case Rc.IN_COLUMN_GROUP:
        gl(this, e8);
        break;
      case Rc.AFTER_BODY:
        kl(this, e8);
        break;
      case Rc.AFTER_AFTER_BODY:
        vl(this, e8);
    }
  }
  onNullCharacter(e8) {
    if (this.skipNextNewLine = false, this.tokenizer.inForeignNode) !(function(e9, t2) {
      t2.chars = "\uFFFD", e9._insertCharacters(t2);
    })(this, e8);
    else switch (this.insertionMode) {
      case Rc.INITIAL:
        Vc(this, e8);
        break;
      case Rc.BEFORE_HTML:
        Wc(this, e8);
        break;
      case Rc.BEFORE_HEAD:
        Qc(this, e8);
        break;
      case Rc.IN_HEAD:
        zc(this, e8);
        break;
      case Rc.IN_HEAD_NO_SCRIPT:
        Jc(this, e8);
        break;
      case Rc.AFTER_HEAD:
        Zc(this, e8);
        break;
      case Rc.TEXT:
        this._insertCharacters(e8);
        break;
      case Rc.IN_TABLE:
      case Rc.IN_TABLE_BODY:
      case Rc.IN_ROW:
        ul(this, e8);
        break;
      case Rc.IN_COLUMN_GROUP:
        gl(this, e8);
        break;
      case Rc.AFTER_BODY:
        kl(this, e8);
        break;
      case Rc.AFTER_AFTER_BODY:
        vl(this, e8);
    }
  }
  onComment(e8) {
    if (this.skipNextNewLine = false, this.currentNotInHTML) Kc(this, e8);
    else switch (this.insertionMode) {
      case Rc.INITIAL:
      case Rc.BEFORE_HTML:
      case Rc.BEFORE_HEAD:
      case Rc.IN_HEAD:
      case Rc.IN_HEAD_NO_SCRIPT:
      case Rc.AFTER_HEAD:
      case Rc.IN_BODY:
      case Rc.IN_TABLE:
      case Rc.IN_CAPTION:
      case Rc.IN_COLUMN_GROUP:
      case Rc.IN_TABLE_BODY:
      case Rc.IN_ROW:
      case Rc.IN_CELL:
      case Rc.IN_SELECT:
      case Rc.IN_SELECT_IN_TABLE:
      case Rc.IN_TEMPLATE:
      case Rc.IN_FRAMESET:
      case Rc.AFTER_FRAMESET:
        Kc(this, e8);
        break;
      case Rc.IN_TABLE_TEXT:
        Tl(this, e8);
        break;
      case Rc.AFTER_BODY:
        !(function(e9, t2) {
          e9._appendCommentNode(t2, e9.openElements.items[0]);
        })(this, e8);
        break;
      case Rc.AFTER_AFTER_BODY:
      case Rc.AFTER_AFTER_FRAMESET:
        !(function(e9, t2) {
          e9._appendCommentNode(t2, e9.document);
        })(this, e8);
    }
  }
  onDoctype(e8) {
    switch (this.skipNextNewLine = false, this.insertionMode) {
      case Rc.INITIAL:
        !(function(e9, t2) {
          e9._setDocumentType(t2);
          const n2 = t2.forceQuirks ? Ro.QUIRKS : (function(e10) {
            if ("html" !== e10.name) return Ro.QUIRKS;
            const { systemId: t3 } = e10;
            if (t3 && "http://www.ibm.com/data/dtd/v11/ibmxhtml1-transitional.dtd" === t3.toLowerCase()) return Ro.QUIRKS;
            let { publicId: n3 } = e10;
            if (null !== n3) {
              if (n3 = n3.toLowerCase(), _c.has(n3)) return Ro.QUIRKS;
              let e11 = null === t3 ? Tc : mc;
              if (Nc(n3, e11)) return Ro.QUIRKS;
              if (e11 = null === t3 ? Ac : gc, Nc(n3, e11)) return Ro.LIMITED_QUIRKS;
            }
            return Ro.NO_QUIRKS;
          })(t2);
          (function(e10) {
            return "html" === e10.name && null === e10.publicId && (null === e10.systemId || "about:legacy-compat" === e10.systemId);
          })(t2) || e9._err(t2, Eo.nonConformingDoctype);
          e9.treeAdapter.setDocumentMode(e9.document, n2), e9.insertionMode = Rc.BEFORE_HTML;
        })(this, e8);
        break;
      case Rc.BEFORE_HEAD:
      case Rc.IN_HEAD:
      case Rc.IN_HEAD_NO_SCRIPT:
      case Rc.AFTER_HEAD:
        this._err(e8, Eo.misplacedDoctype);
        break;
      case Rc.IN_TABLE_TEXT:
        Tl(this, e8);
    }
  }
  onStartTag(e8) {
    this.skipNextNewLine = false, this.currentToken = e8, this._processStartTag(e8), e8.selfClosing && !e8.ackSelfClosing && this._err(e8, Eo.nonVoidHtmlElementStartTagWithTrailingSolidus);
  }
  _processStartTag(e8) {
    this.shouldProcessStartTagTokenInForeignContent(e8) ? (function(e9, t2) {
      if ((function(e10) {
        const t3 = e10.tagID;
        return t3 === Po.FONT && e10.attrs.some((({ name: e11 }) => e11 === Do.COLOR || e11 === Do.SIZE || e11 === Do.FACE)) || yc.has(t3);
      })(t2)) Dl(e9), e9._startTagOutsideForeignContent(t2);
      else {
        const n2 = e9._getAdjustedCurrentElement(), r2 = e9.treeAdapter.getNamespaceURI(n2);
        r2 === vo.MATHML ? Lc(t2) : r2 === vo.SVG && (!(function(e10) {
          const t3 = Oc.get(e10.tagName);
          null != t3 && (e10.tagName = t3, e10.tagID = Fo(e10.tagName));
        })(t2), kc(t2)), vc(t2), t2.selfClosing ? e9._appendElement(t2, r2) : e9._insertElement(t2, r2), t2.ackSelfClosing = true;
      }
    })(this, e8) : this._startTagOutsideForeignContent(e8);
  }
  _startTagOutsideForeignContent(e8) {
    switch (this.insertionMode) {
      case Rc.INITIAL:
        Vc(this, e8);
        break;
      case Rc.BEFORE_HTML:
        !(function(e9, t2) {
          t2.tagID === Po.HTML ? (e9._insertElement(t2, vo.HTML), e9.insertionMode = Rc.BEFORE_HEAD) : Wc(e9, t2);
        })(this, e8);
        break;
      case Rc.BEFORE_HEAD:
        !(function(e9, t2) {
          switch (t2.tagID) {
            case Po.HTML:
              ol(e9, t2);
              break;
            case Po.HEAD:
              e9._insertElement(t2, vo.HTML), e9.headElement = e9.openElements.current, e9.insertionMode = Rc.IN_HEAD;
              break;
            default:
              Qc(e9, t2);
          }
        })(this, e8);
        break;
      case Rc.IN_HEAD:
        Xc(this, e8);
        break;
      case Rc.IN_HEAD_NO_SCRIPT:
        !(function(e9, t2) {
          switch (t2.tagID) {
            case Po.HTML:
              ol(e9, t2);
              break;
            case Po.BASEFONT:
            case Po.BGSOUND:
            case Po.HEAD:
            case Po.LINK:
            case Po.META:
            case Po.NOFRAMES:
            case Po.STYLE:
              Xc(e9, t2);
              break;
            case Po.NOSCRIPT:
              e9._err(t2, Eo.nestedNoscriptInHead);
              break;
            default:
              Jc(e9, t2);
          }
        })(this, e8);
        break;
      case Rc.AFTER_HEAD:
        !(function(e9, t2) {
          switch (t2.tagID) {
            case Po.HTML:
              ol(e9, t2);
              break;
            case Po.BODY:
              e9._insertElement(t2, vo.HTML), e9.framesetOk = false, e9.insertionMode = Rc.IN_BODY;
              break;
            case Po.FRAMESET:
              e9._insertElement(t2, vo.HTML), e9.insertionMode = Rc.IN_FRAMESET;
              break;
            case Po.BASE:
            case Po.BASEFONT:
            case Po.BGSOUND:
            case Po.LINK:
            case Po.META:
            case Po.NOFRAMES:
            case Po.SCRIPT:
            case Po.STYLE:
            case Po.TEMPLATE:
            case Po.TITLE:
              e9._err(t2, Eo.abandonedHeadElementChild), e9.openElements.push(e9.headElement, Po.HEAD), Xc(e9, t2), e9.openElements.remove(e9.headElement);
              break;
            case Po.HEAD:
              e9._err(t2, Eo.misplacedStartTagForHeadElement);
              break;
            default:
              Zc(e9, t2);
          }
        })(this, e8);
        break;
      case Rc.IN_BODY:
        ol(this, e8);
        break;
      case Rc.IN_TABLE:
        pl(this, e8);
        break;
      case Rc.IN_TABLE_TEXT:
        Tl(this, e8);
        break;
      case Rc.IN_CAPTION:
        !(function(e9, t2) {
          const n2 = t2.tagID;
          _l.has(n2) ? e9.openElements.hasInTableScope(Po.CAPTION) && (e9.openElements.generateImpliedEndTags(), e9.openElements.popUntilTagNamePopped(Po.CAPTION), e9.activeFormattingElements.clearToLastMarker(), e9.insertionMode = Rc.IN_TABLE, pl(e9, t2)) : ol(e9, t2);
        })(this, e8);
        break;
      case Rc.IN_COLUMN_GROUP:
        Al(this, e8);
        break;
      case Rc.IN_TABLE_BODY:
        Nl(this, e8);
        break;
      case Rc.IN_ROW:
        Il(this, e8);
        break;
      case Rc.IN_CELL:
        !(function(e9, t2) {
          const n2 = t2.tagID;
          _l.has(n2) ? (e9.openElements.hasInTableScope(Po.TD) || e9.openElements.hasInTableScope(Po.TH)) && (e9._closeTableCell(), Il(e9, t2)) : ol(e9, t2);
        })(this, e8);
        break;
      case Rc.IN_SELECT:
        bl(this, e8);
        break;
      case Rc.IN_SELECT_IN_TABLE:
        !(function(e9, t2) {
          const n2 = t2.tagID;
          n2 === Po.CAPTION || n2 === Po.TABLE || n2 === Po.TBODY || n2 === Po.TFOOT || n2 === Po.THEAD || n2 === Po.TR || n2 === Po.TD || n2 === Po.TH ? (e9.openElements.popUntilTagNamePopped(Po.SELECT), e9._resetInsertionMode(), e9._processStartTag(t2)) : bl(e9, t2);
        })(this, e8);
        break;
      case Rc.IN_TEMPLATE:
        !(function(e9, t2) {
          switch (t2.tagID) {
            case Po.BASE:
            case Po.BASEFONT:
            case Po.BGSOUND:
            case Po.LINK:
            case Po.META:
            case Po.NOFRAMES:
            case Po.SCRIPT:
            case Po.STYLE:
            case Po.TEMPLATE:
            case Po.TITLE:
              Xc(e9, t2);
              break;
            case Po.CAPTION:
            case Po.COLGROUP:
            case Po.TBODY:
            case Po.TFOOT:
            case Po.THEAD:
              e9.tmplInsertionModeStack[0] = Rc.IN_TABLE, e9.insertionMode = Rc.IN_TABLE, pl(e9, t2);
              break;
            case Po.COL:
              e9.tmplInsertionModeStack[0] = Rc.IN_COLUMN_GROUP, e9.insertionMode = Rc.IN_COLUMN_GROUP, Al(e9, t2);
              break;
            case Po.TR:
              e9.tmplInsertionModeStack[0] = Rc.IN_TABLE_BODY, e9.insertionMode = Rc.IN_TABLE_BODY, Nl(e9, t2);
              break;
            case Po.TD:
            case Po.TH:
              e9.tmplInsertionModeStack[0] = Rc.IN_ROW, e9.insertionMode = Rc.IN_ROW, Il(e9, t2);
              break;
            default:
              e9.tmplInsertionModeStack[0] = Rc.IN_BODY, e9.insertionMode = Rc.IN_BODY, ol(e9, t2);
          }
        })(this, e8);
        break;
      case Rc.AFTER_BODY:
        !(function(e9, t2) {
          t2.tagID === Po.HTML ? ol(e9, t2) : kl(e9, t2);
        })(this, e8);
        break;
      case Rc.IN_FRAMESET:
        !(function(e9, t2) {
          switch (t2.tagID) {
            case Po.HTML:
              ol(e9, t2);
              break;
            case Po.FRAMESET:
              e9._insertElement(t2, vo.HTML);
              break;
            case Po.FRAME:
              e9._appendElement(t2, vo.HTML), t2.ackSelfClosing = true;
              break;
            case Po.NOFRAMES:
              Xc(e9, t2);
          }
        })(this, e8);
        break;
      case Rc.AFTER_FRAMESET:
        !(function(e9, t2) {
          switch (t2.tagID) {
            case Po.HTML:
              ol(e9, t2);
              break;
            case Po.NOFRAMES:
              Xc(e9, t2);
          }
        })(this, e8);
        break;
      case Rc.AFTER_AFTER_BODY:
        !(function(e9, t2) {
          t2.tagID === Po.HTML ? ol(e9, t2) : vl(e9, t2);
        })(this, e8);
        break;
      case Rc.AFTER_AFTER_FRAMESET:
        !(function(e9, t2) {
          switch (t2.tagID) {
            case Po.HTML:
              ol(e9, t2);
              break;
            case Po.NOFRAMES:
              Xc(e9, t2);
          }
        })(this, e8);
    }
  }
  onEndTag(e8) {
    this.skipNextNewLine = false, this.currentToken = e8, this.currentNotInHTML ? (function(e9, t2) {
      if (t2.tagID === Po.P || t2.tagID === Po.BR) return Dl(e9), void e9._endTagOutsideForeignContent(t2);
      for (let n2 = e9.openElements.stackTop; n2 > 0; n2--) {
        const r2 = e9.openElements.items[n2];
        if (e9.treeAdapter.getNamespaceURI(r2) === vo.HTML) {
          e9._endTagOutsideForeignContent(t2);
          break;
        }
        const i2 = e9.treeAdapter.getTagName(r2);
        if (i2.toLowerCase() === t2.tagName) {
          t2.tagName = i2, e9.openElements.shortenToLength(n2);
          break;
        }
      }
    })(this, e8) : this._endTagOutsideForeignContent(e8);
  }
  _endTagOutsideForeignContent(e8) {
    switch (this.insertionMode) {
      case Rc.INITIAL:
        Vc(this, e8);
        break;
      case Rc.BEFORE_HTML:
        !(function(e9, t2) {
          const n2 = t2.tagID;
          n2 !== Po.HTML && n2 !== Po.HEAD && n2 !== Po.BODY && n2 !== Po.BR || Wc(e9, t2);
        })(this, e8);
        break;
      case Rc.BEFORE_HEAD:
        !(function(e9, t2) {
          const n2 = t2.tagID;
          n2 === Po.HEAD || n2 === Po.BODY || n2 === Po.HTML || n2 === Po.BR ? Qc(e9, t2) : e9._err(t2, Eo.endTagWithoutMatchingOpenElement);
        })(this, e8);
        break;
      case Rc.IN_HEAD:
        !(function(e9, t2) {
          switch (t2.tagID) {
            case Po.HEAD:
              e9.openElements.pop(), e9.insertionMode = Rc.AFTER_HEAD;
              break;
            case Po.BODY:
            case Po.BR:
            case Po.HTML:
              zc(e9, t2);
              break;
            case Po.TEMPLATE:
              $c(e9, t2);
              break;
            default:
              e9._err(t2, Eo.endTagWithoutMatchingOpenElement);
          }
        })(this, e8);
        break;
      case Rc.IN_HEAD_NO_SCRIPT:
        !(function(e9, t2) {
          switch (t2.tagID) {
            case Po.NOSCRIPT:
              e9.openElements.pop(), e9.insertionMode = Rc.IN_HEAD;
              break;
            case Po.BR:
              Jc(e9, t2);
              break;
            default:
              e9._err(t2, Eo.endTagWithoutMatchingOpenElement);
          }
        })(this, e8);
        break;
      case Rc.AFTER_HEAD:
        !(function(e9, t2) {
          switch (t2.tagID) {
            case Po.BODY:
            case Po.HTML:
            case Po.BR:
              Zc(e9, t2);
              break;
            case Po.TEMPLATE:
              $c(e9, t2);
              break;
            default:
              e9._err(t2, Eo.endTagWithoutMatchingOpenElement);
          }
        })(this, e8);
        break;
      case Rc.IN_BODY:
        ll(this, e8);
        break;
      case Rc.TEXT:
        !(function(e9, t2) {
          var n2;
          t2.tagID === Po.SCRIPT && (null === (n2 = e9.scriptHandler) || void 0 === n2 || n2.call(e9, e9.openElements.current));
          e9.openElements.pop(), e9.insertionMode = e9.originalInsertionMode;
        })(this, e8);
        break;
      case Rc.IN_TABLE:
        fl(this, e8);
        break;
      case Rc.IN_TABLE_TEXT:
        Tl(this, e8);
        break;
      case Rc.IN_CAPTION:
        !(function(e9, t2) {
          const n2 = t2.tagID;
          switch (n2) {
            case Po.CAPTION:
            case Po.TABLE:
              e9.openElements.hasInTableScope(Po.CAPTION) && (e9.openElements.generateImpliedEndTags(), e9.openElements.popUntilTagNamePopped(Po.CAPTION), e9.activeFormattingElements.clearToLastMarker(), e9.insertionMode = Rc.IN_TABLE, n2 === Po.TABLE && fl(e9, t2));
              break;
            case Po.BODY:
            case Po.COL:
            case Po.COLGROUP:
            case Po.HTML:
            case Po.TBODY:
            case Po.TD:
            case Po.TFOOT:
            case Po.TH:
            case Po.THEAD:
            case Po.TR:
              break;
            default:
              ll(e9, t2);
          }
        })(this, e8);
        break;
      case Rc.IN_COLUMN_GROUP:
        !(function(e9, t2) {
          switch (t2.tagID) {
            case Po.COLGROUP:
              e9.openElements.currentTagId === Po.COLGROUP && (e9.openElements.pop(), e9.insertionMode = Rc.IN_TABLE);
              break;
            case Po.TEMPLATE:
              $c(e9, t2);
              break;
            case Po.COL:
              break;
            default:
              gl(e9, t2);
          }
        })(this, e8);
        break;
      case Rc.IN_TABLE_BODY:
        Cl(this, e8);
        break;
      case Rc.IN_ROW:
        Sl(this, e8);
        break;
      case Rc.IN_CELL:
        !(function(e9, t2) {
          const n2 = t2.tagID;
          switch (n2) {
            case Po.TD:
            case Po.TH:
              e9.openElements.hasInTableScope(n2) && (e9.openElements.generateImpliedEndTags(), e9.openElements.popUntilTagNamePopped(n2), e9.activeFormattingElements.clearToLastMarker(), e9.insertionMode = Rc.IN_ROW);
              break;
            case Po.TABLE:
            case Po.TBODY:
            case Po.TFOOT:
            case Po.THEAD:
            case Po.TR:
              e9.openElements.hasInTableScope(n2) && (e9._closeTableCell(), Sl(e9, t2));
              break;
            case Po.BODY:
            case Po.CAPTION:
            case Po.COL:
            case Po.COLGROUP:
            case Po.HTML:
              break;
            default:
              ll(e9, t2);
          }
        })(this, e8);
        break;
      case Rc.IN_SELECT:
        Ol(this, e8);
        break;
      case Rc.IN_SELECT_IN_TABLE:
        !(function(e9, t2) {
          const n2 = t2.tagID;
          n2 === Po.CAPTION || n2 === Po.TABLE || n2 === Po.TBODY || n2 === Po.TFOOT || n2 === Po.THEAD || n2 === Po.TR || n2 === Po.TD || n2 === Po.TH ? e9.openElements.hasInTableScope(n2) && (e9.openElements.popUntilTagNamePopped(Po.SELECT), e9._resetInsertionMode(), e9.onEndTag(t2)) : Ol(e9, t2);
        })(this, e8);
        break;
      case Rc.IN_TEMPLATE:
        !(function(e9, t2) {
          t2.tagID === Po.TEMPLATE && $c(e9, t2);
        })(this, e8);
        break;
      case Rc.AFTER_BODY:
        Ll(this, e8);
        break;
      case Rc.IN_FRAMESET:
        !(function(e9, t2) {
          t2.tagID !== Po.FRAMESET || e9.openElements.isRootHtmlElementCurrent() || (e9.openElements.pop(), e9.fragmentContext || e9.openElements.currentTagId === Po.FRAMESET || (e9.insertionMode = Rc.AFTER_FRAMESET));
        })(this, e8);
        break;
      case Rc.AFTER_FRAMESET:
        !(function(e9, t2) {
          t2.tagID === Po.HTML && (e9.insertionMode = Rc.AFTER_AFTER_FRAMESET);
        })(this, e8);
        break;
      case Rc.AFTER_AFTER_BODY:
        vl(this, e8);
    }
  }
  onEof(e8) {
    switch (this.insertionMode) {
      case Rc.INITIAL:
        Vc(this, e8);
        break;
      case Rc.BEFORE_HTML:
        Wc(this, e8);
        break;
      case Rc.BEFORE_HEAD:
        Qc(this, e8);
        break;
      case Rc.IN_HEAD:
        zc(this, e8);
        break;
      case Rc.IN_HEAD_NO_SCRIPT:
        Jc(this, e8);
        break;
      case Rc.AFTER_HEAD:
        Zc(this, e8);
        break;
      case Rc.IN_BODY:
      case Rc.IN_TABLE:
      case Rc.IN_CAPTION:
      case Rc.IN_COLUMN_GROUP:
      case Rc.IN_TABLE_BODY:
      case Rc.IN_ROW:
      case Rc.IN_CELL:
      case Rc.IN_SELECT:
      case Rc.IN_SELECT_IN_TABLE:
        hl(this, e8);
        break;
      case Rc.TEXT:
        !(function(e9, t2) {
          e9._err(t2, Eo.eofInElementThatCanContainOnlyText), e9.openElements.pop(), e9.insertionMode = e9.originalInsertionMode, e9.onEof(t2);
        })(this, e8);
        break;
      case Rc.IN_TABLE_TEXT:
        Tl(this, e8);
        break;
      case Rc.IN_TEMPLATE:
        yl(this, e8);
        break;
      case Rc.AFTER_BODY:
      case Rc.IN_FRAMESET:
      case Rc.AFTER_FRAMESET:
      case Rc.AFTER_AFTER_BODY:
      case Rc.AFTER_AFTER_FRAMESET:
        jc(this, e8);
    }
  }
  onWhitespaceCharacter(e8) {
    if (this.skipNextNewLine && (this.skipNextNewLine = false, e8.chars.charCodeAt(0) === io.LINE_FEED)) {
      if (1 === e8.chars.length) return;
      e8.chars = e8.chars.substr(1);
    }
    if (this.tokenizer.inForeignNode) this._insertCharacters(e8);
    else switch (this.insertionMode) {
      case Rc.IN_HEAD:
      case Rc.IN_HEAD_NO_SCRIPT:
      case Rc.AFTER_HEAD:
      case Rc.TEXT:
      case Rc.IN_COLUMN_GROUP:
      case Rc.IN_SELECT:
      case Rc.IN_SELECT_IN_TABLE:
      case Rc.IN_FRAMESET:
      case Rc.AFTER_FRAMESET:
        this._insertCharacters(e8);
        break;
      case Rc.IN_BODY:
      case Rc.IN_CAPTION:
      case Rc.IN_CELL:
      case Rc.IN_TEMPLATE:
      case Rc.AFTER_BODY:
      case Rc.AFTER_AFTER_BODY:
      case Rc.AFTER_AFTER_FRAMESET:
        tl(this, e8);
        break;
      case Rc.IN_TABLE:
      case Rc.IN_TABLE_BODY:
      case Rc.IN_ROW:
        ul(this, e8);
        break;
      case Rc.IN_TABLE_TEXT:
        El(this, e8);
    }
  }
}
function Bc(e8, t2) {
  let n2 = e8.activeFormattingElements.getElementEntryInScopeWithTagName(t2.tagName);
  return n2 ? e8.openElements.contains(n2.element) ? e8.openElements.hasInScope(t2.tagID) || (n2 = null) : (e8.activeFormattingElements.removeEntry(n2), n2 = null) : cl(e8, t2), n2;
}
function Fc(e8, t2) {
  let n2 = null, r2 = e8.openElements.stackTop;
  for (; r2 >= 0; r2--) {
    const i2 = e8.openElements.items[r2];
    if (i2 === t2.element) break;
    e8._isSpecialElement(i2, e8.openElements.tagIDs[r2]) && (n2 = i2);
  }
  return n2 || (e8.openElements.shortenToLength(r2 < 0 ? 0 : r2), e8.activeFormattingElements.removeEntry(t2)), n2;
}
function Uc(e8, t2, n2) {
  let r2 = t2, i2 = e8.openElements.getCommonAncestor(t2);
  for (let s2 = 0, a2 = i2; a2 !== n2; s2++, a2 = i2) {
    i2 = e8.openElements.getCommonAncestor(a2);
    const n3 = e8.activeFormattingElements.getElementEntry(a2), o2 = n3 && s2 >= 3;
    !n3 || o2 ? (o2 && e8.activeFormattingElements.removeEntry(n3), e8.openElements.remove(a2)) : (a2 = Hc(e8, n3), r2 === t2 && (e8.activeFormattingElements.bookmark = n3), e8.treeAdapter.detachNode(r2), e8.treeAdapter.appendChild(a2, r2), r2 = a2);
  }
  return r2;
}
function Hc(e8, t2) {
  const n2 = e8.treeAdapter.getNamespaceURI(t2.element), r2 = e8.treeAdapter.createElement(t2.token.tagName, n2, t2.token.attrs);
  return e8.openElements.replace(t2.element, r2), t2.element = r2, r2;
}
function Gc(e8, t2, n2) {
  const r2 = Fo(e8.treeAdapter.getTagName(t2));
  if (e8._isElementCausesFosterParenting(r2)) e8._fosterParentElement(n2);
  else {
    const i2 = e8.treeAdapter.getNamespaceURI(t2);
    r2 === Po.TEMPLATE && i2 === vo.HTML && (t2 = e8.treeAdapter.getTemplateContent(t2)), e8.treeAdapter.appendChild(t2, n2);
  }
}
function Yc(e8, t2, n2) {
  const r2 = e8.treeAdapter.getNamespaceURI(n2.element), { token: i2 } = n2, s2 = e8.treeAdapter.createElement(i2.tagName, r2, i2.attrs);
  e8._adoptNodes(t2, s2), e8.treeAdapter.appendChild(t2, s2), e8.activeFormattingElements.insertElementAfterBookmark(s2, i2), e8.activeFormattingElements.removeEntry(n2), e8.openElements.remove(n2.element), e8.openElements.insertAfter(t2, s2, i2.tagID);
}
function qc(e8, t2) {
  for (let n2 = 0; n2 < 8; n2++) {
    const n3 = Bc(e8, t2);
    if (!n3) break;
    const r2 = Fc(e8, n3);
    if (!r2) break;
    e8.activeFormattingElements.bookmark = n3;
    const i2 = Uc(e8, r2, n3.element), s2 = e8.openElements.getCommonAncestor(n3.element);
    e8.treeAdapter.detachNode(i2), s2 && Gc(e8, s2, i2), Yc(e8, r2, n3);
  }
}
function Kc(e8, t2) {
  e8._appendCommentNode(t2, e8.openElements.currentTmplContentOrNode);
}
function jc(e8, t2) {
  if (e8.stopped = true, t2.location) {
    const n2 = e8.fragmentContext ? 0 : 2;
    for (let r2 = e8.openElements.stackTop; r2 >= n2; r2--) e8._setEndLocation(e8.openElements.items[r2], t2);
    if (!e8.fragmentContext && e8.openElements.stackTop >= 0) {
      const n3 = e8.openElements.items[0], r2 = e8.treeAdapter.getNodeSourceCodeLocation(n3);
      if (r2 && !r2.endTag && (e8._setEndLocation(n3, t2), e8.openElements.stackTop >= 1)) {
        const n4 = e8.openElements.items[1], r3 = e8.treeAdapter.getNodeSourceCodeLocation(n4);
        r3 && !r3.endTag && e8._setEndLocation(n4, t2);
      }
    }
  }
}
function Vc(e8, t2) {
  e8._err(t2, Eo.missingDoctype, true), e8.treeAdapter.setDocumentMode(e8.document, Ro.QUIRKS), e8.insertionMode = Rc.BEFORE_HTML, e8._processToken(t2);
}
function Wc(e8, t2) {
  e8._insertFakeRootElement(), e8.insertionMode = Rc.BEFORE_HEAD, e8._processToken(t2);
}
function Qc(e8, t2) {
  e8._insertFakeElement(Mo.HEAD, Po.HEAD), e8.headElement = e8.openElements.current, e8.insertionMode = Rc.IN_HEAD, e8._processToken(t2);
}
function Xc(e8, t2) {
  switch (t2.tagID) {
    case Po.HTML:
      ol(e8, t2);
      break;
    case Po.BASE:
    case Po.BASEFONT:
    case Po.BGSOUND:
    case Po.LINK:
    case Po.META:
      e8._appendElement(t2, vo.HTML), t2.ackSelfClosing = true;
      break;
    case Po.TITLE:
      e8._switchToTextParsing(t2, jo.RCDATA);
      break;
    case Po.NOSCRIPT:
      e8.options.scriptingEnabled ? e8._switchToTextParsing(t2, jo.RAWTEXT) : (e8._insertElement(t2, vo.HTML), e8.insertionMode = Rc.IN_HEAD_NO_SCRIPT);
      break;
    case Po.NOFRAMES:
    case Po.STYLE:
      e8._switchToTextParsing(t2, jo.RAWTEXT);
      break;
    case Po.SCRIPT:
      e8._switchToTextParsing(t2, jo.SCRIPT_DATA);
      break;
    case Po.TEMPLATE:
      e8._insertTemplate(t2), e8.activeFormattingElements.insertMarker(), e8.framesetOk = false, e8.insertionMode = Rc.IN_TEMPLATE, e8.tmplInsertionModeStack.unshift(Rc.IN_TEMPLATE);
      break;
    case Po.HEAD:
      e8._err(t2, Eo.misplacedStartTagForHeadElement);
      break;
    default:
      zc(e8, t2);
  }
}
function $c(e8, t2) {
  e8.openElements.tmplCount > 0 ? (e8.openElements.generateImpliedEndTagsThoroughly(), e8.openElements.currentTagId !== Po.TEMPLATE && e8._err(t2, Eo.closingOfElementWithOpenChildElements), e8.openElements.popUntilTagNamePopped(Po.TEMPLATE), e8.activeFormattingElements.clearToLastMarker(), e8.tmplInsertionModeStack.shift(), e8._resetInsertionMode()) : e8._err(t2, Eo.endTagWithoutMatchingOpenElement);
}
function zc(e8, t2) {
  e8.openElements.pop(), e8.insertionMode = Rc.AFTER_HEAD, e8._processToken(t2);
}
function Jc(e8, t2) {
  const n2 = t2.type === To.EOF ? Eo.openElementsLeftAfterEof : Eo.disallowedContentInNoscriptInHead;
  e8._err(t2, n2), e8.openElements.pop(), e8.insertionMode = Rc.IN_HEAD, e8._processToken(t2);
}
function Zc(e8, t2) {
  e8._insertFakeElement(Mo.BODY, Po.BODY), e8.insertionMode = Rc.IN_BODY, el(e8, t2);
}
function el(e8, t2) {
  switch (t2.type) {
    case To.CHARACTER:
      nl(e8, t2);
      break;
    case To.WHITESPACE_CHARACTER:
      tl(e8, t2);
      break;
    case To.COMMENT:
      Kc(e8, t2);
      break;
    case To.START_TAG:
      ol(e8, t2);
      break;
    case To.END_TAG:
      ll(e8, t2);
      break;
    case To.EOF:
      hl(e8, t2);
  }
}
function tl(e8, t2) {
  e8._reconstructActiveFormattingElements(), e8._insertCharacters(t2);
}
function nl(e8, t2) {
  e8._reconstructActiveFormattingElements(), e8._insertCharacters(t2), e8.framesetOk = false;
}
function rl(e8, t2) {
  e8._reconstructActiveFormattingElements(), e8._appendElement(t2, vo.HTML), e8.framesetOk = false, t2.ackSelfClosing = true;
}
function il(e8) {
  const t2 = _o(e8, Do.TYPE);
  return null != t2 && "hidden" === t2.toLowerCase();
}
function sl(e8, t2) {
  e8._switchToTextParsing(t2, jo.RAWTEXT);
}
function al(e8, t2) {
  e8._reconstructActiveFormattingElements(), e8._insertElement(t2, vo.HTML);
}
function ol(e8, t2) {
  switch (t2.tagID) {
    case Po.I:
    case Po.S:
    case Po.B:
    case Po.U:
    case Po.EM:
    case Po.TT:
    case Po.BIG:
    case Po.CODE:
    case Po.FONT:
    case Po.SMALL:
    case Po.STRIKE:
    case Po.STRONG:
      !(function(e9, t3) {
        e9._reconstructActiveFormattingElements(), e9._insertElement(t3, vo.HTML), e9.activeFormattingElements.pushElement(e9.openElements.current, t3);
      })(e8, t2);
      break;
    case Po.A:
      !(function(e9, t3) {
        const n2 = e9.activeFormattingElements.getElementEntryInScopeWithTagName(Mo.A);
        n2 && (qc(e9, t3), e9.openElements.remove(n2.element), e9.activeFormattingElements.removeEntry(n2)), e9._reconstructActiveFormattingElements(), e9._insertElement(t3, vo.HTML), e9.activeFormattingElements.pushElement(e9.openElements.current, t3);
      })(e8, t2);
      break;
    case Po.H1:
    case Po.H2:
    case Po.H3:
    case Po.H4:
    case Po.H5:
    case Po.H6:
      !(function(e9, t3) {
        e9.openElements.hasInButtonScope(Po.P) && e9._closePElement(), Go(e9.openElements.currentTagId) && e9.openElements.pop(), e9._insertElement(t3, vo.HTML);
      })(e8, t2);
      break;
    case Po.P:
    case Po.DL:
    case Po.OL:
    case Po.UL:
    case Po.DIV:
    case Po.DIR:
    case Po.NAV:
    case Po.MAIN:
    case Po.MENU:
    case Po.ASIDE:
    case Po.CENTER:
    case Po.FIGURE:
    case Po.FOOTER:
    case Po.HEADER:
    case Po.HGROUP:
    case Po.DIALOG:
    case Po.DETAILS:
    case Po.ADDRESS:
    case Po.ARTICLE:
    case Po.SECTION:
    case Po.SUMMARY:
    case Po.FIELDSET:
    case Po.BLOCKQUOTE:
    case Po.FIGCAPTION:
      !(function(e9, t3) {
        e9.openElements.hasInButtonScope(Po.P) && e9._closePElement(), e9._insertElement(t3, vo.HTML);
      })(e8, t2);
      break;
    case Po.LI:
    case Po.DD:
    case Po.DT:
      !(function(e9, t3) {
        e9.framesetOk = false;
        const n2 = t3.tagID;
        for (let t4 = e9.openElements.stackTop; t4 >= 0; t4--) {
          const r2 = e9.openElements.tagIDs[t4];
          if (n2 === Po.LI && r2 === Po.LI || (n2 === Po.DD || n2 === Po.DT) && (r2 === Po.DD || r2 === Po.DT)) {
            e9.openElements.generateImpliedEndTagsWithExclusion(r2), e9.openElements.popUntilTagNamePopped(r2);
            break;
          }
          if (r2 !== Po.ADDRESS && r2 !== Po.DIV && r2 !== Po.P && e9._isSpecialElement(e9.openElements.items[t4], r2)) break;
        }
        e9.openElements.hasInButtonScope(Po.P) && e9._closePElement(), e9._insertElement(t3, vo.HTML);
      })(e8, t2);
      break;
    case Po.BR:
    case Po.IMG:
    case Po.WBR:
    case Po.AREA:
    case Po.EMBED:
    case Po.KEYGEN:
      rl(e8, t2);
      break;
    case Po.HR:
      !(function(e9, t3) {
        e9.openElements.hasInButtonScope(Po.P) && e9._closePElement(), e9._appendElement(t3, vo.HTML), e9.framesetOk = false, t3.ackSelfClosing = true;
      })(e8, t2);
      break;
    case Po.RB:
    case Po.RTC:
      !(function(e9, t3) {
        e9.openElements.hasInScope(Po.RUBY) && e9.openElements.generateImpliedEndTags(), e9._insertElement(t3, vo.HTML);
      })(e8, t2);
      break;
    case Po.RT:
    case Po.RP:
      !(function(e9, t3) {
        e9.openElements.hasInScope(Po.RUBY) && e9.openElements.generateImpliedEndTagsWithExclusion(Po.RTC), e9._insertElement(t3, vo.HTML);
      })(e8, t2);
      break;
    case Po.PRE:
    case Po.LISTING:
      !(function(e9, t3) {
        e9.openElements.hasInButtonScope(Po.P) && e9._closePElement(), e9._insertElement(t3, vo.HTML), e9.skipNextNewLine = true, e9.framesetOk = false;
      })(e8, t2);
      break;
    case Po.XMP:
      !(function(e9, t3) {
        e9.openElements.hasInButtonScope(Po.P) && e9._closePElement(), e9._reconstructActiveFormattingElements(), e9.framesetOk = false, e9._switchToTextParsing(t3, jo.RAWTEXT);
      })(e8, t2);
      break;
    case Po.SVG:
      !(function(e9, t3) {
        e9._reconstructActiveFormattingElements(), kc(t3), vc(t3), t3.selfClosing ? e9._appendElement(t3, vo.SVG) : e9._insertElement(t3, vo.SVG), t3.ackSelfClosing = true;
      })(e8, t2);
      break;
    case Po.HTML:
      !(function(e9, t3) {
        0 === e9.openElements.tmplCount && e9.treeAdapter.adoptAttributes(e9.openElements.items[0], t3.attrs);
      })(e8, t2);
      break;
    case Po.BASE:
    case Po.LINK:
    case Po.META:
    case Po.STYLE:
    case Po.TITLE:
    case Po.SCRIPT:
    case Po.BGSOUND:
    case Po.BASEFONT:
    case Po.TEMPLATE:
      Xc(e8, t2);
      break;
    case Po.BODY:
      !(function(e9, t3) {
        const n2 = e9.openElements.tryPeekProperlyNestedBodyElement();
        n2 && 0 === e9.openElements.tmplCount && (e9.framesetOk = false, e9.treeAdapter.adoptAttributes(n2, t3.attrs));
      })(e8, t2);
      break;
    case Po.FORM:
      !(function(e9, t3) {
        const n2 = e9.openElements.tmplCount > 0;
        e9.formElement && !n2 || (e9.openElements.hasInButtonScope(Po.P) && e9._closePElement(), e9._insertElement(t3, vo.HTML), n2 || (e9.formElement = e9.openElements.current));
      })(e8, t2);
      break;
    case Po.NOBR:
      !(function(e9, t3) {
        e9._reconstructActiveFormattingElements(), e9.openElements.hasInScope(Po.NOBR) && (qc(e9, t3), e9._reconstructActiveFormattingElements()), e9._insertElement(t3, vo.HTML), e9.activeFormattingElements.pushElement(e9.openElements.current, t3);
      })(e8, t2);
      break;
    case Po.MATH:
      !(function(e9, t3) {
        e9._reconstructActiveFormattingElements(), Lc(t3), vc(t3), t3.selfClosing ? e9._appendElement(t3, vo.MATHML) : e9._insertElement(t3, vo.MATHML), t3.ackSelfClosing = true;
      })(e8, t2);
      break;
    case Po.TABLE:
      !(function(e9, t3) {
        e9.treeAdapter.getDocumentMode(e9.document) !== Ro.QUIRKS && e9.openElements.hasInButtonScope(Po.P) && e9._closePElement(), e9._insertElement(t3, vo.HTML), e9.framesetOk = false, e9.insertionMode = Rc.IN_TABLE;
      })(e8, t2);
      break;
    case Po.INPUT:
      !(function(e9, t3) {
        e9._reconstructActiveFormattingElements(), e9._appendElement(t3, vo.HTML), il(t3) || (e9.framesetOk = false), t3.ackSelfClosing = true;
      })(e8, t2);
      break;
    case Po.PARAM:
    case Po.TRACK:
    case Po.SOURCE:
      !(function(e9, t3) {
        e9._appendElement(t3, vo.HTML), t3.ackSelfClosing = true;
      })(e8, t2);
      break;
    case Po.IMAGE:
      !(function(e9, t3) {
        t3.tagName = Mo.IMG, t3.tagID = Po.IMG, rl(e9, t3);
      })(e8, t2);
      break;
    case Po.BUTTON:
      !(function(e9, t3) {
        e9.openElements.hasInScope(Po.BUTTON) && (e9.openElements.generateImpliedEndTags(), e9.openElements.popUntilTagNamePopped(Po.BUTTON)), e9._reconstructActiveFormattingElements(), e9._insertElement(t3, vo.HTML), e9.framesetOk = false;
      })(e8, t2);
      break;
    case Po.APPLET:
    case Po.OBJECT:
    case Po.MARQUEE:
      !(function(e9, t3) {
        e9._reconstructActiveFormattingElements(), e9._insertElement(t3, vo.HTML), e9.activeFormattingElements.insertMarker(), e9.framesetOk = false;
      })(e8, t2);
      break;
    case Po.IFRAME:
      !(function(e9, t3) {
        e9.framesetOk = false, e9._switchToTextParsing(t3, jo.RAWTEXT);
      })(e8, t2);
      break;
    case Po.SELECT:
      !(function(e9, t3) {
        e9._reconstructActiveFormattingElements(), e9._insertElement(t3, vo.HTML), e9.framesetOk = false, e9.insertionMode = e9.insertionMode === Rc.IN_TABLE || e9.insertionMode === Rc.IN_CAPTION || e9.insertionMode === Rc.IN_TABLE_BODY || e9.insertionMode === Rc.IN_ROW || e9.insertionMode === Rc.IN_CELL ? Rc.IN_SELECT_IN_TABLE : Rc.IN_SELECT;
      })(e8, t2);
      break;
    case Po.OPTION:
    case Po.OPTGROUP:
      !(function(e9, t3) {
        e9.openElements.currentTagId === Po.OPTION && e9.openElements.pop(), e9._reconstructActiveFormattingElements(), e9._insertElement(t3, vo.HTML);
      })(e8, t2);
      break;
    case Po.NOEMBED:
      sl(e8, t2);
      break;
    case Po.FRAMESET:
      !(function(e9, t3) {
        const n2 = e9.openElements.tryPeekProperlyNestedBodyElement();
        e9.framesetOk && n2 && (e9.treeAdapter.detachNode(n2), e9.openElements.popAllUpToHtmlElement(), e9._insertElement(t3, vo.HTML), e9.insertionMode = Rc.IN_FRAMESET);
      })(e8, t2);
      break;
    case Po.TEXTAREA:
      !(function(e9, t3) {
        e9._insertElement(t3, vo.HTML), e9.skipNextNewLine = true, e9.tokenizer.state = jo.RCDATA, e9.originalInsertionMode = e9.insertionMode, e9.framesetOk = false, e9.insertionMode = Rc.TEXT;
      })(e8, t2);
      break;
    case Po.NOSCRIPT:
      e8.options.scriptingEnabled ? sl(e8, t2) : al(e8, t2);
      break;
    case Po.PLAINTEXT:
      !(function(e9, t3) {
        e9.openElements.hasInButtonScope(Po.P) && e9._closePElement(), e9._insertElement(t3, vo.HTML), e9.tokenizer.state = jo.PLAINTEXT;
      })(e8, t2);
      break;
    case Po.COL:
    case Po.TH:
    case Po.TD:
    case Po.TR:
    case Po.HEAD:
    case Po.FRAME:
    case Po.TBODY:
    case Po.TFOOT:
    case Po.THEAD:
    case Po.CAPTION:
    case Po.COLGROUP:
      break;
    default:
      al(e8, t2);
  }
}
function cl(e8, t2) {
  const n2 = t2.tagName, r2 = t2.tagID;
  for (let t3 = e8.openElements.stackTop; t3 > 0; t3--) {
    const i2 = e8.openElements.items[t3], s2 = e8.openElements.tagIDs[t3];
    if (r2 === s2 && (r2 !== Po.UNKNOWN || e8.treeAdapter.getTagName(i2) === n2)) {
      e8.openElements.generateImpliedEndTagsWithExclusion(r2), e8.openElements.stackTop >= t3 && e8.openElements.shortenToLength(t3);
      break;
    }
    if (e8._isSpecialElement(i2, s2)) break;
  }
}
function ll(e8, t2) {
  switch (t2.tagID) {
    case Po.A:
    case Po.B:
    case Po.I:
    case Po.S:
    case Po.U:
    case Po.EM:
    case Po.TT:
    case Po.BIG:
    case Po.CODE:
    case Po.FONT:
    case Po.NOBR:
    case Po.SMALL:
    case Po.STRIKE:
    case Po.STRONG:
      qc(e8, t2);
      break;
    case Po.P:
      !(function(e9) {
        e9.openElements.hasInButtonScope(Po.P) || e9._insertFakeElement(Mo.P, Po.P), e9._closePElement();
      })(e8);
      break;
    case Po.DL:
    case Po.UL:
    case Po.OL:
    case Po.DIR:
    case Po.DIV:
    case Po.NAV:
    case Po.PRE:
    case Po.MAIN:
    case Po.MENU:
    case Po.ASIDE:
    case Po.BUTTON:
    case Po.CENTER:
    case Po.FIGURE:
    case Po.FOOTER:
    case Po.HEADER:
    case Po.HGROUP:
    case Po.DIALOG:
    case Po.ADDRESS:
    case Po.ARTICLE:
    case Po.DETAILS:
    case Po.SECTION:
    case Po.SUMMARY:
    case Po.LISTING:
    case Po.FIELDSET:
    case Po.BLOCKQUOTE:
    case Po.FIGCAPTION:
      !(function(e9, t3) {
        const n2 = t3.tagID;
        e9.openElements.hasInScope(n2) && (e9.openElements.generateImpliedEndTags(), e9.openElements.popUntilTagNamePopped(n2));
      })(e8, t2);
      break;
    case Po.LI:
      !(function(e9) {
        e9.openElements.hasInListItemScope(Po.LI) && (e9.openElements.generateImpliedEndTagsWithExclusion(Po.LI), e9.openElements.popUntilTagNamePopped(Po.LI));
      })(e8);
      break;
    case Po.DD:
    case Po.DT:
      !(function(e9, t3) {
        const n2 = t3.tagID;
        e9.openElements.hasInScope(n2) && (e9.openElements.generateImpliedEndTagsWithExclusion(n2), e9.openElements.popUntilTagNamePopped(n2));
      })(e8, t2);
      break;
    case Po.H1:
    case Po.H2:
    case Po.H3:
    case Po.H4:
    case Po.H5:
    case Po.H6:
      !(function(e9) {
        e9.openElements.hasNumberedHeaderInScope() && (e9.openElements.generateImpliedEndTags(), e9.openElements.popUntilNumberedHeaderPopped());
      })(e8);
      break;
    case Po.BR:
      !(function(e9) {
        e9._reconstructActiveFormattingElements(), e9._insertFakeElement(Mo.BR, Po.BR), e9.openElements.pop(), e9.framesetOk = false;
      })(e8);
      break;
    case Po.BODY:
      !(function(e9, t3) {
        if (e9.openElements.hasInScope(Po.BODY) && (e9.insertionMode = Rc.AFTER_BODY, e9.options.sourceCodeLocationInfo)) {
          const n2 = e9.openElements.tryPeekProperlyNestedBodyElement();
          n2 && e9._setEndLocation(n2, t3);
        }
      })(e8, t2);
      break;
    case Po.HTML:
      !(function(e9, t3) {
        e9.openElements.hasInScope(Po.BODY) && (e9.insertionMode = Rc.AFTER_BODY, Ll(e9, t3));
      })(e8, t2);
      break;
    case Po.FORM:
      !(function(e9) {
        const t3 = e9.openElements.tmplCount > 0, { formElement: n2 } = e9;
        t3 || (e9.formElement = null), (n2 || t3) && e9.openElements.hasInScope(Po.FORM) && (e9.openElements.generateImpliedEndTags(), t3 ? e9.openElements.popUntilTagNamePopped(Po.FORM) : n2 && e9.openElements.remove(n2));
      })(e8);
      break;
    case Po.APPLET:
    case Po.OBJECT:
    case Po.MARQUEE:
      !(function(e9, t3) {
        const n2 = t3.tagID;
        e9.openElements.hasInScope(n2) && (e9.openElements.generateImpliedEndTags(), e9.openElements.popUntilTagNamePopped(n2), e9.activeFormattingElements.clearToLastMarker());
      })(e8, t2);
      break;
    case Po.TEMPLATE:
      $c(e8, t2);
      break;
    default:
      cl(e8, t2);
  }
}
function hl(e8, t2) {
  e8.tmplInsertionModeStack.length > 0 ? yl(e8, t2) : jc(e8, t2);
}
function ul(e8, t2) {
  if (Pc.has(e8.openElements.currentTagId)) switch (e8.pendingCharacterTokens.length = 0, e8.hasNonWhitespacePendingCharacterToken = false, e8.originalInsertionMode = e8.insertionMode, e8.insertionMode = Rc.IN_TABLE_TEXT, t2.type) {
    case To.CHARACTER:
      ml(e8, t2);
      break;
    case To.WHITESPACE_CHARACTER:
      El(e8, t2);
  }
  else dl(e8, t2);
}
function pl(e8, t2) {
  switch (t2.tagID) {
    case Po.TD:
    case Po.TH:
    case Po.TR:
      !(function(e9, t3) {
        e9.openElements.clearBackToTableContext(), e9._insertFakeElement(Mo.TBODY, Po.TBODY), e9.insertionMode = Rc.IN_TABLE_BODY, Nl(e9, t3);
      })(e8, t2);
      break;
    case Po.STYLE:
    case Po.SCRIPT:
    case Po.TEMPLATE:
      Xc(e8, t2);
      break;
    case Po.COL:
      !(function(e9, t3) {
        e9.openElements.clearBackToTableContext(), e9._insertFakeElement(Mo.COLGROUP, Po.COLGROUP), e9.insertionMode = Rc.IN_COLUMN_GROUP, Al(e9, t3);
      })(e8, t2);
      break;
    case Po.FORM:
      !(function(e9, t3) {
        e9.formElement || 0 !== e9.openElements.tmplCount || (e9._insertElement(t3, vo.HTML), e9.formElement = e9.openElements.current, e9.openElements.pop());
      })(e8, t2);
      break;
    case Po.TABLE:
      !(function(e9, t3) {
        e9.openElements.hasInTableScope(Po.TABLE) && (e9.openElements.popUntilTagNamePopped(Po.TABLE), e9._resetInsertionMode(), e9._processStartTag(t3));
      })(e8, t2);
      break;
    case Po.TBODY:
    case Po.TFOOT:
    case Po.THEAD:
      !(function(e9, t3) {
        e9.openElements.clearBackToTableContext(), e9._insertElement(t3, vo.HTML), e9.insertionMode = Rc.IN_TABLE_BODY;
      })(e8, t2);
      break;
    case Po.INPUT:
      !(function(e9, t3) {
        il(t3) ? e9._appendElement(t3, vo.HTML) : dl(e9, t3), t3.ackSelfClosing = true;
      })(e8, t2);
      break;
    case Po.CAPTION:
      !(function(e9, t3) {
        e9.openElements.clearBackToTableContext(), e9.activeFormattingElements.insertMarker(), e9._insertElement(t3, vo.HTML), e9.insertionMode = Rc.IN_CAPTION;
      })(e8, t2);
      break;
    case Po.COLGROUP:
      !(function(e9, t3) {
        e9.openElements.clearBackToTableContext(), e9._insertElement(t3, vo.HTML), e9.insertionMode = Rc.IN_COLUMN_GROUP;
      })(e8, t2);
      break;
    default:
      dl(e8, t2);
  }
}
function fl(e8, t2) {
  switch (t2.tagID) {
    case Po.TABLE:
      e8.openElements.hasInTableScope(Po.TABLE) && (e8.openElements.popUntilTagNamePopped(Po.TABLE), e8._resetInsertionMode());
      break;
    case Po.TEMPLATE:
      $c(e8, t2);
      break;
    case Po.BODY:
    case Po.CAPTION:
    case Po.COL:
    case Po.COLGROUP:
    case Po.HTML:
    case Po.TBODY:
    case Po.TD:
    case Po.TFOOT:
    case Po.TH:
    case Po.THEAD:
    case Po.TR:
      break;
    default:
      dl(e8, t2);
  }
}
function dl(e8, t2) {
  const n2 = e8.fosterParentingEnabled;
  e8.fosterParentingEnabled = true, el(e8, t2), e8.fosterParentingEnabled = n2;
}
function El(e8, t2) {
  e8.pendingCharacterTokens.push(t2);
}
function ml(e8, t2) {
  e8.pendingCharacterTokens.push(t2), e8.hasNonWhitespacePendingCharacterToken = true;
}
function Tl(e8, t2) {
  let n2 = 0;
  if (e8.hasNonWhitespacePendingCharacterToken) for (; n2 < e8.pendingCharacterTokens.length; n2++) dl(e8, e8.pendingCharacterTokens[n2]);
  else for (; n2 < e8.pendingCharacterTokens.length; n2++) e8._insertCharacters(e8.pendingCharacterTokens[n2]);
  e8.insertionMode = e8.originalInsertionMode, e8._processToken(t2);
}
const _l = /* @__PURE__ */ new Set([Po.CAPTION, Po.COL, Po.COLGROUP, Po.TBODY, Po.TD, Po.TFOOT, Po.TH, Po.THEAD, Po.TR]);
function Al(e8, t2) {
  switch (t2.tagID) {
    case Po.HTML:
      ol(e8, t2);
      break;
    case Po.COL:
      e8._appendElement(t2, vo.HTML), t2.ackSelfClosing = true;
      break;
    case Po.TEMPLATE:
      Xc(e8, t2);
      break;
    default:
      gl(e8, t2);
  }
}
function gl(e8, t2) {
  e8.openElements.currentTagId === Po.COLGROUP && (e8.openElements.pop(), e8.insertionMode = Rc.IN_TABLE, e8._processToken(t2));
}
function Nl(e8, t2) {
  switch (t2.tagID) {
    case Po.TR:
      e8.openElements.clearBackToTableBodyContext(), e8._insertElement(t2, vo.HTML), e8.insertionMode = Rc.IN_ROW;
      break;
    case Po.TH:
    case Po.TD:
      e8.openElements.clearBackToTableBodyContext(), e8._insertFakeElement(Mo.TR, Po.TR), e8.insertionMode = Rc.IN_ROW, Il(e8, t2);
      break;
    case Po.CAPTION:
    case Po.COL:
    case Po.COLGROUP:
    case Po.TBODY:
    case Po.TFOOT:
    case Po.THEAD:
      e8.openElements.hasTableBodyContextInTableScope() && (e8.openElements.clearBackToTableBodyContext(), e8.openElements.pop(), e8.insertionMode = Rc.IN_TABLE, pl(e8, t2));
      break;
    default:
      pl(e8, t2);
  }
}
function Cl(e8, t2) {
  const n2 = t2.tagID;
  switch (t2.tagID) {
    case Po.TBODY:
    case Po.TFOOT:
    case Po.THEAD:
      e8.openElements.hasInTableScope(n2) && (e8.openElements.clearBackToTableBodyContext(), e8.openElements.pop(), e8.insertionMode = Rc.IN_TABLE);
      break;
    case Po.TABLE:
      e8.openElements.hasTableBodyContextInTableScope() && (e8.openElements.clearBackToTableBodyContext(), e8.openElements.pop(), e8.insertionMode = Rc.IN_TABLE, fl(e8, t2));
      break;
    case Po.BODY:
    case Po.CAPTION:
    case Po.COL:
    case Po.COLGROUP:
    case Po.HTML:
    case Po.TD:
    case Po.TH:
    case Po.TR:
      break;
    default:
      fl(e8, t2);
  }
}
function Il(e8, t2) {
  switch (t2.tagID) {
    case Po.TH:
    case Po.TD:
      e8.openElements.clearBackToTableRowContext(), e8._insertElement(t2, vo.HTML), e8.insertionMode = Rc.IN_CELL, e8.activeFormattingElements.insertMarker();
      break;
    case Po.CAPTION:
    case Po.COL:
    case Po.COLGROUP:
    case Po.TBODY:
    case Po.TFOOT:
    case Po.THEAD:
    case Po.TR:
      e8.openElements.hasInTableScope(Po.TR) && (e8.openElements.clearBackToTableRowContext(), e8.openElements.pop(), e8.insertionMode = Rc.IN_TABLE_BODY, Nl(e8, t2));
      break;
    default:
      pl(e8, t2);
  }
}
function Sl(e8, t2) {
  switch (t2.tagID) {
    case Po.TR:
      e8.openElements.hasInTableScope(Po.TR) && (e8.openElements.clearBackToTableRowContext(), e8.openElements.pop(), e8.insertionMode = Rc.IN_TABLE_BODY);
      break;
    case Po.TABLE:
      e8.openElements.hasInTableScope(Po.TR) && (e8.openElements.clearBackToTableRowContext(), e8.openElements.pop(), e8.insertionMode = Rc.IN_TABLE_BODY, Cl(e8, t2));
      break;
    case Po.TBODY:
    case Po.TFOOT:
    case Po.THEAD:
      (e8.openElements.hasInTableScope(t2.tagID) || e8.openElements.hasInTableScope(Po.TR)) && (e8.openElements.clearBackToTableRowContext(), e8.openElements.pop(), e8.insertionMode = Rc.IN_TABLE_BODY, Cl(e8, t2));
      break;
    case Po.BODY:
    case Po.CAPTION:
    case Po.COL:
    case Po.COLGROUP:
    case Po.HTML:
    case Po.TD:
    case Po.TH:
      break;
    default:
      fl(e8, t2);
  }
}
function bl(e8, t2) {
  switch (t2.tagID) {
    case Po.HTML:
      ol(e8, t2);
      break;
    case Po.OPTION:
      e8.openElements.currentTagId === Po.OPTION && e8.openElements.pop(), e8._insertElement(t2, vo.HTML);
      break;
    case Po.OPTGROUP:
      e8.openElements.currentTagId === Po.OPTION && e8.openElements.pop(), e8.openElements.currentTagId === Po.OPTGROUP && e8.openElements.pop(), e8._insertElement(t2, vo.HTML);
      break;
    case Po.INPUT:
    case Po.KEYGEN:
    case Po.TEXTAREA:
    case Po.SELECT:
      e8.openElements.hasInSelectScope(Po.SELECT) && (e8.openElements.popUntilTagNamePopped(Po.SELECT), e8._resetInsertionMode(), t2.tagID !== Po.SELECT && e8._processStartTag(t2));
      break;
    case Po.SCRIPT:
    case Po.TEMPLATE:
      Xc(e8, t2);
  }
}
function Ol(e8, t2) {
  switch (t2.tagID) {
    case Po.OPTGROUP:
      e8.openElements.stackTop > 0 && e8.openElements.currentTagId === Po.OPTION && e8.openElements.tagIDs[e8.openElements.stackTop - 1] === Po.OPTGROUP && e8.openElements.pop(), e8.openElements.currentTagId === Po.OPTGROUP && e8.openElements.pop();
      break;
    case Po.OPTION:
      e8.openElements.currentTagId === Po.OPTION && e8.openElements.pop();
      break;
    case Po.SELECT:
      e8.openElements.hasInSelectScope(Po.SELECT) && (e8.openElements.popUntilTagNamePopped(Po.SELECT), e8._resetInsertionMode());
      break;
    case Po.TEMPLATE:
      $c(e8, t2);
  }
}
function yl(e8, t2) {
  e8.openElements.tmplCount > 0 ? (e8.openElements.popUntilTagNamePopped(Po.TEMPLATE), e8.activeFormattingElements.clearToLastMarker(), e8.tmplInsertionModeStack.shift(), e8._resetInsertionMode(), e8.onEof(t2)) : jc(e8, t2);
}
function Ll(e8, t2) {
  var n2;
  if (t2.tagID === Po.HTML) {
    if (e8.fragmentContext || (e8.insertionMode = Rc.AFTER_AFTER_BODY), e8.options.sourceCodeLocationInfo && e8.openElements.tagIDs[0] === Po.HTML) {
      e8._setEndLocation(e8.openElements.items[0], t2);
      const r2 = e8.openElements.items[1];
      r2 && !(null === (n2 = e8.treeAdapter.getNodeSourceCodeLocation(r2)) || void 0 === n2 ? void 0 : n2.endTag) && e8._setEndLocation(r2, t2);
    }
  } else kl(e8, t2);
}
function kl(e8, t2) {
  e8.insertionMode = Rc.IN_BODY, el(e8, t2);
}
function vl(e8, t2) {
  e8.insertionMode = Rc.IN_BODY, el(e8, t2);
}
function Dl(e8) {
  for (; e8.treeAdapter.getNamespaceURI(e8.openElements.current) !== vo.HTML && !e8._isIntegrationPoint(e8.openElements.currentTagId, e8.openElements.current); ) e8.openElements.pop();
}
var Rl = Ke((function(e8, t2) {
  Object.defineProperty(t2, "__esModule", { value: true }), t2.escapeText = t2.escapeAttribute = t2.escapeUTF8 = t2.escape = t2.encodeXML = t2.getCodePoint = t2.xmlReplacer = void 0, t2.xmlReplacer = /["&'<>$\x80-\uFFFF]/g;
  var n2 = /* @__PURE__ */ new Map([[34, "&quot;"], [38, "&amp;"], [39, "&apos;"], [60, "&lt;"], [62, "&gt;"]]);
  function r2(e9) {
    for (var r3, i3 = "", s2 = 0; null !== (r3 = t2.xmlReplacer.exec(e9)); ) {
      var a2 = r3.index, o2 = e9.charCodeAt(a2), c2 = n2.get(o2);
      void 0 !== c2 ? (i3 += e9.substring(s2, a2) + c2, s2 = a2 + 1) : (i3 += "".concat(e9.substring(s2, a2), "&#x").concat((0, t2.getCodePoint)(e9, a2).toString(16), ";"), s2 = t2.xmlReplacer.lastIndex += Number(55296 == (64512 & o2)));
    }
    return i3 + e9.substr(s2);
  }
  function i2(e9, t3) {
    return function(n3) {
      for (var r3, i3 = 0, s2 = ""; r3 = e9.exec(n3); ) i3 !== r3.index && (s2 += n3.substring(i3, r3.index)), s2 += t3.get(r3[0].charCodeAt(0)), i3 = r3.index + 1;
      return s2 + n3.substring(i3);
    };
  }
  t2.getCodePoint = null != String.prototype.codePointAt ? function(e9, t3) {
    return e9.codePointAt(t3);
  } : function(e9, t3) {
    return 55296 == (64512 & e9.charCodeAt(t3)) ? 1024 * (e9.charCodeAt(t3) - 55296) + e9.charCodeAt(t3 + 1) - 56320 + 65536 : e9.charCodeAt(t3);
  }, t2.encodeXML = r2, t2.escape = r2, t2.escapeUTF8 = i2(/[&<>'"]/g, n2), t2.escapeAttribute = i2(/["&\u00A0]/g, /* @__PURE__ */ new Map([[34, "&quot;"], [38, "&amp;"], [160, "&nbsp;"]])), t2.escapeText = i2(/[&<>\u00A0]/g, /* @__PURE__ */ new Map([[38, "&amp;"], [60, "&lt;"], [62, "&gt;"], [160, "&nbsp;"]]));
}));
qe(Rl);
var Ml = Rl.escapeText, Pl = Rl.escapeAttribute;
Rl.escapeUTF8, Rl.escape, Rl.encodeXML, Rl.getCodePoint, Rl.xmlReplacer;
const xl = /* @__PURE__ */ new Set([Mo.AREA, Mo.BASE, Mo.BASEFONT, Mo.BGSOUND, Mo.BR, Mo.COL, Mo.EMBED, Mo.FRAME, Mo.HR, Mo.IMG, Mo.INPUT, Mo.KEYGEN, Mo.LINK, Mo.META, Mo.PARAM, Mo.SOURCE, Mo.TRACK, Mo.WBR]);
const wl = { treeAdapter: Ec, scriptingEnabled: true };
function Bl(e8, t2) {
  return Fl(e8, { ...wl, ...t2 });
}
function Fl(e8, t2) {
  return t2.treeAdapter.isElementNode(e8) ? (function(e9, t3) {
    const n2 = t3.treeAdapter.getTagName(e9);
    return `<${n2}${(function(e10, { treeAdapter: t4 }) {
      let n3 = "";
      for (const r2 of t4.getAttrList(e10)) {
        if (n3 += " ", r2.namespace) switch (r2.namespace) {
          case vo.XML:
            n3 += `xml:${r2.name}`;
            break;
          case vo.XMLNS:
            "xmlns" !== r2.name && (n3 += "xmlns:"), n3 += r2.name;
            break;
          case vo.XLINK:
            n3 += `xlink:${r2.name}`;
            break;
          default:
            n3 += `${r2.prefix}:${r2.name}`;
        }
        else n3 += r2.name;
        n3 += `="${Pl(r2.value)}"`;
      }
      return n3;
    })(e9, t3)}>${(function(e10, t4) {
      return t4.treeAdapter.isElementNode(e10) && t4.treeAdapter.getNamespaceURI(e10) === vo.HTML && xl.has(t4.treeAdapter.getTagName(e10));
    })(e9, t3) ? "" : `${(function(e10, t4) {
      let n3 = "";
      const r2 = t4.treeAdapter.isElementNode(e10) && t4.treeAdapter.getTagName(e10) === Mo.TEMPLATE && t4.treeAdapter.getNamespaceURI(e10) === vo.HTML ? t4.treeAdapter.getTemplateContent(e10) : e10, i2 = t4.treeAdapter.getChildNodes(r2);
      if (i2) for (const e11 of i2) n3 += Fl(e11, t4);
      return n3;
    })(e9, t3)}</${n2}>`}`;
  })(e8, t2) : t2.treeAdapter.isTextNode(e8) ? (function(e9, t3) {
    const { treeAdapter: n2 } = t3, r2 = n2.getTextNodeContent(e9), i2 = n2.getParentNode(e9), s2 = i2 && n2.isElementNode(i2) && n2.getTagName(i2);
    return s2 && n2.getNamespaceURI(i2) === vo.HTML && (a2 = s2, o2 = t3.scriptingEnabled, Yo.has(a2) || o2 && a2 === Mo.NOSCRIPT) ? r2 : Ml(r2);
    var a2, o2;
  })(e8, t2) : t2.treeAdapter.isCommentNode(e8) ? (function(e9, { treeAdapter: t3 }) {
    return `<!--${t3.getCommentNodeContent(e9)}-->`;
  })(e8, t2) : t2.treeAdapter.isDocumentTypeNode(e8) ? (function(e9, { treeAdapter: t3 }) {
    return `<!DOCTYPE ${t3.getDocumentTypeNodeName(e9)}>`;
  })(e8, t2) : "";
}
function Ul(e8) {
  return new m(e8);
}
function Hl(e8) {
  const t2 = e8.includes('"') ? "'" : '"';
  return t2 + e8 + t2;
}
const Gl = { isCommentNode: O, isElementNode: I, isTextNode: b, createDocument() {
  const e8 = new N([]);
  return e8["x-mode"] = Ro.NO_QUIRKS, e8;
}, createDocumentFragment: () => new N([]), createElement(e8, t2, n2) {
  const r2 = /* @__PURE__ */ Object.create(null), i2 = /* @__PURE__ */ Object.create(null), s2 = /* @__PURE__ */ Object.create(null);
  for (let e9 = 0; e9 < n2.length; e9++) {
    const t3 = n2[e9].name;
    r2[t3] = n2[e9].value, i2[t3] = n2[e9].namespace, s2[t3] = n2[e9].prefix;
  }
  const a2 = new C(e8, r2, []);
  return a2.namespace = t2, a2["x-attribsNamespace"] = i2, a2["x-attribsPrefix"] = s2, a2;
}, createCommentNode: (e8) => new T(e8), appendChild(e8, t2) {
  const n2 = e8.children[e8.children.length - 1];
  n2 && (n2.next = t2, t2.prev = n2), e8.children.push(t2), t2.parent = e8;
}, insertBefore(e8, t2, n2) {
  const r2 = e8.children.indexOf(n2), { prev: i2 } = n2;
  i2 && (i2.next = t2, t2.prev = i2), n2.prev = t2, t2.next = n2, e8.children.splice(r2, 0, t2), t2.parent = e8;
}, setTemplateContent(e8, t2) {
  Gl.appendChild(e8, t2);
}, getTemplateContent: (e8) => e8.children[0], setDocumentType(e8, t2, n2, r2) {
  const i2 = (function(e9, t3, n3) {
    let r3 = "!DOCTYPE ";
    return e9 && (r3 += e9), t3 ? r3 += ` PUBLIC ${Hl(t3)}` : n3 && (r3 += " SYSTEM"), n3 && (r3 += ` ${Hl(n3)}`), r3;
  })(t2, n2, r2);
  let s2 = e8.children.find(((e9) => y(e9) && "!doctype" === e9.name));
  s2 ? s2.data = null != i2 ? i2 : null : (s2 = new _("!doctype", i2), Gl.appendChild(e8, s2)), s2["x-name"] = null != t2 ? t2 : void 0, s2["x-publicId"] = null != n2 ? n2 : void 0, s2["x-systemId"] = null != r2 ? r2 : void 0;
}, setDocumentMode(e8, t2) {
  e8["x-mode"] = t2;
}, getDocumentMode: (e8) => e8["x-mode"], detachNode(e8) {
  if (e8.parent) {
    const t2 = e8.parent.children.indexOf(e8), { prev: n2, next: r2 } = e8;
    e8.prev = null, e8.next = null, n2 && (n2.next = r2), r2 && (r2.prev = n2), e8.parent.children.splice(t2, 1), e8.parent = null;
  }
}, insertText(e8, t2) {
  const n2 = e8.children[e8.children.length - 1];
  n2 && b(n2) ? n2.data += t2 : Gl.appendChild(e8, Ul(t2));
}, insertTextBefore(e8, t2, n2) {
  const r2 = e8.children[e8.children.indexOf(n2) - 1];
  r2 && b(r2) ? r2.data += t2 : Gl.insertBefore(e8, Ul(t2), n2);
}, adoptAttributes(e8, t2) {
  for (let n2 = 0; n2 < t2.length; n2++) {
    const r2 = t2[n2].name;
    void 0 === e8.attribs[r2] && (e8.attribs[r2] = t2[n2].value, e8["x-attribsNamespace"][r2] = t2[n2].namespace, e8["x-attribsPrefix"][r2] = t2[n2].prefix);
  }
}, getFirstChild: (e8) => e8.children[0], getChildNodes: (e8) => e8.children, getParentNode: (e8) => e8.parent, getAttrList: (e8) => e8.attributes, getTagName: (e8) => e8.name, getNamespaceURI: (e8) => e8.namespace, getTextNodeContent: (e8) => e8.data, getCommentNodeContent: (e8) => e8.data, getDocumentTypeNodeName(e8) {
  var t2;
  return null !== (t2 = e8["x-name"]) && void 0 !== t2 ? t2 : "";
}, getDocumentTypeNodePublicId(e8) {
  var t2;
  return null !== (t2 = e8["x-publicId"]) && void 0 !== t2 ? t2 : "";
}, getDocumentTypeNodeSystemId(e8) {
  var t2;
  return null !== (t2 = e8["x-systemId"]) && void 0 !== t2 ? t2 : "";
}, isDocumentTypeNode: (e8) => y(e8) && "!doctype" === e8.name, setNodeSourceCodeLocation(e8, t2) {
  t2 && (e8.startIndex = t2.startOffset, e8.endIndex = t2.endOffset), e8.sourceCodeLocation = t2;
}, getNodeSourceCodeLocation: (e8) => e8.sourceCodeLocation, updateNodeSourceCodeLocation(e8, t2) {
  null != t2.endOffset && (e8.endIndex = t2.endOffset), e8.sourceCodeLocation = { ...e8.sourceCodeLocation, ...t2 };
} };
var Yl = function(e8, t2, n2) {
  if (n2 || 2 === arguments.length) for (var r2, i2 = 0, s2 = t2.length; i2 < s2; i2++) !r2 && i2 in t2 || (r2 || (r2 = Array.prototype.slice.call(t2, 0, i2)), r2[i2] = t2[i2]);
  return e8.concat(r2 || Array.prototype.slice.call(t2));
};
function ql(e8, t2, n2, r2) {
  var i2 = { scriptingEnabled: "boolean" != typeof t2.scriptingEnabled || t2.scriptingEnabled, treeAdapter: Gl, sourceCodeLocationInfo: t2.sourceCodeLocationInfo };
  return n2 ? (function(e9, t3) {
    return wc.parse(e9, t3);
  })(e8, i2) : (function(e9, t3, n3) {
    "string" == typeof e9 && (n3 = t3, t3 = e9, e9 = null);
    const r3 = wc.getFragmentParser(e9, n3);
    return r3.tokenizer.write(t3, true), r3.getFragment();
  })(r2, e8, i2);
}
var Kl, jl, Vl, Wl = { treeAdapter: Gl };
function Ql(e8) {
  return e8 === Kl.Space || e8 === Kl.NewLine || e8 === Kl.Tab || e8 === Kl.FormFeed || e8 === Kl.CarriageReturn;
}
function Xl(e8) {
  return e8 === Kl.Slash || e8 === Kl.Gt || Ql(e8);
}
function $l(e8) {
  return e8 >= Kl.Zero && e8 <= Kl.Nine;
}
!(function(e8) {
  e8[e8.Tab = 9] = "Tab", e8[e8.NewLine = 10] = "NewLine", e8[e8.FormFeed = 12] = "FormFeed", e8[e8.CarriageReturn = 13] = "CarriageReturn", e8[e8.Space = 32] = "Space", e8[e8.ExclamationMark = 33] = "ExclamationMark", e8[e8.Num = 35] = "Num", e8[e8.Amp = 38] = "Amp", e8[e8.SingleQuote = 39] = "SingleQuote", e8[e8.DoubleQuote = 34] = "DoubleQuote", e8[e8.Dash = 45] = "Dash", e8[e8.Slash = 47] = "Slash", e8[e8.Zero = 48] = "Zero", e8[e8.Nine = 57] = "Nine", e8[e8.Semi = 59] = "Semi", e8[e8.Lt = 60] = "Lt", e8[e8.Eq = 61] = "Eq", e8[e8.Gt = 62] = "Gt", e8[e8.Questionmark = 63] = "Questionmark", e8[e8.UpperA = 65] = "UpperA", e8[e8.LowerA = 97] = "LowerA", e8[e8.UpperF = 70] = "UpperF", e8[e8.LowerF = 102] = "LowerF", e8[e8.UpperZ = 90] = "UpperZ", e8[e8.LowerZ = 122] = "LowerZ", e8[e8.LowerX = 120] = "LowerX", e8[e8.OpeningSquareBracket = 91] = "OpeningSquareBracket";
})(Kl || (Kl = {})), (function(e8) {
  e8[e8.Text = 1] = "Text", e8[e8.BeforeTagName = 2] = "BeforeTagName", e8[e8.InTagName = 3] = "InTagName", e8[e8.InSelfClosingTag = 4] = "InSelfClosingTag", e8[e8.BeforeClosingTagName = 5] = "BeforeClosingTagName", e8[e8.InClosingTagName = 6] = "InClosingTagName", e8[e8.AfterClosingTagName = 7] = "AfterClosingTagName", e8[e8.BeforeAttributeName = 8] = "BeforeAttributeName", e8[e8.InAttributeName = 9] = "InAttributeName", e8[e8.AfterAttributeName = 10] = "AfterAttributeName", e8[e8.BeforeAttributeValue = 11] = "BeforeAttributeValue", e8[e8.InAttributeValueDq = 12] = "InAttributeValueDq", e8[e8.InAttributeValueSq = 13] = "InAttributeValueSq", e8[e8.InAttributeValueNq = 14] = "InAttributeValueNq", e8[e8.BeforeDeclaration = 15] = "BeforeDeclaration", e8[e8.InDeclaration = 16] = "InDeclaration", e8[e8.InProcessingInstruction = 17] = "InProcessingInstruction", e8[e8.BeforeComment = 18] = "BeforeComment", e8[e8.CDATASequence = 19] = "CDATASequence", e8[e8.InSpecialComment = 20] = "InSpecialComment", e8[e8.InCommentLike = 21] = "InCommentLike", e8[e8.BeforeSpecialS = 22] = "BeforeSpecialS", e8[e8.SpecialStartSequence = 23] = "SpecialStartSequence", e8[e8.InSpecialTag = 24] = "InSpecialTag", e8[e8.BeforeEntity = 25] = "BeforeEntity", e8[e8.BeforeNumericEntity = 26] = "BeforeNumericEntity", e8[e8.InNamedEntity = 27] = "InNamedEntity", e8[e8.InNumericEntity = 28] = "InNumericEntity", e8[e8.InHexEntity = 29] = "InHexEntity";
})(jl || (jl = {})), (function(e8) {
  e8[e8.NoValue = 0] = "NoValue", e8[e8.Unquoted = 1] = "Unquoted", e8[e8.Single = 2] = "Single", e8[e8.Double = 3] = "Double";
})(Vl || (Vl = {}));
const zl = { Cdata: new Uint8Array([67, 68, 65, 84, 65, 91]), CdataEnd: new Uint8Array([93, 93, 62]), CommentEnd: new Uint8Array([45, 45, 62]), ScriptEnd: new Uint8Array([60, 47, 115, 99, 114, 105, 112, 116]), StyleEnd: new Uint8Array([60, 47, 115, 116, 121, 108, 101]), TitleEnd: new Uint8Array([60, 47, 116, 105, 116, 108, 101]) };
class Jl {
  constructor({ xmlMode: e8 = false, decodeEntities: t2 = true }, n2) {
    this.cbs = n2, this.state = jl.Text, this.buffer = "", this.sectionStart = 0, this.index = 0, this.baseState = jl.Text, this.isSpecial = false, this.running = true, this.offset = 0, this.sequenceIndex = 0, this.trieIndex = 0, this.trieCurrent = 0, this.entityResult = 0, this.entityExcess = 0, this.xmlMode = e8, this.decodeEntities = t2, this.entityTrie = e8 ? xo : wo;
  }
  reset() {
    this.state = jl.Text, this.buffer = "", this.sectionStart = 0, this.index = 0, this.baseState = jl.Text, this.currentSequence = void 0, this.running = true, this.offset = 0;
  }
  write(e8) {
    this.offset += this.buffer.length, this.buffer = e8, this.parse();
  }
  end() {
    this.running && this.finish();
  }
  pause() {
    this.running = false;
  }
  resume() {
    this.running = true, this.index < this.buffer.length + this.offset && this.parse();
  }
  getIndex() {
    return this.index;
  }
  getSectionStart() {
    return this.sectionStart;
  }
  stateText(e8) {
    e8 === Kl.Lt || !this.decodeEntities && this.fastForwardTo(Kl.Lt) ? (this.index > this.sectionStart && this.cbs.ontext(this.sectionStart, this.index), this.state = jl.BeforeTagName, this.sectionStart = this.index) : this.decodeEntities && e8 === Kl.Amp && (this.state = jl.BeforeEntity);
  }
  stateSpecialStartSequence(e8) {
    const t2 = this.sequenceIndex === this.currentSequence.length;
    if (t2 ? Xl(e8) : (32 | e8) === this.currentSequence[this.sequenceIndex]) {
      if (!t2) return void this.sequenceIndex++;
    } else this.isSpecial = false;
    this.sequenceIndex = 0, this.state = jl.InTagName, this.stateInTagName(e8);
  }
  stateInSpecialTag(e8) {
    if (this.sequenceIndex === this.currentSequence.length) {
      if (e8 === Kl.Gt || Ql(e8)) {
        const t2 = this.index - this.currentSequence.length;
        if (this.sectionStart < t2) {
          const e9 = this.index;
          this.index = t2, this.cbs.ontext(this.sectionStart, t2), this.index = e9;
        }
        return this.isSpecial = false, this.sectionStart = t2 + 2, void this.stateInClosingTagName(e8);
      }
      this.sequenceIndex = 0;
    }
    (32 | e8) === this.currentSequence[this.sequenceIndex] ? this.sequenceIndex += 1 : 0 === this.sequenceIndex ? this.currentSequence === zl.TitleEnd ? this.decodeEntities && e8 === Kl.Amp && (this.state = jl.BeforeEntity) : this.fastForwardTo(Kl.Lt) && (this.sequenceIndex = 1) : this.sequenceIndex = Number(e8 === Kl.Lt);
  }
  stateCDATASequence(e8) {
    e8 === zl.Cdata[this.sequenceIndex] ? ++this.sequenceIndex === zl.Cdata.length && (this.state = jl.InCommentLike, this.currentSequence = zl.CdataEnd, this.sequenceIndex = 0, this.sectionStart = this.index + 1) : (this.sequenceIndex = 0, this.state = jl.InDeclaration, this.stateInDeclaration(e8));
  }
  fastForwardTo(e8) {
    for (; ++this.index < this.buffer.length + this.offset; ) if (this.buffer.charCodeAt(this.index - this.offset) === e8) return true;
    return this.index = this.buffer.length + this.offset - 1, false;
  }
  stateInCommentLike(e8) {
    e8 === this.currentSequence[this.sequenceIndex] ? ++this.sequenceIndex === this.currentSequence.length && (this.currentSequence === zl.CdataEnd ? this.cbs.oncdata(this.sectionStart, this.index, 2) : this.cbs.oncomment(this.sectionStart, this.index, 2), this.sequenceIndex = 0, this.sectionStart = this.index + 1, this.state = jl.Text) : 0 === this.sequenceIndex ? this.fastForwardTo(this.currentSequence[0]) && (this.sequenceIndex = 1) : e8 !== this.currentSequence[this.sequenceIndex - 1] && (this.sequenceIndex = 0);
  }
  isTagStartChar(e8) {
    return this.xmlMode ? !Xl(e8) : (function(e9) {
      return e9 >= Kl.LowerA && e9 <= Kl.LowerZ || e9 >= Kl.UpperA && e9 <= Kl.UpperZ;
    })(e8);
  }
  startSpecial(e8, t2) {
    this.isSpecial = true, this.currentSequence = e8, this.sequenceIndex = t2, this.state = jl.SpecialStartSequence;
  }
  stateBeforeTagName(e8) {
    if (e8 === Kl.ExclamationMark) this.state = jl.BeforeDeclaration, this.sectionStart = this.index + 1;
    else if (e8 === Kl.Questionmark) this.state = jl.InProcessingInstruction, this.sectionStart = this.index + 1;
    else if (this.isTagStartChar(e8)) {
      const t2 = 32 | e8;
      this.sectionStart = this.index, this.xmlMode || t2 !== zl.TitleEnd[2] ? this.state = this.xmlMode || t2 !== zl.ScriptEnd[2] ? jl.InTagName : jl.BeforeSpecialS : this.startSpecial(zl.TitleEnd, 3);
    } else e8 === Kl.Slash ? this.state = jl.BeforeClosingTagName : (this.state = jl.Text, this.stateText(e8));
  }
  stateInTagName(e8) {
    Xl(e8) && (this.cbs.onopentagname(this.sectionStart, this.index), this.sectionStart = -1, this.state = jl.BeforeAttributeName, this.stateBeforeAttributeName(e8));
  }
  stateBeforeClosingTagName(e8) {
    Ql(e8) || (e8 === Kl.Gt ? this.state = jl.Text : (this.state = this.isTagStartChar(e8) ? jl.InClosingTagName : jl.InSpecialComment, this.sectionStart = this.index));
  }
  stateInClosingTagName(e8) {
    (e8 === Kl.Gt || Ql(e8)) && (this.cbs.onclosetag(this.sectionStart, this.index), this.sectionStart = -1, this.state = jl.AfterClosingTagName, this.stateAfterClosingTagName(e8));
  }
  stateAfterClosingTagName(e8) {
    (e8 === Kl.Gt || this.fastForwardTo(Kl.Gt)) && (this.state = jl.Text, this.sectionStart = this.index + 1);
  }
  stateBeforeAttributeName(e8) {
    e8 === Kl.Gt ? (this.cbs.onopentagend(this.index), this.isSpecial ? (this.state = jl.InSpecialTag, this.sequenceIndex = 0) : this.state = jl.Text, this.baseState = this.state, this.sectionStart = this.index + 1) : e8 === Kl.Slash ? this.state = jl.InSelfClosingTag : Ql(e8) || (this.state = jl.InAttributeName, this.sectionStart = this.index);
  }
  stateInSelfClosingTag(e8) {
    e8 === Kl.Gt ? (this.cbs.onselfclosingtag(this.index), this.state = jl.Text, this.baseState = jl.Text, this.sectionStart = this.index + 1, this.isSpecial = false) : Ql(e8) || (this.state = jl.BeforeAttributeName, this.stateBeforeAttributeName(e8));
  }
  stateInAttributeName(e8) {
    (e8 === Kl.Eq || Xl(e8)) && (this.cbs.onattribname(this.sectionStart, this.index), this.sectionStart = -1, this.state = jl.AfterAttributeName, this.stateAfterAttributeName(e8));
  }
  stateAfterAttributeName(e8) {
    e8 === Kl.Eq ? this.state = jl.BeforeAttributeValue : e8 === Kl.Slash || e8 === Kl.Gt ? (this.cbs.onattribend(Vl.NoValue, this.index), this.state = jl.BeforeAttributeName, this.stateBeforeAttributeName(e8)) : Ql(e8) || (this.cbs.onattribend(Vl.NoValue, this.index), this.state = jl.InAttributeName, this.sectionStart = this.index);
  }
  stateBeforeAttributeValue(e8) {
    e8 === Kl.DoubleQuote ? (this.state = jl.InAttributeValueDq, this.sectionStart = this.index + 1) : e8 === Kl.SingleQuote ? (this.state = jl.InAttributeValueSq, this.sectionStart = this.index + 1) : Ql(e8) || (this.sectionStart = this.index, this.state = jl.InAttributeValueNq, this.stateInAttributeValueNoQuotes(e8));
  }
  handleInAttributeValue(e8, t2) {
    e8 === t2 || !this.decodeEntities && this.fastForwardTo(t2) ? (this.cbs.onattribdata(this.sectionStart, this.index), this.sectionStart = -1, this.cbs.onattribend(t2 === Kl.DoubleQuote ? Vl.Double : Vl.Single, this.index), this.state = jl.BeforeAttributeName) : this.decodeEntities && e8 === Kl.Amp && (this.baseState = this.state, this.state = jl.BeforeEntity);
  }
  stateInAttributeValueDoubleQuotes(e8) {
    this.handleInAttributeValue(e8, Kl.DoubleQuote);
  }
  stateInAttributeValueSingleQuotes(e8) {
    this.handleInAttributeValue(e8, Kl.SingleQuote);
  }
  stateInAttributeValueNoQuotes(e8) {
    Ql(e8) || e8 === Kl.Gt ? (this.cbs.onattribdata(this.sectionStart, this.index), this.sectionStart = -1, this.cbs.onattribend(Vl.Unquoted, this.index), this.state = jl.BeforeAttributeName, this.stateBeforeAttributeName(e8)) : this.decodeEntities && e8 === Kl.Amp && (this.baseState = this.state, this.state = jl.BeforeEntity);
  }
  stateBeforeDeclaration(e8) {
    e8 === Kl.OpeningSquareBracket ? (this.state = jl.CDATASequence, this.sequenceIndex = 0) : this.state = e8 === Kl.Dash ? jl.BeforeComment : jl.InDeclaration;
  }
  stateInDeclaration(e8) {
    (e8 === Kl.Gt || this.fastForwardTo(Kl.Gt)) && (this.cbs.ondeclaration(this.sectionStart, this.index), this.state = jl.Text, this.sectionStart = this.index + 1);
  }
  stateInProcessingInstruction(e8) {
    (e8 === Kl.Gt || this.fastForwardTo(Kl.Gt)) && (this.cbs.onprocessinginstruction(this.sectionStart, this.index), this.state = jl.Text, this.sectionStart = this.index + 1);
  }
  stateBeforeComment(e8) {
    e8 === Kl.Dash ? (this.state = jl.InCommentLike, this.currentSequence = zl.CommentEnd, this.sequenceIndex = 2, this.sectionStart = this.index + 1) : this.state = jl.InDeclaration;
  }
  stateInSpecialComment(e8) {
    (e8 === Kl.Gt || this.fastForwardTo(Kl.Gt)) && (this.cbs.oncomment(this.sectionStart, this.index, 0), this.state = jl.Text, this.sectionStart = this.index + 1);
  }
  stateBeforeSpecialS(e8) {
    const t2 = 32 | e8;
    t2 === zl.ScriptEnd[3] ? this.startSpecial(zl.ScriptEnd, 4) : t2 === zl.StyleEnd[3] ? this.startSpecial(zl.StyleEnd, 4) : (this.state = jl.InTagName, this.stateInTagName(e8));
  }
  stateBeforeEntity(e8) {
    this.entityExcess = 1, this.entityResult = 0, e8 === Kl.Num ? this.state = jl.BeforeNumericEntity : e8 === Kl.Amp || (this.trieIndex = 0, this.trieCurrent = this.entityTrie[0], this.state = jl.InNamedEntity, this.stateInNamedEntity(e8));
  }
  stateInNamedEntity(e8) {
    if (this.entityExcess += 1, this.trieIndex = Oo(this.entityTrie, this.trieCurrent, this.trieIndex + 1, e8), this.trieIndex < 0) return this.emitNamedEntity(), void this.index--;
    this.trieCurrent = this.entityTrie[this.trieIndex];
    const t2 = this.trieCurrent & yo.VALUE_LENGTH;
    if (t2) {
      const n2 = (t2 >> 14) - 1;
      if (this.allowLegacyEntity() || e8 === Kl.Semi) {
        const e9 = this.index - this.entityExcess + 1;
        e9 > this.sectionStart && this.emitPartial(this.sectionStart, e9), this.entityResult = this.trieIndex, this.trieIndex += n2, this.entityExcess = 0, this.sectionStart = this.index + 1, 0 === n2 && this.emitNamedEntity();
      } else this.trieIndex += n2;
    }
  }
  emitNamedEntity() {
    if (this.state = this.baseState, 0 === this.entityResult) return;
    switch ((this.entityTrie[this.entityResult] & yo.VALUE_LENGTH) >> 14) {
      case 1:
        this.emitCodePoint(this.entityTrie[this.entityResult] & ~yo.VALUE_LENGTH);
        break;
      case 2:
        this.emitCodePoint(this.entityTrie[this.entityResult + 1]);
        break;
      case 3:
        this.emitCodePoint(this.entityTrie[this.entityResult + 1]), this.emitCodePoint(this.entityTrie[this.entityResult + 2]);
    }
  }
  stateBeforeNumericEntity(e8) {
    (32 | e8) === Kl.LowerX ? (this.entityExcess++, this.state = jl.InHexEntity) : (this.state = jl.InNumericEntity, this.stateInNumericEntity(e8));
  }
  emitNumericEntity(e8) {
    const t2 = this.index - this.entityExcess - 1;
    t2 + 2 + Number(this.state === jl.InHexEntity) !== this.index && (t2 > this.sectionStart && this.emitPartial(this.sectionStart, t2), this.sectionStart = this.index + Number(e8), this.emitCodePoint(ko(this.entityResult))), this.state = this.baseState;
  }
  stateInNumericEntity(e8) {
    e8 === Kl.Semi ? this.emitNumericEntity(true) : $l(e8) ? (this.entityResult = 10 * this.entityResult + (e8 - Kl.Zero), this.entityExcess++) : (this.allowLegacyEntity() ? this.emitNumericEntity(false) : this.state = this.baseState, this.index--);
  }
  stateInHexEntity(e8) {
    e8 === Kl.Semi ? this.emitNumericEntity(true) : $l(e8) ? (this.entityResult = 16 * this.entityResult + (e8 - Kl.Zero), this.entityExcess++) : !(function(e9) {
      return e9 >= Kl.UpperA && e9 <= Kl.UpperF || e9 >= Kl.LowerA && e9 <= Kl.LowerF;
    })(e8) ? (this.allowLegacyEntity() ? this.emitNumericEntity(false) : this.state = this.baseState, this.index--) : (this.entityResult = 16 * this.entityResult + ((32 | e8) - Kl.LowerA + 10), this.entityExcess++);
  }
  allowLegacyEntity() {
    return !this.xmlMode && (this.baseState === jl.Text || this.baseState === jl.InSpecialTag);
  }
  cleanup() {
    this.running && this.sectionStart !== this.index && (this.state === jl.Text || this.state === jl.InSpecialTag && 0 === this.sequenceIndex ? (this.cbs.ontext(this.sectionStart, this.index), this.sectionStart = this.index) : this.state !== jl.InAttributeValueDq && this.state !== jl.InAttributeValueSq && this.state !== jl.InAttributeValueNq || (this.cbs.onattribdata(this.sectionStart, this.index), this.sectionStart = this.index));
  }
  shouldContinue() {
    return this.index < this.buffer.length + this.offset && this.running;
  }
  parse() {
    for (; this.shouldContinue(); ) {
      const e8 = this.buffer.charCodeAt(this.index - this.offset);
      this.state === jl.Text ? this.stateText(e8) : this.state === jl.SpecialStartSequence ? this.stateSpecialStartSequence(e8) : this.state === jl.InSpecialTag ? this.stateInSpecialTag(e8) : this.state === jl.CDATASequence ? this.stateCDATASequence(e8) : this.state === jl.InAttributeValueDq ? this.stateInAttributeValueDoubleQuotes(e8) : this.state === jl.InAttributeName ? this.stateInAttributeName(e8) : this.state === jl.InCommentLike ? this.stateInCommentLike(e8) : this.state === jl.InSpecialComment ? this.stateInSpecialComment(e8) : this.state === jl.BeforeAttributeName ? this.stateBeforeAttributeName(e8) : this.state === jl.InTagName ? this.stateInTagName(e8) : this.state === jl.InClosingTagName ? this.stateInClosingTagName(e8) : this.state === jl.BeforeTagName ? this.stateBeforeTagName(e8) : this.state === jl.AfterAttributeName ? this.stateAfterAttributeName(e8) : this.state === jl.InAttributeValueSq ? this.stateInAttributeValueSingleQuotes(e8) : this.state === jl.BeforeAttributeValue ? this.stateBeforeAttributeValue(e8) : this.state === jl.BeforeClosingTagName ? this.stateBeforeClosingTagName(e8) : this.state === jl.AfterClosingTagName ? this.stateAfterClosingTagName(e8) : this.state === jl.BeforeSpecialS ? this.stateBeforeSpecialS(e8) : this.state === jl.InAttributeValueNq ? this.stateInAttributeValueNoQuotes(e8) : this.state === jl.InSelfClosingTag ? this.stateInSelfClosingTag(e8) : this.state === jl.InDeclaration ? this.stateInDeclaration(e8) : this.state === jl.BeforeDeclaration ? this.stateBeforeDeclaration(e8) : this.state === jl.BeforeComment ? this.stateBeforeComment(e8) : this.state === jl.InProcessingInstruction ? this.stateInProcessingInstruction(e8) : this.state === jl.InNamedEntity ? this.stateInNamedEntity(e8) : this.state === jl.BeforeEntity ? this.stateBeforeEntity(e8) : this.state === jl.InHexEntity ? this.stateInHexEntity(e8) : this.state === jl.InNumericEntity ? this.stateInNumericEntity(e8) : this.stateBeforeNumericEntity(e8), this.index++;
    }
    this.cleanup();
  }
  finish() {
    this.state === jl.InNamedEntity && this.emitNamedEntity(), this.sectionStart < this.index && this.handleTrailingData(), this.cbs.onend();
  }
  handleTrailingData() {
    const e8 = this.buffer.length + this.offset;
    this.state === jl.InCommentLike ? this.currentSequence === zl.CdataEnd ? this.cbs.oncdata(this.sectionStart, e8, 0) : this.cbs.oncomment(this.sectionStart, e8, 0) : this.state === jl.InNumericEntity && this.allowLegacyEntity() || this.state === jl.InHexEntity && this.allowLegacyEntity() ? this.emitNumericEntity(false) : this.state === jl.InTagName || this.state === jl.BeforeAttributeName || this.state === jl.BeforeAttributeValue || this.state === jl.AfterAttributeName || this.state === jl.InAttributeName || this.state === jl.InAttributeValueSq || this.state === jl.InAttributeValueDq || this.state === jl.InAttributeValueNq || this.state === jl.InClosingTagName || this.cbs.ontext(this.sectionStart, e8);
  }
  emitPartial(e8, t2) {
    this.baseState !== jl.Text && this.baseState !== jl.InSpecialTag ? this.cbs.onattribdata(e8, t2) : this.cbs.ontext(e8, t2);
  }
  emitCodePoint(e8) {
    this.baseState !== jl.Text && this.baseState !== jl.InSpecialTag ? this.cbs.onattribentity(e8) : this.cbs.ontextentity(e8);
  }
}
const Zl = /* @__PURE__ */ new Set(["input", "option", "optgroup", "select", "button", "datalist", "textarea"]), eh = /* @__PURE__ */ new Set(["p"]), th = /* @__PURE__ */ new Set(["thead", "tbody"]), nh = /* @__PURE__ */ new Set(["dd", "dt"]), rh = /* @__PURE__ */ new Set(["rt", "rp"]), ih = /* @__PURE__ */ new Map([["tr", /* @__PURE__ */ new Set(["tr", "th", "td"])], ["th", /* @__PURE__ */ new Set(["th"])], ["td", /* @__PURE__ */ new Set(["thead", "th", "td"])], ["body", /* @__PURE__ */ new Set(["head", "link", "script"])], ["li", /* @__PURE__ */ new Set(["li"])], ["p", eh], ["h1", eh], ["h2", eh], ["h3", eh], ["h4", eh], ["h5", eh], ["h6", eh], ["select", Zl], ["input", Zl], ["output", Zl], ["button", Zl], ["datalist", Zl], ["textarea", Zl], ["option", /* @__PURE__ */ new Set(["option"])], ["optgroup", /* @__PURE__ */ new Set(["optgroup", "option"])], ["dd", nh], ["dt", nh], ["address", eh], ["article", eh], ["aside", eh], ["blockquote", eh], ["details", eh], ["div", eh], ["dl", eh], ["fieldset", eh], ["figcaption", eh], ["figure", eh], ["footer", eh], ["form", eh], ["header", eh], ["hr", eh], ["main", eh], ["nav", eh], ["ol", eh], ["pre", eh], ["section", eh], ["table", eh], ["ul", eh], ["rt", rh], ["rp", rh], ["tbody", th], ["tfoot", th]]), sh = /* @__PURE__ */ new Set(["area", "base", "basefont", "br", "col", "command", "embed", "frame", "hr", "img", "input", "isindex", "keygen", "link", "meta", "param", "source", "track", "wbr"]), ah = /* @__PURE__ */ new Set(["math", "svg"]), oh = /* @__PURE__ */ new Set(["mi", "mo", "mn", "ms", "mtext", "annotation-xml", "foreignobject", "desc", "title"]), ch = /\s|\//;
class lh {
  constructor(e8, t2 = {}) {
    var n2, r2, i2, s2, a2;
    this.options = t2, this.startIndex = 0, this.endIndex = 0, this.openTagStart = 0, this.tagname = "", this.attribname = "", this.attribvalue = "", this.attribs = null, this.stack = [], this.foreignContext = [], this.buffers = [], this.bufferOffset = 0, this.writeIndex = 0, this.ended = false, this.cbs = null != e8 ? e8 : {}, this.lowerCaseTagNames = null !== (n2 = t2.lowerCaseTags) && void 0 !== n2 ? n2 : !t2.xmlMode, this.lowerCaseAttributeNames = null !== (r2 = t2.lowerCaseAttributeNames) && void 0 !== r2 ? r2 : !t2.xmlMode, this.tokenizer = new (null !== (i2 = t2.Tokenizer) && void 0 !== i2 ? i2 : Jl)(this.options, this), null === (a2 = (s2 = this.cbs).onparserinit) || void 0 === a2 || a2.call(s2, this);
  }
  ontext(e8, t2) {
    var n2, r2;
    const i2 = this.getSlice(e8, t2);
    this.endIndex = t2 - 1, null === (r2 = (n2 = this.cbs).ontext) || void 0 === r2 || r2.call(n2, i2), this.startIndex = t2;
  }
  ontextentity(e8) {
    var t2, n2;
    const r2 = this.tokenizer.getSectionStart();
    this.endIndex = r2 - 1, null === (n2 = (t2 = this.cbs).ontext) || void 0 === n2 || n2.call(t2, Lo(e8)), this.startIndex = r2;
  }
  isVoidElement(e8) {
    return !this.options.xmlMode && sh.has(e8);
  }
  onopentagname(e8, t2) {
    this.endIndex = t2;
    let n2 = this.getSlice(e8, t2);
    this.lowerCaseTagNames && (n2 = n2.toLowerCase()), this.emitOpenTag(n2);
  }
  emitOpenTag(e8) {
    var t2, n2, r2, i2;
    this.openTagStart = this.startIndex, this.tagname = e8;
    const s2 = !this.options.xmlMode && ih.get(e8);
    if (s2) for (; this.stack.length > 0 && s2.has(this.stack[this.stack.length - 1]); ) {
      const e9 = this.stack.pop();
      null === (n2 = (t2 = this.cbs).onclosetag) || void 0 === n2 || n2.call(t2, e9, true);
    }
    this.isVoidElement(e8) || (this.stack.push(e8), ah.has(e8) ? this.foreignContext.push(true) : oh.has(e8) && this.foreignContext.push(false)), null === (i2 = (r2 = this.cbs).onopentagname) || void 0 === i2 || i2.call(r2, e8), this.cbs.onopentag && (this.attribs = {});
  }
  endOpenTag(e8) {
    var t2, n2;
    this.startIndex = this.openTagStart, this.attribs && (null === (n2 = (t2 = this.cbs).onopentag) || void 0 === n2 || n2.call(t2, this.tagname, this.attribs, e8), this.attribs = null), this.cbs.onclosetag && this.isVoidElement(this.tagname) && this.cbs.onclosetag(this.tagname, true), this.tagname = "";
  }
  onopentagend(e8) {
    this.endIndex = e8, this.endOpenTag(false), this.startIndex = e8 + 1;
  }
  onclosetag(e8, t2) {
    var n2, r2, i2, s2, a2, o2;
    this.endIndex = t2;
    let c2 = this.getSlice(e8, t2);
    if (this.lowerCaseTagNames && (c2 = c2.toLowerCase()), (ah.has(c2) || oh.has(c2)) && this.foreignContext.pop(), this.isVoidElement(c2)) this.options.xmlMode || "br" !== c2 || (null === (r2 = (n2 = this.cbs).onopentagname) || void 0 === r2 || r2.call(n2, "br"), null === (s2 = (i2 = this.cbs).onopentag) || void 0 === s2 || s2.call(i2, "br", {}, true), null === (o2 = (a2 = this.cbs).onclosetag) || void 0 === o2 || o2.call(a2, "br", false));
    else {
      const e9 = this.stack.lastIndexOf(c2);
      if (-1 !== e9) if (this.cbs.onclosetag) {
        let t3 = this.stack.length - e9;
        for (; t3--; ) this.cbs.onclosetag(this.stack.pop(), 0 !== t3);
      } else this.stack.length = e9;
      else this.options.xmlMode || "p" !== c2 || (this.emitOpenTag("p"), this.closeCurrentTag(true));
    }
    this.startIndex = t2 + 1;
  }
  onselfclosingtag(e8) {
    this.endIndex = e8, this.options.xmlMode || this.options.recognizeSelfClosing || this.foreignContext[this.foreignContext.length - 1] ? (this.closeCurrentTag(false), this.startIndex = e8 + 1) : this.onopentagend(e8);
  }
  closeCurrentTag(e8) {
    var t2, n2;
    const r2 = this.tagname;
    this.endOpenTag(e8), this.stack[this.stack.length - 1] === r2 && (null === (n2 = (t2 = this.cbs).onclosetag) || void 0 === n2 || n2.call(t2, r2, !e8), this.stack.pop());
  }
  onattribname(e8, t2) {
    this.startIndex = e8;
    const n2 = this.getSlice(e8, t2);
    this.attribname = this.lowerCaseAttributeNames ? n2.toLowerCase() : n2;
  }
  onattribdata(e8, t2) {
    this.attribvalue += this.getSlice(e8, t2);
  }
  onattribentity(e8) {
    this.attribvalue += Lo(e8);
  }
  onattribend(e8, t2) {
    var n2, r2;
    this.endIndex = t2, null === (r2 = (n2 = this.cbs).onattribute) || void 0 === r2 || r2.call(n2, this.attribname, this.attribvalue, e8 === Vl.Double ? '"' : e8 === Vl.Single ? "'" : e8 === Vl.NoValue ? void 0 : null), this.attribs && !Object.prototype.hasOwnProperty.call(this.attribs, this.attribname) && (this.attribs[this.attribname] = this.attribvalue), this.attribvalue = "";
  }
  getInstructionName(e8) {
    const t2 = e8.search(ch);
    let n2 = t2 < 0 ? e8 : e8.substr(0, t2);
    return this.lowerCaseTagNames && (n2 = n2.toLowerCase()), n2;
  }
  ondeclaration(e8, t2) {
    this.endIndex = t2;
    const n2 = this.getSlice(e8, t2);
    if (this.cbs.onprocessinginstruction) {
      const e9 = this.getInstructionName(n2);
      this.cbs.onprocessinginstruction(`!${e9}`, `!${n2}`);
    }
    this.startIndex = t2 + 1;
  }
  onprocessinginstruction(e8, t2) {
    this.endIndex = t2;
    const n2 = this.getSlice(e8, t2);
    if (this.cbs.onprocessinginstruction) {
      const e9 = this.getInstructionName(n2);
      this.cbs.onprocessinginstruction(`?${e9}`, `?${n2}`);
    }
    this.startIndex = t2 + 1;
  }
  oncomment(e8, t2, n2) {
    var r2, i2, s2, a2;
    this.endIndex = t2, null === (i2 = (r2 = this.cbs).oncomment) || void 0 === i2 || i2.call(r2, this.getSlice(e8, t2 - n2)), null === (a2 = (s2 = this.cbs).oncommentend) || void 0 === a2 || a2.call(s2), this.startIndex = t2 + 1;
  }
  oncdata(e8, t2, n2) {
    var r2, i2, s2, a2, o2, c2, l2, h2, u2, p2;
    this.endIndex = t2;
    const f2 = this.getSlice(e8, t2 - n2);
    this.options.xmlMode || this.options.recognizeCDATA ? (null === (i2 = (r2 = this.cbs).oncdatastart) || void 0 === i2 || i2.call(r2), null === (a2 = (s2 = this.cbs).ontext) || void 0 === a2 || a2.call(s2, f2), null === (c2 = (o2 = this.cbs).oncdataend) || void 0 === c2 || c2.call(o2)) : (null === (h2 = (l2 = this.cbs).oncomment) || void 0 === h2 || h2.call(l2, `[CDATA[${f2}]]`), null === (p2 = (u2 = this.cbs).oncommentend) || void 0 === p2 || p2.call(u2)), this.startIndex = t2 + 1;
  }
  onend() {
    var e8, t2;
    if (this.cbs.onclosetag) {
      this.endIndex = this.startIndex;
      for (let e9 = this.stack.length; e9 > 0; this.cbs.onclosetag(this.stack[--e9], true)) ;
    }
    null === (t2 = (e8 = this.cbs).onend) || void 0 === t2 || t2.call(e8);
  }
  reset() {
    var e8, t2, n2, r2;
    null === (t2 = (e8 = this.cbs).onreset) || void 0 === t2 || t2.call(e8), this.tokenizer.reset(), this.tagname = "", this.attribname = "", this.attribs = null, this.stack.length = 0, this.startIndex = 0, this.endIndex = 0, null === (r2 = (n2 = this.cbs).onparserinit) || void 0 === r2 || r2.call(n2, this), this.buffers.length = 0, this.bufferOffset = 0, this.writeIndex = 0, this.ended = false;
  }
  parseComplete(e8) {
    this.reset(), this.end(e8);
  }
  getSlice(e8, t2) {
    for (; e8 - this.bufferOffset >= this.buffers[0].length; ) this.shiftBuffer();
    let n2 = this.buffers[0].slice(e8 - this.bufferOffset, t2 - this.bufferOffset);
    for (; t2 - this.bufferOffset > this.buffers[0].length; ) this.shiftBuffer(), n2 += this.buffers[0].slice(0, t2 - this.bufferOffset);
    return n2;
  }
  shiftBuffer() {
    this.bufferOffset += this.buffers[0].length, this.writeIndex--, this.buffers.shift();
  }
  write(e8) {
    var t2, n2;
    this.ended ? null === (n2 = (t2 = this.cbs).onerror) || void 0 === n2 || n2.call(t2, new Error(".write() after done!")) : (this.buffers.push(e8), this.tokenizer.running && (this.tokenizer.write(e8), this.writeIndex++));
  }
  end(e8) {
    var t2, n2;
    this.ended ? null === (n2 = (t2 = this.cbs).onerror) || void 0 === n2 || n2.call(t2, Error(".end() after done!")) : (e8 && this.write(e8), this.ended = true, this.tokenizer.end());
  }
  pause() {
    this.tokenizer.pause();
  }
  resume() {
    for (this.tokenizer.resume(); this.tokenizer.running && this.writeIndex < this.buffers.length; ) this.tokenizer.write(this.buffers[this.writeIndex++]);
    this.ended && this.tokenizer.end();
  }
  parseChunk(e8) {
    this.write(e8);
  }
  done(e8) {
    this.end(e8);
  }
}
var hh = /* @__PURE__ */ (function(e8) {
  return function(t2, n2, r2, i2) {
    if ("undefined" != typeof Buffer && Buffer.isBuffer(t2) && (t2 = t2.toString()), "string" == typeof t2) return e8(t2, n2, r2, i2);
    var s2 = t2;
    if (!Array.isArray(s2) && L(s2)) return s2;
    var a2 = new N([]);
    return Ba(s2, a2), a2;
  };
})((function(e8, t2, n2, r2) {
  return t2.xmlMode || t2._useHtmlParser2 ? (function(e9, t3) {
    const n3 = new M(void 0, t3);
    return new lh(n3, t3).end(e9), n3.root;
  })(e8, t2) : ql(e8, t2, n2, r2);
})), uh = /* @__PURE__ */ (function(e8, t2) {
  return function r2(s2, a2, o2) {
    if (void 0 === o2 && (o2 = true), null == s2) throw new Error("cheerio.load() expects a string");
    var c2 = no(no({}, n), i(a2)), l2 = e8(s2, c2, o2, null), h2 = (function(n2) {
      function r3() {
        return null !== n2 && n2.apply(this, arguments) || this;
      }
      return to(r3, n2), r3.prototype._make = function(e9, t3) {
        var n3 = u2(e9, t3);
        return n3.prevObject = this, n3;
      }, r3.prototype._parse = function(t3, n3, r4, i2) {
        return e8(t3, n3, r4, i2);
      }, r3.prototype._render = function(e9) {
        return t2(e9, this.options);
      }, r3;
    })(Za);
    function u2(t3, n2, r3, s3) {
      if (void 0 === r3 && (r3 = l2), t3 && wi(t3)) return t3;
      var a3 = no(no({}, c2), i(s3)), o3 = "string" == typeof r3 ? [e8(r3, a3, false, null)] : "length" in r3 ? r3 : [r3], u3 = wi(o3) ? o3 : new h2(o3, null, a3);
      if (u3._root = u3, !t3) return new h2(void 0, u3, a3);
      var p2, f2 = "string" == typeof t3 && Ui(t3) ? e8(t3, a3, false, null).children : (p2 = t3).name || "root" === p2.type || "text" === p2.type || "comment" === p2.type ? [t3] : Array.isArray(t3) ? t3 : void 0, d2 = new h2(f2, u3, a3);
      if (f2) return d2;
      if ("string" != typeof t3) throw new Error("Unexpected type of selector");
      var E2 = t3, m2 = n2 ? "string" == typeof n2 ? Ui(n2) ? new h2([e8(n2, a3, false, null)], u3, a3) : (E2 = "".concat(n2, " ").concat(E2), u3) : wi(n2) ? n2 : new h2(Array.isArray(n2) ? n2 : [n2], u3, a3) : u3;
      return m2 ? m2.find(E2) : d2;
    }
    return Object.assign(u2, xi, { load: r2, _root: l2, _options: c2, fn: h2.prototype, prototype: h2.prototype }), u2;
  };
})(hh, (function(e8, t2) {
  return t2.xmlMode || t2._useHtmlParser2 ? V(e8, t2) : (function(e9) {
    for (var t3, n2 = ("length" in e9) ? e9 : [e9], r2 = 0; r2 < n2.length; r2 += 1) L(s2 = n2[r2]) && (t3 = Array.prototype.splice).call.apply(t3, Yl([n2, r2, 1], s2.children, false));
    var i2 = "";
    for (r2 = 0; r2 < n2.length; r2 += 1) {
      var s2;
      i2 += Bl(s2 = n2[r2], Wl);
    }
    return i2;
  })(e8);
}));
function ph(e8, t2) {
  return He({ path: e8, json: t2 });
}
function fh(e8) {
  return He(e8);
}
function dh(e8, t2) {
  return Ii(e8, t2);
}
var Eh = uh([]), mh = Di, Th = Ri, _h = ki, Ah = vi;
