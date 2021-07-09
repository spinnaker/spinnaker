import { CacheFactory, CacheOptions } from 'cachefactory';

import { noop } from '../utils';

import { InfrastructureCaches } from './infrastructureCaches';
import { DeckCacheFactory, ICacheConfig, ICache } from './deckCacheFactory';

interface ICacheInstantiation {
  cacheId: string;
  config: ICacheConfig;
}

interface ITestCacheFactory extends CacheFactory {
  getCacheInstantiations(): ICacheInstantiation[];
  getKeys(): string[];
  getRemoveCalls(): string[];
  getRemoveAllCalls(): string[];
}

class TestCacheFactory implements ITestCacheFactory {
  private cacheInstantiations: ICacheInstantiation[] = [];
  private allKeys: any = [];
  private removeCalls: string[] = [];
  private removeAllCalls: string[] = [];

  public createCache(cacheId: string, config: ICacheConfig): any {
    this.cacheInstantiations.push({ cacheId, config });
  }

  public get(cacheId: string): ICache {
    return {
      destroy: noop,
      keys: (): string[] => {
        return this.allKeys;
      },
      remove: (key: string): void => {
        this.removeCalls.push(key);
      },
      removeAll: (): void => {
        this.removeAllCalls.push(cacheId);
      },
    } as ICache;
  }

  // stubs to satisfy extends CacheFactory
  public clearAll(): void {}
  public exists(_id: string): boolean {
    return false;
  }
  public info(): any {}
  public destroy(): any {}
  public destroyAll(): any {}
  public disableAll(): any {}
  public enabledAll(): any {}
  public keySet(): any {}
  public removeExpiredFromAll(): any {}
  public touchAll(): any {}
  public keys(): any {}

  public getCacheInstantiations(): ICacheInstantiation[] {
    return this.cacheInstantiations;
  }

  public getKeys(): string[] {
    return this.allKeys;
  }

  public getRemoveCalls(): string[] {
    return this.removeCalls;
  }

  public getRemoveAllCalls(): string[] {
    return this.removeAllCalls;
  }
}

describe('spinnaker.core.cache.infrastructure', function () {
  describe('cache initialization', function () {
    let cacheFactory: TestCacheFactory;
    beforeEach(() => {
      cacheFactory = new TestCacheFactory();
    });

    it('should remove all keys from previous versions', function () {
      const config: ICacheConfig = {
        version: 2,
        cacheFactory,
      } as any;

      InfrastructureCaches.createCache('myCache', config);

      expect(cacheFactory.getCacheInstantiations().length).toBe(3);
      for (let i = 0; i < 3; i++) {
        expect((cacheFactory.getCacheInstantiations()[i].config as CacheOptions).storagePrefix).toBe(
          DeckCacheFactory.getStoragePrefix('infrastructure:myCache', i),
        );
      }
      expect(cacheFactory.getRemoveAllCalls().length).toBe(2);
      expect(cacheFactory.getRemoveAllCalls()).toEqual(['infrastructure:myCache', 'infrastructure:myCache']);
    });

    it('should remove non-versioned, even if version not explicitly specified, and use version 1', function () {
      const config: ICacheConfig = {
        cacheFactory,
      } as any;
      InfrastructureCaches.createCache('myCache', config);

      expect(cacheFactory.getCacheInstantiations().length).toBe(2);
      expect((cacheFactory.getCacheInstantiations()[0].config as CacheOptions).storagePrefix).toBe(
        DeckCacheFactory.getStoragePrefix('infrastructure:myCache', 0),
      );
      expect((cacheFactory.getCacheInstantiations()[1].config as CacheOptions).storagePrefix).toBe(
        DeckCacheFactory.getStoragePrefix('infrastructure:myCache', 1),
      );
      expect(cacheFactory.getRemoveAllCalls().length).toBe(1);
      expect(cacheFactory.getRemoveAllCalls()).toEqual(['infrastructure:myCache']);
    });

    it('should remove each key when clearCache called', function () {
      const config: ICacheConfig = {
        cacheFactory,
        onReset: [],
        version: 0,
      } as any;
      InfrastructureCaches.createCache('someBadCache', config);

      cacheFactory.getKeys().push('a');
      cacheFactory.getKeys().push('b');

      const removalCallsAfterInitialization = cacheFactory.getRemoveAllCalls().length;
      InfrastructureCaches.clearCache('someBadCache');
      expect(cacheFactory.getRemoveAllCalls().length).toBe(removalCallsAfterInitialization);
      expect(cacheFactory.getRemoveCalls()).toEqual(['a', 'b']);
    });
  });
});
