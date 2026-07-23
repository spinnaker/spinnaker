import { $timeout } from 'ngimport';

type CancellableTimeout = PromiseLike<unknown> & { timeoutId?: ReturnType<typeof setTimeout> };

export interface IRetryablePromise<T> {
  cancel: () => void;
  promise: PromiseLike<T>;
}

export const retryablePromise = <T>(
  closure: () => PromiseLike<T>,
  interval = 1000,
  maxTries = 0,
): IRetryablePromise<T> => {
  let currentTimeout: CancellableTimeout;
  let currentTries = 0;
  const scheduleTimeout = (fn: () => PromiseLike<T>, delay: number): CancellableTimeout => {
    if ($timeout) {
      return ($timeout(fn, delay) as unknown) as CancellableTimeout;
    }
    let timeoutId: ReturnType<typeof setTimeout> | undefined;
    const promise = new Promise((resolve) => {
      timeoutId = setTimeout(() => resolve(fn()), delay);
    }) as CancellableTimeout;
    if (timeoutId) {
      promise.timeoutId = timeoutId;
    }
    return promise;
  };
  const cancelTimeout = (timeout: CancellableTimeout): void => {
    if ($timeout) {
      $timeout.cancel(timeout as any);
    } else if (timeout.timeoutId) {
      clearTimeout(timeout.timeoutId);
    }
  };
  const retryPromise: () => PromiseLike<T> = () => {
    currentTries++;
    if (maxTries === 0 || currentTries <= maxTries) {
      return closure().catch(() => {
        currentTimeout = scheduleTimeout(retryPromise, interval);
        return currentTimeout as PromiseLike<T>;
      });
    } else {
      return closure();
    }
  };

  const promise = retryPromise();
  const cancel = () => {
    if (currentTimeout) {
      cancelTimeout(currentTimeout);
    }
  };
  return { promise, cancel };
};
