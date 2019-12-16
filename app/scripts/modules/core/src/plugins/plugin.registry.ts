import { Registry } from 'core/registry';
import { IStageTypeConfig } from '../domain';

export interface IDeckPlugin {
  stages?: IStageTypeConfig[];
}

export interface IPluginManifestData {
  name: string;
  version: number;
}

export interface ILocalDevPluginManifestData extends IPluginManifestData {
  url: string;
}

export class PluginRegistry {
  private pluginMetaData: IPluginManifestData[] = [];

  register(pluginMetaData: IPluginManifestData) {
    this.pluginMetaData.push(pluginMetaData);
  }

  validateMetadata(pluginMetaData: IPluginManifestData) {
    if (!pluginMetaData) {
      throw new Error(`Invalid plugin metadata: received ${pluginMetaData} object`);
    }
    const keys: Array<keyof IPluginManifestData> = ['name', 'version'];
    const missingKeys = keys.filter(key => !pluginMetaData[key]);
    if (missingKeys.length) {
      throw new Error(`Invalid plugin metadata: Required key is missing: '${missingKeys.join(', ')}'`);
    }
  }

  public loadPlugins(): Promise<any[]> {
    return Promise.all(this.pluginMetaData.map(plugin => this.load(plugin)));
  }

  private async load(pluginMetaData: IPluginManifestData | ILocalDevPluginManifestData) {
    this.validateMetadata(pluginMetaData);

    // Use `url` from the manifest, if it exists.
    // This will be the case only during local development.
    const devUrl = (pluginMetaData as ILocalDevPluginManifestData).url;

    // This will eventually build the url from name/version and fetch binaries from Gate/Front50
    // const { name, version } = pluginMetaData;
    // const url = devUrl || `${gateurl}/plugins/${name}/${version}/index.js`;

    try {
      // This inline comment is used by webpack to emit a native import() (instead of doing a webpack import)
      // See: https://webpack.js.org/api/module-methods/
      const module = await import(/* webpackIgnore: true */ devUrl);
      Object.assign(pluginMetaData, { module });

      if (!module || !module.plugin) {
        throw new Error(
          `Successfully loaded plugin module from ${devUrl}, but it doesn't export an object called 'plugin'`,
        );
      }

      const plugin: IDeckPlugin = module.plugin;

      // Register extensions with deck.
      plugin.stages?.forEach(stage => Registry.pipeline.registerStage(stage));
    } catch (error) {
      console.error(`Failed to load plugin from ${devUrl}`);
      throw error;
    }
  }
}
