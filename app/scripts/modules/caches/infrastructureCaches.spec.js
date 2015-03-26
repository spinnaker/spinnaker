'use strict';

describe('deckApp.caches.infrastructure', function() {

  var infrastructureCaches;

  beforeEach(module('deckApp.caches.infrastructure'));
  beforeEach(inject(function(_infrastructureCaches_) {
    infrastructureCaches = _infrastructureCaches_;
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

      expect(this.cacheInstantiations.length).toBe(3);
      expect(this.cacheInstantiations[0].config.storagePrefix).toBeUndefined();
      expect(this.cacheInstantiations[1].config.storagePrefix).toBe(infrastructureCaches.getStoragePrefix('myCache', 1));
      expect(this.cacheInstantiations[2].config.storagePrefix).toBe(infrastructureCaches.getStoragePrefix('myCache', 2));
      expect(this.removalCalls.length).toBe(2);
      expect(this.removalCalls).toEqual(['myCache', 'myCache']);
      expect(this.destroyCalls).toEqual(['myCache', 'myCache']);

    });

    it('should remove non-versioned, even if version not explicitly specified, and use version 1', function() {
      infrastructureCaches.createCache('myCache', {
        cacheFactory: this.cacheFactory,
      });

      expect(this.cacheInstantiations.length).toBe(2);
      expect(this.cacheInstantiations[0].config.storagePrefix).toBeUndefined();
      expect(this.cacheInstantiations[1].config.storagePrefix).toBe(infrastructureCaches.getStoragePrefix('myCache', 1));
      expect(this.removalCalls.length).toBe(1);
      expect(this.removalCalls).toEqual(['myCache']);
    });

  });
});
