import { useEffect } from 'react';

import { SchedulerFactory } from '../../scheduler';

import { useData } from './useData.hook';
import { useLatestCallback } from './useLatestCallback.hook';
import { IUseLatestPromiseResult } from './useLatestPromise.hook';

/**
 * A react hook which invokes a promise factory callback whenever any of its dependencies
 * changes *and* on a specific polling schedule.
 *
 * Returns the default result until the first promise resolves.
 *
 * The promise factory is not called if any of the deps are null or undefined.
 *
 * This can be useful when fetching data based on a users keyboard input, for example.
 * This behavior is similar to RxJS switchMap.
 *
 * @param callback the callback to be invoked whenever dependencies change and on a polling schedule
 * @param defaultResult the default result returned before the first promise resolves
 * @param pollingInterval the time interval (in milliseconds) for how often to invoke the callback
 * @param deps array of dependencies, which (when changed) cause the callback to be invoked again
 * @returns an object with the result and current status of the promise
 */
export function usePollingData<T>(
  callback: () => PromiseLike<T>,
  defaultResult: T,
  pollingInterval: number,
  deps: any[],
): IUseLatestPromiseResult<T> {
  const result = useData<T>(callback, defaultResult, deps);
  const refreshCallback = useLatestCallback(result.refresh);

  useEffect(() => {
    const { subscribe, unsubscribe } = SchedulerFactory.createScheduler(pollingInterval);

    subscribe(refreshCallback);

    return unsubscribe;
  }, deps);

  return result;
}
