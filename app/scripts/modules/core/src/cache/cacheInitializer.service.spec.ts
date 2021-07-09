import { flatten } from 'lodash';

import { mock, IQService, IRootScopeService } from 'angular';
import { CACHE_INITIALIZER_SERVICE, CacheInitializerService } from './cacheInitializer.service';
import { AccountService } from '../account/AccountService';
import { InfrastructureCaches } from './';
import { SECURITY_GROUP_READER, SecurityGroupReader } from '../securityGroup/securityGroupReader.service';

interface IKeys {
  [key: string]: string[];
  sg: string[];
}

describe('Service: cacheInitializer', function () {
  let $q: IQService;
  let $root: IRootScopeService;
  let cacheInitializer: CacheInitializerService;
  let securityGroupReader: SecurityGroupReader;

  beforeEach(mock.module(CACHE_INITIALIZER_SERVICE, SECURITY_GROUP_READER));
  beforeEach(
    mock.inject(function (
      _$q_: IQService,
      _$rootScope_: IRootScopeService,
      _cacheInitializer_: CacheInitializerService,
      _securityGroupReader_: SecurityGroupReader,
    ) {
      $q = _$q_;
      $root = _$rootScope_;
      cacheInitializer = _cacheInitializer_;
      securityGroupReader = _securityGroupReader_;
    }),
  );
  beforeEach(() => {
    InfrastructureCaches.destroyCaches();
  });

  it('should initialize injected dependencies', function () {
    expect($q).toBeDefined();
    expect($root).toBeDefined();
    expect(cacheInitializer).toBeDefined();
    expect(securityGroupReader).toBeDefined();
  });

  describe('spinnaker.core.cache.initializer', () => {
    const keys: IKeys = {
      sg: ['sg1', 'sg2', 'sg3'],
    };
    let initialized: boolean;

    beforeEach(() => {
      initialized = false;
      spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue($q.when(keys.sg as any));
      spyOn(AccountService, 'listProviders').and.returnValue($q.when([]));
    });

    it('should initialize the cache initializer with the initialization values', () => {
      cacheInitializer.initialize().then((result: any[]) => {
        expect(result.length).toBe(5); // from infrastructure cache config
        const flattened: string[][] = flatten(result); // only the arrays that actually contain data
        expect(flattened.length).toBe(1); // the four initialized string[] above used for the spyOns.
        expect(flattened[0]).toEqual(keys.sg);
        initialized = true;
      });
      $root.$digest();
      expect(initialized).toBeTruthy();
    });

    it('should remove all items from all caches', () => {
      cacheInitializer.refreshCaches().then((result: any[]) => {
        expect(result.length).toBe(5);
        result.forEach((item: any) => expect(item).toBeUndefined());
        initialized = true;
      });
      $root.$digest();
      expect(initialized).toBeTruthy();
    });

    it('should remove all items from the specified cache', () => {
      let cache = InfrastructureCaches.get('securityGroups');
      expect(cache).toBeUndefined();

      cacheInitializer.initialize().then(() => {
        cache = InfrastructureCaches.get('securityGroups');
        expect(cache).toBeDefined();
        expect(cache.keys().length).toBe(0);

        const key = 'myTestCacheKey';
        const value = 'myTestCacheValue';
        cache.put(key, value);

        expect(cache.keys().length).toBe(1);
        expect(cache.get(key)).toBe(value);

        cacheInitializer.refreshCache('securityGroups').then((result: any[]) => {
          expect(flatten(result)).toEqual(keys.sg);
          cache = InfrastructureCaches.get('securityGroups');
          expect(cache.keys().length).toBe(0);
        });
        initialized = true;
      });
      $root.$digest();
      expect(initialized).toBeTruthy();
    });
  });
});
