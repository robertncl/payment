/* @ds-bundle: {"format":4,"namespace":"ACMEDesignSystem_739be1","components":[{"name":"Button","sourcePath":"components/actions/Button.jsx"},{"name":"Wordmark","sourcePath":"components/brand/Wordmark.jsx"},{"name":"Alert","sourcePath":"components/display/Alert.jsx"},{"name":"Badge","sourcePath":"components/display/Badge.jsx"},{"name":"Card","sourcePath":"components/display/Card.jsx"},{"name":"Table","sourcePath":"components/display/Table.jsx"},{"name":"Choice","sourcePath":"components/forms/Choice.jsx"},{"name":"Field","sourcePath":"components/forms/Field.jsx"},{"name":"Input","sourcePath":"components/forms/Input.jsx"},{"name":"Select","sourcePath":"components/forms/Select.jsx"},{"name":"Switch","sourcePath":"components/forms/Switch.jsx"},{"name":"Textarea","sourcePath":"components/forms/Textarea.jsx"},{"name":"Breadcrumbs","sourcePath":"components/navigation/Breadcrumbs.jsx"},{"name":"Tabs","sourcePath":"components/navigation/Tabs.jsx"},{"name":"Topbar","sourcePath":"components/navigation/Topbar.jsx"},{"name":"Modal","sourcePath":"components/overlay/Modal.jsx"}],"sourceHashes":{"components/actions/Button.jsx":"3549ee5e9e12","components/brand/Wordmark.jsx":"e414bf9f640e","components/display/Alert.jsx":"b30f894c690e","components/display/Badge.jsx":"1ce1faaae73b","components/display/Card.jsx":"396448c02533","components/display/Table.jsx":"9c6d3bb9f89f","components/forms/Choice.jsx":"0e5c5c883e43","components/forms/Field.jsx":"34b264d43c87","components/forms/Input.jsx":"a89af1f8b786","components/forms/Select.jsx":"730218c777a2","components/forms/Switch.jsx":"1436c0df8246","components/forms/Textarea.jsx":"8db51ce9bc53","components/navigation/Breadcrumbs.jsx":"036d727f9ee9","components/navigation/Tabs.jsx":"6497e4a7e676","components/navigation/Topbar.jsx":"3c7b98b937a1","components/overlay/Modal.jsx":"5d97745d868d"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.ACMEDesignSystem_739be1 = window.ACMEDesignSystem_739be1 || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// components/actions/Button.jsx
try { (() => {
function Button({
  variant = 'secondary',
  size,
  className = '',
  children,
  ...rest
}) {
  const cls = ['acme-btn', 'acme-btn--' + variant, size ? 'acme-btn--' + size : '', className].filter(Boolean).join(' ');
  return /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: cls,
    ...rest
  }, children);
}
Object.assign(__ds_scope, { Button });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/actions/Button.jsx", error: String((e && e.message) || e) }); }

// components/brand/Wordmark.jsx
try { (() => {
function Wordmark({
  href = '/',
  markOnly = false,
  onDark = false,
  className = '',
  style,
  ...rest
}) {
  const cls = ['acme-wordmark', onDark ? 'acme-wordmark--on-dark' : '', className].filter(Boolean).join(' ');
  if (markOnly) {
    return /*#__PURE__*/React.createElement("span", {
      className: cls,
      style: style,
      role: "img",
      "aria-label": "ACME",
      ...rest
    }, /*#__PURE__*/React.createElement("span", {
      className: "acme-wordmark__mark",
      "aria-hidden": "true"
    }, "A"));
  }
  return /*#__PURE__*/React.createElement("a", {
    className: cls,
    href: href,
    style: style,
    ...rest
  }, /*#__PURE__*/React.createElement("span", {
    className: "acme-wordmark__mark",
    "aria-hidden": "true"
  }, "A"), "ACME");
}
Object.assign(__ds_scope, { Wordmark });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/brand/Wordmark.jsx", error: String((e && e.message) || e) }); }

