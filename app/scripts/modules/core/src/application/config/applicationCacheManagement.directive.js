'use strict';

const angular = require('angular');

import { CACHE_INITIALIZER_SERVICE, InfrastructureCaches } from 'core/cache';
import { OVERRIDE_REGISTRY } from 'core/overrideRegistry/override.registry';

module.exports = angular
  .module('spinnaker.core.application.config.cache.management.directive', [
    CACHE_INITIALIZER_SERVICE,
    OVERRIDE_REGISTRY,
  ])
  .directive('applicationCacheManagement', function(overrideRegistry) {
    return {
      restrict: 'E',
      templateUrl: overrideRegistry.getTemplate(
        'applicationCacheManagementDirective',
        require('./applicationCacheManagement.directive.html'),
      ),
      scope: {},
      bindToController: {
        application: '=',
      },
      controller: 'ApplicationCacheManagementCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ApplicationCacheManagementCtrl', [
    '$log',
    'cacheInitializer',
    function($log, cacheInitializer) {
      this.refreshCaches = () => {
        this.clearingCaches = true;
        cacheInitializer.refreshCaches().then(
          () => {
            this.clearingCaches = false;
          },
          e => {
            $log.error('Error refreshing caches:', e);
            this.clearingCaches = false;
          },
        );
      };

      this.hasCache = cache => {
        return InfrastructureCaches.get(cache) !== undefined;
      };

      this.getCacheInfo = cache => {
        return InfrastructureCaches.get(cache).getStats();
      };

      this.refreshCache = function(key) {
        this.clearingCache = this.clearingCache || {};
        this.clearingCache[key] = true;
        cacheInitializer.refreshCache(key).then(
          () => {
            this.clearingCache[key] = false;
          },
          e => {
            $log.error('Error refreshing caches:', e);
            this.clearingCaches = false;
          },
        );
      };
    },
  ]);
