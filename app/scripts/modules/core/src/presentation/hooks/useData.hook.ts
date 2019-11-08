import { IPromise } from 'angular';
import { isNil } from 'lodash';

import { useLatestPromise, IUseLatestPromiseResult } from './useLatestPromise.hook';

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
 * @param deps array of dependencies, which (when changed) cause the callback to be invoked again
 * @returns an object with the result and current status of the promise
 */
export function useData<T>(callback: () => IPromise<T>, defaultResult: T, deps: any[]): IUseLatestPromiseResult<T> {
  const anyDepsMissing = deps.some(dep => isNil(dep));
  const result = useLatestPromise<T>(anyDepsMissing ? () => null : callback, deps);
  const useDefaultValue = result.requestId === 0 && result.status !== 'RESOLVED';
  return useDefaultValue ? { ...result, result: defaultResult } : result;
}
