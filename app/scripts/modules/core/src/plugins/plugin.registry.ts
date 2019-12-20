import { Registry } from 'core/registry';
import { IStageTypeConfig } from '../domain';

export interface IDeckPlugin {
  stages?: IStageTypeConfig[];
}

export interface IPluginManifest {
  name: string;
  version: string;
  devUrl?: string;
  module?: any;
}

export class PluginRegistry {
  private pluginManifests: IPluginManifest[] = [];

  getRegisteredPlugins() {
    return this.pluginManifests.slice();
  }

  register(pluginManifest: IPluginManifest) {
    this.validateManifest(pluginManifest);
    this.pluginManifests.push(pluginManifest);
  }

  validateManifest(pluginManifest: IPluginManifest) {
    if (!pluginManifest) {
      throw new Error(`Invalid plugin manifest: received ${pluginManifest} object`);
    }
    const keys: Array<keyof IPluginManifest> = ['name', 'version'];
    const missingKeys = keys.filter(key => !pluginManifest[key]);
    if (missingKeys.length) {
      throw new Error(`Invalid plugin manifest: Required key is missing: '${missingKeys.join(', ')}'`);
    }
  }

  public loadPlugins(): Promise<any[]> {
    return Promise.all(this.pluginManifests.map(plugin => this.load(plugin)));
  }

  private async load(pluginManifest: IPluginManifest) {
    this.validateManifest(pluginManifest);

    // Use `url` from the manifest, if it exists.
    // This will be the case only during local development.
    const { devUrl } = pluginManifest;

    // This will eventually build the url from name/version and fetch binaries from Gate/Front50
    // const { name, version } = pluginManifest;
    // const url = devUrl || `${gateurl}/plugins/${name}/${version}/index.js`;

    try {
      const module = await this.loadModuleFromUrl(devUrl);
      Object.assign(pluginManifest, { module });

      if (!module || !module.plugin) {
        throw new Error(
          `Successfully loaded plugin module from ${devUrl}, but it doesn't export an object called 'plugin'`,
        );
      }

      const plugin: IDeckPlugin = module.plugin;

      // Register extensions with deck.
      plugin.stages?.forEach(stage => Registry.pipeline.registerStage(stage));

      return module;
    } catch (error) {
      console.error(`Failed to load plugin from ${devUrl}`);
      throw error;
    }
  }

  private loadModuleFromUrl(url: string) {
    // This inline comment is used by webpack to emit a native import() (instead of doing a webpack import)
    // See: https://webpack.js.org/api/module-methods/
    return import(/* webpackIgnore: true */ url);
  }
}
