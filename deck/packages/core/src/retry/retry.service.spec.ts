import { mock } from 'angular';
import * as ngimport from 'ngimport';

import { RetryService } from './retry.service';

describe('Service: Retry', function () {
  let $q: ng.IQService;
  let $timeout: ng.ITimeoutService;

  beforeEach(
    mock.inject(function (_$q_: ng.IQService, _$timeout_: ng.ITimeoutService) {
      $q = _$q_;
      $timeout = _$timeout_;
    }),
  );

  describe('RetryService.buildRetrySequence', () => {
    it('should only call callback once if result passes stop condition', () => {
      let callCount = 0;
      const callback = () => {
        callCount++;
        return $q.resolve(true);
      };
      const stopCondition = (val: any) => val;

      RetryService.buildRetrySequence<boolean>(callback, stopCondition, 100, 0).then((result: boolean) => {
        expect(result).toEqual(true);
        expect(callCount).toEqual(1);
      });

      $timeout.flush();
    });

    it('should return callback result and stop sequence if result passes stop condition', () => {
      let callCount = 0;
      const callback = () => $q.resolve(++callCount);
      const stopCondition = (val: any) => val === 8;

      RetryService.buildRetrySequence<number>(callback, stopCondition, 100, 0).then((result: number) => {
        expect(result).toEqual(8);
        expect(callCount).toEqual(8);
      });

      $timeout.flush();
    });

    it(`should return callback result after retry limit has been met
         even if result does not pass stop condition`, () => {
      let callCount = 0;
      const callback = () => {
        callCount++;
        return $q.resolve([]);
      };
      const stopCondition = (result: any[]) => result.length > 0;

      RetryService.buildRetrySequence<any[]>(callback, stopCondition, 100, 0).then((result: any[]) => {
        expect(result).toEqual([]);
        expect(callCount).toEqual(101);
      });

      $timeout.flush();
    });

    it(`should be tolerant of a function that does not return a promise
        (only relevant if stopCondition is met on first try)`, () => {
      const callback = () => true;
      const stopCondition = () => true;
      expect(() => RetryService.buildRetrySequence(callback, stopCondition, 100, 0)).not.toThrow();
    });

    it('should retry if promise is rejected', () => {
      let callCount = 0;
      const callback = () => {
        callCount++;
        return callCount > 1 ? $q.resolve([]) : $q.reject('something failed');
      };
      const stopCondition = (result: any[]) => result === [];
      RetryService.buildRetrySequence(callback, stopCondition, 1, 0).then((result: any[]) => {
        expect(result).toEqual([]);
        expect(callCount).toEqual(2);
      });
      $timeout.flush();
    });

    it('should use direct fallbacks when Angular services are unavailable', async () => {
      const originalInjector = ngimport.$injector;
      const originalQ = ngimport.$q;
      const originalTimeout = ngimport.$timeout;
      (ngimport as any).$injector = undefined;
      (ngimport as any).$q = undefined;
      (ngimport as any).$timeout = undefined;
      let callCount = 0;

      try {
        const result = await RetryService.buildRetrySequence<boolean>(
          () => ++callCount > 1,
          (value) => value,
          1,
          0,
        );

        expect(result).toBe(true);
        expect(callCount).toBe(2);
      } finally {
        (ngimport as any).$injector = originalInjector;
        (ngimport as any).$q = originalQ;
        (ngimport as any).$timeout = originalTimeout;
      }
    });
  });
});
