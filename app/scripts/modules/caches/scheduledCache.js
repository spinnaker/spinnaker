'use strict';

angular.module('spinnaker.caches.scheduled', ['spinnaker.scheduler'])
  .factory('scheduledCache', function($cacheFactory, $http, scheduler) {
    // returns a cache that is cleared according to the scheduler
    var that = {};

    function disposeAll() {
      Object.keys(that.schedules).forEach(function(k) {
        that.schedules[k].dispose();
      });
    }

    that.schedules = {};

    that.cycles =  10;

    that.cache = $cacheFactory('scheduledHttp');

    that.info = function() {
      return that.cache.info();
    };

    that.put = function(k, v) {
      if (that.schedules[k]) {
        that.schedules[k].dispose();
      }
      that.schedules[k] = scheduler.subscribeEveryN(that.cycles, function() {
        $http.get(k, { cache: that.cache })
          .success(angular.noop);
      });
      return that.cache.put(k,v);
    };

    that.get = function(k) {
      return that.cache.get(k);
    };

    that.remove = function(k) {
      if (that.schedules[k]) {
        that.schedules[k].dispose();
        delete that.schedules[k];
      }
      that.cache.remove(k);
    };

    that.removeAll = function() {
      disposeAll();
      that.cache.removeAll();
    };

    that.destroy = function() {
      disposeAll();
      that.cache.destroy();
      delete that.cache;
    };

    return that;
  });

