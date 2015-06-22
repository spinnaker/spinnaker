'use strict';

describe('spinnaker.caches.infrastructure', function() {

  var infrastructureCaches, deckCacheFactory, authenticationService, settings;

  beforeEach(
    window.module(
      require('./infrastructureCaches.js')
    )
  );
  
  beforeEach(window.inject(function(_infrastructureCaches_, _deckCacheFactory_, _authenticationService_, _settings_) {
    infrastructureCaches = _infrastructureCaches_;
    deckCacheFactory = _deckCacheFactory_;
    authenticationService = _authenticationService_;
    settings = _settings_;
  }));

  describe('injected values', function () {
    it('should be valid instances', function () {
      expect(infrastructureCaches).toBeDefined();
      expect(deckCacheFactory).toBeDefined();
    });
  });

  describe('cache initialization', function() {

    beforeEach(function() {
      var cacheInstantiations = [];
      var removalCalls = [];

      var cacheFactory = function(cacheId, config) {
        cacheInstantiations.push({cacheId: cacheId, config: config});
      };

      cacheFactory.createCache = function(cacheId, config) {
        cacheInstantiations.push({cacheId: cacheId, config: config});
      };

      cacheFactory.get = function(cacheId) {
        return {
          removeAll: function() {
            removalCalls.push(cacheId);
          },
          destroy: angular.noop,
        };
      };

      this.cacheFactory = cacheFactory;
      this.cacheInstantiations = cacheInstantiations;
      this.removalCalls = removalCalls;

    });

    it('should remove all keys from previous versions', function() {

      var config = {
        version: 2,
        cacheFactory: this.cacheFactory,
      };

      infrastructureCaches.createCache('myCache', config);

      expect(this.cacheInstantiations.length).toBe(6);
      expect(this.cacheInstantiations[0].config.storagePrefix).toBeUndefined();
      expect(this.cacheInstantiations[1].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('myCache', 1));
      expect(this.cacheInstantiations[2].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('infrastructure:myCache', 0));
      expect(this.cacheInstantiations[3].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('myCache', 2));
      expect(this.cacheInstantiations[4].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('infrastructure:myCache', 1));
      expect(this.cacheInstantiations[5].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('infrastructure:myCache', 2));
      expect(this.removalCalls.length).toBe(5);
      expect(this.removalCalls).toEqual(['myCache', 'myCache', 'infrastructure:myCache', 'myCache', 'infrastructure:myCache']);

    });

    it('should remove non-versioned, even if version not explicitly specified, and use version 1', function() {
      infrastructureCaches.createCache('myCache', {
        cacheFactory: this.cacheFactory,
      });

      expect(this.cacheInstantiations.length).toBe(4);
      expect(this.cacheInstantiations[0].config.storagePrefix).toBeUndefined();
      expect(this.cacheInstantiations[1].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('myCache', 1));
      expect(this.cacheInstantiations[2].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('infrastructure:myCache', 0));
      expect(this.cacheInstantiations[3].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('infrastructure:myCache', 1));
      expect(this.removalCalls.length).toBe(3);
      expect(this.removalCalls).toEqual(['myCache', 'myCache', 'infrastructure:myCache']);
    });

    it('should remove all keys when clearCache called', function() {
      infrastructureCaches.createCache('someBadCache', {
        cacheFactory: this.cacheFactory,
        version: 0,
      });

      var removalCallsAfterInitialization = this.removalCalls.length;
      infrastructureCaches.clearCache('someBadCache');
      expect(this.removalCalls.length).toBe(removalCallsAfterInitialization + 1);
    });

    it('should set disabled flag and register event when authEnabled', function () {
      var originalAuthEnabled = settings.authEnabled;
      settings.authEnabled = true;
      spyOn(authenticationService, 'getAuthenticatedUser').and.returnValue({authenticated: false});
      spyOn(authenticationService, 'onAuthentication');
      infrastructureCaches.createCache('authCache', {
        cacheFactory: this.cacheFactory,
        authEnabled: true,
      });
      expect(this.cacheInstantiations[3].config.disabled).toBe(true);
      expect(authenticationService.onAuthentication.calls.count()).toBe(1);
      settings.authEnabled = originalAuthEnabled;
    });

    it('should ignore authEnabled flag when settings disable auth', function () {
      var originalAuthEnabled = settings.authEnabled;
      settings.authEnabled = false;
      spyOn(authenticationService, 'getAuthenticatedUser').and.returnValue({authenticated: false});
      spyOn(authenticationService, 'onAuthentication');
      infrastructureCaches.createCache('authCache', {
        cacheFactory: this.cacheFactory,
        authEnabled: true,
      });
      expect(this.cacheInstantiations[3].config.disabled).toBe(false);
      expect(authenticationService.onAuthentication.calls.count()).toBe(0);
      settings.authEnabled = originalAuthEnabled;
    });
  });
});
