import { DependencyList, useEffect, useRef, useState } from 'react';
import { useIsMountedRef } from './useIsMountedRef.hook';

export type IRequestStatus = 'NONE' | 'PENDING' | 'REJECTED' | 'RESOLVED';

export interface IUseLatestPromiseResult<T> {
  // The value of the resolved promise returned from the callback
  result: T;
  // The status of the latest promise returned from the callback
  status: IRequestStatus;
  // The value of the rejected promise
  error: any;
  // A function that causes the callback to be invoked again
  refresh: () => void;
  // The current request ID -- could be used to count requests made, for example
  requestId: number;
}

type IPromiseState<T> = Pick<IUseLatestPromiseResult<T>, 'result' | 'status' | 'error' | 'requestId'>;
const initialPromiseState: IPromiseState<any> = {
  error: undefined,
  requestId: 0,
  result: undefined,
  status: 'NONE',
};

/**
 * A react hook which invokes a callback that returns a promise.
 * If multiple requests are made concurrently, only returns data from the latest request.
 *
 * This can be useful when fetching data based on a users keyboard input, for example.
 * This behavior is similar to RxJS switchMap.
 *
 * example:
 * const fetch = useLatestPromise(() => fetch(url + '?foo=" + foo).then(x=>x.json()), [foo]);
 * return (fetch.status === 'RESOLVED' ? <pre>{JSON.stringify(fetch.result, null, 2)}</pre> :
 *         fetch.status === 'REJECTED' ? <span>Error: {fetch.error}</span> :
 *         fetch.status === 'PENDING' ? <span>Loading...</span> : null);
 *
 * @param callback a callback that returns a PromiseLike
 * @param deps array of dependencies, which (when changed) cause the callback to be invoked again
 * @returns an object with the result and current status of the promise
 */
export function useLatestPromise<T>(callback: () => PromiseLike<T>, deps: DependencyList): IUseLatestPromiseResult<T> {
  const isMountedRef = useIsMountedRef();
  // Capture the isMountedRef.current value before effects run
  const isInitialRender = !isMountedRef.current;
  const requestInFlight = useRef<PromiseLike<T>>();
  // A counter that is used to trigger the promise handling useEffect
  const [requestIdTrigger, setRequestIdTrigger] = useState(0);
  const [promiseState, setPromiseState] = useState<IPromiseState<T>>(initialPromiseState);
  const { result, error, status, requestId } = promiseState;

  // Starts a new request (runs the callback again)
  const refresh = () => setRequestIdTrigger((id) => id + 1);

  // refresh whenever any dependency in the dependency list changes
  useEffect(() => {
    !isInitialRender && refresh();
  }, deps);

  // Invokes the callback and manages its lifecycle.
  // This is triggered when the requestId changes
  useEffect(() => {
    const promise = callback();
    const isCurrent = () => isMountedRef.current && promise === requestInFlight.current;

    // If no promise is returned from the callback, noop this effect.
    if (!promise) {
      return;
    }

    // Don't clear out previous error/result when a new request is pending
    setPromiseState({ status: 'PENDING', error, result, requestId: requestIdTrigger });
    requestInFlight.current = promise;

    const resolve = (newResult: T) => {
      if (isCurrent()) {
        setPromiseState({ status: 'RESOLVED', result: newResult, error: undefined, requestId: requestIdTrigger });
      }
    };

    const reject = (rejection: any) => {
      if (isCurrent()) {
        setPromiseState({ status: 'REJECTED', result: undefined, error: rejection, requestId: requestIdTrigger });
      }
    };

    promise.then(resolve, reject);
  }, [requestIdTrigger]);

  return { result, status, error, refresh, requestId };
}
