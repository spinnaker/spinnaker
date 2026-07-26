import { cancellableTimeout } from './cancellableTimeout';

type ScheduledTimeout = Promise<unknown>;
type RetryTimeout = {
  <T>(callback: () => T | PromiseLike<T>, delay?: number): Promise<T>;
  cancel(promise?: any): boolean;
};

export interface IRetryablePromise<T> {
  cancel: () => void;
  promise: Promise<T>;
}

export const retryablePromise = <T>(
  closure: () => PromiseLike<T>,
  interval = 1000,
  maxTries = 0,
  timeout: RetryTimeout = cancellableTimeout,
): IRetryablePromise<T> => {
  let currentTimeout: ScheduledTimeout;
  let currentTries = 0;
  const scheduleTimeout = (fn: () => Promise<T>, delay: number): ScheduledTimeout => {
    return timeout(fn, delay);
  };
  const cancelTimeout = (scheduledTimeout: ScheduledTimeout): void => {
    timeout.cancel(scheduledTimeout);
  };
  const retryPromise: () => Promise<T> = () => {
    currentTries++;
    if (maxTries === 0 || currentTries <= maxTries) {
      return Promise.resolve(closure()).catch(() => {
        currentTimeout = scheduleTimeout(retryPromise, interval);
        return currentTimeout as Promise<T>;
      });
    } else {
      return Promise.resolve(closure());
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
