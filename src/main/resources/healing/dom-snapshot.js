(el, arg) => {
  // In-page collector for the self-healing engine; see PlaywrightAdapter.
  //   describe  - fingerprint of el, the element a working selector resolved to
  //   snapshot  - fingerprints of every visible element; the nodes stay on window.__tsHeal
  //   selectors - selectors for window.__tsHeal.els[arg.index], most robust first
  //   release   - forget the snapshot
  // Never reads input values: a fingerprint must not capture what was typed.

  const MAX_TEXT = 80;
  // Containers holding more text than this are not identified by their text.
  const MAX_TEXT_SOURCE = 400;
  const SKIP = new Set(['script', 'style', 'noscript', 'template', 'head', 'meta', 'link', 'title', 'br', 'wbr']);
  const NON_TEXT_INPUTS = ['button', 'submit', 'reset', 'image', 'checkbox', 'radio', 'hidden', 'file', 'range', 'color'];
  const TEST_ID_ATTRS = ['data-testid', 'data-test-id', 'data-test', 'data-qa', 'data-cy'];

  const norm = s => (s == null ? '' : String(s)).replace(/\s+/g, ' ').trim();
  const clip = s => { const t = norm(s); return t.length > MAX_TEXT ? t.slice(0, MAX_TEXT) : t; };
  const attr = (e, n) => { const v = e.getAttribute(n); return v == null || v === '' ? null : clip(v); };
  const tagOf = e => e.tagName.toLowerCase();
  const inputType = e => (e.getAttribute('type') || 'text').toLowerCase();
  const isField = e => ['input', 'textarea', 'select'].includes(tagOf(e));

  // Generated ids (React useId, Radix, MUI, long numbers) change between builds.
  const isStableId = id => /^[A-Za-z][\w-]*$/.test(id) && !/\d{3,}/.test(id)
      && !/^(radix|headlessui|mui|react|ember|rc-)/i.test(id);

  const implicitRole = e => {
    switch (tagOf(e)) {
      case 'a': return e.hasAttribute('href') ? 'link' : null;
      case 'button': return 'button';
      case 'textarea': return 'textbox';
      case 'select': return e.multiple ? 'listbox' : 'combobox';
      case 'img': return 'img';
      case 'nav': return 'navigation';
      case 'main': return 'main';
      case 'aside': return 'complementary';
      case 'form': return 'form';
      case 'dialog': return 'dialog';
      case 'ul': case 'ol': return 'list';
      case 'li': return 'listitem';
      case 'h1': case 'h2': case 'h3': case 'h4': case 'h5': case 'h6': return 'heading';
      case 'input': {
        const t = inputType(e);
        if (['button', 'submit', 'reset', 'image'].includes(t)) return 'button';
        if (t === 'checkbox') return 'checkbox';
        if (t === 'radio') return 'radio';
        if (t === 'range') return 'slider';
        if (t === 'search') return 'searchbox';
        if (t === 'hidden') return null;
        return 'textbox';
      }
      default: return null;
    }
  };
  const roleOf = e => e.getAttribute('role') || implicitRole(e);

  const textOf = e => {
    if (isField(e)) return clip(e.labels ? Array.from(e.labels).map(l => l.textContent).join(' ') : '');
    if (tagOf(e) === 'img') return clip(e.getAttribute('alt'));
    const raw = e.textContent || '';
    if (raw.length > MAX_TEXT_SOURCE) return '';
    return clip(e.innerText !== undefined ? e.innerText : raw);
  };

  const isEditable = e => {
    if (e.disabled || e.readOnly) return false;
    const tag = tagOf(e);
    if (tag === 'input') return !NON_TEXT_INPUTS.includes(inputType(e));
    return tag === 'textarea' || tag === 'select' || e.isContentEditable;
  };

  // Same rule as Playwright: a non-empty box and not visibility:hidden.
  const isVisible = e => {
    const r = e.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    const s = getComputedStyle(e);
    return s.visibility !== 'hidden' && s.display !== 'none';
  };

  const ancestorName = e => {
    const tag = tagOf(e);
    if (e.id && isStableId(e.id)) return tag + '#' + e.id;
    const cls = Array.from(e.classList).slice(0, 2);
    return cls.length ? tag + '.' + cls.join('.') : tag;
  };

  const fingerprint = e => {
    const tag = tagOf(e);
    const r = e.getBoundingClientRect();
    const ancestors = [];
    for (let p = e.parentElement; p && ancestors.length < 4 && p !== document.body
        && p !== document.documentElement; p = p.parentElement) {
      ancestors.push(ancestorName(p));
    }
    const sameTag = e.parentElement
        ? Array.from(e.parentElement.children).filter(c => c.tagName === e.tagName) : [e];
    let testId = null;
    for (const a of TEST_ID_ATTRS) { testId = testId || attr(e, a); }
    return {
      tag,
      id: attr(e, 'id'),
      testId,
      name: attr(e, 'name'),
      type: tag === 'input' ? inputType(e) : (tag === 'button' ? attr(e, 'type') : null),
      role: roleOf(e),
      ariaLabel: attr(e, 'aria-label'),
      placeholder: attr(e, 'placeholder'),
      title: attr(e, 'title'),
      href: attr(e, 'href'),
      text: textOf(e) || null,
      classes: Array.from(e.classList).slice(0, 12),
      ancestors,
      siblingIndex: sameTag.indexOf(e),
      x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height),
      editable: isEditable(e)
    };
  };

  const cssEsc = v => (window.CSS && CSS.escape) ? CSS.escape(v) : String(v).replace(/([^\w-])/g, '\\$1');
  const quote = v => '"' + String(v).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"';
  const cssName = e => {
    const tag = tagOf(e);
    if (e.id && isStableId(e.id)) return tag + '#' + cssEsc(e.id);
    const cls = Array.from(e.classList).slice(0, 2).map(cssEsc);
    return cls.length ? tag + '.' + cls.join('.') : tag;
  };

  const selectorsFor = e => {
    const tag = tagOf(e);
    const out = [];
    const isOnly = s => {
      try { const m = document.querySelectorAll(s); return m.length === 1 && m[0] === e; } catch (_) { return false; }
    };
    const isOnlyXPath = xp => {
      try {
        const r = document.evaluate(xp, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
        return r.snapshotLength === 1 && r.snapshotItem(0) === e;
      } catch (_) { return false; }
    };
    // CSS is verified here; Playwright-only syntax is verified by the caller.
    const css = s => { if (!out.includes(s) && isOnly(s)) out.push(s); };
    const engine = s => { if (!out.includes(s)) out.push(s); };

    for (const a of TEST_ID_ATTRS) { const v = e.getAttribute(a); if (v) css(`[${a}=${quote(v)}]`); }
    if (e.id && isStableId(e.id)) css('#' + cssEsc(e.id));

    const role = roleOf(e);
    const label = norm(e.getAttribute('aria-label')) || (isField(e) ? '' : norm(e.textContent));
    if (role && label && label.length <= 60) engine(`role=${role}[name=${quote(label)}s]`);

    for (const a of ['aria-label', 'name', 'placeholder', 'title']) {
      const v = e.getAttribute(a);
      if (v) css(`${tag}[${a}=${quote(v)}]`);
    }

    const text = norm(e.textContent);
    if (!isField(e) && text && text.length <= 60) {
      engine(`${tag}:text-is(${quote(text)})`);
      if (!text.includes('"')) {
        const xp = `//${tag}[normalize-space(.)="${text}"]`;
        if (isOnlyXPath(xp)) out.push('xpath=' + xp);
      }
    }

    const classes = Array.from(e.classList).filter(c => /^[A-Za-z_-][\w-]*$/.test(c));
    for (const c of classes) css(`${tag}.${cssEsc(c)}`);
    for (let i = 0; i < classes.length; i++) {
      for (let j = i + 1; j < classes.length; j++) css(`${tag}.${cssEsc(classes[i])}.${cssEsc(classes[j])}`);
    }

    const parent = e.parentElement;
    if (parent && parent !== document.body) {
      const scope = cssName(parent);
      for (const c of classes) css(`${scope} > ${tag}.${cssEsc(c)}`);
      const sameTag = Array.from(parent.children).filter(c => c.tagName === e.tagName);
      css(`${scope} > ${tag}:nth-of-type(${sameTag.indexOf(e) + 1})`);
    }
    return out;
  };

  switch (arg && arg.mode) {
    case 'describe':
      return JSON.stringify(fingerprint(el));
    case 'snapshot': {
      const els = [];
      const out = [];
      const all = document.body ? document.body.getElementsByTagName('*') : [];
      for (const e of all) {
        if (els.length >= arg.limit) break;
        if (SKIP.has(tagOf(e)) || e.ownerSVGElement) continue;
        if (!isVisible(e)) continue;
        els.push(e);
        out.push(fingerprint(e));
      }
      window.__tsHeal = { els };
      return JSON.stringify(out);
    }
    case 'selectors': {
      const e = window.__tsHeal && window.__tsHeal.els[arg.index];
      return JSON.stringify(e && e.isConnected ? selectorsFor(e) : []);
    }
    case 'release':
      delete window.__tsHeal;
      return null;
    default:
      throw new Error('dom-snapshot: unknown mode ' + (arg && arg.mode));
  }
}
