import { IPromise } from 'angular';
import { $q } from 'ngimport';
import { isNil } from 'lodash';

import { useLatestPromise, IUseLatestPromiseResult } from './useLatestPromise';

/**
 * A react hook which invokes a promise returning callback whenever any of its dependencies changes.
 * Returns the provided default value whenever any dependency is null or undefined
 *
 * This can be useful when fetching data based on a users keyboard input, for example.
 * This behavior is similar to RxJS switchMap.
 *
 * @param callback the callback to be invoked whenever dependencies change
 * @param defaultValue the default value to return when any dependencies are null or undefined
 * @param deps array of dependencies, which (when changed) cause the callback to be invoked again
 * @returns an object with the result and current status of the promise
 */
export function useData<T>(callback: () => IPromise<T>, defaultValue: T, deps: any[]): IUseLatestPromiseResult<T> {
  const anyDepsMissing = deps.some(dep => isNil(dep));
  const defaultValueCallback = () => $q.resolve(defaultValue);
  return useLatestPromise(anyDepsMissing ? defaultValueCallback : callback, deps);
}
