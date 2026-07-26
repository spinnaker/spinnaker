import type { IPluginMetaData } from './plugin.registry';
import { PluginRegistry } from './plugin.registry';
import { initializePlugins, resetPluginInitializationForTests, runPlugins } from './plugin.module';
import { sharedLibraries } from './sharedLibraries';

describe('initializePlugins', () => {
  let pluginRegistry: jasmine.SpyObj<PluginRegistry>;

  beforeEach(() => {
    resetPluginInitializationForTests();
    pluginRegistry = jasmine.createSpyObj<PluginRegistry>('pluginRegistry', [
      'loadPluginManifestFromDeck',
      'loadPluginManifestFromGate',
      'loadPlugins',
    ]);
  });

  afterEach(() => resetPluginInitializationForTests());

  it('exposes shared libraries, loads both manifests, then waits for every plugin attempt', async () => {
    const calls: string[] = [];
    let resolveDeckManifest!: (plugins: IPluginMetaData[]) => void;
    let resolveGateManifest!: (plugins: IPluginMetaData[]) => void;
    let resolvePluginLoads!: (plugins: any[]) => void;
    let resolvePluginLoadsStarted!: () => void;
    const deckManifestPromise = new Promise<IPluginMetaData[]>((resolve) => (resolveDeckManifest = resolve));
    const gateManifestPromise = new Promise<IPluginMetaData[]>((resolve) => (resolveGateManifest = resolve));
    const pluginLoadsPromise = new Promise<any[]>((resolve) => (resolvePluginLoads = resolve));
    const pluginLoadsStartedPromise = new Promise<void>((resolve) => (resolvePluginLoadsStarted = resolve));
    spyOn(sharedLibraries, 'exposeSharedLibraries').and.callFake(() => calls.push('expose'));
    pluginRegistry.loadPluginManifestFromDeck.and.callFake(() => {
      calls.push('deck manifest');
      return deckManifestPromise;
    });
    pluginRegistry.loadPluginManifestFromGate.and.callFake(() => {
      calls.push('gate manifest');
      return gateManifestPromise;
    });
    pluginRegistry.loadPlugins.and.callFake(() => {
      calls.push('plugins');
      resolvePluginLoadsStarted();
      return pluginLoadsPromise;
    });

    let settled = false;
    const initializationPromise = initializePlugins(pluginRegistry).then(() => (settled = true));

    expect(calls).toEqual(['expose', 'deck manifest', 'gate manifest']);
    expect(pluginRegistry.loadPlugins).not.toHaveBeenCalled();

    resolveDeckManifest([]);
    await Promise.resolve();
    expect(pluginRegistry.loadPlugins).not.toHaveBeenCalled();

    resolveGateManifest([]);
    await pluginLoadsStartedPromise;
    expect(calls).toEqual(['expose', 'deck manifest', 'gate manifest', 'plugins']);
    expect(settled).toBeFalse();

    resolvePluginLoads([]);
    await initializationPromise;
    expect(settled).toBeTrue();
  });

  it('rejects on a manifest failure without starting plugin loads', async () => {
    const manifestError = new Error('manifest failed');
    let rejectDeckManifest!: (reason?: any) => void;
    const deckManifestPromise = new Promise<IPluginMetaData[]>((_, reject) => (rejectDeckManifest = reject));
    spyOn(sharedLibraries, 'exposeSharedLibraries').and.stub();
    pluginRegistry.loadPluginManifestFromDeck.and.returnValue(deckManifestPromise);
    pluginRegistry.loadPluginManifestFromGate.and.returnValue(Promise.resolve([]));

    const initializationPromise = initializePlugins(pluginRegistry);
    rejectDeckManifest(manifestError);

    await expectAsync(initializationPromise).toBeRejectedWith(manifestError);
    expect(pluginRegistry.loadPluginManifestFromDeck).toHaveBeenCalled();
    expect(pluginRegistry.loadPluginManifestFromGate).toHaveBeenCalled();
    expect(pluginRegistry.loadPlugins).not.toHaveBeenCalled();
  });

  it('shares one startup across concurrent and repeated default calls', async () => {
    let resolveDeckManifest!: (plugins: any[]) => void;
    let resolveGateManifest!: (plugins: any[]) => void;
    let resolvePluginLoads!: (plugins: any[]) => void;
    let resolvePluginLoadsStarted!: () => void;
    const deckManifestPromise = new Promise<any[]>((resolve) => (resolveDeckManifest = resolve));
    const gateManifestPromise = new Promise<any[]>((resolve) => (resolveGateManifest = resolve));
    const pluginLoadsPromise = new Promise<any[]>((resolve) => (resolvePluginLoads = resolve));
    const pluginLoadsStartedPromise = new Promise<void>((resolve) => (resolvePluginLoadsStarted = resolve));
    const exposeSpy = spyOn(sharedLibraries, 'exposeSharedLibraries').and.stub();
    const deckManifestSpy = spyOn(PluginRegistry.prototype, 'loadPluginManifestFromDeck').and.returnValue(
      deckManifestPromise,
    );
    const gateManifestSpy = spyOn(PluginRegistry.prototype, 'loadPluginManifestFromGate').and.returnValue(
      gateManifestPromise,
    );
    const pluginLoadsSpy = spyOn(PluginRegistry.prototype, 'loadPlugins').and.callFake(() => {
      resolvePluginLoadsStarted();
      return pluginLoadsPromise;
    });

    const firstInitialization = initializePlugins();
    const concurrentInitialization = initializePlugins();

    expect(concurrentInitialization).toBe(firstInitialization);
    expect(exposeSpy).toHaveBeenCalledTimes(1);
    expect(deckManifestSpy).toHaveBeenCalledTimes(1);
    expect(gateManifestSpy).toHaveBeenCalledTimes(1);

    resolveDeckManifest([]);
    resolveGateManifest([]);
    await pluginLoadsStartedPromise;
    expect(pluginLoadsSpy).toHaveBeenCalledTimes(1);

    resolvePluginLoads([]);
    await Promise.all([firstInitialization, concurrentInitialization]);

    const repeatedInitialization = initializePlugins();
    expect(repeatedInitialization).toBe(firstInitialization);
    await repeatedInitialization;
    expect(exposeSpy).toHaveBeenCalledTimes(1);
    expect(deckManifestSpy).toHaveBeenCalledTimes(1);
    expect(gateManifestSpy).toHaveBeenCalledTimes(1);
    expect(pluginLoadsSpy).toHaveBeenCalledTimes(1);
  });

  it('allows a default startup retry after a rejected attempt', async () => {
    const startupError = new Error('startup failed');
    const exposeSpy = spyOn(sharedLibraries, 'exposeSharedLibraries').and.stub();
    const deckManifestSpy = spyOn(PluginRegistry.prototype, 'loadPluginManifestFromDeck').and.returnValues(
      Promise.reject(startupError),
      Promise.resolve([]),
    );
    const gateManifestSpy = spyOn(PluginRegistry.prototype, 'loadPluginManifestFromGate').and.returnValue(
      Promise.resolve([]),
    );
    const pluginLoadsSpy = spyOn(PluginRegistry.prototype, 'loadPlugins').and.returnValue(Promise.resolve([]));

    const failedInitialization = initializePlugins();
    await expectAsync(failedInitialization).toBeRejectedWith(startupError);

    const retriedInitialization = initializePlugins();
    expect(retriedInitialization).not.toBe(failedInitialization);
    await retriedInitialization;

    expect(exposeSpy).toHaveBeenCalledTimes(2);
    expect(deckManifestSpy).toHaveBeenCalledTimes(2);
    expect(gateManifestSpy).toHaveBeenCalledTimes(2);
    expect(pluginLoadsSpy).toHaveBeenCalledTimes(1);
  });

  it('does not cache initialization for explicitly injected registries', async () => {
    spyOn(sharedLibraries, 'exposeSharedLibraries').and.stub();
    pluginRegistry.loadPluginManifestFromDeck.and.returnValue(Promise.resolve([]));
    pluginRegistry.loadPluginManifestFromGate.and.returnValue(Promise.resolve([]));
    pluginRegistry.loadPlugins.and.returnValue(Promise.resolve([]));

    const firstInitialization = initializePlugins(pluginRegistry);
    const secondInitialization = initializePlugins(pluginRegistry);

    expect(secondInitialization).not.toBe(firstInitialization);
    await Promise.all([firstInitialization, secondInitialization]);
    expect(pluginRegistry.loadPluginManifestFromDeck).toHaveBeenCalledTimes(2);
    expect(pluginRegistry.loadPluginManifestFromGate).toHaveBeenCalledTimes(2);
    expect(pluginRegistry.loadPlugins).toHaveBeenCalledTimes(2);
  });
});

