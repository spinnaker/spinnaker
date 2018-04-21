'use strict';

const angular = require('angular');

import { API } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.canary.deployment.history.service', [])
  .factory('canaryDeploymentHistoryService', function() {
    function getAnalysisHistory(canaryDeploymentId) {
      return API.one('canaryDeployments')
        .one(canaryDeploymentId)
        .all('canaryAnalysisHistory')
        .getList();
    }

    return {
      getAnalysisHistory: getAnalysisHistory,
    };
  });
