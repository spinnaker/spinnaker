import React from 'react';

import { useLatestCallback } from './useLatestCallback.hook';

const { useEffect } = React;

export const useEventListener = (
  element: EventTarget,
  eventName: string,
  listener?: (event: Event) => any,
  options?: AddEventListenerOptions,
) => {
  const memoizedListener = useLatestCallback(listener);

  useEffect(() => {
    if (!listener) {
      return undefined;
    }

    element.addEventListener(eventName, memoizedListener, options);

    return () => {
      element.removeEventListener(eventName, memoizedListener, options);
    };
  }, [
    element,
    eventName,
    !!listener,
    Object.values(options || {})
      .sort()
      .join(),
  ]);
};
