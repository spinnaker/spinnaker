import { cancellableTimeout } from './cancellableTimeout';

type ScheduledTimeout = PromiseLike<unknown>;
type RetryTimeout = {
  <T>(callback: () => T | PromiseLike<T>, delay?: number): PromiseLike<T>;
  cancel(promise?: any): boolean;
};

export interface IRetryablePromise<T> {
  cancel: () => void;
  promise: PromiseLike<T>;
}

export const retryablePromise = <T>(
  closure: () => PromiseLike<T>,
  interval = 1000,
  maxTries = 0,
  timeout: RetryTimeout = cancellableTimeout,
): IRetryablePromise<T> => {
  let currentTimeout: ScheduledTimeout;
  let currentTries = 0;
  const scheduleTimeout = (fn: () => PromiseLike<T>, delay: number): ScheduledTimeout => {
    return timeout(fn, delay);
  };
  const cancelTimeout = (scheduledTimeout: ScheduledTimeout): void => {
    timeout.cancel(scheduledTimeout);
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
