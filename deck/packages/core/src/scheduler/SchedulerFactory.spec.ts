import { SchedulerFactory } from './SchedulerFactory';

describe('SchedulerFactory browser integration', function () {
  it('uses browser online and offline events', function () {
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
    handle: number;
  }

  let pendingTimeouts: PendingTimeout[];
  let flushTimeout: () => void;
  let cancelTimeout: jasmine.Spy;

  beforeEach(function () {
    pendingTimeouts = [];
    let nextHandle = 1;
    spyOn(window, 'setTimeout').and.callFake((callback: TimerHandler) => {
      if (typeof callback !== 'function') {
        throw new Error('Expected a timeout callback');
      }
      const pending = { callback, cancelled: false, handle: nextHandle++ };
      pendingTimeouts.push(pending);
      return pending.handle;
    });
    cancelTimeout = spyOn(window, 'clearTimeout').and.callFake((handle?: number) => {
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

    it('can schedule after a pending timeout fires while the scheduler is suspended', function () {
      const subscriber = jasmine.createSpy('subscriber');
      this.scheduler.subscribe(subscriber);
      this.scheduler.scheduleImmediate();
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
      this.scheduler.scheduleImmediate();
      const pendingOwnerTimeout = pendingTimeouts[0];

      this.scheduler.unsubscribe();
      this.scheduler.unsubscribe();

      expect(pendingOwnerTimeout.cancelled).toBe(true);
      expect(cancelTimeout.calls.allArgs().filter(([handle]) => handle === pendingOwnerTimeout.handle).length).toBe(1);
      expect(flushTimeout).toThrowError('No pending timeouts');
    });
  });
});
