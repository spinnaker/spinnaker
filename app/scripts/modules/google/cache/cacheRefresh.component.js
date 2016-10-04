'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.cacheRefresh.component', [
    require('../../core/cache/cacheInitializer.js'),
    require('../../core/cache/infrastructureCaches.js'),
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
      this.getRefreshTime = () => infrastructureCaches[this.cacheKey].getStats().ageMax;

      this.refresh = () => {
        this.refreshing = true;
        cacheInitializer.refreshCache(this.cacheKey)
          .then(() => this.onRefresh())
          .then(() => { this.refreshing = false; });
      };
    }
  });