describe('plugin Angular run wrapper', () => {
  afterEach(() => resetPluginInitializationForTests());

  it('reports initialization failure once and always resumes the router', async () => {
    const calls: string[] = [];
    const initializationError = new Error('plugin initialization failed');
    let resolveRouterResumed!: () => void;
    const routerResumedPromise = new Promise<void>((resolve) => (resolveRouterResumed = resolve));
    const initializer = jasmine.createSpy('initializer').and.returnValue(Promise.reject(initializationError));
    const exceptionHandler = jasmine.createSpy('exceptionHandler').and.callFake(() => calls.push('error'));
    const listen = jasmine.createSpy('listen').and.callFake(() => calls.push('listen'));
    const sync = jasmine.createSpy('sync').and.callFake(() => {
      calls.push('sync');
      resolveRouterResumed();
    });
    const rootScope = { $applyAsync: (callback: () => void) => callback() } as any;
    const uiRouter = { urlService: { listen, sync } } as any;

    const result = runPlugins(rootScope, uiRouter, exceptionHandler, initializer);

    expect(result).toBeUndefined();
    await routerResumedPromise;
    expect(initializer).toHaveBeenCalledTimes(1);
    expect(exceptionHandler).toHaveBeenCalledOnceWith(initializationError);
    expect(listen).toHaveBeenCalledTimes(1);
    expect(sync).toHaveBeenCalledTimes(1);
    expect(calls).toEqual(['error', 'listen', 'sync']);
  });
});
