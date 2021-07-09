import { isNil } from 'lodash';
import { useMemo, useRef } from 'react';

import { IUseLatestPromiseResult, useLatestPromise } from './useLatestPromise.hook';

/**
 * A react hook which invokes a promise factory callback whenever any of its dependencies changes.
 *
 * Returns the default result until the first promise resolves.
 *
 * The promise factory is not called if any of the deps are null or undefined.
 *
 * This can be useful when fetching data based on a users keyboard input, for example.
 * This behavior is similar to RxJS switchMap.
 *
 * @param callback the callback to be invoked whenever dependencies change
 * @param defaultResult the default result returned before the first promise resolves
 *                      this value will be memoized on the first render
 * @param deps array of dependencies, which (when changed) cause the callback to be invoked again
 * @returns an object with the result and current status of the promise
 */
export function useData<T>(callback: () => PromiseLike<T>, defaultResult: T, deps: any[]): IUseLatestPromiseResult<T> {
  const memoizedDefaultResult = useMemo(() => defaultResult, []);
  const anyDepsMissing = deps.some((dep) => isNil(dep));
  const result = useLatestPromise<T>(anyDepsMissing ? () => null : callback, deps);
  const hasResolvedAtLeastOnceRef = useRef(false);
  if (result.status === 'RESOLVED') {
    hasResolvedAtLeastOnceRef.current = true;
  }
  return hasResolvedAtLeastOnceRef.current ? result : { ...result, result: memoizedDefaultResult };
}
