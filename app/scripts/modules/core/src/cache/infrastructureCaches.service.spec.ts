import {mock, noop} from 'angular';

import {INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService} from './infrastructureCaches.service';
import {DeckCacheService, ICacheFactory, ICacheConfig, ICache, ICacheConfigOptions} from './deckCache.service';

interface ICacheInstantiation {
  cacheId: string;
  config: ICacheConfig | ICacheConfigOptions;
}

interface ITestCacheFactory extends ICacheFactory {
  getCacheInstantiations(): ICacheInstantiation[];
  getKeys(): string[];
  getRemoveCalls(): string[];
  getRemoveAllCalls(): string[];
}

class TestCacheFactory implements ITestCacheFactory {

  private cacheInstantiations: ICacheInstantiation[] = [];
  private keys: string[] = [];
  private removeCalls: string[] = [];
  private removeAllCalls: string[] = [];

  public createCache(cacheId: string, config: ICacheConfig) {
    this.cacheInstantiations.push({ cacheId, config });
  }

  public get(cacheId: string): ICache {
    return {
      destroy: noop,
      keys: (): string[] => {
        return this.keys;
      },
      remove: (key: string): void => {
        this.removeCalls.push(key);
      },
      removeAll: (): void => {
        this.removeAllCalls.push(cacheId);
      }
    };
  }

  public getCacheInstantiations(): ICacheInstantiation[] {
    return this.cacheInstantiations;
  }

  public getKeys(): string[] {
    return this.keys;
  }

  public getRemoveCalls(): string[] {
    return this.removeCalls;
  }

  public getRemoveAllCalls(): string[] {
    return this.removeAllCalls;
  }
}

describe('spinnaker.core.cache.infrastructure', function () {

  let deckCacheService: DeckCacheService,
    infrastructureCacheService: InfrastructureCacheService;

  beforeEach(mock.module(INFRASTRUCTURE_CACHE_SERVICE));
  beforeEach(mock.inject(
    function (_infrastructureCaches_: InfrastructureCacheService,
              _deckCacheFactory_: DeckCacheService) {
      infrastructureCacheService = _infrastructureCaches_;
      deckCacheService = _deckCacheFactory_;
    }));

  it('should inject defined objects', function () {
    expect(infrastructureCacheService).toBeDefined();
    expect(deckCacheService).toBeDefined();
  });

  describe('cache initialization', function () {

    let cacheFactory: TestCacheFactory;
    beforeEach(() => {
      cacheFactory = new TestCacheFactory();
    });

    it('should remove all keys from previous versions', function () {

      const config: ICacheConfig = {
        version: 2,
        cacheFactory
      };

      infrastructureCacheService.createCache('myCache', config);

      expect(cacheFactory.getCacheInstantiations().length).toBe(3);
      for (let i = 0; i < 3; i++) {
        expect((<ICacheConfigOptions>cacheFactory.getCacheInstantiations()[i].config).storagePrefix).toBe(DeckCacheService.getStoragePrefix('infrastructure:myCache', i));
      }
      expect(cacheFactory.getRemoveAllCalls().length).toBe(2);
      expect(cacheFactory.getRemoveAllCalls()).toEqual(['infrastructure:myCache', 'infrastructure:myCache']);
    });

    it('should remove non-versioned, even if version not explicitly specified, and use version 1', function () {

      const config: ICacheConfig = {
        cacheFactory
      };
      infrastructureCacheService.createCache('myCache', config);

      expect(cacheFactory.getCacheInstantiations().length).toBe(2);
      expect((<ICacheConfigOptions>cacheFactory.getCacheInstantiations()[0].config).storagePrefix).toBe(DeckCacheService.getStoragePrefix('infrastructure:myCache', 0));
      expect((<ICacheConfigOptions>cacheFactory.getCacheInstantiations()[1].config).storagePrefix).toBe(DeckCacheService.getStoragePrefix('infrastructure:myCache', 1));
      expect(cacheFactory.getRemoveAllCalls().length).toBe(1);
      expect(cacheFactory.getRemoveAllCalls()).toEqual(['infrastructure:myCache']);
    });

    it('should remove each key when clearCache called', function () {

      const config: ICacheConfig = {
        cacheFactory,
        onReset: [],
        version: 0
      };
      infrastructureCacheService.createCache('someBadCache', config);

      cacheFactory.getKeys().push('a');
      cacheFactory.getKeys().push('b');

      const removalCallsAfterInitialization = cacheFactory.getRemoveAllCalls().length;
      infrastructureCacheService.clearCache('someBadCache');
      expect(cacheFactory.getRemoveAllCalls().length).toBe(removalCallsAfterInitialization);
      expect(cacheFactory.getRemoveCalls()).toEqual(['a', 'b']);
    });
  });
});
