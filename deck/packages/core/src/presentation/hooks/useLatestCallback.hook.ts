import React from 'react';

const { useRef, useCallback } = React;

/**
 * Returns a stable function reference that delegates to the latest version
 * of the unstable function argument. Allows the latest version of the unstable
 * function to access the latest version of its closure.
 */
export const useLatestCallback = <T extends (...args: any) => any>(callback: T) => {
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  return useCallback((...args: Parameters<T>) => {
    return callbackRef.current && (callbackRef.current.apply(null, args) as ReturnType<T>);
  }, []);
};
