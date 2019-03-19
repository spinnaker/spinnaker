import { IPromise } from 'angular';
import { $timeout } from 'ngimport';

export interface IRetryablePromise<T> {
  cancel: () => void;
  promise: IPromise<T>;
}

export const retryablePromise: <T>(
  closure: () => IPromise<T>,
  interval?: number,
  maxTries?: number,
) => IRetryablePromise<T> = <T>(closure: () => IPromise<T>, interval = 1000, maxTries = 0) => {
  let currentTimeout: IPromise<T>;
  let currentTries = 0;
  const retryPromise: () => IPromise<T> = () => {
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
