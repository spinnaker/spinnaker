import {DeckCacheService, ICache, ICacheConfig, ICacheMap, ICacheFactory} from './deckCache.service';

export abstract class AbstractBaseCacheService implements ICacheFactory {

  protected caches: ICacheMap = Object.create(null);

  constructor(private deckCacheFactory: DeckCacheService) {}

  public abstract getNamespace(): string;

  public abstract clearCache(key: string): void;

  public createCache(key: string, config: ICacheConfig): ICache {
    this.deckCacheFactory.createCache(this.getNamespace(), key, config);
    this.caches[key] = this.deckCacheFactory.getCache(this.getNamespace(), key);

    return this.caches[key];
  }

  public get(key: string): ICache {
    return this.caches[key];
  }

  public clearCaches(): void {
    Object.keys(this.caches).forEach((k: string) => this.clearCache(k));
  }
}
