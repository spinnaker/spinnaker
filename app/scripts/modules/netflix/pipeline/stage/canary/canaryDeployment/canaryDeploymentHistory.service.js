'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stages.canary.deployment.history.service', [API_SERVICE])
  .factory('canaryDeploymentHistoryService', function (API) {

    function getAnalysisHistory(canaryDeploymentId) {
      return API.one('canaryDeployments').one(canaryDeploymentId).all('canaryAnalysisHistory').getList();
    }

    return {
      getAnalysisHistory: getAnalysisHistory,
    };

  });
