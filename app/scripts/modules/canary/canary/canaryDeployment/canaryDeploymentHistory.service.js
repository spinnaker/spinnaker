'use strict';

import { module } from 'angular';

import { API } from '@spinnaker/core';

export const CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTHISTORY_SERVICE =
  'spinnaker.canary.deployment.history.service';
export const name = CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTHISTORY_SERVICE; // for backwards compatibility
module(CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTHISTORY_SERVICE, []).factory(
  'canaryDeploymentHistoryService',
  function () {
    function getAnalysisHistory(canaryDeploymentId) {
      return API.one('canaryDeployments').one(canaryDeploymentId).all('canaryAnalysisHistory').getList();
    }

    return {
      getAnalysisHistory: getAnalysisHistory,
    };
  },
);
