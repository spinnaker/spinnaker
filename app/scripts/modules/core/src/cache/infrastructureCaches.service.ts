import {module} from 'angular';

import {AbstractBaseCacheService} from './abstractBaseCache.service';
import {DECK_CACHE_SERVICE, DeckCacheService, ICache, ICacheConfig} from './deckCache.service';

export class InfrastructureCacheService extends AbstractBaseCacheService {

  private static NAMESPACE = 'infrastructure';

  constructor(deckCacheFactory: DeckCacheService) {
    super(deckCacheFactory);
  }

  public getNamespace(): string {
    return InfrastructureCacheService.NAMESPACE;
  }

  public clearCache(key: string): void {
    const item: ICache = this.caches[key];
    if (item && item.removeAll) {
      item.keys().forEach((k: string) => item.remove(k));
      item.onReset.forEach((method: Function) => method());
    }
  }

  public createCache(key: string, config: ICacheConfig): ICache {
    const cache: ICache = super.createCache(key, config);
    cache.onReset = config.onReset;

    return cache;
  }
}

export const INFRASTRUCTURE_CACHE_SERVICE = 'spinnaker.core.cache.infrastructure';
module(INFRASTRUCTURE_CACHE_SERVICE, [DECK_CACHE_SERVICE])
  .service('infrastructureCaches', InfrastructureCacheService);
