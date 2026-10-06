// jsdom performs no layout: element box metrics (offsetWidth, getBoundingClientRect) are always 0,
// and getComputedStyle returns percentage dimensions verbatim ('100%') instead of resolving them to
// pixels. Chrome (Karma) resolved both. A few specs assert resolved pixel dimensions, so provide a
// minimal, well-scoped layout model:
//
//   * getComputedStyle width/height: percentages are resolved to px against the containing block
//     (the viewport at the document root). Non-percentage values are returned unchanged.
//   * box metrics (getBoundingClientRect / offset* / client*): px is used as-is, percentages are
//     resolved as above, and an `auto` width fills the nearest ancestor that has an EXPLICIT pixel
//     width. When no such ancestor exists the value stays 0 — identical to today's jsdom — so the
//     shim only activates for element trees where a test deliberately sets a pixel size.
//
// This keeps the shim inert for the vast majority of the suite while restoring the browser-parity
// the layout-dependent specs rely on.

type Axis = 'width' | 'height';

let nativeGetComputedStyle: typeof window.getComputedStyle;

function viewport(axis: Axis): number {
  return axis === 'width' ? window.innerWidth : window.innerHeight;
}

function isDocumentElement(element: Element): boolean {
  return element === document.documentElement;
}

function specifiedValue(element: Element, axis: Axis): string {
  const inline = (element as HTMLElement).style?.[axis];
  if (inline) {
    return inline;
  }
  try {
    return nativeGetComputedStyle(element).getPropertyValue(axis);
  } catch {
    return '';
  }
}

function asPx(value: string): number | null {
  const match = /^(-?[\d.]+)px$/.exec((value || '').trim());
  return match ? parseFloat(match[1]) : null;
}

function asPercent(value: string): number | null {
  const match = /^([\d.]+)%$/.exec((value || '').trim());
  return match ? parseFloat(match[1]) : null;
}

// Resolves a containing-block dimension for percentage resolution: an `auto` box fills its parent,
// bottoming out at the viewport for the document root.
function resolvedContainingDimension(element: Element, axis: Axis, depth = 0): number {
  if (!element || depth > 100) {
    return viewport(axis);
  }
  const value = specifiedValue(element, axis);
  const px = asPx(value);
  if (px != null) {
    return px;
  }
  const parent = element.parentElement;
  const percent = asPercent(value);
  if (percent != null) {
    const container =
      isDocumentElement(element) || !parent ? viewport(axis) : resolvedContainingDimension(parent, axis, depth + 1);
    return (container * percent) / 100;
  }
  // auto / empty: fill the containing block.
  if (isDocumentElement(element) || !parent) {
    return viewport(axis);
  }
  return resolvedContainingDimension(parent, axis, depth + 1);
}

// The nearest ancestor with an EXPLICIT pixel dimension, or null when none exists.
function nearestExplicitAncestorPx(element: Element, axis: Axis): number | null {
  let node: Element | null = element.parentElement;
  let depth = 0;
  while (node && depth++ < 100) {
    const px = asPx(specifiedValue(node, axis));
    if (px != null) {
      return px;
    }
    node = node.parentElement;
  }
  return null;
}

// Pixel value for a box metric (getBoundingClientRect / offset* / client*).
function boxDimension(element: Element, axis: Axis): number {
  const value = specifiedValue(element, axis);
  const px = asPx(value);
  if (px != null) {
    return px;
  }
  const percent = asPercent(value);
  if (percent != null) {
    const parent = element.parentElement;
    const container =
      isDocumentElement(element) || !parent ? viewport(axis) : resolvedContainingDimension(parent, axis);
    return (container * percent) / 100;
  }
  // auto / empty. Block-level elements fill their container's width; height is content-driven and
  // therefore left at 0. Only fill width when an explicit-pixel ancestor exists (the test signal).
  if (axis === 'width') {
    return nearestExplicitAncestorPx(element, axis) ?? 0;
  }
  return 0;
}

// Total padding + border on the axis, from the native computed style.
function paddingBorderExtent(element: Element, axis: Axis): number {
  let style: CSSStyleDeclaration;
  try {
    style = nativeGetComputedStyle(element);
  } catch {
    return 0;
  }
  const sides = axis === 'width' ? ['left', 'right'] : ['top', 'bottom'];
  return sides.reduce((total, side) => {
    const padding = asPx(style.getPropertyValue(`padding-${side}`)) || 0;
    const border = asPx(style.getPropertyValue(`border-${side}-width`)) || 0;
    return total + padding + border;
  }, 0);
}

