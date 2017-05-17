import {mock} from 'angular';
import {RETRY_SERVICE, RetryService} from './retry.service';

describe('Service: Retry', function () {

  let retryService: RetryService;
  let $q: ng.IQService;
  let $timeout: ng.ITimeoutService;

  beforeEach(mock.module(RETRY_SERVICE));
  beforeEach(
    mock.inject(
      function (_retryService_: RetryService,
                _$q_: ng.IQService,
                _$timeout_: ng.ITimeoutService) {
        retryService = _retryService_;
        $q = _$q_;
        $timeout = _$timeout_;
      }));

  describe('retryService.buildRetrySequence', () => {
    it('should only call callback once if result passes stop condition', () => {
      let callCount = 0;
      const callback = () => {
        callCount++;
        return $q.resolve(true);
      };
      const stopCondition = (val: any) => val;

      retryService.buildRetrySequence<boolean>(callback, stopCondition, 100, 0)
        .then((result: boolean) => {
          expect(result).toEqual(true);
          expect(callCount).toEqual(1);
        });

      $timeout.flush();
    });

    it('should return callback result and stop sequence if result passes stop condition', () => {
      let callCount = 0;
      const callback = () => $q.resolve(++callCount);
      const stopCondition = (val: any) => val === 8;

      retryService.buildRetrySequence<number>(callback, stopCondition, 100, 0)
        .then((result: number) => {
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

      retryService.buildRetrySequence<any[]>(callback, stopCondition, 100, 0)
        .then((result: any[]) => {
          expect(result).toEqual([]);
          expect(callCount).toEqual(101);
        });

      $timeout.flush();
    });

    it(`should be tolerant of a function that does not return a promise
        (only relevant if stopCondition is met on first try)`, () => {
      const callback = () => true;
      const stopCondition = () => true;
      expect(() => retryService.buildRetrySequence(callback, stopCondition, 100, 0)).not.toThrow();
    });

    it('should retry if promise is rejected', () => {
      let callCount = 0;
      const callback = () => {
        callCount++;
        return callCount > 1 ? $q.resolve([]) : $q.reject('something failed');
      };
      const stopCondition = (result: any[]) => result === [];
      retryService.buildRetrySequence(callback, stopCondition, 1, 0)
        .then((result: any[]) => {
        expect(result).toEqual([]);
        expect(callCount).toEqual(2);
      });
      $timeout.flush();
    });
  });
});
