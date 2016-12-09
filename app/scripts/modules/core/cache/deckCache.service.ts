import * as moment from 'moment';
import {module} from 'angular';

interface ILocalStorage {
  getItem: (key: string) => void;
  removeItem: (key: string) => void;
  setItem: (key: string, value: any) => void;
}

interface ICacheProxy {
  [key: string]: any;
}

class SelfClearingLocalStorage implements ILocalStorage {

  constructor(private $log: ng.ILogService, private settings: any, private cacheProxy: ICacheProxy) {}

  public setItem(k: string, v: any) {
    try {
      if (k.includes(this.settings.gateUrl)) {
        const response = JSON.parse(v);
        if (response.value && Array.isArray(response.value) && (response.value.length > 2) && Array.isArray(response.value[2])) {

          const val: any = response.value[2]['content-type'];
          if (val && !val.includes('application/json')) {
            return;
          }
        }
      }

      window.localStorage.setItem(k, v);
      this.cacheProxy[k] = v;
    } catch (e) {
      this.$log.warn('Local Storage Error! Clearing caches and trying again.\nException:', e);
      this.cacheProxy = Object.create(null);
      window.localStorage.clear();
      window.localStorage.setItem(k, v);
    }
  }

  public getItem(k: string): any {

    let result: any;
    if (this.cacheProxy[k] !== undefined) {
      result = this.cacheProxy[k];
    } else {
      result = window.localStorage.getItem(k);
    }

    return result;
  }

  public removeItem(k: string): void {
    delete this.cacheProxy[k];
    window.localStorage.removeItem(k);
  }
}

interface IInfo {
  created?: number;
}

interface IStats {
  ageMax: number;
  ageMin: number;
  keys: number;
}

export interface ICacheConfigOptions {
  deleteOnExpire?: string;
  disabled?: boolean;
  maxAge?: number;
  recycleFreq?: number;
  storageImpl?: ILocalStorage;
  storageMode: string;
  storagePrefix: string;
}

export interface ICacheFactory {
  createCache: (key: string, options: ICacheConfig | ICacheConfigOptions) => void;
  get: (key: string) => ICache;
}

export interface ICacheConfig {
  cacheFactory: ICacheFactory;
  disabled?: boolean;
  get?: (key: string) => string;
  initializers?: Function[];
  maxAge?: number;
  onReset?: Function[];
  version?: number;
}

export interface ICache {
  config?: ICacheConfig;
  destroy: () => void;
  get?: (key: string) => string;
  getStats?: () => IStats;
  info?: (key: string) => IInfo;
  keys: () => string[];
  onReset?: Function[];
  put?: (key: string, value: string) => string;
  remove: (key: string) => void;
  removeAll: () => void;
}

export interface ICacheMap {
  [key: string]: ICache;
}

export class DeckCacheService {

  static get $inject(): string[] {
    return ['$log', 'CacheFactory', 'settings'];
  }

  private caches: ICacheMap = Object.create(null);
  private cacheProxy: ICacheProxy = Object.create(null);

  public static getStoragePrefix(key: string, version: number): string {
    return `angular-cache.caches.${key}:${version}.`;
  }

  private static buildCacheKey(namespace: string, cacheId: string): string {

    let result: string;
    if (!namespace || !cacheId) {
      result = namespace || cacheId;
    } else {
      result = `${namespace}:${cacheId}`;
    }

    return result;
  }

  private static bombCorruptedCache(namespace: string, cacheId: string, currentVersion: number): void {

    // if the "meta-key" (the key that represents the cached keys) somehow got deleted or emptied
    // but the data did not, we need to remove the data or the cache will always return the old stale data
    const basekey: string = DeckCacheService.buildCacheKey(namespace, cacheId);
    const indexKey = `${DeckCacheService.getStoragePrefix(basekey, currentVersion)}${basekey}`;
    if (!window.localStorage[`${indexKey}.keys`] || window.localStorage[`${indexKey}.keys`] === '[]') {
      Object.keys(window.localStorage)
        .filter((key: string) => key.includes(indexKey))
        .forEach((key: string) => window.localStorage.removeItem(key));
    }
  }

  private static clearPreviousVersions(namespace: string,
                                       cacheId: string,
                                       currentVersion: number,
                                       cacheFactory: ICacheFactory): void {

    if (currentVersion) {
      DeckCacheService.bombCorruptedCache(namespace, cacheId, currentVersion);

      // clear previous versions
      for (let i = 0; i < currentVersion; i++) {
        const key = DeckCacheService.buildCacheKey(namespace, cacheId);
        if (cacheFactory.get(key)) {
          cacheFactory.get(key).destroy();
        }

        cacheFactory.createCache(key, {
          storageMode: 'localStorage',
          storagePrefix: DeckCacheService.getStoragePrefix(key, i)
        });
        cacheFactory.get(key).removeAll();
        cacheFactory.get(key).destroy();
      }
    }
  }

  private static getStats(cache: ICache): IStats {

    const keys: string[] = cache.keys();
    let ageMin = moment.now(), ageMax = 0;

    keys.forEach((key: string) => {
      const info: IInfo = cache.info(key) || {};
      ageMin = Math.min(ageMin, info.created);
      ageMax = Math.max(ageMax, info.created);
    });

    return {
      ageMax: ageMax || null,
      ageMin: ageMin || null,
      keys: keys.length
    };
  }

  private addLocalStorageCache(namespace: string, cacheId: string, cacheConfig: ICacheConfig): void {

    const key: string = DeckCacheService.buildCacheKey(namespace, cacheId);
    const cacheFactory: ICacheFactory = cacheConfig.cacheFactory || this.CacheFactory;
    const currentVersion: number = cacheConfig.version || 1;

    DeckCacheService.clearPreviousVersions(namespace, cacheId, currentVersion, cacheFactory);
    cacheFactory.createCache(key, {
      deleteOnExpire: 'aggressive',
      disabled: cacheConfig.disabled,
      maxAge: cacheConfig.maxAge || moment.duration(2, 'days').asMilliseconds(),
      recycleFreq: moment.duration(5, 'seconds').asMilliseconds(),
      storageImpl: new SelfClearingLocalStorage(this.$log, this.settings, this.cacheProxy),
      storageMode: 'localStorage',
      storagePrefix: DeckCacheService.getStoragePrefix(key, currentVersion)
    });
    this.caches[key] = cacheFactory.get(key);
    this.caches[key].getStats = DeckCacheService.getStats.bind(null, this.caches[key]);
    this.caches[key].config = cacheConfig;
  }

  constructor(private $log: ng.ILogService,
              private CacheFactory: any,
              private settings: any) {}

  public clearCache(namespace: string, key: string): void {
    if (this.caches[key] && this.caches[key].destroy) {
      this.caches[key].destroy();
      this.createCache(namespace, key, this.caches[key].config);
    }
  }

  public createCache(namespace: string, cacheId: string, config: ICacheConfig): void {
    this.addLocalStorageCache(namespace, cacheId, config);
  }

  public getCache(namespace: string = null, cacheId: string = null): ICache {
    return this.caches[DeckCacheService.buildCacheKey(namespace, cacheId)];
  }
}

export const DECK_CACHE_SERVICE = 'spinnaker.core.cache.deckCacheService';
module(DECK_CACHE_SERVICE, [
  require('angular-cache'),
  require('core/config/settings')
])
  .service('deckCacheFactory', DeckCacheService);
