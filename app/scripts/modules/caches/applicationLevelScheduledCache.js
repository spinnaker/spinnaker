'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.caches.applicationLevelScheduled', [
])
  .factory('applicationLevelScheduledCache', function(scheduledCache, $rootScope, $stateParams) {
    var currentApplication = '';
    var toRemove = [];

    $rootScope.$on('$stateChangeStart', function() {
      if ($stateParams.application && $stateParams.application !== currentApplication) {
        currentApplication = $stateParams.application;
        toRemove.forEach(scheduledCache.remove);
        toRemove = [];
      }
    });

    return {
      info: scheduledCache.info,
      put: function(k, v) {
        toRemove.push(k);
        return scheduledCache.put(k, v);
      },
      get: scheduledCache.get,
      removeAll: scheduledCache.removeAll,
      remove: scheduledCache.remove,
      destroy: scheduledCache.destroy,
      toRemove: function() {
        return toRemove;
      },
      currentApplication: function() {
        return currentApplication;
      },
    };
  });
