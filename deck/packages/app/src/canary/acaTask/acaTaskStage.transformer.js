'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { OrchestratedItemTransformer } from '@spinnaker/core';

export const CANARY_ACATASK_ACATASKSTAGE_TRANSFORMER = 'spinnaker.canary.acaTask.transformer';
export const name = CANARY_ACATASK_ACATASKSTAGE_TRANSFORMER; // for backwards compatibility
module(CANARY_ACATASK_ACATASKSTAGE_TRANSFORMER, []).service('acaTaskTransformer', function () {
  function getException(stage) {
    return stage && stage.isFailed ? stage.failureMessage : null;
  }

  this.transform = function (application, execution) {
    execution.stages.forEach(function (stage) {
      if (stage.type === 'acaTask' && execution.hydrated) {
        OrchestratedItemTransformer.defineProperties(stage);
        stage.exceptions = [];

        if (getException(stage)) {
          stage.exceptions.push('Canary failure: ' + getException(stage));
        }

        stage.exceptions = _.uniq(stage.exceptions);

        let status = stage.status;

        const canaryStatus = stage.context.canary.status;

        const canaryResult = stage.context.canary.canaryResult && stage.context.canary.canaryResult.overallResult;

        if (canaryStatus && status !== 'CANCELED') {
          if (canaryStatus.status === 'LAUNCHED' || canaryStatus.status === 'RUNNING') {
            status = 'RUNNING';
          }
          if (canaryStatus.complete && canaryResult === 'SUCCESS') {
            status = 'SUCCEEDED';
          }
          if (canaryStatus.status === 'DISABLED') {
            status = 'DISABLED';
          }
          if (canaryStatus.status === 'FAILED' || canaryResult === 'FAILURE') {
            status = 'FAILED';
          }
          if (canaryStatus.status === 'TERMINATED') {
            status = 'TERMINATED';
          }
          canaryStatus.status = status;
        } else {
          stage.context.canary.status = { status: 'UNKNOWN' };
        }
        stage.status = status;
      }
    });
  };
});
