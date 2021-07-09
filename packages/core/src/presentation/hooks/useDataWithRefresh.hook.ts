import { useEffect } from 'react';
import { useApplicationContextSafe } from './useApplicationContext.hook';

import { useData } from './useData.hook';
import { useLatestCallback } from './useLatestCallback.hook';
import { IUseLatestPromiseResult } from './useLatestPromise.hook';

/**
 * A react hook which invokes a promise factory callback whenever any of its dependencies
 * changes *and* on app-wide refreshes
 *
 * Returns the default result until the first promise resolves.
 *
 * The promise factory is not called if any of the deps are null or undefined.
 *
 * This can be useful when fetching data based on a users keyboard input, for example.
 * This behavior is similar to RxJS switchMap.
 *
 * @param callback the callback to be invoked whenever dependencies change and on a polling schedule
 * @param deps array of dependencies, which (when changed) cause the callback to be invoked again
 * @returns an object with the result and current status of the promise
 */
export function useDataWithRefresh<T>(
  callback: () => PromiseLike<T>,
  defaultResult: T,
  deps: any[],
): IUseLatestPromiseResult<T> {
  const app = useApplicationContextSafe();
  const result = useData<T>(callback, defaultResult, [...deps, app]);
  const refreshCallback = useLatestCallback(result.refresh);

  useEffect(() => {
    if (!app) return () => {};
    const unsubscribe = app.subscribeToRefresh(refreshCallback);
    return unsubscribe;
  }, [app, refreshCallback]);

  return result;
}
