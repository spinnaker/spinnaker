'use strict';

describe('scheduledCache', function() {
  beforeEach(function() {
    module('spinnaker.caches.scheduled');
  });

  beforeEach(inject(function(scheduledCache, $http, $cacheFactory, scheduler) {
    this.scheduledCache = scheduledCache;
    this.$http = $http;
    this.$cacheFactory = $cacheFactory;
    this.scheduler = scheduler;
  }));

  describe('#info', function() {
    it('passes through to the underlying cache', function() {
      spyOn(this.scheduledCache.cache, 'info');
      this.scheduledCache.info(null);
      expect(this.scheduledCache.cache.info).toHaveBeenCalled();
    });
  });

  describe('#get', function() {
    it('passes through to the underlying cache', function() {
      spyOn(this.scheduledCache.cache, 'get');
      this.scheduledCache.get('a value');
      expect(this.scheduledCache.cache.get).toHaveBeenCalledWith('a value');
    });
  });


  describe('#remove', function() {
    it('passes through to the underlying cache', function() {
      spyOn(this.scheduledCache.cache, 'remove');
      this.scheduledCache.remove('a value');
      expect(this.scheduledCache.cache.remove).toHaveBeenCalledWith('a value');
    });
  });

  describe('#put', function() {
    it('removes any active schedule for k by calling dispose', function() {
      var disposable = {
        dispose: angular.noop,
      };
      spyOn(disposable, 'dispose');
      this.scheduledCache.schedules['a key'] = disposable;
      this.scheduledCache.put('a key', 'a value');
      expect(disposable.dispose).toHaveBeenCalled();
    });

    it('subscribes to the scheduler via subscribeEveryN', function() {
      spyOn(this.scheduler, 'subscribeEveryN');
      this.scheduledCache.put('a key', 'a value');
      expect(this.scheduler.subscribeEveryN)
        .toHaveBeenCalledWith(this.scheduledCache.cycles, jasmine.any(Function));
    });

    describe('the update', function() {
      beforeEach(function() {
        spyOn(this.scheduler, 'subscribeEveryN').and.callFake(function(cycles, fn) {
          fn();
        });
      });

      it('makes a get request to the cache key (a url)', function() {
        spyOn(this.$http, 'get').and.callThrough();
        this.scheduledCache.put('a key', 'a value');
        expect(this.$http.get).toHaveBeenCalledWith('a key', jasmine.any(Object));
      });

      it('supplies the underlying cache for use in the $http request', function() {
        spyOn(this.$http, 'get').and.callFake(function(url, config) {
          expect(config.cache).toEqual(this.scheduledCache.cache);
          return { success: angular.noop };
        }.bind(this));
        this.scheduledCache.put('a key', 'a value');
      });
    });
  });
});
