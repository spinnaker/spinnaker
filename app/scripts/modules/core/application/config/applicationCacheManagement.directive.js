'use strict';

const angular = require('angular');
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';
import {CACHE_INITIALIZER_SERVICE} from 'core/cache/cacheInitializer.service';

module.exports = angular
  .module('spinnaker.core.application.config.cache.management.directive', [
    CACHE_INITIALIZER_SERVICE,
    INFRASTRUCTURE_CACHE_SERVICE
  ])
  .directive('applicationCacheManagement', function (overrideRegistry) {
    return {
      restrict: 'E',
      templateUrl: overrideRegistry.getTemplate('applicationCacheManagementDirective', require('./applicationCacheManagement.directive.html')),
      scope: {},
      bindToController: {
        application: '=',
      },
      controller: 'ApplicationCacheManagementCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ApplicationCacheManagementCtrl', function ($log, infrastructureCaches, cacheInitializer) {
    this.refreshCaches = () => {
      this.clearingCaches = true;
      cacheInitializer.refreshCaches().then(
        () => {
          this.clearingCaches = false;
        },
        (e) => {
          $log.error('Error refreshing caches:', e);
          this.clearingCaches = false;
        });
    };

    this.getCacheInfo = (cache) => {
      return infrastructureCaches.get(cache).getStats();
    };

    this.refreshCache = function (key) {
      this.clearingCache = this.clearingCache || {};
      this.clearingCache[key] = true;
      cacheInitializer.refreshCache(key).then(
        () => {
          this.clearingCache[key] = false;
        },
        (e) => {
          $log.error('Error refreshing caches:', e);
          this.clearingCaches = false;
        }
      );
    };
  });
