'use strict';

describe('spinnaker.caches.infrastructure', function() {

  var infrastructureCaches, deckCacheFactory;

  beforeEach(module('spinnaker.caches.infrastructure'));
  beforeEach(inject(function(_infrastructureCaches_, _deckCacheFactory_) {
    infrastructureCaches = _infrastructureCaches_;
    deckCacheFactory = _deckCacheFactory_;
  }));

  describe('cache initialization', function() {

    beforeEach(function() {
      var cacheInstantiations = [];
      var removalCalls = [];
      var destroyCalls = [];

      var cacheFactory = function(cacheId, config) {
        cacheInstantiations.push({cacheId: cacheId, config: config});
      };

      cacheFactory.get = function(cacheId) {
        return {
          removeAll: function() {
            removalCalls.push(cacheId);
          },
          destroy: function() {
            destroyCalls.push(cacheId);
          }
        };
      };

      this.cacheFactory = cacheFactory;
      this.cacheInstantiations = cacheInstantiations;
      this.removalCalls = removalCalls;
      this.destroyCalls = destroyCalls;

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

  });
});
