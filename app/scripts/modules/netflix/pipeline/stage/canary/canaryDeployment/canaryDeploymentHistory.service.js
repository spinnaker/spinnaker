'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stages.canary.deployment.history.service', [
  require('core/api/api.service')
])
  .factory('canaryDeploymentHistoryService', function (API) {

    function getAnalysisHistory(canaryDeploymentId) {
      return API.one('canaryDeployments').one(canaryDeploymentId).all('canaryAnalysisHistory').getList();
    }

    return {
      getAnalysisHistory: getAnalysisHistory,
    };

  });
