'use strict';

let angular = require('angular');
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';
import {CACHE_INITIALIZER_SERVICE} from 'core/cache/cacheInitializer.service';

module.exports = angular.module('spinnaker.deck.gce.cacheRefresh.component', [
    CACHE_INITIALIZER_SERVICE,
    INFRASTRUCTURE_CACHE_SERVICE
  ])
  .component('gceCacheRefresh', {
    bindings: {
      onRefresh: '&',
      cacheKey: '@',
      cacheKeyAlias: '@',
    },
    templateUrl: require('./cacheRefresh.component.html'),
    controller: function (cacheInitializer, infrastructureCaches) {
      let cacheKeyAlias = this.cacheKeyAlias || this.cacheKey;

      this.capitalizedKey = cacheKeyAlias[0].toUpperCase() + cacheKeyAlias.substring(1);
      this.depluralizedKey = cacheKeyAlias.substring(0, cacheKeyAlias.length - 1);
      this.getRefreshTime = () => infrastructureCaches.get(this.cacheKey).getStats().ageMax;

      this.refresh = () => {
        this.refreshing = true;
        cacheInitializer.refreshCache(this.cacheKey)
          .then(() => this.onRefresh())
          .then(() => { this.refreshing = false; });
      };
    }
  });
