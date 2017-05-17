'use strict';

const angular = require('angular');

import { API_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.netflix.pipeline.stages.canary.deployment.history.service', [API_SERVICE])
  .factory('canaryDeploymentHistoryService', function (API) {

    function getAnalysisHistory(canaryDeploymentId) {
      return API.one('canaryDeployments').one(canaryDeploymentId).all('canaryAnalysisHistory').getList();
    }

    return {
      getAnalysisHistory: getAnalysisHistory,
    };

  });
