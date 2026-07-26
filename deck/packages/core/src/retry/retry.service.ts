export class RetryService {
  // interval is in milliseconds
  public static buildRetrySequence<T>(
    func: () => T | PromiseLike<T>,
    stopCondition: (results: T) => boolean,
    limit: number,
    interval: number,
    signal?: AbortSignal,
  ): PromiseLike<T> {
    const abortError = () => {
      const error = new Error('Retry sequence aborted');
      error.name = 'AbortError';
      return error;
    };
    const rejectIfAborted = () => {
      if (signal?.aborted) {
        throw abortError();
      }
    };

    if (signal?.aborted) {
      return Promise.reject(abortError());
    }
    const call: T | PromiseLike<T> = func();
    const promise = Promise.resolve(call);
    const delay = () =>
      new Promise<void>((resolve, reject) => {
        if (signal?.aborted) {
          reject(abortError());
          return;
        }

        const onAbort = () => {
          clearTimeout(timeout);
          reject(abortError());
        };
        const onDelayComplete = () => {
          signal?.removeEventListener('abort', onAbort);
          resolve();
        };
        signal?.addEventListener('abort', onAbort, { once: true });
        const timeout = setTimeout(onDelayComplete, interval);
      });
    if (limit === 0) {
      return promise.then(
        (result) => {
          rejectIfAborted();
          return result;
        },
        (error) => {
          rejectIfAborted();
          throw error;
        },
      );
    } else {
      return promise
        .then((result: T) => {
          rejectIfAborted();
          if (stopCondition(result)) {
            return result;
          } else {
            return delay().then(() => this.buildRetrySequence(func, stopCondition, limit - 1, interval, signal));
          }
        })
        .catch((error) => {
          if (signal?.aborted || error?.name === 'AbortError') {
            throw abortError();
          }
          return delay().then(() => this.buildRetrySequence(func, stopCondition, limit - 1, interval, signal));
        });
    }
  }
}
