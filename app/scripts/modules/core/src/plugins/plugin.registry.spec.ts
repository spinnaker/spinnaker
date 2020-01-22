import { IStageTypeConfig } from 'core/domain';
import { IDeckPlugin, IPluginManifest, PluginRegistry } from 'core/plugins/plugin.registry';
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
      expect(() => pluginRegistry.register({} as any)).toThrowError(/Invalid plugin manifest/);
    });

    it('should add plugins manifests to the registry', () => {
      const plugin1 = { name: 'foo', version: '1.0.0' };
      const plugin2 = { name: 'bar', version: '1.0.0' };
      pluginRegistry.register(plugin1);
      pluginRegistry.register(plugin2);
      expect(pluginRegistry.getRegisteredPlugins()).toEqual([plugin1, plugin2]);
    });
  });

  describe('loadPlugins()', () => {
    let pluginModule: { plugin: IDeckPlugin };
    beforeEach(() => {
      pluginModule = {
        plugin: {
          stages: [({ name: 'mystage' } as any) as IStageTypeConfig],
        },
      };
    });
    it('should load all registered plugins', () => {
      const loadSpy = spyOn(pluginRegistry, 'load').and.callFake(fakePromise());
      const plugin1 = { name: 'foo', version: '1.0.0' };
      const plugin2 = { name: 'bar', version: '1.0.0' };
      pluginRegistry.register(plugin1);
      pluginRegistry.register(plugin2);

      expect(loadSpy.calls.count()).toBe(0);

      pluginRegistry.loadPlugins();

      expect(loadSpy.calls.count()).toBe(2);
      expect(loadSpy.calls.first().args[0]).toBe(plugin1);
      expect(loadSpy.calls.mostRecent().args[0]).toBe(plugin2);
    });

    it('should return a rejected promise if a loaded module doesnt contain an export named plugin', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise({}));
      spyOn(console, 'error').and.stub();
      const plugin1 = { name: 'foo', version: '1.0.0' };
      pluginRegistry.register(plugin1);

      await expectAsync(pluginRegistry.loadPlugins()).toBeRejected();
    });

    it('should resolve to all loaded plugin modules', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { name: 'foo', version: '1.0.0' };
      pluginRegistry.register(plugin1);

      await expectAsync(pluginRegistry.loadPlugins()).toBeResolvedTo([pluginModule]);
    });

    it('should store the loaded module onto the manifest object', async () => {
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { name: 'foo', version: '1.0.0' } as IPluginManifest;
      pluginRegistry.register(plugin1);

      await pluginRegistry.loadPlugins();
      expect(plugin1.module).toBe(pluginModule);
    });

    it('should register stages found on the plugin', async () => {
      const registerStageSpy = spyOn(Registry.pipeline, 'registerStage');
      loadModuleFromUrlSpy.and.callFake(fakePromise(pluginModule));
      const plugin1 = { name: 'foo', version: '1.0.0' };
      pluginRegistry.register(plugin1);

      await pluginRegistry.loadPlugins();
      expect(registerStageSpy).toHaveBeenCalledTimes(1);
      expect(registerStageSpy).toHaveBeenCalledWith(pluginModule.plugin.stages[0]);
    });
  });
});
