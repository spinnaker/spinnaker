import { useEffect } from 'react';

import { useLatestCallback } from './useLatestCallback.hook';

/**
 * A react hook which invokes a callback function on a specific millisecond interval.
 * Works just like setInterval(), but in a hook.
 *
 * If no callback is provided during a render (i.e. passing null or another falsey value)
 * then no interval will be set and the existing interval will be canceled if one is already running.
 * This is useful for components which only need to run an interval conditionally but not all the time.
 *
 * Passing a different/changed interval will reset the cycle with the new interval time.
 * *
 * @param callback the callback to be invoked at every interval, or null/undefined
 * for no interval at all
 * @param interval the length of time in milliseconds between each interval
 * @returns void
 */
export function useInterval(callback: () => any, interval: number): void {
  const stableCallback = useLatestCallback(callback);

  useEffect(() => {
    const id = !!callback && interval && setInterval(stableCallback, interval);

    return () => id && clearInterval(id);
  }, [!!callback, interval]);
}
