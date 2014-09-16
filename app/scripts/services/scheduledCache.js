'use strict';

var angular = require('angular');
angular.module('deckApp')
  .factory('scheduledCache', function($cacheFactory, scheduler, $http) {
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

    that.info = that.cache.info;

    that.put = function(k, v) {
      if (that.schedules[k]) {
        that.schedules[k].dispose();
      }
      that.schedules[k] = scheduler.get()
        .skip(that.cycles)
        .subscribe(function() {
          $http.get(k).success(function(data) {
            that.cache.remove(k);
            that.cache.put(k,data);
          });
        });
      return that.cache.put(k,v);
    };

    that.get = that.cache.get;

    that.remove = function(k) {
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

