'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stages.canary.deployment.history.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
])
  .factory('canaryDeploymentHistoryService', function (Restangular) {

    function getAnalysisHistory(canaryDeploymentId) {
      return Restangular.one('canaryDeployments').one(canaryDeploymentId).all('canaryAnalysisHistory').getList();
    }

    return {
      getAnalysisHistory: getAnalysisHistory,
    };

  });
