import { $http } from 'ngimport';

import { REST } from '../api/ApiService';
import { SETTINGS } from '../config/settings';
import { IDeckPlugin, registerPluginExtensions } from './deck.plugin';

/** The shape of plugin metadata objects in plugin-manifest.json */
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

type ISource = 'gate' | 'deck';

export interface INormalizedPluginMetaData {
  id: string;
  source: ISource;
  version: string;
  url?: string;
  module?: any;
}

export class PluginRegistry {
  private pluginManifests: INormalizedPluginMetaData[] = [];

  getRegisteredPlugins() {
    return this.pluginManifests.slice();
  }

  registerPluginMetaData(source: ISource, pluginMetaData: IPluginMetaData): INormalizedPluginMetaData | undefined {
    const metaData = this.normalize(source, pluginMetaData);
    const duplicateMetaData = this.pluginManifests.find((x) => x.id === metaData.id);

    // Handle duplicate plugin ids
    if (duplicateMetaData) {
      if (metaData.source === duplicateMetaData.source) {
        console.error(`Attempted to load two copies of the same plugin from ${source}`, metaData, duplicateMetaData);
        throw new Error(`Attempted to load two copies of the same plugin from ${source}`);
      } else {
        // If gate and deck both register the same plugin id, deck wins
        // eslint-disable-next-line no-console
        console.log(`Attempted to load plugin ${pluginMetaData.id} from gate and deck.   Using plugin from deck.`);
        if (source === 'deck') {
          this.pluginManifests = this.pluginManifests.filter((x) => x !== duplicateMetaData);
        } else if (source === 'gate') {
          return undefined;
        }
      }
    }

    this.validateMetaData(metaData);
    this.pluginManifests.push(metaData);
    return metaData;
  }

  // Temporary backwards compat, remove in 2020 after armory has migrated
  normalize(source: ISource, pluginMetaData: IPluginMetaData): INormalizedPluginMetaData {
    const { devUrl, url, id, name, ...rest } = pluginMetaData;

    return {
      id: id ?? name,
      url: url ?? devUrl,
      source,
      ...rest,
    };
  }

  validateMetaData(pluginMetaData: IPluginMetaData) {
    if (!pluginMetaData) {
      throw new Error(`Invalid plugin manifest: received ${pluginMetaData} object`);
    }
    const keys: Array<keyof IPluginMetaData> = ['id', 'version'];
    const missingKeys = keys.filter((key) => !pluginMetaData[key]);
    if (missingKeys.length) {
      throw new Error(`Invalid plugin manifest: Required key is missing: '${missingKeys.join(', ')}'`);
    }
  }

  /** Loads plugin manifest file served as a custom deck asset */
  public loadPluginManifestFromDeck() {
    const source = 'deck';
    const uri = '/plugin-manifest.json';
    const loadPromise = Promise.resolve($http.get<IPluginMetaData[]>(uri))
      .then((response) => response.data)
      .catch((error: any) => {
        console.error(`Failed to load ${uri} from ${source}`);
        throw error;
      });

    return this.loadPluginManifest(source, uri, loadPromise);
  }

  /** Loads plugin manifest file served from gate */
  public loadPluginManifestFromGate() {
    const source = 'gate';
    const uri = '/plugins/deck/plugin-manifest.json';
    const loadPromise: PromiseLike<IPluginMetaData[]> = REST(uri)
      .get()
      .catch((error: any) => {
        console.error(`Failed to load ${uri} from ${source}`);
        // If we cannot hit the Gate URL, ignore it
        if (error.data.status === 404) {
          console.error(error);
          return Promise.resolve([]);
        }
        throw error;
      });

    return this.loadPluginManifest(source, uri, loadPromise);
  }

  /**
   * Loads plugin manifests from Gate and from `/plugin-manifest.json` in the deck resources
   *
   * plugin-manifest.json should contain an array of IPluginMetaData objects, i.e.,
   * [{ 'name': 'myPlugin', 'version': '1.2.3', 'url': '/plugins/index.js' }]
   */
  public async loadPluginManifest(
    source: ISource,
    location: string,
    pluginsMetaDataPromise: PromiseLike<IPluginMetaData[]>,
  ): Promise<IPluginMetaData[]> {
    try {
      const plugins = await pluginsMetaDataPromise;
      return plugins.map((pluginMetaData) => this.registerPluginMetaData(source, pluginMetaData));
    } catch (error) {
      console.error(`Error loading plugin manifest from ${location}`);
      throw error;
    }
  }

  public loadPlugins(): Promise<any[]> {
    return Promise.all(this.pluginManifests.map((plugin) => this.load(plugin)));
  }

  private async load(pluginMetaData: IPluginMetaData) {
    this.validateMetaData(pluginMetaData);

    // Use `url` from the manifest, if it exists. This will be the case during local development.
    const { devUrl, url } = pluginMetaData;
    const gateUrl = `${SETTINGS.gateUrl}/plugins/deck/${pluginMetaData.id}/${pluginMetaData.version}/index.js`;
    const pluginUrl = url ?? devUrl ?? gateUrl;

    try {
      const module = await this.loadModuleFromUrl(pluginUrl);
      Object.assign(pluginMetaData, { module });

      if (!module || !module.plugin) {
        throw new Error(
          `Successfully loaded plugin module from ${pluginUrl}, but it doesn't export an object called 'plugin'`,
        );
      }

      return registerPluginExtensions(module.plugin as IDeckPlugin).then(() => module);
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
