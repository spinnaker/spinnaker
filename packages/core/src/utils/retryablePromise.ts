import { $timeout } from 'ngimport';

export interface IRetryablePromise<T> {
  cancel: () => void;
  promise: PromiseLike<T>;
}

export const retryablePromise = <T>(
  closure: () => PromiseLike<T>,
  interval = 1000,
  maxTries = 0,
): IRetryablePromise<T> => {
  let currentTimeout: PromiseLike<T>;
  let currentTries = 0;
  const retryPromise: () => PromiseLike<T> = () => {
    currentTries++;
    if (maxTries === 0 || currentTries <= maxTries) {
      return closure().catch(() => {
        currentTimeout = $timeout(retryPromise, interval);
        return currentTimeout;
      });
    } else {
      return closure();
    }
  };

  const promise = retryPromise();
  const cancel = () => {
    if (currentTimeout) {
      $timeout.cancel(currentTimeout);
    }
  };
  return { promise, cancel };
};
