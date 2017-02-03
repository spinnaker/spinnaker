import {module} from 'angular';

export class RetryService {

  static get inject(): string[] {
    return ['$timeout', '$q'];
  }

  constructor(private $timeout: ng.ITimeoutService, private $q: ng.IQService) {}

  // interval is in milliseconds
  public buildRetrySequence<T>(func: () => T | ng.IPromise<T>,
                            stopCondition: (results: T) => boolean,
                            limit: number,
                            interval: number): ng.IPromise<T> {

    const call: T | ng.IPromise<T> = func();
    const promise: ng.IPromise<T> = call.hasOwnProperty('then') ? call as ng.IPromise<T> : this.$q.resolve(call);
    if (limit === 0) {
      return promise;
    } else {
      return promise.then((result: T) => {
        if (stopCondition(result)) {
          return result;
        } else {
          return this.$timeout(interval).then(() => this.buildRetrySequence(func, stopCondition, limit - 1, interval));
        }
      }).catch(() => this.$timeout(interval).then(
        () => this.buildRetrySequence(func, stopCondition, limit - 1, interval))
      );
    }
  }
}

export const RETRY_SERVICE = 'spinnaker.deck.core.retry.service';
module(RETRY_SERVICE, [])
  .service('retryService', RetryService);
