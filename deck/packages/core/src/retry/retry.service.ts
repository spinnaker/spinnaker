import { $q, $timeout } from 'ngimport';

export class RetryService {
  // interval is in milliseconds
  public static buildRetrySequence<T>(
    func: () => T | PromiseLike<T>,
    stopCondition: (results: T) => boolean,
    limit: number,
    interval: number,
  ): PromiseLike<T> {
    const call: T | PromiseLike<T> = func();
    const promise: PromiseLike<T> = call.hasOwnProperty('then') ? (call as PromiseLike<T>) : $q.resolve(call);
    if (limit === 0) {
      return promise;
    } else {
      return promise
        .then((result: T) => {
          if (stopCondition(result)) {
            return result;
          } else {
            return $timeout(interval).then(() => this.buildRetrySequence(func, stopCondition, limit - 1, interval));
          }
        })
        .catch(() => $timeout(interval).then(() => this.buildRetrySequence(func, stopCondition, limit - 1, interval)));
    }
  }
}