// A border-box element's used size cannot be smaller than its padding + border, so browsers floor
// the computed dimension there (keeping content size >= 0). jsdom does not, which makes jQuery's
// content-width math produce spurious negatives; mirror the browser floor.
function applyBorderBoxFloor(element: Element, axis: Axis, value: number): number {
  let boxSizing = '';
  try {
    boxSizing = nativeGetComputedStyle(element).getPropertyValue('box-sizing');
  } catch {
    boxSizing = '';
  }
  if (boxSizing === 'border-box') {
    return Math.max(value, paddingBorderExtent(element, axis));
  }
  return value;
}

// getComputedStyle resolves percentage dimensions to px (browsers do; jsdom does not), and resolves
// an `auto` width to the filled px value when an explicit-pixel ancestor exists. Pixel values are
// returned unchanged, and `auto` without an explicit-pixel ancestor is left as-is so the shim only
// affects element trees a test has explicitly sized.
function computedDimension(element: Element, axis: Axis, nativeValue: string): string {
  if (asPx(nativeValue) != null) {
    return nativeValue;
  }
  const percent = asPercent(nativeValue);
  if (percent != null) {
    const parent = element.parentElement;
    const container =
      isDocumentElement(element) || !parent ? viewport(axis) : resolvedContainingDimension(parent, axis);
    return `${applyBorderBoxFloor(element, axis, (container * percent) / 100)}px`;
  }
  // auto / empty width: block-level elements fill their containing block. Only resolve when an
  // explicit-pixel ancestor exists (the deliberate test signal); otherwise leave 'auto' untouched.
  if (axis === 'width') {
    const ancestorPx = nearestExplicitAncestorPx(element, 'width');
    if (ancestorPx != null) {
      return `${applyBorderBoxFloor(element, axis, ancestorPx)}px`;
    }
  }
  return nativeValue;
}

export function installJsdomLayout(): void {
  const win = (window as unknown) as Record<string, unknown>;
  if (win.__jsdomLayoutInstalled) {
    return;
  }
  win.__jsdomLayoutInstalled = true;

  // Wrap whatever getComputedStyle is currently installed (e.g. the CSS-variable resolver) so both
  // behaviors compose.
  const currentGetComputedStyle = window.getComputedStyle.bind(window);
  nativeGetComputedStyle = currentGetComputedStyle;

  window.getComputedStyle = function layoutGetComputedStyle(
    element: Element,
    pseudoElement?: string | null,
  ): CSSStyleDeclaration {
    const declaration = currentGetComputedStyle(element, pseudoElement as string);
    if (pseudoElement || !element || typeof (element as Element).matches !== 'function') {
      return declaration;
    }
    return new Proxy(declaration, {
      get(target, key) {
        if (key === 'getPropertyValue') {
          return (name: string) => {
            const value = target.getPropertyValue(name);
            return name === 'width' || name === 'height' ? computedDimension(element, name, value) : value;
          };
        }
        if (key === 'width' || key === 'height') {
          return computedDimension(element, key, ((target as unknown) as Record<string, string>)[key]);
        }
        const value = ((target as unknown) as Record<string | symbol, unknown>)[key];
        return typeof value === 'function' ? (value as () => unknown).bind(target) : value;
      },
    });
  } as typeof window.getComputedStyle;

  const nativeGetBoundingClientRect = Element.prototype.getBoundingClientRect;
  Element.prototype.getBoundingClientRect = function patchedGetBoundingClientRect(this: Element): DOMRect {
    const rect = nativeGetBoundingClientRect.call(this);
    const width = boxDimension(this, 'width');
    const height = boxDimension(this, 'height');
    if (!width && !height) {
      return rect;
    }
    const left = rect.left || 0;
    const top = rect.top || 0;
    return {
      x: left,
      y: top,
      left,
      top,
      width,
      height,
      right: left + width,
      bottom: top + height,
      toJSON: () => ({}),
    } as DOMRect;
  };

  const defineBoxMetric = (property: string, axis: Axis) => {
    const proto = (HTMLElement.prototype as unknown) as object;
    const existing = Object.getOwnPropertyDescriptor(proto, property);
    if (existing && !existing.configurable) {
      return;
    }
    Object.defineProperty(proto, property, {
      configurable: true,
      get(this: HTMLElement) {
        return Math.round(boxDimension(this, axis));
      },
    });
  };
  defineBoxMetric('offsetWidth', 'width');
  defineBoxMetric('offsetHeight', 'height');
  defineBoxMetric('clientWidth', 'width');
  defineBoxMetric('clientHeight', 'height');
}