// components/display/Alert.jsx
try { (() => {
const PATHS = {
  success: ['M20 6 9 17l-5-5'],
  warning: ['M12 3 2 20h20L12 3z', 'M12 10v4', 'M12 17h.01'],
  danger: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M15 9l-6 6', 'M9 9l6 6'],
  info: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 16v-4', 'M12 8h.01']
};
function StatusIcon({
  variant,
  size
}) {
  const d = PATHS[variant];
  if (!d) return null;
  return /*#__PURE__*/React.createElement("svg", {
    className: "acme-status-icon",
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: "2",
    strokeLinecap: "round",
    strokeLinejoin: "round",
    "aria-hidden": "true",
    focusable: "false"
  }, d.map((p, i) => /*#__PURE__*/React.createElement("path", {
    key: i,
    d: p
  })));
}
function Alert({
  variant = 'info',
  title,
  icon = true,
  children
}) {
  // Only danger interrupts a screen reader; everything else is announced politely.
  const role = variant === 'danger' ? 'alert' : 'status';
  return /*#__PURE__*/React.createElement("div", {
    className: 'acme-alert acme-alert--' + variant,
    role: role
  }, icon && /*#__PURE__*/React.createElement("span", {
    className: "acme-alert__icon"
  }, /*#__PURE__*/React.createElement(StatusIcon, {
    variant: variant,
    size: 16
  })), /*#__PURE__*/React.createElement("div", null, title && /*#__PURE__*/React.createElement("p", {
    className: "acme-alert__title"
  }, title), typeof children === 'string' ? /*#__PURE__*/React.createElement("p", null, children) : children));
}
Object.assign(__ds_scope, { Alert });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/display/Alert.jsx", error: String((e && e.message) || e) }); }

// components/display/Badge.jsx
try { (() => {
// Status glyphs drawn on a 24px grid, 2px stroke: they keep status legible without colour (WCAG 1.4.1).
const PATHS = {
  success: ['M20 6 9 17l-5-5'],
  warning: ['M12 3 2 20h20L12 3z', 'M12 10v4', 'M12 17h.01'],
  danger: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M15 9l-6 6', 'M9 9l6 6'],
  info: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 16v-4', 'M12 8h.01']
};
function StatusIcon({
  variant,
  size
}) {
  const d = PATHS[variant];
  if (!d) return null;
  return /*#__PURE__*/React.createElement("svg", {
    className: "acme-status-icon",
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: "2.25",
    strokeLinecap: "round",
    strokeLinejoin: "round",
    "aria-hidden": "true",
    focusable: "false"
  }, d.map((p, i) => /*#__PURE__*/React.createElement("path", {
    key: i,
    d: p
  })));
}
function Badge({
  variant = 'neutral',
  icon = true,
  children
}) {
  const v = variant === 'neutral' ? '' : variant;
  return /*#__PURE__*/React.createElement("span", {
    className: 'acme-badge' + (v ? ' acme-badge--' + v : '')
  }, icon && v && /*#__PURE__*/React.createElement(StatusIcon, {
    variant: v,
    size: 12
  }), children);
}
Object.assign(__ds_scope, { Badge });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/display/Badge.jsx", error: String((e && e.message) || e) }); }

// components/display/Card.jsx
try { (() => {
function Card({
  media,
  title,
  footer,
  interactive = false,
  style,
  children,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("article", {
    className: 'acme-card' + (interactive ? ' acme-card--interactive' : ''),
    style: style,
    ...rest
  }, media !== undefined && /*#__PURE__*/React.createElement("div", {
    className: "acme-card__media"
  }, media), /*#__PURE__*/React.createElement("div", {
    className: "acme-card__body"
  }, title && /*#__PURE__*/React.createElement("h3", {
    className: "acme-card__title"
  }, title), children), footer && /*#__PURE__*/React.createElement("div", {
    className: "acme-card__footer"
  }, footer));
}
Object.assign(__ds_scope, { Card });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/display/Card.jsx", error: String((e && e.message) || e) }); }

// components/display/Table.jsx
try { (() => {
const VISUALLY_HIDDEN = {
  position: 'absolute',
  width: 1,
  height: 1,
  padding: 0,
  margin: -1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
  border: 0
};
function Table({
  columns,
  rows,
  caption,
  children
}) {
  const cap = caption && /*#__PURE__*/React.createElement("caption", {
    style: VISUALLY_HIDDEN
  }, caption);
  if (children) return /*#__PURE__*/React.createElement("table", {
    className: "acme-table"
  }, cap, children);
  return /*#__PURE__*/React.createElement("table", {
    className: "acme-table"
  }, cap, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, (columns || []).map(c => /*#__PURE__*/React.createElement("th", {
    key: c.key,
    scope: "col",
    className: c.numeric ? 'acme-table__num' : undefined
  }, c.label)))), /*#__PURE__*/React.createElement("tbody", null, (rows || []).map((r, i) => /*#__PURE__*/React.createElement("tr", {
    key: i
  }, (columns || []).map(c => /*#__PURE__*/React.createElement("td", {
    key: c.key,
    className: c.numeric ? 'acme-table__num' : undefined
  }, r[c.key]))))));
}
Object.assign(__ds_scope, { Table });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/display/Table.jsx", error: String((e && e.message) || e) }); }

// components/forms/Choice.jsx
try { (() => {
function Choice({
  type = 'checkbox',
  label,
  description,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("label", {
    className: "acme-choice"
  }, /*#__PURE__*/React.createElement("input", {
    type: type,
    ...rest
  }), /*#__PURE__*/React.createElement("span", null, label, description && /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'block',
      fontSize: 'var(--acme-text-xs)',
      color: 'var(--acme-color-text-muted)'
    }
  }, description)));
}
Object.assign(__ds_scope, { Choice });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Choice.jsx", error: String((e && e.message) || e) }); }

// components/forms/Field.jsx
try { (() => {
function Field({
  label,
  optional = false,
  help,
  error,
  htmlFor,
  children
}) {
  const auto = React.useId();
  const only = React.Children.count(children) === 1 && React.isValidElement(children) ? children : null;
  const controlId = htmlFor || only && only.props.id || (only ? 'acme-field' + auto.replace(/:/g, '') : undefined);
  const msgId = controlId ? controlId + '-msg' : undefined;
  const msg = error || help;
  let control = children;
  if (only) {
    const describedBy = [only.props['aria-describedby'], msg ? msgId : null].filter(Boolean).join(' ') || undefined;
    control = React.cloneElement(only, {
      id: controlId,
      'aria-describedby': describedBy,
      ...(error ? typeof only.type === 'string' ? {
        'aria-invalid': true
      } : {
        invalid: only.props.invalid ?? true
      } : null)
    });
  }
  return /*#__PURE__*/React.createElement("div", {
    className: "acme-field"
  }, label && /*#__PURE__*/React.createElement("label", {
    className: "acme-label",
    htmlFor: controlId
  }, label, optional && /*#__PURE__*/React.createElement("span", {
    className: "acme-label__optional"
  }, " (optional)")), control, error ? /*#__PURE__*/React.createElement("p", {
    className: "acme-error",
    id: msgId
  }, error) : help ? /*#__PURE__*/React.createElement("p", {
    className: "acme-help",
    id: msgId
  }, help) : null);
}
Object.assign(__ds_scope, { Field });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Field.jsx", error: String((e && e.message) || e) }); }

// components/forms/Input.jsx
try { (() => {
function Input({
  invalid = false,
  className = '',
  ...rest
}) {
  return /*#__PURE__*/React.createElement("input", {
    className: ('acme-input ' + className).trim(),
    "aria-invalid": invalid || undefined,
    ...rest
  });
}
Object.assign(__ds_scope, { Input });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Input.jsx", error: String((e && e.message) || e) }); }

// components/forms/Select.jsx
try { (() => {
function Select({
  invalid = false,
  className = '',
  children,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("select", {
    className: ('acme-input ' + className).trim(),
    "aria-invalid": invalid || undefined,
    ...rest
  }, children);
}
Object.assign(__ds_scope, { Select });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Select.jsx", error: String((e && e.message) || e) }); }

// components/forms/Switch.jsx
try { (() => {
function Switch({
  label,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("label", {
    className: "acme-switch"
  }, /*#__PURE__*/React.createElement("input", {
    type: "checkbox",
    role: "switch",
    ...rest
  }), label && /*#__PURE__*/React.createElement("span", null, label));
}
Object.assign(__ds_scope, { Switch });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Switch.jsx", error: String((e && e.message) || e) }); }

// components/forms/Textarea.jsx
try { (() => {
function Textarea({
  invalid = false,
  className = '',
  ...rest
}) {
  return /*#__PURE__*/React.createElement("textarea", {
    className: ('acme-input ' + className).trim(),
    "aria-invalid": invalid || undefined,
    ...rest
  });
}
Object.assign(__ds_scope, { Textarea });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Textarea.jsx", error: String((e && e.message) || e) }); }

// components/navigation/Breadcrumbs.jsx
try { (() => {
function Breadcrumbs({
  items = []
}) {
  return /*#__PURE__*/React.createElement("nav", {
    "aria-label": "Breadcrumb"
  }, /*#__PURE__*/React.createElement("div", {
    className: "acme-breadcrumbs"
  }, items.map((it, i) => {
    const last = i === items.length - 1;
    const label = typeof it === 'string' ? it : it.label;
    const href = typeof it === 'string' ? '#' : it.href || '#';
    return /*#__PURE__*/React.createElement(React.Fragment, {
      key: i
    }, i > 0 && /*#__PURE__*/React.createElement("span", {
      "aria-hidden": "true"
    }, "/"), last ? /*#__PURE__*/React.createElement("span", {
      "aria-current": "page"
    }, label) : /*#__PURE__*/React.createElement("a", {
      href: href
    }, label));
  })));
}
Object.assign(__ds_scope, { Breadcrumbs });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/Breadcrumbs.jsx", error: String((e && e.message) || e) }); }

// components/navigation/Tabs.jsx
try { (() => {
function Tabs({
  tabs = [],
  active,
  onChange,
  idPrefix,
  label
}) {
  const auto = React.useId().replace(/:/g, '');
  const base = idPrefix || 'acme-tabs' + auto;
  const ids = tabs.map(t => typeof t === 'string' ? t : t.id);
  const current = ids.includes(active) ? active : ids[0];
  const refs = React.useRef({});
  const move = (e, i) => {
    let n = null;
    if (e.key === 'ArrowRight') n = (i + 1) % ids.length;else if (e.key === 'ArrowLeft') n = (i - 1 + ids.length) % ids.length;else if (e.key === 'Home') n = 0;else if (e.key === 'End') n = ids.length - 1;
    if (n === null) return;
    e.preventDefault();
    const el = refs.current[ids[n]];
    if (el) el.focus();
    if (onChange) onChange(ids[n]);
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "acme-tabs",
    role: "tablist",
    "aria-label": label
  }, tabs.map((t, i) => {
    const id = ids[i];
    const text = typeof t === 'string' ? t : t.label;
    const count = typeof t === 'string' ? undefined : t.count;
    const sel = id === current;
    return /*#__PURE__*/React.createElement("button", {
      key: id,
      ref: el => {
        refs.current[id] = el;
      },
      type: "button",
      role: "tab",
      className: "acme-tab",
      id: base + '-tab-' + id,
      "aria-controls": base + '-panel-' + id,
      "aria-selected": sel,
      tabIndex: sel ? 0 : -1,
      onClick: onChange ? () => onChange(id) : undefined,
      onKeyDown: e => move(e, i)
    }, text, count !== undefined && /*#__PURE__*/React.createElement("span", {
      className: "acme-tab__count"
    }, count));
  }));
}
Object.assign(__ds_scope, { Tabs });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/Tabs.jsx", error: String((e && e.message) || e) }); }

// components/navigation/Topbar.jsx
try { (() => {
function Topbar({
  links = [],
  current,
  children
}) {
  return /*#__PURE__*/React.createElement("header", {
    className: "acme-topbar"
  }, /*#__PURE__*/React.createElement(__ds_scope.Wordmark, null), /*#__PURE__*/React.createElement("nav", {
    className: "acme-topbar__nav",
    "aria-label": "Primary"
  }, links.map(l => {
    const label = typeof l === 'string' ? l : l.label;
    const href = typeof l === 'string' ? '#' : l.href || '#';
    return /*#__PURE__*/React.createElement("a", {
      key: label,
      className: "acme-topbar__link",
      href: href,
      "aria-current": label === current ? 'page' : undefined
    }, label);
  })), children && /*#__PURE__*/React.createElement("div", {
    className: "acme-topbar__actions"
  }, children));
}
Object.assign(__ds_scope, { Topbar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/Topbar.jsx", error: String((e && e.message) || e) }); }

// components/overlay/Modal.jsx
try { (() => {
const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
function Modal({
  open = true,
  title,
  footer,
  onClose,
  dismissOnBackdrop = true,
  children
}) {
  const titleId = 'acme-modal' + React.useId().replace(/:/g, '') + '-title';
  const ref = React.useRef(null);
  const closeRef = React.useRef(onClose);
  closeRef.current = onClose;
  React.useEffect(() => {
    if (!open || !ref.current) return undefined;
    const prev = document.activeElement;
    const box = ref.current;
    // Initial focus: the first field, else the safe (non-destructive) first action, else the dialog.
    const first = box.querySelector('.acme-modal__body ' + FOCUSABLE) || box.querySelector('.acme-modal__footer ' + FOCUSABLE) || box;
    first.focus();
    const onKey = e => {
      if (e.key === 'Escape' && closeRef.current) {
        e.stopPropagation();
        closeRef.current();
        return;
      }
      if (e.key !== 'Tab') return;
      const items = Array.from(box.querySelectorAll(FOCUSABLE));
      if (!items.length) {
        e.preventDefault();
        return;
      }
      const a = items[0],
        z = items[items.length - 1];
      if (e.shiftKey && document.activeElement === a) {
        e.preventDefault();
        z.focus();
      } else if (!e.shiftKey && document.activeElement === z) {
        e.preventDefault();
        a.focus();
      }
    };
    box.addEventListener('keydown', onKey);
    return () => {
      box.removeEventListener('keydown', onKey);
      if (prev && prev.focus) prev.focus();
    };
  }, [open]);
  if (!open) return null;
  return /*#__PURE__*/React.createElement("div", {
    className: "acme-modal-backdrop",
    onClick: dismissOnBackdrop && onClose ? e => {
      if (e.target === e.currentTarget) onClose();
    } : undefined
  }, /*#__PURE__*/React.createElement("div", {
    className: "acme-modal",
    role: "dialog",
    "aria-modal": "true",
    "aria-labelledby": title ? titleId : undefined,
    tabIndex: -1,
    ref: ref
  }, /*#__PURE__*/React.createElement("div", {
    className: "acme-modal__header"
  }, /*#__PURE__*/React.createElement("h3", {
    className: "acme-modal__title",
    id: titleId
  }, title), onClose && /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: "acme-btn acme-btn--ghost acme-btn--sm acme-btn--icon",
    "aria-label": "Close",
    onClick: onClose
  }, /*#__PURE__*/React.createElement("svg", {
    width: "16",
    height: "16",
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: "2",
    strokeLinecap: "round",
    "aria-hidden": "true",
    focusable: "false"
  }, /*#__PURE__*/React.createElement("path", {
    d: "M18 6 6 18"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M6 6l12 12"
  })))), /*#__PURE__*/React.createElement("div", {
    className: "acme-modal__body"
  }, children), footer && /*#__PURE__*/React.createElement("div", {
    className: "acme-modal__footer"
  }, footer)));
}
Object.assign(__ds_scope, { Modal });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/overlay/Modal.jsx", error: String((e && e.message) || e) }); }

__ds_ns.Button = __ds_scope.Button;
__ds_ns.Wordmark = __ds_scope.Wordmark;
__ds_ns.Alert = __ds_scope.Alert;
__ds_ns.Badge = __ds_scope.Badge;
__ds_ns.Card = __ds_scope.Card;
__ds_ns.Table = __ds_scope.Table;
__ds_ns.Choice = __ds_scope.Choice;
__ds_ns.Field = __ds_scope.Field;
__ds_ns.Input = __ds_scope.Input;
__ds_ns.Select = __ds_scope.Select;
__ds_ns.Switch = __ds_scope.Switch;
__ds_ns.Textarea = __ds_scope.Textarea;
__ds_ns.Breadcrumbs = __ds_scope.Breadcrumbs;
__ds_ns.Tabs = __ds_scope.Tabs;
__ds_ns.Topbar = __ds_scope.Topbar;
__ds_ns.Modal = __ds_scope.Modal;

})();
