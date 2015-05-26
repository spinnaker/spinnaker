'use strict';


angular.module('spinnaker.pipelines.stages.canary.deployment.history.service', [
  'restangular',
  'spinnaker.settings',
])
  .factory('canaryDeploymentHistoryService', function (settings, Restangular) {

    function getAnalysisHistory(canaryDeploymentId) {
      return Restangular.one('canaryDeployments').one(canaryDeploymentId).all('canaryAnalysisHistory').getList();
    }

    return {
      getAnalysisHistory: getAnalysisHistory,
    };

  });
