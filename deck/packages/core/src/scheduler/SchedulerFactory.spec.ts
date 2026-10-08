import type { Mock } from 'vitest';
import { SchedulerFactory } from './SchedulerFactory';

const testContext: any = {};

describe('SchedulerFactory browser integration', function () {
  it('uses browser online and offline events', function () {
    const addEventListener = vi.spyOn(window, 'addEventListener');
    const removeEventListener = vi.spyOn(window, 'removeEventListener');

    const scheduler = SchedulerFactory.createScheduler(25);
    scheduler.unsubscribe();

    expect(addEventListener).toHaveBeenCalledWith('offline', expect.any(Function));
    expect(addEventListener).toHaveBeenCalledWith('online', expect.any(Function));
    expect(removeEventListener).toHaveBeenCalledWith('offline', expect.any(Function));
    expect(removeEventListener).toHaveBeenCalledWith('online', expect.any(Function));
  });

  describe('#unsubscribe', () => {
    it('stops timer emissions from reaching subscribers', () => {
      let emitTimer: () => void;
      let timerActive = true;
      vi.spyOn(window, 'setInterval').mockImplementation((handler: TimerHandler) => {
        emitTimer = () => {
          if (timerActive && typeof handler === 'function') {
            handler();
          }
        };
        return 1;
      });
      const clearInterval = vi.spyOn(window, 'clearInterval').mockImplementation(() => (timerActive = false));
      const subscriber = vi.fn();
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
    handle: number;
  }

  let pendingTimeouts: PendingTimeout[];
  let flushTimeout: () => void;
  let cancelTimeout: Mock;

  beforeEach(function () {
    pendingTimeouts = [];
    let nextHandle = 1;
    vi.spyOn(window, 'setTimeout').mockImplementation((callback: TimerHandler) => {
      if (typeof callback !== 'function') {
        throw new Error('Expected a timeout callback');
      }
      const pending = { callback, cancelled: false, handle: nextHandle++ };
      pendingTimeouts.push(pending);
      return pending.handle;
    });
    cancelTimeout = vi.spyOn(window, 'clearTimeout').mockImplementation((handle?: number) => {
      const pending = pendingTimeouts.find((candidate) => candidate.handle === handle);
      if (pending) {
        pending.cancelled = true;
      }
    });
    flushTimeout = () => {
      const activeTimeouts = pendingTimeouts.filter(({ cancelled }) => !cancelled);
      pendingTimeouts = [];
      if (!activeTimeouts.length) {
        throw new Error('No pending timeouts');
      }
      activeTimeouts.forEach(({ callback }) => callback());
    };

    testContext.scheduler = SchedulerFactory.createScheduler(60000);

    testContext.test = {
      call: () => undefined,
    };
  });

  afterEach(function () {
    testContext.scheduler.unsubscribe();
  });

  describe('#scheduleImmediate', function () {
    it('invokes all subscribed callbacks immediately', function () {
      const numSubscribers = 20;

      vi.spyOn(testContext.test, 'call').mockReturnValue(undefined);
      for (let i = 0; i < numSubscribers; i++) {
        testContext.scheduler.subscribe(testContext.test.call);
      }
      const pre = testContext.test.call.mock.calls.length;
      testContext.scheduler.scheduleImmediate();
      expect(testContext.test.call.mock.calls.length - pre).toBe(numSubscribers);
    });

    it('does not fire next repeatedly when scheduleImmediate is called within the interval window', function () {
      vi.spyOn(testContext.test, 'call').mockReturnValue(undefined);
      testContext.scheduler.subscribe(testContext.test.call);
      testContext.scheduler.scheduleImmediate();
      testContext.scheduler.scheduleImmediate();
      testContext.scheduler.scheduleImmediate();
      testContext.scheduler.scheduleImmediate();
      expect(testContext.test.call.mock.calls.length).toBe(4);

      flushTimeout();
      expect(testContext.test.call.mock.calls.length).toBe(5);

      // verify no outstanding timeouts
      expect(flushTimeout).toThrow();
    });

    it('does not schedule another run when a subscriber unsubscribes during immediate notification', function () {
      const scheduler: ReturnType<typeof SchedulerFactory.createScheduler> = testContext.scheduler;
      const subscriber = vi.fn().mockImplementation(() => scheduler.unsubscribe());
      scheduler.subscribe(subscriber);

      scheduler.scheduleImmediate();

      expect(subscriber).toHaveBeenCalledTimes(1);
      expect(flushTimeout).toThrowError('No pending timeouts');
      expect(() => scheduler.scheduleImmediate()).not.toThrow();
      expect(subscriber).toHaveBeenCalledTimes(1);
    });

    it('stops notifying later subscribers when a subscriber unsubscribes during immediate notification', function () {
      const scheduler: ReturnType<typeof SchedulerFactory.createScheduler> = testContext.scheduler;
      const firstSubscriber = vi.fn().mockImplementation(() => scheduler.unsubscribe());
      const secondSubscriber = vi.fn();
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

    it('can schedule after a pending timeout fires while the scheduler is suspended', function () {
      const subscriber = vi.fn();
      testContext.scheduler.subscribe(subscriber);
      testContext.scheduler.scheduleImmediate();
      window.dispatchEvent(new Event('offline'));

      flushTimeout();
      expect(subscriber).toHaveBeenCalledTimes(1);

      window.dispatchEvent(new Event('online'));
      flushTimeout();

      expect(subscriber).toHaveBeenCalledTimes(2);
    });
  });

  describe('#unsubscribe', function () {
    it('cancels its pending owner timeout once and repeated unsubscribe is harmless', function () {
      testContext.scheduler.scheduleImmediate();
      const pendingOwnerTimeout = pendingTimeouts[0];

      testContext.scheduler.unsubscribe();
      testContext.scheduler.unsubscribe();

      expect(pendingOwnerTimeout.cancelled).toBe(true);
      expect(cancelTimeout.mock.calls.filter(([handle]) => handle === pendingOwnerTimeout.handle).length).toBe(1);
      expect(flushTimeout).toThrowError('No pending timeouts');
    });
  });
});
