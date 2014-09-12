'use strict';

var angular = require('angular');
angular.module('deckApp')
  .factory('scheduledCache', function($cacheFactory, scheduler) {
    // returns a cache that is cleared according to the scheduler
    return function(id, cycles) {
      function ScheduledCache(id, cycles) {
        var that = this;
        that.cycles = cycles || 0;

        that.cache = $cacheFactory(id);

        that.disposable = scheduler.get()
          .skip(that.cycles)
          .subscribe(function() {
            that.cache.removeAll();
          });
        
        that.info = that.cache.info;  
        that.put = that.cache.put;  
        that.get = that.cache.get;  
        that.remove = that.cache.remove;  
        that.removeAll = that.cache.removeAll;  

        that.destroy = function() {
          that.disposable.dispose();
          that.cache.destroy();
          delete that.disposable;
          delete that.cache;
        };
        
        return this;
      }

      return new ScheduledCache(id, cycles);
    };
  });

