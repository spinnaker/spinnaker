'use strict';

describe('Service: scheduledCache', function() {
  beforeEach(function() {
    var subject = new Rx.Subject();
    this.subject = subject;
    var scheduler = {
      get: angular.noop,
    };
    this.scheduler = scheduler;
    spyOn(scheduler, 'get').andReturn(subject);

    module('deckApp');
    module(function($provide) {
      $provide.value('scheduler', scheduler);
    });

    inject(function(scheduledCache, $cacheFactory) {
      this.scheduledCache = scheduledCache;
      this.$cacheFactory = $cacheFactory;
    });
  });

  describe('initializing the cache', function() {

    it('creates an angular cache at the specified id', function() {
      var cache = this.scheduledCache('id');
      expect(this.$cacheFactory.get('id').info().id).toEqual(cache.info().id);
    });

    it('allows an optional parameter to specify the refresh interval', function() {
      var cache = this.scheduledCache('id', 2);
      expect(cache.cycles).toBe(2);
    });

    it('defaults to refreshing every cycle', function() {
      var cache = this.scheduledCache('id');
      expect(cache.cycles).toBe(0);
    });

    it('gets the global scheduler on initialization', function() {
      this.scheduledCache('id');
      expect(this.scheduler.get.callCount).toEqual(1);
    });

    it('will throw if the same id is initialized twice', function() {
      this.scheduledCache('id');
      expect(function() {
        this.scheduledCache('id');
      }).toThrow();  
    });

    it('shares the same id space as $cacheFactory', function() {
      this.scheduledCache('id');
      expect(function() {
        this.$cacheFactory('id');
      }.bind(this)).toThrow();  
    });
  });

  describe('the schedule', function() {
    it('clears the cache once every n cycles', function() {
      var cache = this.scheduledCache('id');
      cache.put('foo', 'bar');
      expect(cache.get('foo')).toEqual('bar');
      this.subject.onNext();
      expect(cache.get('foo')).toBeUndefined();

      var cache = this.scheduledCache('id2', 1);
      cache.put('foo', 'bar');
      expect(cache.get('foo')).toEqual('bar');
      this.subject.onNext();
      expect(cache.get('foo')).toEqual('bar');
      this.subject.onNext();
      expect(cache.get('foo')).toBeUndefined();
    });
  });
});
