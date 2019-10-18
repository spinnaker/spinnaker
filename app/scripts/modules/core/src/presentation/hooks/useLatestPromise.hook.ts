import { IPromise } from 'angular';
import { DependencyList, useEffect, useRef, useState } from 'react';

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

/**
 * A react hook which invokes a callback that returns a promise.
 * If multiple requests are made concurrently, only returns data from the latest request.
 *
 * This can be useful when fetching data based on a users keyboard input, for example.
 * This behavior is similar to RxJS switchMap.
 *
 * @param callback a callback that returns an IPromise
 * @param deps array of dependencies, which (when changed) cause the callback to be invoked again
 * @returns an object with the result and current status of the promise
 */
export function useLatestPromise<T>(callback: () => IPromise<T>, deps: DependencyList): IUseLatestPromiseResult<T> {
  const isMounted = useRef(false);
  const requestInFlight = useRef<IPromise<T>>();
  const [status, setStatus] = useState<IRequestStatus>('NONE');
  const [result, setResult] = useState<T>();
  const [error, setError] = useState<any>();
  const [requestId, setRequestId] = useState(0);

  // Starts a new request (runs the callback again)
  const refresh = () => setRequestId(id => id + 1);

  // refresh whenever any dependency in the dependency list changes
  useEffect(() => {
    if (isMounted.current) {
      refresh();
    }
  }, deps);

  // Manage the mount/unmounted state
  useEffect(() => {
    isMounted.current = true;
    return () => (isMounted.current = false);
  }, []);

  // Invokes the callback and manages its lifecycle.
  // This is triggered when the requestId changes
  useEffect(() => {
    const promise = callback();
    const isCurrent = () => isMounted.current === true && promise === requestInFlight.current;

    // If no promise is returned from the callback, noop this effect.
    if (!promise) {
      return;
    }

    setStatus('PENDING');
    requestInFlight.current = promise;

    const resolve = (newResult: T) => {
      if (isCurrent()) {
        setResult(newResult);
        setStatus('RESOLVED');
      }
    };

    const reject = (rejection: any) => {
      if (isCurrent()) {
        setError(rejection);
        setStatus('REJECTED');
      }
    };

    promise.then(resolve, reject);
  }, [requestId]);

  return { result, status, error, refresh, requestId };
}
