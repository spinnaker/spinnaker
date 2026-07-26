import { REST } from '../api/ApiService';
import { SETTINGS } from '../config/settings';
import type { IDeckPlugin } from './deck.plugin';
import { registerPluginExtensions } from './deck.plugin';

export interface IPluginModule {
  plugin: IDeckPlugin;
  [key: string]: unknown;
}

/** The shape of plugin metadata objects in plugin-manifest.json */
export interface IPluginMetaData {
  id?: string;
  /** @deprecated use `id` instead */
  name?: string;
  version: string;
  url?: string;
  /** @deprecated if using a plugin-manifest.json baked into a deck instance, use `url` instead */
  devUrl?: string;
  module?: IPluginModule;
}

type ISource = 'gate' | 'deck';

export interface INormalizedPluginMetaData {
  id: string;
  source: ISource;
  version: string;
  url?: string;
  module?: IPluginModule;
}

function getSafePluginId(pluginMetaData: IPluginMetaData): string {
  const id = pluginMetaData.id ?? pluginMetaData.name;
  if (typeof id !== 'string') {
    return '<unknown>';
  }
  const safeId = Array.from(id)
    .filter((character) => character.charCodeAt(0) >= 32 && character.charCodeAt(0) !== 127)
    .join('')
    .slice(0, 128);
  return safeId || '<unknown>';
}

function getSafePluginUrl(url: string): string {
  try {
    const parsedUrl = new URL(url, window.location.origin);
    if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
      return `<${parsedUrl.protocol.replace(':', '')} plugin URL>`;
    }
    const isRelative = !/^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(url);
    return isRelative ? parsedUrl.pathname : `${parsedUrl.protocol}//${parsedUrl.host}${parsedUrl.pathname}`;
  } catch {
    return '<invalid plugin URL>';
  }
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
        console.error(`Attempted to load two copies of the same plugin from ${source}`);
        throw new Error(`Attempted to load two copies of the same plugin from ${source}`);
      } else {
        // If gate and deck both register the same plugin id, deck wins
        // eslint-disable-next-line no-console
        console.log(
          `Attempted to load plugin ${getSafePluginId(pluginMetaData)} from gate and deck.   Using plugin from deck.`,
        );
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
    const { devUrl, url, id, name, version } = pluginMetaData;

    return {
      version,
      id: id ?? name,
      url: url ?? devUrl,
      source,
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
    const loadPromise = fetch(uri, { credentials: 'include' })
      .then((response) => (response.ok ? response.json() : []))
      .catch((): IPluginMetaData[] => {
        console.error(`Failed to load ${uri} from ${source}`);
        return [];
      });

    return this.loadPluginManifest(source, uri, loadPromise);
  }

  /** Loads plugin manifest file served from gate */
  public loadPluginManifestFromGate() {
    const source = 'gate';
    const uri = '/plugins/deck/plugin-manifest.json';
    const loadPromise: PromiseLike<IPluginMetaData[]> = REST(uri)
      .get()
      .catch((): IPluginMetaData[] => {
        console.error(`Failed to load ${uri} from ${source}`);
        // If the Gate plugin manifest cannot be loaded, ignore and continue
        return [];
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
  ): Promise<Array<INormalizedPluginMetaData | undefined>> {
    try {
      const plugins = await pluginsMetaDataPromise;
      return (plugins || []).map((pluginMetaData) => this.registerPluginMetaData(source, pluginMetaData));
    } catch {
      console.error(`Error loading plugin manifest from ${location}`);
      return [];
    }
  }

  public loadPlugins(): Promise<Array<IPluginModule | undefined>> {
    return Promise.all(
      this.pluginManifests.map((plugin) =>
        Promise.resolve()
          .then(() => this.load(plugin))
          .catch(() => {
            console.error(
              `Failed to load plugin ${getSafePluginId(plugin)} code from ${getSafePluginUrl(
                this.getPluginUrl(plugin),
              )}`,
            );
            return undefined;
          }),
      ),
    );
  }

  private async load(pluginMetaData: INormalizedPluginMetaData): Promise<IPluginModule> {
    this.validateMetaData(pluginMetaData);

    const pluginUrl = this.getPluginUrl(pluginMetaData);
    const module = await this.loadModuleFromUrl(pluginUrl);
    if (!module || !module.plugin) {
      throw new Error(`Successfully loaded plugin module, but it doesn't export an object called 'plugin'`);
    }

    await registerPluginExtensions(module.plugin);
    pluginMetaData.module = module;
    return module;
  }

  private getPluginUrl(pluginMetaData: IPluginMetaData): string {
    // Use `url` from the manifest, if it exists. This will be the case during local development.
    const { devUrl, url } = pluginMetaData;
    const gateUrl = `${SETTINGS.gateUrl}/plugins/deck/${pluginMetaData.id}/${pluginMetaData.version}/index.js`;
    return url ?? devUrl ?? gateUrl;
  }

  private loadModuleFromUrl(url: string): Promise<IPluginModule> {
    // This inline comment is used by webpack to emit a native import() (instead of doing a webpack import)
    // See: https://webpack.js.org/api/module-methods/
    return import(/* webpackIgnore: true */ /* @vite-ignore */ url);
  }
}
