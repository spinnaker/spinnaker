export type CancellableTimeoutPromise<T> = Promise<T> & {
  timeoutId?: ReturnType<typeof setTimeout>;
};

export interface CancellableTimeout {
  <T>(callback: () => T | PromiseLike<T>, delay?: number): CancellableTimeoutPromise<T>;
  (delay?: number): CancellableTimeoutPromise<void>;
  cancel: (promise?: Partial<CancellableTimeoutPromise<unknown>>) => boolean;
  dispose: () => void;
}

export function createCancellableTimeout(): CancellableTimeout {
  interface PendingTimeout {
    promise: CancellableTimeoutPromise<unknown>;
    reject: (reason: unknown) => void;
  }

  const pending = new Map<ReturnType<typeof setTimeout>, PendingTimeout>();
  let disposed = false;
  const timeout = (<T>(callbackOrDelay?: (() => T | PromiseLike<T>) | number, delay?: number) => {
    if (disposed) {
      const promise = Promise.reject('canceled') as CancellableTimeoutPromise<T | void>;
      promise.catch(() => undefined);
      return promise;
    }

    const hasCallback = typeof callbackOrDelay === 'function';
    const timeoutDelay = hasCallback ? delay : callbackOrDelay;
    let timeoutId: ReturnType<typeof setTimeout>;
    let rejectPromise: (reason: unknown) => void;
    const promise = new Promise<T | void>((resolve, reject) => {
      rejectPromise = reject;
      timeoutId = setTimeout(() => {
        pending.delete(timeoutId);
        try {
          resolve(hasCallback ? callbackOrDelay() : undefined);
        } catch (error) {
          reject(error);
        }
      }, timeoutDelay ?? 0);
    }) as CancellableTimeoutPromise<T | void>;
    promise.timeoutId = timeoutId;
    pending.set(timeoutId, { promise, reject: rejectPromise });
    promise.catch(() => undefined);
    return promise;
  }) as CancellableTimeout;

  timeout.cancel = (promise) => {
    const timeoutId = promise?.timeoutId;
    const pendingTimeout = timeoutId === undefined ? undefined : pending.get(timeoutId);
    if (!pendingTimeout || pendingTimeout.promise !== promise) {
      return false;
    }

    clearTimeout(timeoutId);
    pending.delete(timeoutId);
    pendingTimeout.reject('canceled');
    return true;
  };
  timeout.dispose = () => {
    if (disposed) {
      return;
    }
    disposed = true;
    pending.forEach(({ reject }, timeoutId) => {
      clearTimeout(timeoutId);
      reject('canceled');
    });
    pending.clear();
  };

  return timeout;
}

export const cancellableTimeout = createCancellableTimeout();
