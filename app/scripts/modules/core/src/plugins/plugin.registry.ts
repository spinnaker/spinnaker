import { API } from 'core/api';
import { Registry } from 'core/registry';
import { IStageTypeConfig } from 'core/domain';

export interface IDeckPlugin {
  stages?: IStageTypeConfig[];
}

export interface IPluginManifest {
  plugins: IPluginMetaData[];
}

export interface IPluginMetaData {
  id?: string;
  /** @deprecated use `id` instead */
  name?: string;
  version: string;
  url?: string;
  /** @deprecated if using a plugin-manifest.json baked into a deck instance, use `url` instead */
  devUrl?: string;
  module?: any;
}

export class PluginRegistry {
  private pluginManifests: IPluginMetaData[] = [];

  getRegisteredPlugins() {
    return this.pluginManifests.slice();
  }

  registerPluginMetaData(pluginMetaData: IPluginMetaData): IPluginMetaData {
    const normalizedMetaData = this.normalize(pluginMetaData);
    this.validateMetaData(normalizedMetaData);
    this.pluginManifests.push(normalizedMetaData);
    return normalizedMetaData;
  }

  // Temporary backwards compat, remove in 2020 after armory has migrated
  normalize(pluginMetaData: IPluginMetaData): IPluginMetaData {
    const { devUrl, url, id, name, ...rest } = pluginMetaData;

    return {
      id: id ?? name,
      url: url ?? devUrl,
      ...rest,
    };
  }

  validateMetaData(pluginMetaData: IPluginMetaData) {
    if (!pluginMetaData) {
      throw new Error(`Invalid plugin manifest: received ${pluginMetaData} object`);
    }
    const keys: Array<keyof IPluginMetaData> = ['id', 'version'];
    const missingKeys = keys.filter(key => !pluginMetaData[key]);
    if (missingKeys.length) {
      throw new Error(`Invalid plugin manifest: Required key is missing: '${missingKeys.join(', ')}'`);
    }
  }

  /** Loads plugin manifest file served as a custom deck asset */
  public loadPluginManifestFromDeck() {
    const uri = '/plugin-manifest.js';
    const loadPromise = this.loadModuleFromUrl(uri)
      .then((pluginManifest: IPluginManifest) => {
        if (!pluginManifest || !pluginManifest.plugins) {
          throw new Error(`Expected plugin-manifest.js to contain an export named 'plugins' but it did not.`);
        }
        return pluginManifest.plugins;
      })
      .catch(error => {
        console.error(`Failed to load ${uri} from deck`);
        throw error;
      });

    return this.loadPluginManifest(uri, loadPromise);
  }

  /** Loads plugin manifest file served from gate */
  public loadPluginManifestFromGate() {
    const uri = '/plugins/deck/plugin-manifest.json';
    const loadPromise = API.one(uri)
      .get()
      .catch((error: any) => {
        console.error(`Failed to load ${uri} from gate`);
        throw error;
      });

    return this.loadPluginManifest(uri, loadPromise);
  }

  /**
   * Loads plugin manifests from Gate and from `/plugin-manifest.json` in the deck resources
   *
   * plugin-manifest.json should contain an array of IPluginManifest(s) exported as `plugin`, i.e.,
   * export const plugins = [{ 'name': 'myPlugin', 'version': '1.2.3', 'devUrl': '/plugins/index.js' }]
   */
  public async loadPluginManifest(
    location: string,
    pluginsMetaDataPromise: Promise<IPluginMetaData[]>,
  ): Promise<IPluginMetaData[]> {
    try {
      const plugins = await pluginsMetaDataPromise;
      return plugins.map(pluginMetaData => this.registerPluginMetaData(pluginMetaData));
    } catch (error) {
      console.error(`Error loading plugin manifest from ${location}`);
      throw error;
    }
  }

  public loadPlugins(): Promise<any[]> {
    return Promise.all(this.pluginManifests.map(plugin => this.load(plugin)));
  }

  private async load(pluginMetaData: IPluginMetaData) {
    this.validateMetaData(pluginMetaData);

    // Use `devUrl` from the manifest, if it exists.
    // This will be the case during local development.
    const { devUrl, url } = pluginMetaData;
    const gateUrl = `${API.baseUrl}/plugins/deck/${pluginMetaData.id}/${pluginMetaData.version}/index.js`;
    const pluginUrl = url ?? devUrl ?? gateUrl;

    try {
      const module = await this.loadModuleFromUrl(pluginUrl);
      Object.assign(pluginMetaData, { module });

      if (!module || !module.plugin) {
        throw new Error(
          `Successfully loaded plugin module from ${pluginUrl}, but it doesn't export an object called 'plugin'`,
        );
      }

      const plugin: IDeckPlugin = module.plugin;

      // Register extensions with deck.
      plugin.stages?.forEach(stage => Registry.pipeline.registerStage(stage));

      return module;
    } catch (error) {
      console.error(`Failed to load plugin code from ${pluginUrl}`);
      throw error;
    }
  }

  private loadModuleFromUrl(url: string) {
    // This inline comment is used by webpack to emit a native import() (instead of doing a webpack import)
    // See: https://webpack.js.org/api/module-methods/
    return import(/* webpackIgnore: true */ url);
  }
}
