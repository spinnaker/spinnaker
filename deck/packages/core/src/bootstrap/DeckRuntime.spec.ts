import { DeckRuntimeContext, createDeckRuntime } from './DeckRuntime';

describe('createDeckRuntime', () => {
  beforeEach(() => jasmine.clock().install());
  afterEach(() => jasmine.clock().uninstall());

  it('assembles direct runtime dependencies', () => {
    const runtime = createDeckRuntime();

    expect(runtime.promiseService).toEqual(jasmine.any(Function));
    expect(runtime.timeoutService).toEqual(jasmine.any(Function));
    expect(runtime.logger.error).toEqual(jasmine.any(Function));
    expect(runtime.interpolate).toEqual(jasmine.any(Function));
    expect(runtime.providerServiceDelegate).toBeDefined();
    expect(DeckRuntimeContext._currentValue).toBeNull();
  });

  it('cancels pending runtime work when disposed', () => {
    const runtime = createDeckRuntime();
    const callback = jasmine.createSpy('callback');
    runtime.timeoutService(callback, 100);

    runtime.dispose();
    jasmine.clock().tick(100);

    expect(callback).not.toHaveBeenCalled();
  });
});
