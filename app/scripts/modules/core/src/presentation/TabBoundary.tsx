import React from 'react';

import { useEventListener } from '../index';

export const TabBoundary = ({ children }: { children: React.ReactNode }) => {
  const boundaryRef = React.createRef<HTMLDivElement>();

  const keydownCallback = (event: KeyboardEvent) => {
    const { keyCode, shiftKey, target } = event;
    if (keyCode === 9) {
      let targetOverride = null;
      // tabbable selector via https://kennethbi.com/posts/5
      const tabbableElements: HTMLElement[] = Array.from(
        (boundaryRef.current?.querySelectorAll(`
          input:not([tabindex^="-"]):not([disabled]),
          select:not([tabindex^="-"]):not([disabled]),
          textarea:not([tabindex^="-"]):not([disabled]),
          button:not([tabindex^="-"]):not([disabled]),
          a[href]:not([tabindex^="-"]):not([disabled]),
          [tabindex]:not([tabindex^="-"]):not([disabled])
      `) as NodeListOf<HTMLElement>) ?? [],
      ).filter((ele: HTMLElement) => ele.offsetParent !== null);
      if (!tabbableElements.length) {
        return;
      }
      const firstElem = tabbableElements[0];
      const lastElem = tabbableElements[tabbableElements.length - 1];
      if (firstElem === target && shiftKey) {
        targetOverride = lastElem;
      }
      if (lastElem === target && !shiftKey) {
        targetOverride = firstElem;
      }
      if (!tabbableElements.includes(target as HTMLElement)) {
        targetOverride = shiftKey ? lastElem : firstElem;
      }
      if (targetOverride) {
        targetOverride.focus();
        event.preventDefault();
      }
    }
  };
  useEventListener(document, 'keydown', keydownCallback);

  return <div ref={boundaryRef}>{children}</div>;
};
