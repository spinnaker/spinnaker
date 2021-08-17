'use strict';

import { module } from 'angular';

import { REST } from '@spinnaker/core';

export const CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTHISTORY_SERVICE =
  'spinnaker.canary.deployment.history.service';
export const name = CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTHISTORY_SERVICE; // for backwards compatibility
module(CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTHISTORY_SERVICE, []).factory(
  'canaryDeploymentHistoryService',
  function () {
    function getAnalysisHistory(canaryDeploymentId) {
      return REST('/canaryDeployments').path(canaryDeploymentId, 'canaryAnalysisHistory').get();
    }

    return {
      getAnalysisHistory: getAnalysisHistory,
    };
  },
);
