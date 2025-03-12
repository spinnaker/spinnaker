import { AbstractBaseCache } from './abstractBaseCache';

export class ViewStateCacheInternal extends AbstractBaseCache {
  private static NAMESPACE = 'viewStateCache';

  public getNamespace(): string {
    return ViewStateCacheInternal.NAMESPACE;
  }

  public clearCache(key: string): void {
    const item = this.caches[key];
    if (item && item.destroy) {
      item.destroy();
      this.createCache(key, item.config);
    }
  }
}

export const ViewStateCache = new ViewStateCacheInternal();
