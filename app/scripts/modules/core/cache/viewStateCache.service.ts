import {module} from 'angular';

import {AbstractBaseCacheService} from './abstractBaseCache.service';
import {DECK_CACHE_SERVICE, DeckCacheService, ICache} from './deckCache.service';

export class ViewStateCacheService extends AbstractBaseCacheService {

  private static NAMESPACE = 'viewStateCache';

  constructor(deckCacheFactory: DeckCacheService) {
    super(deckCacheFactory);
  }

  public getNamespace(): string {
    return ViewStateCacheService.NAMESPACE;
  }

  public clearCache(key: string): void {
    const item: ICache = this.caches[key];
    if (item && item.destroy) {
      item.destroy();
      this.createCache(key, item.config);
    }
  }

}

export const VIEW_STATE_CACHE_SERVICE = 'spinnaker.core.cache.viewStateCache';
module(VIEW_STATE_CACHE_SERVICE, [DECK_CACHE_SERVICE])
  .service('viewStateCache', ViewStateCacheService);
