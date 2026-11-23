/*
 * Tiny no-build runtime for the ACME Exchange pages.
 *
 * - `html` is htm bound to an `h` that accepts HTML-style attributes
 *   (class, for, kebab-case names, style strings) and turns them into React props.
 * - `DS` is the ACME design-system component namespace from ds/components/bundle.js.
 * - `DCLogic` is the base class each screen's Component extends; `renderVals()`
 *   returns the values its template reads.
 */
(function () {
  'use strict';

  var RENAME = {
    class: 'className',
    for: 'htmlFor',
    novalidate: 'noValidate',
    inputmode: 'inputMode',
    autocomplete: 'autoComplete',
    readonly: 'readOnly',
    maxlength: 'maxLength',
    tabindex: 'tabIndex'
  };

  function camel(name) {
    return name.replace(/-([a-z])/g, function (_, c) { return c.toUpperCase(); });
  }

  function styleObject(text) {
    var out = {};
    String(text).split(';').forEach(function (decl) {
      var i = decl.indexOf(':');
      if (i < 0) return;
      var key = decl.slice(0, i).trim();
      var value = decl.slice(i + 1).trim();
      if (!key || value === '') return;
      out[key.indexOf('--') === 0 ? key : camel(key)] = value;
    });
    return out;
  }

  function normalize(props) {
    if (!props) return props;
    var out = {};
    Object.keys(props).forEach(function (key) {
      var value = props[key];
      var name = RENAME[key] || (/^(aria|data)-/.test(key) ? key : camel(key));
      if (name === 'style' && typeof value === 'string') value = styleObject(value);
      out[name] = value;
    });
    return out;
  }

  function h(type, props) {
    var children = Array.prototype.slice.call(arguments, 2);
    return React.createElement.apply(React, [type, normalize(props)].concat(children));
  }

  function DCLogic(props) {
    React.Component.call(this, props);
  }
  DCLogic.prototype = Object.create(React.Component.prototype);
  DCLogic.prototype.constructor = DCLogic;

  window.html = htm.bind(h);
  window.DS = window.ACMEDesignSystem_739be1;
  window.DCLogic = DCLogic;

  window.mount = function (Component, template, defaults) {
    Component.prototype.render = function () {
      return template(this.renderVals());
    };
    ReactDOM.createRoot(document.getElementById('root'))
      .render(React.createElement(Component, defaults || {}));
  };
})();
