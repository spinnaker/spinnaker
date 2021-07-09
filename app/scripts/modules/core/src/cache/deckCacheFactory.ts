import { Cache, CacheFactory, CacheOptions, ItemInfo } from 'cachefactory';
import { Duration } from 'luxon';

import { SETTINGS } from '../config/settings';

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
  storageMode?: 'memory' | 'localStorage' | 'sessionStorage';
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
    let ageMin = Date.now();
    let ageMax = 0;

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

  public static createCache(namespace: string, cacheId: string, cacheConfig: ICacheConfig): ICache {
    const key: string = DeckCacheFactory.buildCacheKey(namespace, cacheId);
    const cacheFactory: CacheFactory = cacheConfig.cacheFactory || this.cacheFactory;
    const currentVersion: number = cacheConfig.version || 1;

    DeckCacheFactory.clearPreviousVersions(namespace, cacheId, currentVersion, cacheFactory);

    const storageMode = cacheConfig.storageMode ?? 'localStorage';
    cacheFactory.createCache(key, {
      deleteOnExpire: 'aggressive',
      maxAge: cacheConfig.maxAge || Duration.fromObject({ days: 2 }).as('milliseconds'),
      recycleFreq: Duration.fromObject({ seconds: 5 }).as('milliseconds'),
      storageMode,
      storageImpl: storageMode === 'localStorage' ? new SelfClearingLocalStorage(this.cacheProxy) : null,
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

  public static getCache(namespace: string = null, cacheId: string = null): ICache {
    return this.caches[DeckCacheFactory.buildCacheKey(namespace, cacheId)];
  }
}
