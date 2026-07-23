import { AngularServices } from '../angular/services';

import { RetryService } from './retry.service';

describe('Service: Retry', function () {
  describe('RetryService.buildRetrySequence', () => {
    let scheduledIntervals: number[];

    beforeEach(() => {
      scheduledIntervals = [];
      const controlledTimeout = ((interval: number) => {
        scheduledIntervals.push(interval);
        return Promise.resolve();
      }) as any;
      spyOnProperty(AngularServices, '$timeout', 'get').and.returnValue(controlledTimeout);
    });

    it('should only call callback once if result passes stop condition', async () => {
      let callCount = 0;
      const callback = () => {
        callCount++;
        return Promise.resolve(true);
      };
      const stopCondition = (val: any) => val;

      const result = await RetryService.buildRetrySequence<boolean>(callback, stopCondition, 100, 0);

      expect(result).toEqual(true);
      expect(callCount).toEqual(1);
    });

    it('should return callback result and stop sequence if result passes stop condition', async () => {
      let callCount = 0;
      const callback = () => Promise.resolve(++callCount);
      const stopCondition = (val: any) => val === 8;

      const result = await RetryService.buildRetrySequence<number>(callback, stopCondition, 100, 10);

      expect(result).toEqual(8);
      expect(callCount).toEqual(8);
      expect(scheduledIntervals).toEqual(Array(7).fill(10));
    });

    it(`should return callback result after retry limit has been met
         even if result does not pass stop condition`, () => {
      let callCount = 0;
      const callback = () => {
        callCount++;
        return Promise.resolve([]);
      };
      const stopCondition = (result: any[]) => result.length > 0;

      return RetryService.buildRetrySequence<any[]>(callback, stopCondition, 100, 10).then((result: any[]) => {
        expect(result).toEqual([]);
        expect(callCount).toEqual(101);
        expect(scheduledIntervals).toEqual(Array(100).fill(10));
      });
    });

    it(`should be tolerant of a function that does not return a promise
        (only relevant if stopCondition is met on first try)`, () => {
      const callback = () => true;
      const stopCondition = () => true;
      expect(() => RetryService.buildRetrySequence(callback, stopCondition, 100, 0)).not.toThrow();
    });

    it('should retry if promise is rejected', async () => {
      let callCount = 0;
      const callback = () => {
        callCount++;
        return callCount > 1 ? Promise.resolve([]) : Promise.reject('something failed');
      };
      const stopCondition = (result: any[]) => result === [];

      const result = await RetryService.buildRetrySequence(callback, stopCondition, 1, 10);

      expect(result).toEqual([]);
      expect(callCount).toEqual(2);
      expect(scheduledIntervals).toEqual([10]);
    });
  });
});
