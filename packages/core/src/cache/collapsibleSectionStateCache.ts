import { Cache, CacheFactory } from 'cachefactory';

export class CollapsibleSectionStateCacheInternal {
  private cacheFactory = new CacheFactory();
  private cacheId = 'collapsibleSectionStateCache';
  private stateCache: Cache;

  constructor() {
    try {
      this.cacheFactory.createCache(this.cacheId, {
        maxAge: 7 * 24 * 60 * 60 * 1000, // 7 days
        deleteOnExpire: 'aggressive',
        storageMode: 'localStorage',
      });
    } catch (e) {
      // trying to create a cache multiple times throws and Error
    }

    this.stateCache = this.cacheFactory.get(this.cacheId);
  }

  public isSet(heading: string): boolean {
    return !!heading && this.stateCache.get(heading) !== undefined;
  }

  public isExpanded(heading: string): boolean {
    return !!heading && this.stateCache.get(heading) === true;
  }

  public setExpanded(heading: string, expanded: boolean) {
    if (heading) {
      this.stateCache.put(heading, !!expanded);
    }
  }
}

export const CollapsibleSectionStateCache = new CollapsibleSectionStateCacheInternal();
