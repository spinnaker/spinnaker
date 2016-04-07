'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../core/cache/deckCacheFactory.js'),
    require('../canary/canary.read.service')
  ])
  .factory('fastPropertyReader', function (Restangular, canaryReadService, $q, $log) {

    function fetchForAppName(appName) {
      return Restangular.all('fastproperties').all('application').one(appName).get();
    }

    function getPropByIdAndEnv(id, env) {
      return Restangular.all('fastproperties').one('id', id).one('env', env).get();
    }

    function fetchImpactCountForScope(fastPropertyScope) {
      return Restangular.all('fastproperties').all('impact').post(fastPropertyScope);
    }

    function loadPromotions() {
      return Restangular.all('fastproperties').all('promotions').getList();
    }

    function loadPromotionsByApp(appName) {
      return Restangular.all('fastproperties').one('promotions', appName).getList()
        .then( (promotionList) => {

          return $q.all(promotionList.map((promotion) => {
            if (promotion.canaryIds) {
              return $q.all(promotion.canaryIds.map((id) => {
                return canaryReadService.getCanaryById(id);
              }))
              .then((canaries) => {
                promotion.canaries = canaries;
                return promotion;
              });
            }
          }));
        })
        .catch((error) => $log.info('There was an issue loading promotions by app', error));

    }

    return {
      fetchForAppName: fetchForAppName,
      fetchImpactCountForScope: fetchImpactCountForScope,
      loadPromotions: loadPromotions,
      loadPromotionsByApp: loadPromotionsByApp,
      getPropByIdAndEnv: getPropByIdAndEnv
    };
  });
