import * as angular from 'angular';
import type { ITimeoutService } from 'angular';
import { mock } from 'angular';
import { SchedulerFactory } from './SchedulerFactory';

describe('SchedulerFactory without Angular injection', function () {
  it('uses browser globals when ngimport services are unavailable', function () {
    const addEventListener = spyOn(window, 'addEventListener').and.callThrough();
    const removeEventListener = spyOn(window, 'removeEventListener').and.callThrough();

    const scheduler = SchedulerFactory.createScheduler(25);
    scheduler.unsubscribe();

    expect(addEventListener).toHaveBeenCalledWith('offline', jasmine.any(Function));
    expect(addEventListener).toHaveBeenCalledWith('online', jasmine.any(Function));
    expect(removeEventListener).toHaveBeenCalledWith('offline', jasmine.any(Function));
    expect(removeEventListener).toHaveBeenCalledWith('online', jasmine.any(Function));
  });

  describe('#unsubscribe', () => {
    it('stops timer emissions from reaching subscribers', () => {
      let emitTimer: () => void;
      let timerActive = true;
      spyOn(window, 'setInterval').and.callFake((handler: TimerHandler) => {
        emitTimer = () => {
          if (timerActive && typeof handler === 'function') {
            handler();
          }
        };
        return 1;
      });
      const clearInterval = spyOn(window, 'clearInterval').and.callFake(() => (timerActive = false));
      const subscriber = jasmine.createSpy('subscriber');
      const scheduler = SchedulerFactory.createScheduler(25);
      scheduler.subscribe(subscriber);
      emitTimer();

      expect(subscriber).toHaveBeenCalledTimes(1);

      scheduler.unsubscribe();
      emitTimer();

      expect(clearInterval).toHaveBeenCalled();
      expect(subscriber).toHaveBeenCalledTimes(1);
    });
  });
});

describe('SchedulerFactory with Angular services', function () {
  let $timeout: ITimeoutService;

  beforeEach(function () {
    const pollSchedule = 25;

    this.pollSchedule = pollSchedule;

    mock.inject(function (_$timeout_: ITimeoutService) {
      this.scheduler = SchedulerFactory.createScheduler();
      $timeout = _$timeout_;
    });

    this.test = {
      call: angular.noop,
    };
  });

  afterEach(function () {
    this.scheduler.unsubscribe();
  });

  describe('#scheduleImmediate', function () {
    it('invokes all subscribed callbacks immediately', function () {
      const numSubscribers = 20;

      spyOn(this.test, 'call');
      for (let i = 0; i < numSubscribers; i++) {
        this.scheduler.subscribe(this.test.call);
      }
      const pre = this.test.call.calls.count();
      this.scheduler.scheduleImmediate();
      expect(this.test.call.calls.count() - pre).toBe(numSubscribers);
    });

    it('does not fire next repeatedly when scheduleImmediate is called within the interval window', function () {
      spyOn(this.test, 'call');
      this.scheduler.subscribe(this.test.call);
      this.scheduler.scheduleImmediate();
      this.scheduler.scheduleImmediate();
      this.scheduler.scheduleImmediate();
      this.scheduler.scheduleImmediate();
      expect(this.test.call.calls.count()).toBe(4);

      $timeout.flush();
      expect(this.test.call.calls.count()).toBe(5);

      // verify no outstanding timeouts
      expect($timeout.flush).toThrow();
    });

    it('does not schedule another run when a subscriber unsubscribes during immediate notification', function () {
      const scheduler: ReturnType<typeof SchedulerFactory.createScheduler> = this.scheduler;
      const subscriber = jasmine.createSpy('subscriber').and.callFake(() => scheduler.unsubscribe());
      scheduler.subscribe(subscriber);

      scheduler.scheduleImmediate();

      expect(subscriber).toHaveBeenCalledTimes(1);
      expect(() => $timeout.flush()).toThrowError(/No deferred tasks to be flushed/);
      expect(() => scheduler.scheduleImmediate()).not.toThrow();
      expect(subscriber).toHaveBeenCalledTimes(1);
    });

    it('stops notifying later subscribers when a subscriber unsubscribes during immediate notification', function () {
      const scheduler: ReturnType<typeof SchedulerFactory.createScheduler> = this.scheduler;
      const firstSubscriber = jasmine.createSpy('firstSubscriber').and.callFake(() => scheduler.unsubscribe());
      const secondSubscriber = jasmine.createSpy('secondSubscriber');
      scheduler.subscribe(firstSubscriber);
      scheduler.subscribe(secondSubscriber);

      scheduler.scheduleImmediate();

      expect(firstSubscriber).toHaveBeenCalledTimes(1);
      expect(secondSubscriber).not.toHaveBeenCalled();
      expect(() => $timeout.flush()).toThrowError(/No deferred tasks to be flushed/);
      scheduler.scheduleImmediate();
      expect(firstSubscriber).toHaveBeenCalledTimes(1);
      expect(secondSubscriber).not.toHaveBeenCalled();
    });
  });
});
