'use strict';


angular.module('deckApp.pipelines.stages.canary.deployment.history.service', [
  'restangular',
  'deckApp.settings',
])
  .factory('canaryDeploymentHistoryService', function (settings, Restangular) {

    function getAnalysisHistory(canaryDeploymentId) {
      return Restangular.one('canaryDeployments').one(canaryDeploymentId).all('canaryAnalysisHistory').getList();
    }

    return {
      getAnalysisHistory: getAnalysisHistory,
    };

  });
