import { DependencyList, useEffect, useRef, useState } from 'react';
import { IPromise } from 'angular';

export type IRequestStatus = 'NONE' | 'PENDING' | 'REJECTED' | 'RESOLVED';

/**
 * A react hook which invokes a callback that returns a promise.
 * If multiple requests are made concurrently, only returns data from the latest request.
 *
 * This can be useful when fetching data based on a users keyboard input, for example.
 * This behavior is similar to RxJS switchMap.
 *
 * @param callback a callback that returns an IPromise
 * @param deps array of dependencies, which (when changed) cause the callback to be invoked again
 */
export function useLatestPromise<T>(
  callback: () => IPromise<T>,
  deps: DependencyList,
): [T, IRequestStatus, any, number] {
  const requestInFlight = useRef<IPromise<T>>();
  const [status, setStatus] = useState<IRequestStatus>('NONE');
  const [result, setResult] = useState<T>();
  const [error, setError] = useState<any>();
  const [requestId, setRequestId] = useState(0);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    return () => setMounted(false);
  }, []);

  useEffect(() => {
    const promise = callback();
    // If no promise is returned from the callback, noop this effect.
    if (!promise) {
      return;
    }

    setStatus('PENDING');
    setRequestId(requestId + 1);
    requestInFlight.current = promise;

    const resolve = (newResult: T) => {
      if (mounted && promise === requestInFlight.current) {
        setResult(newResult);
        setStatus('RESOLVED');
      }
    };

    const reject = (rejection: any) => {
      if (mounted && promise === requestInFlight.current) {
        setError(rejection);
        setStatus('REJECTED');
      }
    };

    promise.then(resolve, reject);
  }, deps);

  return [result, status, error, requestId];
}
