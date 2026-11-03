import type { Mocked } from 'vitest';
import type { IPluginMetaData } from './plugin.registry';
import { PluginRegistry } from './plugin.registry';
import { initializePlugins, resetPluginInitializationForTests } from './plugin.module';
import { sharedLibraries } from './sharedLibraries';

describe('initializePlugins', () => {
  let pluginRegistry: Mocked<PluginRegistry>;

  beforeEach(() => {
    resetPluginInitializationForTests();
    pluginRegistry = {
      loadPluginManifestFromDeck: vi.fn(),
      loadPluginManifestFromGate: vi.fn(),
      loadPlugins: vi.fn(),
    };
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
    vi.spyOn(sharedLibraries, 'exposeSharedLibraries').mockImplementation(async () => {
      calls.push('expose');
    });
    pluginRegistry.loadPluginManifestFromDeck.mockImplementation(() => {
      calls.push('deck manifest');
      return deckManifestPromise;
    });
    pluginRegistry.loadPluginManifestFromGate.mockImplementation(() => {
      calls.push('gate manifest');
      return gateManifestPromise;
    });
    pluginRegistry.loadPlugins.mockImplementation(() => {
      calls.push('plugins');
      resolvePluginLoadsStarted();
      return pluginLoadsPromise;
    });

    let settled = false;
    const initializationPromise = initializePlugins(pluginRegistry).then(() => (settled = true));
    await Promise.resolve();

    expect(calls).toEqual(['expose', 'deck manifest', 'gate manifest']);
    expect(pluginRegistry.loadPlugins).not.toHaveBeenCalled();

    resolveDeckManifest([]);
    await Promise.resolve();
    expect(pluginRegistry.loadPlugins).not.toHaveBeenCalled();

    resolveGateManifest([]);
    await pluginLoadsStartedPromise;
    expect(calls).toEqual(['expose', 'deck manifest', 'gate manifest', 'plugins']);
    expect(settled).toBe(false);

    resolvePluginLoads([]);
    await initializationPromise;
    expect(settled).toBe(true);
  });

  it('waits for shared library exposure before loading manifests and plugins', async () => {
    let resolveExposure!: () => void;
    const exposurePromise = new Promise<void>((resolve) => (resolveExposure = resolve));
    const exposeSpy = vi.spyOn(sharedLibraries, 'exposeSharedLibraries').mockImplementation(() => exposurePromise);
    pluginRegistry.loadPluginManifestFromDeck.mockResolvedValue([]);
    pluginRegistry.loadPluginManifestFromGate.mockResolvedValue([]);
    pluginRegistry.loadPlugins.mockResolvedValue([]);

    const initializationPromise = initializePlugins(pluginRegistry);
    await Promise.resolve();

    expect(exposeSpy).toHaveBeenCalledTimes(1);
    expect(pluginRegistry.loadPluginManifestFromDeck).not.toHaveBeenCalled();
    expect(pluginRegistry.loadPluginManifestFromGate).not.toHaveBeenCalled();
    expect(pluginRegistry.loadPlugins).not.toHaveBeenCalled();

    resolveExposure();
    await initializationPromise;

    expect(pluginRegistry.loadPluginManifestFromDeck).toHaveBeenCalledTimes(1);
    expect(pluginRegistry.loadPluginManifestFromGate).toHaveBeenCalledTimes(1);
    expect(pluginRegistry.loadPlugins).toHaveBeenCalledTimes(1);
  });

  it('rejects when shared library exposure fails without loading manifests or plugins', async () => {
    const exposureError = new Error('shared library exposure failed');
    vi.spyOn(sharedLibraries, 'exposeSharedLibraries').mockImplementation(async () => {
      throw exposureError;
    });
    pluginRegistry.loadPluginManifestFromDeck.mockResolvedValue([]);
    pluginRegistry.loadPluginManifestFromGate.mockResolvedValue([]);
    pluginRegistry.loadPlugins.mockResolvedValue([]);

    await expectAsync(initializePlugins(pluginRegistry)).toBeRejectedWith(exposureError);

    expect(pluginRegistry.loadPluginManifestFromDeck).not.toHaveBeenCalled();
    expect(pluginRegistry.loadPluginManifestFromGate).not.toHaveBeenCalled();
    expect(pluginRegistry.loadPlugins).not.toHaveBeenCalled();
  });

  it('rejects on a manifest failure without starting plugin loads', async () => {
    const manifestError = new Error('manifest failed');
    let rejectDeckManifest!: (reason?: any) => void;
    const deckManifestPromise = new Promise<IPluginMetaData[]>((_, reject) => (rejectDeckManifest = reject));
    vi.spyOn(sharedLibraries, 'exposeSharedLibraries').mockImplementation(async () => undefined);
    pluginRegistry.loadPluginManifestFromDeck.mockReturnValue(deckManifestPromise);
    pluginRegistry.loadPluginManifestFromGate.mockReturnValue(Promise.resolve([]));

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
    const exposeSpy = vi.spyOn(sharedLibraries, 'exposeSharedLibraries').mockImplementation(async () => undefined);
    const deckManifestSpy = vi
      .spyOn(PluginRegistry.prototype, 'loadPluginManifestFromDeck')
      .mockReturnValue(deckManifestPromise);
    const gateManifestSpy = vi
      .spyOn(PluginRegistry.prototype, 'loadPluginManifestFromGate')
      .mockReturnValue(gateManifestPromise);
    const pluginLoadsSpy = vi.spyOn(PluginRegistry.prototype, 'loadPlugins').mockImplementation(() => {
      resolvePluginLoadsStarted();
      return pluginLoadsPromise;
    });

    const firstInitialization = initializePlugins();
    const concurrentInitialization = initializePlugins();
    await Promise.resolve();

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
    const exposeSpy = vi.spyOn(sharedLibraries, 'exposeSharedLibraries').mockImplementation(async () => undefined);
    const deckManifestSpy = vi
      .spyOn(PluginRegistry.prototype, 'loadPluginManifestFromDeck')
      .mockReturnValueOnce(Promise.reject(startupError))
      .mockReturnValueOnce(Promise.resolve([]));
    const gateManifestSpy = vi
      .spyOn(PluginRegistry.prototype, 'loadPluginManifestFromGate')
      .mockReturnValue(Promise.resolve([]));
    const pluginLoadsSpy = vi.spyOn(PluginRegistry.prototype, 'loadPlugins').mockReturnValue(Promise.resolve([]));

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
    vi.spyOn(sharedLibraries, 'exposeSharedLibraries').mockImplementation(async () => undefined);
    pluginRegistry.loadPluginManifestFromDeck.mockReturnValue(Promise.resolve([]));
    pluginRegistry.loadPluginManifestFromGate.mockReturnValue(Promise.resolve([]));
    pluginRegistry.loadPlugins.mockReturnValue(Promise.resolve([]));

    const firstInitialization = initializePlugins(pluginRegistry);
    const secondInitialization = initializePlugins(pluginRegistry);

    expect(secondInitialization).not.toBe(firstInitialization);
    await Promise.all([firstInitialization, secondInitialization]);
    expect(pluginRegistry.loadPluginManifestFromDeck).toHaveBeenCalledTimes(2);
    expect(pluginRegistry.loadPluginManifestFromGate).toHaveBeenCalledTimes(2);
    expect(pluginRegistry.loadPlugins).toHaveBeenCalledTimes(2);
  });
});
