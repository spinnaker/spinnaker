// jsdom's CSS engine (cssstyle) drops any property whose value references a CSS custom property,
// e.g. `border-color: var(--color-danger)`, so `getComputedStyle(...).borderColor` returns the
// inherited/initial fallback instead of the resolved color. Real browsers (Karma/Chrome) resolve
// it, and several specs assert the resolved value.
//
// The raw `var(...)` text DOES survive on `CSSStyleRule.style`, so we recompute the affected
// properties for an element by scanning the matching style rules and resolving custom properties
// from the element's inherited cascade. This mirrors browser behavior without weakening the tests.
//
// The resolver is inert for test files that never load a stylesheet with `var(...)` (the rule
// cache stays empty and getComputedStyle returns the native declaration unchanged), so the
// suite-wide cost is negligible.

interface VarRule {
  selectorText: string;
  decls: Array<{ prop: string; value: string }>;
}

interface CustomPropRule {
  selectorText: string;
  name: string;
  value: string;
}

const SIDE_PROPERTIES: Record<string, string[]> = {
  'border-color': ['border-top-color', 'border-right-color', 'border-bottom-color', 'border-left-color'],
};

const VAR_REFERENCE = /var\(\s*(--[A-Za-z0-9-_]+)\s*(?:,\s*([^)]*))?\)/;

let cacheSignature = '';
let varRules: VarRule[] = [];
let customPropRules: CustomPropRule[] = [];

function computeSignature(): string {
  const sheets = document.styleSheets;
  let signature = String(sheets.length);
  for (let i = 0; i < sheets.length; i++) {
    try {
      signature += ':' + (sheets[i] as CSSStyleSheet).cssRules.length;
    } catch {
      signature += ':x';
    }
  }
  return signature;
}

function collectFromRules(rules: CSSRuleList): void {
  for (let i = 0; i < rules.length; i++) {
    const rule = rules[i] as CSSStyleRule & { cssRules?: CSSRuleList };
    if (rule.selectorText && rule.style) {
      const style = rule.style;
      const decls: Array<{ prop: string; value: string }> = [];
      for (let k = 0; k < style.length; k++) {
        const prop = style.item(k);
        const value = style.getPropertyValue(prop);
        if (prop.startsWith('--')) {
          customPropRules.push({ selectorText: rule.selectorText, name: prop, value: value.trim() });
        } else if (value.indexOf('var(') !== -1) {
          decls.push({ prop, value });
        }
      }
      if (decls.length) {
        varRules.push({ selectorText: rule.selectorText, decls });
      }
    } else if (rule.cssRules) {
      // @media / @supports and other grouping rules.
      collectFromRules(rule.cssRules);
    }
  }
}

function ensureCache(): void {
  const signature = computeSignature();
  if (signature === cacheSignature) {
    return;
  }
  cacheSignature = signature;
  varRules = [];
  customPropRules = [];
  const sheets = document.styleSheets;
  for (let i = 0; i < sheets.length; i++) {
    try {
      collectFromRules((sheets[i] as CSSStyleSheet).cssRules);
    } catch {
      // Cross-origin or otherwise inaccessible sheet; skip.
    }
  }
}

function safeMatches(element: Element, selectorText: string): boolean {
  try {
    return element.matches(selectorText);
  } catch {
    return false;
  }
}

function lookupCustomProperty(element: Element, name: string): string {
  // Custom properties inherit: the nearest ancestor that defines the property wins. Inline styles
  // take priority over rule declarations on the same node.
  let node: Element | null = element;
  while (node) {
    const inline = (node as HTMLElement).style?.getPropertyValue(name);
    if (inline && inline.trim() !== '') {
      return inline.trim();
    }
    let matched = '';
    for (const rule of customPropRules) {
      if (rule.name === name && safeMatches(node, rule.selectorText)) {
        matched = rule.value; // Later source-order declarations win.
      }
    }
    if (matched !== '') {
      return matched;
    }
    node = node.parentElement;
  }
  return '';
}

function resolveVars(element: Element, value: string): string {
  let resolved = value;
  let guard = 0;
  while (guard++ < 20) {
    const match = VAR_REFERENCE.exec(resolved);
    if (!match) {
      break;
    }
    const name = match[1];
    const fallback = match[2] != null ? match[2].trim() : '';
    const replacement = lookupCustomProperty(element, name) || fallback;
    resolved = resolved.slice(0, match.index) + replacement + resolved.slice(match.index + match[0].length);
  }
  return resolved.trim();
}

function dashedName(key: string): string {
  return key.replace(/[A-Z]/g, (character) => '-' + character.toLowerCase());
}

export function installCssVarResolver(): void {
  const win = (window as unknown) as Record<string, unknown>;
  if (win.__cssVarResolverInstalled) {
    return;
  }
  win.__cssVarResolverInstalled = true;

  const nativeGetComputedStyle = window.getComputedStyle.bind(window);

  window.getComputedStyle = function patchedGetComputedStyle(
    element: Element,
    pseudoElement?: string | null,
  ): CSSStyleDeclaration {
    const declaration = nativeGetComputedStyle(element, pseudoElement as string);
    if (pseudoElement || !element || typeof (element as Element).matches !== 'function') {
      return declaration;
    }

    ensureCache();
    if (varRules.length === 0) {
      return declaration;
    }

    const resolved = new Map<string, string>();
    for (const rule of varRules) {
      if (!safeMatches(element, rule.selectorText)) {
        continue;
      }
      for (const { prop, value } of rule.decls) {
        const value2 = resolveVars(element, value);
        if (value2 === '' || VAR_REFERENCE.test(value2)) {
          continue; // Still unresolved: leave the native fallback in place.
        }
        resolved.set(prop, value2);
        const sides = SIDE_PROPERTIES[prop];
        if (sides) {
          sides.forEach((side) => resolved.set(side, value2));
        }
      }
    }

    if (resolved.size === 0) {
      return declaration;
    }

    return new Proxy(declaration, {
      get(target, key) {
        if (key === 'getPropertyValue') {
          return (name: string) => (resolved.has(name) ? resolved.get(name)! : target.getPropertyValue(name));
        }
        if (typeof key === 'string' && resolved.has(dashedName(key))) {
          return resolved.get(dashedName(key))!;
        }
        const value = ((target as unknown) as Record<string | symbol, unknown>)[key];
        return typeof value === 'function' ? (value as () => unknown).bind(target) : value;
      },
    });
  } as typeof window.getComputedStyle;
}
