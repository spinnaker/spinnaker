import { IStageTypeConfig } from '../domain';
import { IDeckPlugin } from './deck.plugin';
import { RequestBuilder } from '../api';
import { IPluginMetaData, PluginRegistry } from './plugin.registry';
import { Registry } from '../registry';
import { mock } from 'angular';

const fakePromise = (result: any = undefined) => () => new Promise((resolve) => resolve(result));

describe('PluginRegistry', () => {
  let pluginRegistry: PluginRegistry;
  let loadModuleFromUrlSpy: jasmine.Spy;
  beforeEach(() => {
    pluginRegistry = new PluginRegistry() as any;
    // @ts-ignore
    loadModuleFromUrlSpy = spyOn(pluginRegistry, 'loadModuleFromUrl');
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
      expect(pluginRegistry.getRegisteredPlugins()[0]).toEqual(jasmine.objectContaining(plugin1));
      expect(pluginRegistry.getRegisteredPlugins()[1]).toEqual(jasmine.objectContaining(plugin2));
    });
  });

  it(
    'loadPluginManifestFromDeck() should fetch /plugin-manifest.json from deck assets',
    mock.inject(async ($http: any) => {
      const spy = spyOn($http, 'get').and.callFake(() => Promise.resolve({ data: [] }));
      await pluginRegistry.loadPluginManifestFromDeck();
      expect(spy).toHaveBeenCalledWith('/plugin-manifest.json');
    }),
  );

  it('loadPluginManifestFromGate() should fetch from gate /plugins/deck/plugin-manifest.json', async () => {
    const spy = spyOn(RequestBuilder.defaultHttpClient, 'get').and.callFake(() => Promise.resolve([] as any));
    await pluginRegistry.loadPluginManifestFromGate();
    expect(spy).toHaveBeenCalled();
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

    it('should load all registered plugins', () => {
      // @ts-ignore
      const loadSpy = spyOn(pluginRegistry, 'load').and.callFake(fakePromise());
      const plugin1 = { id: 'foo', version: '1.0.0' };
      const plugin2 = { id: 'bar', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData('deck', plugin1);
      pluginRegistry.registerPluginMetaData('deck', plugin2);

      expect(loadSpy.calls.count()).toBe(0);

      pluginRegistry.loadPlugins();

      expect(loadSpy.calls.count()).toBe(2);
      expect(loadSpy.calls.first().args[0]).toEqual(jasmine.objectContaining(plugin1));
      expect(loadSpy.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining(plugin2));
    });

    it('should return a rejected promise if a loaded module doesnt contain an export named plugin', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise({}));
      spyOn(console, 'error').and.stub();
      const plugin1 = { id: 'foo', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData('deck', plugin1);

      await expectAsync(pluginRegistry.loadPlugins()).toBeRejected();
    });

    it('should resolve to all loaded plugin modules', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData('deck', plugin1);

      await expectAsync(pluginRegistry.loadPlugins()).toBeResolvedTo([pluginModule]);
    });

    it('should normalize the metadata object', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { name: 'io.spinnaker.test.plugin', devUrl: 'abc', version: '1.0.0' } as IPluginMetaData;
      pluginRegistry.registerPluginMetaData('deck', plugin1);
      expect(pluginRegistry.getRegisteredPlugins()[0]).toEqual({
        id: 'io.spinnaker.test.plugin',
        source: 'deck',
        url: 'abc',
        version: '1.0.0',
      });
    });

    it('prefers plugins from deck manifest over plugins from gate manifest', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const deckManifest = { id: 'io.spinnaker.test.plugin', version: '1.0.1', url: 'test' } as IPluginMetaData;
      const gateManifest = { id: 'io.spinnaker.test.plugin', version: '1.0.0' } as IPluginMetaData;
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
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const deckManifest = { id: 'io.spinnaker.test.plugin', version: '1.0.1', url: 'test' } as IPluginMetaData;
      const gateManifest = { id: 'io.spinnaker.test.plugin', version: '1.0.0' } as IPluginMetaData;
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
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' } as IPluginMetaData;
      const normalized = pluginRegistry.registerPluginMetaData('deck', plugin1);
      expect(pluginRegistry.getRegisteredPlugins()[0]).toBe(normalized);
    });

    it('should store the loaded module onto the metadata object', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' } as IPluginMetaData;
      const normalized = pluginRegistry.registerPluginMetaData('deck', plugin1);

      await pluginRegistry.loadPlugins();
      expect(normalized.module).toBe(pluginModule);
    });

    it('should register stages found on the plugin', async () => {
      const registerStageSpy = spyOn(Registry.pipeline, 'registerStage');
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData('deck', plugin1);

      await pluginRegistry.loadPlugins();
      expect(registerStageSpy).toHaveBeenCalledTimes(1);
      expect(registerStageSpy).toHaveBeenCalledWith(pluginModule.plugin.stages[0]);
    });
  });
});
