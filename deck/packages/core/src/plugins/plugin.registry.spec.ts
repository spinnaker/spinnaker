import type { Mock } from 'vitest';
import { RequestBuilder } from '../api';
import type { IDeckPlugin } from './deck.plugin';
import type { IStageTypeConfig } from '../domain';
import { HelpContentsRegistry } from '../help';
import type { IPluginMetaData } from './plugin.registry';
import { PluginRegistry } from './plugin.registry';
import { Registry } from '../registry';

const fakePromise = (result: any = undefined) => () => new Promise((resolve) => resolve(result));
const fakeFetchResponse = (data: any = []) => ({ ok: true, json: () => Promise.resolve(data) } as Response);

describe('PluginRegistry', () => {
  let pluginRegistry: PluginRegistry;
  let loadModuleFromUrlSpy: Mock;
  beforeEach(() => {
    pluginRegistry = new PluginRegistry() as any;
    // @ts-ignore
    loadModuleFromUrlSpy = vi.spyOn(pluginRegistry, 'loadModuleFromUrl').mockReturnValue(undefined);
  });

  describe('.register()', () => {
    it('should validate plugin manifests', () => {
      expect(() => pluginRegistry.registerPluginMetaData('deck', {} as any)).toThrowError(/Invalid plugin manifest/);
    });

    it('should add plugins manifests to the registry', () => {
      const plugin1 = { id: 'foo', version: '1.0.0' };
      const plugin2 = { id: 'bar', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData('deck', plugin1);
      pluginRegistry.registerPluginMetaData('deck', plugin2);
      expect(pluginRegistry.getRegisteredPlugins().length).toBe(2);
      expect(pluginRegistry.getRegisteredPlugins()[0]).toEqual(expect.objectContaining(plugin1));
      expect(pluginRegistry.getRegisteredPlugins()[1]).toEqual(expect.objectContaining(plugin2));
    });
  });

  it('loadPluginManifestFromDeck() should fetch /plugin-manifest.json from deck assets', async () => {
    const spy = vi.spyOn(window, 'fetch').mockImplementation(() => Promise.resolve(fakeFetchResponse()));
    await pluginRegistry.loadPluginManifestFromDeck();
    expect(spy).toHaveBeenCalledWith('/plugin-manifest.json', { credentials: 'include' });
  });

  it('loadPluginManifestFromGate() should fetch from gate /plugins/deck/plugin-manifest.json', async () => {
    const spy = vi.spyOn(RequestBuilder.defaultHttpClient, 'get').mockImplementation(() => Promise.resolve([] as any));
    await pluginRegistry.loadPluginManifestFromGate();
    expect(spy).toHaveBeenCalled();
  });

  it('loadPluginManifestFromDeck() should return empty array on error', async () => {
    vi.spyOn(window, 'fetch').mockImplementation(() => Promise.reject({ data: null }));
    vi.spyOn(console, 'error').mockReturnValue(undefined);
    const result = await pluginRegistry.loadPluginManifestFromDeck();
    expect(result).toEqual([]);
  });

  it('loadPluginManifestFromGate() should return empty array on error', async () => {
    vi.spyOn(RequestBuilder.defaultHttpClient, 'get').mockImplementation(() => Promise.reject({ data: null }));
    vi.spyOn(console, 'error').mockReturnValue(undefined);
    const result = await pluginRegistry.loadPluginManifestFromGate();
    expect(result).toEqual([]);
  });

  describe('loadPlugins()', () => {
    let pluginModule: { plugin: IDeckPlugin };
    beforeEach(() => {
      pluginModule = {
        plugin: {
          stages: [({ id: 'mystage' } as any) as IStageTypeConfig],
          initialize: () => {},
        },
      };
    });

    it('should load all registered plugins', async () => {
      // @ts-ignore
      const loadSpy = vi.spyOn(pluginRegistry, 'load').mockImplementation(fakePromise());
      const plugin1 = { id: 'foo', version: '1.0.0' };
      const plugin2 = { id: 'bar', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData('deck', plugin1);
      pluginRegistry.registerPluginMetaData('deck', plugin2);

      expect(loadSpy.mock.calls.length).toBe(0);

      await pluginRegistry.loadPlugins();

      expect(loadSpy.mock.calls.length).toBe(2);
      expect(loadSpy.mock.calls[0][0]).toEqual(expect.objectContaining(plugin1));
      expect(loadSpy.mock.lastCall[0]).toEqual(expect.objectContaining(plugin2));
    });

    it('should represent a failed plugin load as undefined and log sanitized context', async () => {
      loadModuleFromUrlSpy.mockImplementation(fakePromise({}));
      const errorSpy = vi.spyOn(console, 'error').mockReturnValue(undefined);
      const plugin1 = {
        id: 'foo',
        version: '1.0.0',
        url: 'https://user:password@example.com/plugins/foo.js?token=secret#fragment',
      };
      pluginRegistry.registerPluginMetaData('deck', plugin1);

      await expectAsync(pluginRegistry.loadPlugins()).toBeResolvedTo([undefined]);
      expect(errorSpy).toHaveBeenCalledExactlyOnceWith(
        'Failed to load plugin foo code from https://example.com/plugins/foo.js',
      );
    });

    it('waits for every plugin attempt when one fails and another extension registration is deferred', async () => {
      let rejectFailedModule!: (reason?: any) => void;
      let resolveExtensionRegistration!: (stage: IStageTypeConfig) => void;
      let resolveFailureLogged!: () => void;
      const failedModulePromise = new Promise<never>((_, reject) => (rejectFailedModule = reject));
      const extensionRegistrationPromise = new Promise<IStageTypeConfig>(
        (resolve) => (resolveExtensionRegistration = resolve),
      );
      const failureLoggedPromise = new Promise<void>((resolve) => (resolveFailureLogged = resolve));
      const stage = { key: 'deferred-stage' } as IStageTypeConfig;
      const successfulModule = {
        plugin: {
          preconfiguredJobStages: [stage],
        },
      };
      const failedPlugin = { id: 'failed-plugin', version: '1.0.0', url: '/plugins/failed.js' };
      const deferredPlugin = { id: 'deferred-plugin', version: '1.0.0', url: '/plugins/deferred.js' };
      const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => resolveFailureLogged());
      loadModuleFromUrlSpy.mockImplementation((url: string) =>
        url === failedPlugin.url ? failedModulePromise : Promise.resolve(successfulModule),
      );
      pluginRegistry.registerPluginMetaData('deck', failedPlugin);
      const deferredMetadata = pluginRegistry.registerPluginMetaData('deck', deferredPlugin)!;
      vi.spyOn(Registry.pipeline, 'registerPreconfiguredJobStage').mockReturnValue(extensionRegistrationPromise);

      let resolved = false;
      let rejected = false;
      let result: any[] | undefined;
      const aggregatePromise = pluginRegistry.loadPlugins().then(
        (modules) => {
          resolved = true;
          result = modules;
        },
        () => {
          rejected = true;
        },
      );

      await Promise.resolve();
      expect(loadModuleFromUrlSpy).toHaveBeenCalledTimes(2);
      rejectFailedModule(new Error('failed module load'));
      await failureLoggedPromise;
      await Promise.resolve();

      expect(resolved).toBe(false);
      expect(rejected).toBe(false);
      expect(deferredMetadata.module).toBeUndefined();

      resolveExtensionRegistration(stage);
      await aggregatePromise;

      expect(resolved).toBe(true);
      expect(rejected).toBe(false);
      expect(result).toEqual([undefined, successfulModule]);
      expect(deferredMetadata.module).toBe(successfulModule);
      expect(errorSpy).toHaveBeenCalledExactlyOnceWith(
        'Failed to load plugin failed-plugin code from /plugins/failed.js',
      );
    });

    it('isolates a rejected extension registration and does not expose its module', async () => {
      const stage = { key: 'rejected-stage' } as IStageTypeConfig;
      const registrationError = new Error('untrusted registration error payload');
      const failedModule = { plugin: { preconfiguredJobStages: [stage] } };
      const successfulModule = { plugin: {} };
      const failedPlugin = {
        id: 'failed-plugin',
        version: '1.0.0',
        url: 'https://user:password@example.com/plugins/failed.js?token=secret#fragment',
      };
      const successfulPlugin = { id: 'successful-plugin', version: '1.0.0', url: '/plugins/successful.js' };
      const errorSpy = vi.spyOn(console, 'error').mockReturnValue(undefined);
      vi.spyOn(Registry.pipeline, 'registerPreconfiguredJobStage').mockReturnValue(Promise.reject(registrationError));
      loadModuleFromUrlSpy.mockImplementation((url: string) =>
        Promise.resolve(url === failedPlugin.url ? failedModule : successfulModule),
      );
      const failedMetadata = pluginRegistry.registerPluginMetaData('deck', failedPlugin)!;
      const successfulMetadata = pluginRegistry.registerPluginMetaData('deck', successfulPlugin)!;

      await expectAsync(pluginRegistry.loadPlugins()).toBeResolvedTo([undefined, successfulModule]);

      expect(failedMetadata.module).toBeUndefined();
      expect(successfulMetadata.module).toBe(successfulModule);
      expect(errorSpy).toHaveBeenCalledExactlyOnceWith(
        'Failed to load plugin failed-plugin code from https://example.com/plugins/failed.js',
      );
    });

    it('waits for every extension registration before isolating a rejected plugin', async () => {
      const rejectedStage = { key: 'rejected-stage' } as IStageTypeConfig;
      const deferredStage = { key: 'deferred-stage' } as IStageTypeConfig;
      const registrationError = new Error('registration failed');
      let rejectRegistration!: (reason?: any) => void;
      let resolveRegistration!: (stage: IStageTypeConfig) => void;
      const rejectedRegistration = new Promise<IStageTypeConfig>((_, reject) => (rejectRegistration = reject));
      const deferredRegistration = new Promise<IStageTypeConfig>((resolve) => (resolveRegistration = resolve));
      const plugin = { id: 'failed-plugin', version: '1.0.0', url: '/plugins/failed.js' };
      const pluginModuleWithExtensions = {
        plugin: { preconfiguredJobStages: [rejectedStage, deferredStage] },
      };
      const errorSpy = vi.spyOn(console, 'error').mockReturnValue(undefined);
      vi.spyOn(Registry.pipeline, 'registerPreconfiguredJobStage').mockImplementation((stage) =>
        stage === rejectedStage ? rejectedRegistration : deferredRegistration,
      );
      loadModuleFromUrlSpy.mockReturnValue(Promise.resolve(pluginModuleWithExtensions));
      const metadata = pluginRegistry.registerPluginMetaData('deck', plugin)!;
      let settled = false;

      const aggregatePromise = pluginRegistry.loadPlugins().then((result) => {
        settled = true;
        return result;
      });
      rejectRegistration(registrationError);
      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(settled).toBe(false);
      expect(errorSpy).not.toHaveBeenCalled();
      expect(metadata.module).toBeUndefined();

      resolveRegistration(deferredStage);
      await expectAsync(aggregatePromise).toBeResolvedTo([undefined]);
      expect(errorSpy).toHaveBeenCalledExactlyOnceWith(
        'Failed to load plugin failed-plugin code from /plugins/failed.js',
      );
      expect(metadata.module).toBeUndefined();
    });

    it('waits for pending extension work before isolating a later synchronous registration failure', async () => {
      const stage = { key: 'deferred-stage' } as IStageTypeConfig;
      const registrationError = new Error('help registration failed');
      let resolveRegistration!: (stage: IStageTypeConfig) => void;
      const deferredRegistration = new Promise<IStageTypeConfig>((resolve) => (resolveRegistration = resolve));
      const plugin = { id: 'failed-plugin', version: '1.0.0', url: '/plugins/failed.js' };
      const pluginModuleWithExtensions = {
        plugin: { preconfiguredJobStages: [stage], help: { key: 'value' } },
      };
      const errorSpy = vi.spyOn(console, 'error').mockReturnValue(undefined);
      vi.spyOn(Registry.pipeline, 'registerPreconfiguredJobStage').mockReturnValue(deferredRegistration);
      vi.spyOn(HelpContentsRegistry, 'register').mockImplementation(() => {
        throw registrationError;
      });
      loadModuleFromUrlSpy.mockReturnValue(Promise.resolve(pluginModuleWithExtensions));
      const metadata = pluginRegistry.registerPluginMetaData('deck', plugin)!;
      let settled = false;

      const aggregatePromise = pluginRegistry.loadPlugins().then((result) => {
        settled = true;
        return result;
      });
      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(settled).toBe(false);
      expect(errorSpy).not.toHaveBeenCalled();
      expect(metadata.module).toBeUndefined();

      resolveRegistration(stage);
      await expectAsync(aggregatePromise).toBeResolvedTo([undefined]);
      expect(errorSpy).toHaveBeenCalledExactlyOnceWith(
        'Failed to load plugin failed-plugin code from /plugins/failed.js',
      );
      expect(metadata.module).toBeUndefined();
    });

    it('starts every plugin attempt and isolates a synchronous load throw', async () => {
      const firstPlugin = { id: 'first-plugin', version: '1.0.0', url: '/plugins/first.js' };
      const secondPlugin = { id: 'second-plugin', version: '1.0.0', url: '/plugins/second.js' };
      const loadSpy = vi.spyOn<any>(pluginRegistry, 'load').mockImplementation((plugin: IPluginMetaData) => {
        if (plugin.id === firstPlugin.id) {
          throw new Error('synchronous load failure');
        }
        return Promise.resolve(pluginModule);
      });
      const errorSpy = vi.spyOn(console, 'error').mockReturnValue(undefined);
      pluginRegistry.registerPluginMetaData('deck', firstPlugin);
      pluginRegistry.registerPluginMetaData('deck', secondPlugin);

      await expectAsync(pluginRegistry.loadPlugins()).toBeResolvedTo([undefined, pluginModule]);

      expect(loadSpy).toHaveBeenCalledTimes(2);
      expect(errorSpy).toHaveBeenCalledExactlyOnceWith(
        'Failed to load plugin first-plugin code from /plugins/first.js',
      );
    });

    it('isolates and logs metadata validation failures', async () => {
      const errorSpy = vi.spyOn(console, 'error').mockReturnValue(undefined);
      const invalidMetadata = pluginRegistry.registerPluginMetaData('deck', {
        id: 'invalid-plugin',
        version: '1.0.0',
        url: '/plugins/invalid.js?token=secret#fragment',
      })!;
      invalidMetadata.id = '';

      await expectAsync(pluginRegistry.loadPlugins()).toBeResolvedTo([undefined]);

      expect(loadModuleFromUrlSpy).not.toHaveBeenCalled();
      expect(errorSpy).toHaveBeenCalledExactlyOnceWith('Failed to load plugin <unknown> code from /plugins/invalid.js');
    });

    it('should resolve to all loaded plugin modules', async () => {
      loadModuleFromUrlSpy.mockImplementation(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData('deck', plugin1);

      await expectAsync(pluginRegistry.loadPlugins()).toBeResolvedTo([pluginModule]);
    });

    it('normalizes authoritative id, url, and source without exposing a manifest module', async () => {
      loadModuleFromUrlSpy.mockImplementation(fakePromise(pluginModule));
      const plugin1 = {
        id: 'io.spinnaker.test.plugin',
        name: 'malicious-name',
        url: '/trusted.js',
        devUrl: '/malicious.js',
        version: '1.0.0',
        source: 'gate',
        module: { plugin: { initialize: () => undefined } },
      } as IPluginMetaData & { source: string };
      pluginRegistry.registerPluginMetaData('deck', plugin1);
      expect(pluginRegistry.getRegisteredPlugins()[0]).toEqual({
        id: 'io.spinnaker.test.plugin',
        source: 'deck',
        url: '/trusted.js',
        version: '1.0.0',
      });
    });

    it('prefers plugins from deck manifest over plugins from gate manifest', async () => {
      loadModuleFromUrlSpy.mockImplementation(fakePromise(pluginModule));
      const deckManifest = {
        id: 'io.spinnaker.test.plugin',
        version: '1.0.1',
        url: 'test',
        source: 'gate',
      } as IPluginMetaData & { source: string };
      const gateManifest = {
        id: 'io.spinnaker.test.plugin',
        version: '1.0.0',
        source: 'deck',
      } as IPluginMetaData & { source: string };
      pluginRegistry.registerPluginMetaData('deck', deckManifest);
      pluginRegistry.registerPluginMetaData('gate', gateManifest);
      expect(pluginRegistry.getRegisteredPlugins().length).toBe(1);
      expect(pluginRegistry.getRegisteredPlugins()[0]).toEqual({
        id: 'io.spinnaker.test.plugin',
        source: 'deck',
        url: 'test',
        version: '1.0.1',
      });
    });

    it('prefers plugins from deck manifest regardless of registration order', async () => {
      loadModuleFromUrlSpy.mockImplementation(fakePromise(pluginModule));
      const deckManifest = {
        id: 'io.spinnaker.test.plugin',
        version: '1.0.1',
        url: 'test',
        source: 'gate',
      } as IPluginMetaData & { source: string };
      const gateManifest = {
        id: 'io.spinnaker.test.plugin',
        version: '1.0.0',
        source: 'deck',
      } as IPluginMetaData & { source: string };
      pluginRegistry.registerPluginMetaData('gate', gateManifest);
      pluginRegistry.registerPluginMetaData('deck', deckManifest);
      expect(pluginRegistry.getRegisteredPlugins().length).toBe(1);
      expect(pluginRegistry.getRegisteredPlugins()[0]).toEqual({
        id: 'io.spinnaker.test.plugin',
        source: 'deck',
        url: 'test',
        version: '1.0.1',
      });
    });

    it('should return the normalized metadata object', async () => {
      loadModuleFromUrlSpy.mockImplementation(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' } as IPluginMetaData;
      const normalized = pluginRegistry.registerPluginMetaData('deck', plugin1);
      expect(pluginRegistry.getRegisteredPlugins()[0]).toBe(normalized);
    });

    it('should store the loaded module onto the metadata object', async () => {
      loadModuleFromUrlSpy.mockImplementation(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' } as IPluginMetaData;
      const normalized = pluginRegistry.registerPluginMetaData('deck', plugin1);

      await pluginRegistry.loadPlugins();
      expect(normalized.module).toBe(pluginModule);
    });

    it('should register stages found on the plugin', async () => {
      const registerStageSpy = vi.spyOn(Registry.pipeline, 'registerStage').mockReturnValue(undefined);
      loadModuleFromUrlSpy.mockImplementation(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData('deck', plugin1);

      await pluginRegistry.loadPlugins();
      expect(registerStageSpy).toHaveBeenCalledTimes(1);
      expect(registerStageSpy).toHaveBeenCalledWith(pluginModule.plugin.stages[0]);
    });
  });
});
