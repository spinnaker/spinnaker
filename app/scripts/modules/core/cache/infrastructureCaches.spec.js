'use strict';

describe('spinnaker.core.cache.infrastructure', function() {

  var infrastructureCaches, deckCacheFactory;

  beforeEach(
    window.module(
      require('./infrastructureCaches.js')
    )
  );

  beforeEach(window.inject(function(_infrastructureCaches_, _deckCacheFactory_) {
    infrastructureCaches = _infrastructureCaches_;
    deckCacheFactory = _deckCacheFactory_;
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

      expect(this.cacheInstantiations.length).toBe(3);
      expect(this.cacheInstantiations[0].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('infrastructure:myCache', 0));
      expect(this.cacheInstantiations[1].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('infrastructure:myCache', 1));
      expect(this.cacheInstantiations[2].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('infrastructure:myCache', 2));
      expect(this.removalCalls.length).toBe(2);
      expect(this.removalCalls).toEqual(['infrastructure:myCache', 'infrastructure:myCache']);

    });

    it('should remove non-versioned, even if version not explicitly specified, and use version 1', function() {
      infrastructureCaches.createCache('myCache', {
        cacheFactory: this.cacheFactory,
      });

      expect(this.cacheInstantiations.length).toBe(2);
      expect(this.cacheInstantiations[0].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('infrastructure:myCache', 0));
      expect(this.cacheInstantiations[1].config.storagePrefix).toBe(deckCacheFactory.getStoragePrefix('infrastructure:myCache', 1));
      expect(this.removalCalls.length).toBe(1);
      expect(this.removalCalls).toEqual(['infrastructure:myCache']);
    });

    it('should remove all keys when clearCache called', function() {
      infrastructureCaches.createCache('someBadCache', {
        cacheFactory: this.cacheFactory,
        version: 0,
        onReset: [],
      });

      var removalCallsAfterInitialization = this.removalCalls.length;
      infrastructureCaches.clearCache('someBadCache');
      expect(this.removalCalls.length).toBe(removalCallsAfterInitialization + 1);
    });

  });
});
