import { flatten } from 'lodash';

import { InfrastructureCaches } from './';
import { AccountService } from '../account/AccountService';
import type { DeckRuntime } from '../bootstrap/DeckRuntime';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { CacheInitializerService } from './cacheInitializer.service';
import type { SecurityGroupReader } from '../securityGroup/securityGroupReader.service';
import { nativePromiseService } from '../utils/nativePromiseService';

interface IKeys {
  [key: string]: string[];
  sg: string[];
}

describe('Service: cacheInitializer', function () {
  let cacheInitializer: CacheInitializerService;
  let securityGroupReader: SecurityGroupReader;

  beforeEach(() => {
    InfrastructureCaches.destroyCaches();
    securityGroupReader = { getAllSecurityGroups: () => Promise.resolve([]) } as any;
    cacheInitializer = new CacheInitializerService(nativePromiseService, securityGroupReader, {
      hasDelegate: () => false,
    });
  });

  describe('spinnaker.core.cache.initializer', () => {
    const keys: IKeys = {
      sg: ['sg1', 'sg2', 'sg3'],
    };
    beforeEach(() => {
      spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue(Promise.resolve(keys.sg as any));
      spyOn(AccountService, 'listProviders').and.returnValue(Promise.resolve([]));
    });

    it('should initialize the cache initializer with the initialization values', async () => {
      const result = await cacheInitializer.initialize();

      expect(result.length).toBe(5); // from infrastructure cache config
      const flattened: string[][] = flatten(result); // only the arrays that actually contain data
      expect(flattened.length).toBe(1); // the four initialized string[] above used for the spyOns.
      expect(flattened[0]).toEqual(keys.sg);
    });

    it('should remove all items from all caches', async () => {
      const result = await cacheInitializer.refreshCaches();

      expect(result.length).toBe(5);
      result.forEach((item: any) => expect(item).toBeUndefined());
    });

    it('should remove all items from the specified cache', async () => {
      let cache = InfrastructureCaches.get('securityGroups');
      expect(cache).toBeUndefined();

      await cacheInitializer.initialize();
      cache = InfrastructureCaches.get('securityGroups');
      expect(cache).toBeDefined();
      expect(cache.keys().length).toBe(0);

      const key = 'myTestCacheKey';
      const value = 'myTestCacheValue';
      cache.put(key, value);

      expect(cache.keys().length).toBe(1);
      expect(cache.get(key)).toBe(value);

      const result = await cacheInitializer.refreshCache('securityGroups');
      expect(flatten(result)).toEqual(keys.sg);
      cache = InfrastructureCaches.get('securityGroups');
      expect(cache.keys().length).toBe(0);
    });
  });
});

describe('direct runtime cache initializer', () => {
  let runtime: DeckRuntime;

  beforeEach(() => {
    InfrastructureCaches.destroyCaches();
    runtime = createDeckRuntime();
  });

  afterEach(() => {
    runtime.dispose();
    InfrastructureCaches.destroyCaches();
  });

  it('initializes and refreshes infrastructure caches with native runtime dependencies', async () => {
    spyOn(InfrastructureCaches, 'createCache').and.returnValue({} as any);
    spyOn(InfrastructureCaches, 'clearCache');
    spyOn(AccountService, 'listProviders').and.returnValue(Promise.resolve([]));
    const getAllSecurityGroups = spyOn(runtime.services.securityGroupReader, 'getAllSecurityGroups').and.returnValue(
      Promise.resolve([]),
    );
    const cacheInitializer = runtime.services.cacheInitializer;

    expect(cacheInitializer.initialize).toEqual(jasmine.any(Function));
    expect(cacheInitializer.refreshCache).toEqual(jasmine.any(Function));
    expect(cacheInitializer.refreshCaches).toEqual(jasmine.any(Function));

    await cacheInitializer.initialize();
    await cacheInitializer.refreshCache('securityGroups');
    await cacheInitializer.refreshCaches();

    expect(AccountService.listProviders).toHaveBeenCalledWith();
    expect(getAllSecurityGroups).toHaveBeenCalledTimes(3);
  });
});
