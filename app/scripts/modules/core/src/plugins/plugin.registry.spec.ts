import { IStageTypeConfig } from 'core/domain';
import { API } from '../api';
import { IDeckPlugin, IPluginManifest, IPluginMetaData, PluginRegistry } from './plugin.registry';
import { Registry } from 'core/registry';

type TestPluginRegistry = PluginRegistry & {
  load(pluginManifestData: IPluginManifest): Promise<any>;
  loadModuleFromUrl(url: string): Promise<any>;
};

const fakePromise = (result: any = undefined) => () => new Promise(resolve => resolve(result));

describe('PluginRegistry', () => {
  let pluginRegistry: TestPluginRegistry;
  let loadModuleFromUrlSpy: jasmine.Spy;
  beforeEach(() => {
    pluginRegistry = new PluginRegistry() as any;
    loadModuleFromUrlSpy = spyOn(pluginRegistry, 'loadModuleFromUrl');
  });

  describe('.register()', () => {
    it('should validate plugin manifests', () => {
      expect(() => pluginRegistry.registerPluginMetaData({} as any)).toThrowError(/Invalid plugin manifest/);
    });

    it('should add plugins manifests to the registry', () => {
      const plugin1 = { id: 'foo', version: '1.0.0' };
      const plugin2 = { id: 'bar', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData(plugin1);
      pluginRegistry.registerPluginMetaData(plugin2);
      expect(pluginRegistry.getRegisteredPlugins().length).toBe(2);
      expect(pluginRegistry.getRegisteredPlugins()[0]).toEqual(jasmine.objectContaining(plugin1));
      expect(pluginRegistry.getRegisteredPlugins()[1]).toEqual(jasmine.objectContaining(plugin2));
    });
  });

  it('loadPluginManifestFromDeck() should import() from /plugin-manifest.js', async () => {
    loadModuleFromUrlSpy.and.callFake(() => Promise.resolve({ plugins: [] }));
    await pluginRegistry.loadPluginManifestFromDeck();
    expect(loadModuleFromUrlSpy).toHaveBeenCalledWith('/plugin-manifest.js');
  });

  it('loadPluginManifestFromGate() should fetch from gate /plugins/deck/plugin-manifest.json', async () => {
    const spy = jasmine.createSpy('get', () => Promise.resolve([])).and.callThrough();
    spyOn(API as any, 'getFn').and.callFake(() => spy);
    await pluginRegistry.loadPluginManifestFromGate();
    expect(spy).toHaveBeenCalled();
  });

  describe('loadPlugins()', () => {
    let pluginModule: { plugin: IDeckPlugin };
    beforeEach(() => {
      pluginModule = {
        plugin: {
          stages: [({ id: 'mystage' } as any) as IStageTypeConfig],
        },
      };
    });

    it('should load all registered plugins', () => {
      const loadSpy = spyOn(pluginRegistry, 'load').and.callFake(fakePromise());
      const plugin1 = { id: 'foo', version: '1.0.0' };
      const plugin2 = { id: 'bar', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData(plugin1);
      pluginRegistry.registerPluginMetaData(plugin2);

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
      pluginRegistry.registerPluginMetaData(plugin1);

      await expectAsync(pluginRegistry.loadPlugins()).toBeRejected();
    });

    it('should resolve to all loaded plugin modules', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData(plugin1);

      await expectAsync(pluginRegistry.loadPlugins()).toBeResolvedTo([pluginModule]);
    });

    it('should normalize the metadata object', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { name: 'foo', devUrl: 'abc', version: '1.0.0' } as IPluginMetaData;
      pluginRegistry.registerPluginMetaData(plugin1);
      expect(pluginRegistry.getRegisteredPlugins()[0]).toEqual({ id: 'foo', url: 'abc', version: '1.0.0' });
    });

    it('should return the normalized metadata object', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' } as IPluginMetaData;
      const normalized = pluginRegistry.registerPluginMetaData(plugin1);
      expect(pluginRegistry.getRegisteredPlugins()[0]).toBe(normalized);
    });

    it('should store the loaded module onto the metadata object', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' } as IPluginMetaData;
      const normalized = pluginRegistry.registerPluginMetaData(plugin1);

      await pluginRegistry.loadPlugins();
      expect(normalized.module).toBe(pluginModule);
    });

    it('should register stages found on the plugin', async () => {
      const registerStageSpy = spyOn(Registry.pipeline, 'registerStage');
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { id: 'foo', version: '1.0.0' };
      pluginRegistry.registerPluginMetaData(plugin1);

      await pluginRegistry.loadPlugins();
      expect(registerStageSpy).toHaveBeenCalledTimes(1);
      expect(registerStageSpy).toHaveBeenCalledWith(pluginModule.plugin.stages[0]);
    });
  });
});
