import { IPromise } from 'angular';
import { $timeout } from 'ngimport';

export interface IRetryablePromise<T> {
  cancel: () => void;
  promise: IPromise<T>;
}

export const retryablePromise: <T>(closure: () => IPromise<T>, interval?: number) => IRetryablePromise<T> = <T>(
  closure: () => IPromise<T>,
  interval = 1000,
) => {
  let currentTimeout: IPromise<T>;
  const retryPromise: () => IPromise<T> = () =>
    closure().catch(() => {
      currentTimeout = $timeout(retryPromise, interval);
      return currentTimeout;
    });

  const promise = retryPromise();
  const cancel = () => {
    if (currentTimeout) {
      $timeout.cancel(currentTimeout);
    }
  };
  return { promise, cancel };
};
