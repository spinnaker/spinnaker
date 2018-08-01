import { Cache, CacheFactory, CacheOptions, ItemInfo } from 'cachefactory';
import * as moment from 'moment';

import { SETTINGS } from 'core/config/settings';

export interface ILocalStorage {
  getItem: (key: string) => void;
  removeItem: (key: string) => void;
  setItem: (key: string, value: any) => void;
}

export interface ICacheProxy {
  [key: string]: any;
}

class SelfClearingLocalStorage implements ILocalStorage {
  constructor(private cacheProxy: ICacheProxy) {}

  public setItem(k: string, v: any) {
    try {
      if (k.includes(SETTINGS.gateUrl)) {
        const response = JSON.parse(v);
        if (
          response.value &&
          Array.isArray(response.value) &&
          response.value.length > 2 &&
          response.value[2]['content-type']
        ) {
          const val: string = response.value[2]['content-type'];
          if (val && !val.includes('application/json')) {
            return;
          }
        }
      }

      window.localStorage.setItem(k, v);
      this.cacheProxy[k] = v;
    } catch (e) {
      const Console = console;
      Console.warn('Local Storage Error! Clearing caches and trying again.\nException:', e);
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

export interface IStats {
  ageMax: number;
  ageMin: number;
  keys: number;
}

export interface ICacheOptions extends CacheOptions {
  onReset?: Function[];
}

export interface ICache extends Cache {
  config: ICacheOptions;
  getStats: () => IStats;
  onReset?: Function[];
}

export interface ICacheConfig {
  cacheFactory?: CacheFactory;
  disabled?: boolean;
  get?: (key: string) => string;
  initializers?: Function[];
  maxAge?: number;
  onReset?: Function[];
  version?: number;
}

export interface ICacheMap {
  [key: string]: ICache;
}

export class DeckCacheFactory {
  private static cacheFactory: CacheFactory = new CacheFactory();
  private static caches: ICacheMap = Object.create(null);
  private static cacheProxy: ICacheProxy = Object.create(null);

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
    const basekey: string = DeckCacheFactory.buildCacheKey(namespace, cacheId);
    const indexKey = `${DeckCacheFactory.getStoragePrefix(basekey, currentVersion)}${basekey}`;
    if (
      !(window.localStorage as any)[`${indexKey}.keys`] ||
      (window.localStorage as any)[`${indexKey}.keys`] === '[]'
    ) {
      Object.keys(window.localStorage)
        .filter((key: string) => key.includes(indexKey))
        .forEach((key: string) => window.localStorage.removeItem(key));
    }
  }

  private static clearPreviousVersions(
    namespace: string,
    cacheId: string,
    currentVersion: number,
    cacheFactory: CacheFactory,
  ): void {
    if (currentVersion) {
      DeckCacheFactory.bombCorruptedCache(namespace, cacheId, currentVersion);

      // clear previous versions
      for (let i = 0; i < currentVersion; i++) {
        const key = DeckCacheFactory.buildCacheKey(namespace, cacheId);
        if (cacheFactory.exists(key)) {
          cacheFactory.get(key).destroy();
        }

        cacheFactory.createCache(key, {
          storageMode: 'localStorage',
          storagePrefix: DeckCacheFactory.getStoragePrefix(key, i),
        });
        cacheFactory.get(key).removeAll();
        cacheFactory.get(key).destroy();
      }
    }
  }

  private static getStats(cache: Cache): IStats {
    const keys = cache.keys();
    let ageMin = moment.now(),
      ageMax = 0;

    keys.forEach((key: string) => {
      const info: ItemInfo = (cache.info(key) || {}) as ItemInfo;
      ageMin = Math.min(ageMin, info.created);
      ageMax = Math.max(ageMax, info.created);
    });

    return {
      ageMax: ageMax || null,
      ageMin: ageMin || null,
      keys: keys.length,
    };
  }

  private static addLocalStorageCache(namespace: string, cacheId: string, cacheConfig: ICacheConfig): ICache {
    const key: string = DeckCacheFactory.buildCacheKey(namespace, cacheId);
    const cacheFactory: CacheFactory = cacheConfig.cacheFactory || this.cacheFactory;
    const currentVersion: number = cacheConfig.version || 1;

    DeckCacheFactory.clearPreviousVersions(namespace, cacheId, currentVersion, cacheFactory);
    cacheFactory.createCache(key, {
      deleteOnExpire: 'aggressive',
      maxAge: cacheConfig.maxAge || moment.duration(2, 'days').asMilliseconds(),
      recycleFreq: moment.duration(5, 'seconds').asMilliseconds(),
      storageImpl: new SelfClearingLocalStorage(this.cacheProxy),
      storageMode: 'localStorage',
      storagePrefix: DeckCacheFactory.getStoragePrefix(key, currentVersion),
    });
    const cache = cacheFactory.get(key) as ICache;
    this.caches[key] = cache;
    cache.getStats = DeckCacheFactory.getStats.bind(null, this.caches[key]);
    cache.config = cacheConfig;
    return cache;
  }

  public static clearCache(namespace: string, key: string): void {
    if (this.caches[key] && this.caches[key].destroy) {
      this.caches[key].destroy();
      this.createCache(namespace, key, this.caches[key].config);
    }
  }

  public static createCache(namespace: string, cacheId: string, config: ICacheConfig): ICache {
    return this.addLocalStorageCache(namespace, cacheId, config);
  }

  public static getCache(namespace: string = null, cacheId: string = null): ICache {
    return this.caches[DeckCacheFactory.buildCacheKey(namespace, cacheId)];
  }
}
