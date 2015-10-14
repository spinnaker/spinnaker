'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../core/cache/deckCacheFactory.js')
  ])
  .factory('fastPropertyReader', function (Restangular) {

    function fetchForAppName(appName) {
      return Restangular.all('fastproperties').all('application').one(appName).get();
    }

    function fetchImpactCountForScope(fastPropertyScope) {
      return Restangular.all('fastproperties').all('impact').post(fastPropertyScope);
    }

    function loadPromotions() {
      return Restangular.all('fastproperties').all('promotions').getList();
    }

    function loadPromotionsByApp(appName) {
      return Restangular.all('fastproperties').one('promotions', appName).getList();
    }

    return {
      fetchForAppName: fetchForAppName,
      fetchImpactCountForScope: fetchImpactCountForScope,
      loadPromotions: loadPromotions,
      loadPromotionsByApp: loadPromotionsByApp
    };
  }).name;
