'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stages.canary.deployment.history.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../../../../../caches/deckCacheFactory.js'),
])
  .factory('canaryDeploymentHistoryService', function (settings, Restangular) {

    function getAnalysisHistory(canaryDeploymentId) {
      return Restangular.one('canaryDeployments').one(canaryDeploymentId).all('canaryAnalysisHistory').getList();
    }

    return {
      getAnalysisHistory: getAnalysisHistory,
    };

  });
