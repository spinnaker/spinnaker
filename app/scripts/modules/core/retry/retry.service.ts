import {module} from 'angular';

export class RetryService {

  static get inject(): string[] {
    return ['$timeout', '$q'];
  }

  constructor(private $timeout: ng.ITimeoutService, private $q: ng.IQService) {}

  // interval is in milliseconds
  public buildRetrySequence(func: Function,
                            stopCondition: Function,
                            limit: number,
                            interval: number): ng.IPromise<any> {

    const call: any = func();
    const promise: ng.IPromise<any> = call.then ? call : this.$q.resolve(call);
    if (limit === 0) {
      return promise;
    } else {
      return promise.then((result: any) => {
        if (stopCondition(result)) {
          return result;
        } else {
          return this.$timeout(interval).then(() => this.buildRetrySequence(func, stopCondition, limit - 1, interval));
        }
      });
    }
  }
}

export const RETRY_SERVICE = 'spinnaker.deck.core.retry.service';
module(RETRY_SERVICE, [])
  .service('retryService', RetryService);
