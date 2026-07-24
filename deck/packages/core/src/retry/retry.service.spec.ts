import { RetryService } from './retry.service';

describe('Service: Retry', function () {
  describe('RetryService.buildRetrySequence', () => {
    let scheduledIntervals: number[];

    beforeEach(() => {
      scheduledIntervals = [];
      spyOn(window, 'setTimeout').and.callFake((callback: TimerHandler, interval?: number) => {
        scheduledIntervals.push(interval);
        if (typeof callback === 'function') {
          callback();
        }
        return 0;
      });
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

    it('clears a pending retry delay when its owner is aborted', async () => {
      let delayCallback: () => void;
      const clearTimeout = spyOn(window, 'clearTimeout');
      (window.setTimeout as jasmine.Spy).and.callFake((callback: TimerHandler) => {
        delayCallback = callback as () => void;
        return 42;
      });
      const callback = jasmine.createSpy('callback').and.resolveTo([]);
      const controller = new AbortController();
      const result = Promise.resolve(RetryService.buildRetrySequence(callback, () => false, 1, 10, controller.signal));
      await Promise.resolve();

      controller.abort();
      delayCallback();

      await expectAsync(result).toBeRejectedWith(jasmine.objectContaining({ name: 'AbortError' }));
      expect(clearTimeout).toHaveBeenCalledOnceWith(42);
      expect(callback).toHaveBeenCalledTimes(1);
    });

    it('returns a rejected promise without invoking a callback for a pre-aborted owner', async () => {
      const callback = jasmine.createSpy('callback').and.resolveTo('result');
      const controller = new AbortController();
      controller.abort();
      let result: PromiseLike<string>;

      expect(() => {
        result = RetryService.buildRetrySequence(callback, () => true, 1, 10, controller.signal);
      }).not.toThrow();

      await expectAsync(Promise.resolve(result)).toBeRejectedWith(jasmine.objectContaining({ name: 'AbortError' }));
      expect(callback).not.toHaveBeenCalled();
    });

    it('does not inspect or retry a late in-flight result after cancellation', async () => {
      let resolveRequest: (value: string) => void;
      const request = new Promise<string>((resolve) => (resolveRequest = resolve));
      const callback = jasmine.createSpy('callback').and.returnValue(request);
      const stopCondition = jasmine.createSpy('stopCondition').and.returnValue(false);
      const controller = new AbortController();
      const result = Promise.resolve(
        RetryService.buildRetrySequence(callback, stopCondition, 1, 10, controller.signal),
      );

      controller.abort();
      resolveRequest('late result');

      await expectAsync(result).toBeRejectedWith(jasmine.objectContaining({ name: 'AbortError' }));
      expect(stopCondition).not.toHaveBeenCalled();
      expect(callback).toHaveBeenCalledTimes(1);
      expect(window.setTimeout).not.toHaveBeenCalled();
    });

    it('does not retry a late in-flight rejection after cancellation', async () => {
      let rejectRequest: (reason: unknown) => void;
      const request = new Promise<string>((_resolve, reject) => (rejectRequest = reject));
      const callback = jasmine.createSpy('callback').and.returnValues(request, Promise.resolve('retry'));
      const stopCondition = jasmine.createSpy('stopCondition').and.returnValue(true);
      const controller = new AbortController();
      const result = Promise.resolve(
        RetryService.buildRetrySequence(callback, stopCondition, 1, 10, controller.signal),
      );

      controller.abort();
      rejectRequest(new Error('late failure'));

      await expectAsync(result).toBeRejectedWith(jasmine.objectContaining({ name: 'AbortError' }));
      expect(stopCondition).not.toHaveBeenCalled();
      expect(callback).toHaveBeenCalledTimes(1);
    });
  });
});
