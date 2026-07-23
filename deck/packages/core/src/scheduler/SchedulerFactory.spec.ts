import type { ITimeoutService } from 'angular';

import { AngularServices } from '../angular/services';
import { SchedulerFactory } from './SchedulerFactory';

describe('SchedulerFactory without Angular injection', function () {
  it('uses browser globals without Angular services', function () {
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

describe('SchedulerFactory with direct services', function () {
  interface PendingTimeout {
    callback: () => void;
    cancelled: boolean;
  }

  let pendingTimeouts: PendingTimeout[];
  let flushTimeout: () => void;

  beforeEach(function () {
    pendingTimeouts = [];
    const timeout = (((callback: () => void) => {
      const pending = { callback, cancelled: false };
      pendingTimeouts.push(pending);
      return pending;
    }) as any) as ITimeoutService;
    timeout.cancel = (pending: PendingTimeout) => {
      if (!pending) {
        return false;
      }
      pending.cancelled = true;
      return true;
    };
    flushTimeout = () => {
      const activeTimeouts = pendingTimeouts.filter(({ cancelled }) => !cancelled);
      pendingTimeouts = [];
      if (!activeTimeouts.length) {
        throw new Error('No pending timeouts');
      }
      activeTimeouts.forEach(({ callback }) => callback());
    };
    spyOnProperty(AngularServices, '$timeout', 'get').and.returnValue(timeout);

    this.scheduler = SchedulerFactory.createScheduler(60000);

    this.test = {
      call: () => undefined,
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

      flushTimeout();
      expect(this.test.call.calls.count()).toBe(5);

      // verify no outstanding timeouts
      expect(flushTimeout).toThrow();
    });

    it('does not schedule another run when a subscriber unsubscribes during immediate notification', function () {
      const scheduler: ReturnType<typeof SchedulerFactory.createScheduler> = this.scheduler;
      const subscriber = jasmine.createSpy('subscriber').and.callFake(() => scheduler.unsubscribe());
      scheduler.subscribe(subscriber);

      scheduler.scheduleImmediate();

      expect(subscriber).toHaveBeenCalledTimes(1);
      expect(flushTimeout).toThrowError('No pending timeouts');
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
      expect(flushTimeout).toThrowError('No pending timeouts');
      scheduler.scheduleImmediate();
      expect(firstSubscriber).toHaveBeenCalledTimes(1);
      expect(secondSubscriber).not.toHaveBeenCalled();
    });
  });
});
