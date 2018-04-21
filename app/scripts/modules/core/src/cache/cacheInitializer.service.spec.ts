import { flatten } from 'lodash';

import { mock } from 'angular';
import { CACHE_INITIALIZER_SERVICE, CacheInitializerService } from './cacheInitializer.service';
import { AccountService } from 'core/account/AccountService';
import { APPLICATION_READ_SERVICE, ApplicationReader } from 'core/application/service/application.read.service';
import { InfrastructureCaches } from 'core/cache';
import { SECURITY_GROUP_READER, SecurityGroupReader } from 'core/securityGroup/securityGroupReader.service';
import { IGOR_SERVICE, IgorService } from 'core/ci/igor.service';

interface IKeys {
  [key: string]: string[];
  account: string[];
  sg: string[];
  app: string[];
  bm: string[];
}

interface IFoundKeys {
  [key: string]: boolean;
}

describe('Service: cacheInitializer', function() {
  let $q: ng.IQService;
  let $root: ng.IRootScopeService;
  let cacheInitializer: CacheInitializerService;
  let securityGroupReader: SecurityGroupReader;
  let applicationReader: ApplicationReader;
  let igorService: IgorService;

  beforeEach(mock.module(CACHE_INITIALIZER_SERVICE, SECURITY_GROUP_READER, APPLICATION_READ_SERVICE, IGOR_SERVICE));
  beforeEach(
    mock.inject(function(
      _$q_: ng.IQService,
      _$rootScope_: ng.IRootScopeService,
      _cacheInitializer_: CacheInitializerService,
      _securityGroupReader_: SecurityGroupReader,
      _applicationReader_: ApplicationReader,
      _igorService_: IgorService,
    ) {
      $q = _$q_;
      $root = _$rootScope_;
      cacheInitializer = _cacheInitializer_;
      securityGroupReader = _securityGroupReader_;
      applicationReader = _applicationReader_;
      igorService = _igorService_;
    }),
  );
  beforeEach(() => {
    InfrastructureCaches.destroyCaches();
  });

  it('should initialize injected dependencies', function() {
    expect($q).toBeDefined();
    expect($root).toBeDefined();
    expect(cacheInitializer).toBeDefined();
    expect(securityGroupReader).toBeDefined();
    expect(applicationReader).toBeDefined();
    expect(igorService).toBeDefined();
  });

  describe('spinnaker.core.cache.initializer', () => {
    const keys: IKeys = {
      account: ['account1', 'account2', 'account3'],
      sg: ['sg1', 'sg2', 'sg3'],
      app: ['app1', 'app2', 'app3'],
      bm: ['bm1', 'bm2', 'bm3'],
    };
    let initialized: boolean;

    beforeEach(() => {
      initialized = false;
      spyOn(AccountService, 'listAllAccounts').and.returnValue($q.when(keys.account));
      spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue($q.when(keys.sg));
      spyOn(applicationReader, 'listApplications').and.returnValue($q.when(keys.app));
      spyOn(igorService, 'listMasters').and.returnValue($q.when(keys.bm));
      spyOn(AccountService, 'listProviders').and.returnValue($q.when([]));
    });

    it('should initialize the cache initializer with the initialization values', () => {
      cacheInitializer.initialize().then((result: any[]) => {
        expect(result.length).toBe(13); // from infrastructure cache config
        const flattened: string[][] = flatten(result); // only the arrays that actually contain data
        expect(flattened.length).toBe(4); // the four initialized string[] above used for the spyOns.

        const prefixes: string[] = ['account', 'sg', 'app', 'bm'];
        const foundKeys: IFoundKeys = {
          account: false,
          sg: false,
          app: false,
          bm: false,
        };

        // verify the contents of each array; that they match the values used for initialization
        for (let i = 0; i < 4; i++) {
          const item: string[] = flattened[i];
          for (let j = 0; j < 4; j++) {
            const prefix: string = prefixes[j];
            if (item[0].startsWith(prefix)) {
              expect(item).toEqual(keys[prefix]);
              foundKeys[prefix] = true;
            }
          }
        }

        // finally check the boolean object to confirm each key was found
        Object.keys(foundKeys).forEach((key: string) => expect(foundKeys[key]).toBeTruthy());
        initialized = true;
      });
      $root.$digest();
      expect(initialized).toBeTruthy();
    });

    it('should remove all items from all caches', () => {
      cacheInitializer.refreshCaches().then((result: any[]) => {
        expect(result.length).toBe(13);
        result.forEach((item: any) => expect(item).toBeUndefined());
        initialized = true;
      });
      $root.$digest();
      expect(initialized).toBeTruthy();
    });

    it('should remove all items from the specified cache', () => {
      let cache = InfrastructureCaches.get('credentials');
      expect(cache).toBeUndefined();

      cacheInitializer.initialize().then(() => {
        cache = InfrastructureCaches.get('credentials');
        expect(cache).toBeDefined();
        expect(cache.keys().length).toBe(0);

        const key = 'myTestCacheKey';
        const value = 'myTestCacheValue';
        cache.put(key, value);

        expect(cache.keys().length).toBe(1);
        expect(cache.get(key)).toBe(value);

        cacheInitializer.refreshCache('credentials').then((result: any[]) => {
          expect(flatten(result)).toEqual(keys.account);
          cache = InfrastructureCaches.get('credentials');
          expect(cache.keys().length).toBe(0);
        });
        initialized = true;
      });
      $root.$digest();
      expect(initialized).toBeTruthy();
    });
  });
});
