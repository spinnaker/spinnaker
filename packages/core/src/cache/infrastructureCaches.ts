import { AbstractBaseCache } from './abstractBaseCache';
import { ICache, ICacheConfig } from './deckCacheFactory';

export class InfrastructureCachesInternal extends AbstractBaseCache {
  private static NAMESPACE = 'infrastructure';

  public getNamespace(): string {
    return InfrastructureCachesInternal.NAMESPACE;
  }

  public clearCache(key: string): void {
    const item = this.caches[key];
    if (item && item.removeAll) {
      item.keys() && item.keys().forEach((k: string) => item.remove(k));
      item.onReset && item.onReset.forEach((method: Function) => method());
    }
  }

  public createCache(key: string, config: ICacheConfig): ICache {
    const cache = super.createCache(key, config);
    cache.onReset = config.onReset;

    return cache;
  }
}

export const InfrastructureCaches = new InfrastructureCachesInternal();
