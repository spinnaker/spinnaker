import { createCancellableTimeout } from './cancellableTimeout';

describe('createCancellableTimeout', () => {
  beforeEach(() => jasmine.clock().install());
  afterEach(() => jasmine.clock().uninstall());

  it('completes with the callback result after the delay', async () => {
    const timeout = createCancellableTimeout();
    const result = timeout(() => 'complete', 100);

    jasmine.clock().tick(100);

    await expectAsync(result as Promise<string>).toBeResolvedTo('complete');
  });

  it('settles a cancelled promise, suppresses its callback, and reports only the actual cancellation', async () => {
    const timeout = createCancellableTimeout();
    const callback = jasmine.createSpy('callback');
    const pending = timeout(callback, 100);
    const settlement = expectAsync(pending).toBeRejectedWith('canceled');

    expect(timeout.cancel(pending)).toBe(true);
    expect(timeout.cancel(pending)).toBe(false);
    jasmine.clock().tick(100);

    await settlement;
    expect(callback).not.toHaveBeenCalled();
  });

  it('returns false when there is no pending timeout to cancel', () => {
    const timeout = createCancellableTimeout();

    expect(timeout.cancel()).toBe(false);
    expect(timeout.cancel({})).toBe(false);
    expect(timeout.cancel({ timeoutId: 12345 })).toBe(false);
  });

  it('returns false after a timeout has completed', async () => {
    const timeout = createCancellableTimeout();
    const completed = timeout(() => 'complete', 100);

    jasmine.clock().tick(100);
    await expectAsync(completed).toBeResolvedTo('complete');

    expect(timeout.cancel(completed)).toBe(false);
  });

  it('settles and suppresses all pending work when disposed', async () => {
    const timeout = createCancellableTimeout();
    const firstCallback = jasmine.createSpy('firstCallback');
    const secondCallback = jasmine.createSpy('secondCallback');
    const first = timeout(firstCallback, 100);
    const second = timeout(secondCallback, 200);
    const firstSettlement = expectAsync(first).toBeRejectedWith('canceled');
    const secondSettlement = expectAsync(second).toBeRejectedWith('canceled');

    timeout.dispose();
    jasmine.clock().tick(200);

    await firstSettlement;
    await secondSettlement;
    expect(firstCallback).not.toHaveBeenCalled();
    expect(secondCallback).not.toHaveBeenCalled();
    expect(timeout.cancel(first)).toBe(false);
    expect(timeout.cancel(second)).toBe(false);
  });

  it('rejects scheduling after disposal without creating a native timeout', async () => {
    const originalSetTimeout = window.setTimeout;
    let schedules = 0;
    window.setTimeout = ((handler: TimerHandler, delay?: number, ...args: any[]) => {
      schedules++;
      return originalSetTimeout(handler, delay, ...args);
    }) as typeof window.setTimeout;
    const timeout = createCancellableTimeout();
    const callback = jasmine.createSpy('callback');

    try {
      timeout.dispose();
      const rejected = timeout(callback, 100);

      expect(schedules).toBe(0);
      if (schedules > 0) {
        timeout.cancel(rejected);
      }
      await expectAsync(rejected).toBeRejectedWith('canceled');
      jasmine.clock().tick(100);
      expect(callback).not.toHaveBeenCalled();
    } finally {
      window.setTimeout = originalSetTimeout;
    }
  });

  it('disposes pending work idempotently', async () => {
    const timeout = createCancellableTimeout();
    const pending = timeout(100);
    const settlement = expectAsync(pending).toBeRejectedWith('canceled');

    timeout.dispose();
    timeout.dispose();

    await settlement;
    expect(timeout.cancel(pending)).toBe(false);
  });
});
