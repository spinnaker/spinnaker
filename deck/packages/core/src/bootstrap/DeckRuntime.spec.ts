import { UIRouterReact } from '@uirouter/react';

import { createDeckRuntime } from './DeckRuntime';
import { DeckRuntimeContext } from './DeckRuntimeContext';

describe('createDeckRuntime', () => {
  beforeEach(() => jasmine.clock().install());
  afterEach(() => jasmine.clock().uninstall());

  it('assembles direct runtime dependencies', () => {
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);

    expect(runtime.router).toBe(router);
    expect(runtime.promiseService).toEqual(jasmine.any(Function));
    expect(runtime.timeoutService).toEqual(jasmine.any(Function));
    expect(runtime.logger.error).toEqual(jasmine.any(Function));
    expect(runtime.interpolate).toEqual(jasmine.any(Function));
    expect(runtime.services.providerServiceDelegate).toBeDefined();
    expect(DeckRuntimeContext._currentValue).toBeNull();
    router.dispose();
  });

  it('cancels pending runtime work when disposed', () => {
    const runtime = createDeckRuntime();
    const callback = jasmine.createSpy('callback');
    runtime.timeoutService(callback, 100);

    runtime.dispose();
    jasmine.clock().tick(100);

    expect(callback).not.toHaveBeenCalled();
  });

  it('creates services lazily and preserves their identity within a runtime', () => {
    const runtime = createDeckRuntime();

    expect(() => runtime.services.executionService).toThrowError(
      'Cannot create ExecutionService before the direct UI Router is initialized',
    );

    expect(runtime.services.cacheInitializer).toBe(runtime.services.cacheInitializer);
    expect(runtime.services.serverGroupWriter).toBe(runtime.services.serverGroupWriter);
  });

  it('isolates service instances between runtimes', () => {
    const firstRuntime = createDeckRuntime();
    const secondRuntime = createDeckRuntime();

    expect(firstRuntime.services.cacheInitializer).not.toBe(secondRuntime.services.cacheInitializer);
    expect(firstRuntime.services.serverGroupTransformer).not.toBe(secondRuntime.services.serverGroupTransformer);
  });

  it('releases service instances when disposed', () => {
    const runtime = createDeckRuntime();
    const cacheInitializer = runtime.services.cacheInitializer;
    const serverGroupWriter = runtime.services.serverGroupWriter;

    runtime.dispose();

    expect(runtime.services.cacheInitializer).not.toBe(cacheInitializer);
    expect(runtime.services.serverGroupWriter).not.toBe(serverGroupWriter);
  });
});
