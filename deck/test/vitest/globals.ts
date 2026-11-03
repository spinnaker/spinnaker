// Side-effect module: establishes the jQuery browser globals BEFORE any other import runs.
// ES modules hoist `import` statements, so this must be imported first (as a side-effect) to
// guarantee `$`/`jQuery` exist on the global before dependents like Bootstrap are evaluated.
import jQuery from 'jquery';
import lodash from 'lodash';

(globalThis as Record<string, unknown>).$ = (globalThis as Record<string, unknown>).jQuery = jQuery;
// Deck's ESLint config declares `_` as a global; some legacy specs use bare `_` (lodash).
(globalThis as Record<string, unknown>)._ = lodash;

// jsdom does not implement HTMLElement.prototype.innerText (only textContent). Chrome (Karma) did,
// and some specs read `element.innerText`. Map it to textContent, which is equivalent for the
// plain-text nodes these assertions inspect.
const HtmlElement = (globalThis as Record<string, unknown>).HTMLElement as { prototype: HTMLElement } | undefined;
if (HtmlElement && !Object.getOwnPropertyDescriptor(HtmlElement.prototype, 'innerText')) {
  Object.defineProperty(HtmlElement.prototype, 'innerText', {
    configurable: true,
    get(this: HTMLElement) {
      // Approximate innerText: browsers collapse whitespace runs and trim, unlike textContent.
      return (this.textContent || '').replace(/\s+/g, ' ').trim();
    },
    set(this: HTMLElement, value: string) {
      this.textContent = value;
    },
  });
}

// jsdom does not implement document.execCommand('copy') (it throws "Not implemented"), whereas
// Chrome (Karma) executed it. Clipboard components branch on it succeeding; provide a no-op that
// reports success so the copy-success path is exercised as it was under the browser.
const doc = (globalThis as Record<string, unknown>).document as Document | undefined;
if (doc) {
  ((doc as unknown) as { execCommand: (command: string) => boolean }).execCommand = () => true;
}

// jsdom has no Web Worker. Chrome (Karma) did; the ACE editor (brace) creates a worker for
// syntax validation. Provide an inert Worker so editor-backed components mount without throwing.
if (typeof (globalThis as Record<string, unknown>).Worker === 'undefined') {
  class InertWorker {
    public onmessage: ((event: unknown) => void) | null = null;
    public onerror: ((event: unknown) => void) | null = null;
    public postMessage(): void {}
    public terminate(): void {}
    public addEventListener(): void {}
    public removeEventListener(): void {}
    public dispatchEvent(): boolean {
      return false;
    }
  }
  (globalThis as Record<string, unknown>).Worker = InertWorker;
}
