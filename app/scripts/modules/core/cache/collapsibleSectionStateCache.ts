import { module } from 'angular';

import { ICache, ICacheFactory } from './deckCache.service';

export class CollapsibleSectionStateCache {
  private stateCache: ICache;

  constructor(private CacheFactory: ICacheFactory) {
    'ngInject';
    const cacheId = 'collapsibleSectionStateCache';

    try {
      this.CacheFactory.createCache(cacheId, {
        maxAge: 7 * 24 * 60 * 60 * 1000, // 7 days
        deleteOnExpire: 'aggressive',
        storageMode: 'localStorage',
      });
    } catch (e) {
      // trying to create a cache multiple times throws and Error
    }

    this.stateCache = CacheFactory.get(cacheId);
  }

  public isSet(heading: string): boolean {
    return this.stateCache.get(heading) !== undefined;
  }

  public isExpanded(heading: string): boolean {
    return this.stateCache.get(heading) === true;
  }

  public setExpanded(heading: string, expanded: boolean) {
    this.stateCache.put(heading, !!expanded);
  }
}

export let collapsibleSectionStateCache: CollapsibleSectionStateCache = undefined;
export const COLLAPSIBLE_SECTION_STATE_CACHE = 'spinnaker.core.cache.collapsibleSectionState';
module(COLLAPSIBLE_SECTION_STATE_CACHE, [
  require('angular-cache')
])
  .service('collapsibleSectionStateCache', CollapsibleSectionStateCache)
  .run(($injector: any) => collapsibleSectionStateCache = <CollapsibleSectionStateCache>$injector.get('collapsibleSectionStateCache'));
