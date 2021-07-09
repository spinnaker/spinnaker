import { DeckCacheFactory, ICache, ICacheConfig, ICacheMap } from './deckCacheFactory';

export abstract class AbstractBaseCache {
  protected caches: ICacheMap = Object.create(null);

  public abstract getNamespace(): string;

  public abstract clearCache(key: string): void;

  public createCache(key: string, config: ICacheConfig): ICache {
    DeckCacheFactory.createCache(this.getNamespace(), key, config);
    this.caches[key] = DeckCacheFactory.getCache(this.getNamespace(), key);

    return this.caches[key];
  }

  public get(key: string): ICache {
    return this.caches[key];
  }

  public clearCaches(): void {
    Object.keys(this.caches).forEach((k: string) => this.clearCache(k));
  }

  public destroyCaches(): void {
    this.caches = Object.create(null);
  }
}
