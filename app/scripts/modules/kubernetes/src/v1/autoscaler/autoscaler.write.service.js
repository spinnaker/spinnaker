'use strict';

import * as angular from 'angular';

import { TaskExecutor } from '@spinnaker/core';

export const KUBERNETES_V1_AUTOSCALER_AUTOSCALER_WRITE_SERVICE =
  'spinnaker.kubernetes.serverGroup.details.autoscaler.write.service';
export const name = KUBERNETES_V1_AUTOSCALER_AUTOSCALER_WRITE_SERVICE; // for backwards compatibility
angular.module(KUBERNETES_V1_AUTOSCALER_AUTOSCALER_WRITE_SERVICE, []).factory('kubernetesAutoscalerWriter', function() {
  function upsertAutoscaler(application, serverGroup, params = {}) {
    const job = {
      type: 'upsertScalingPolicy',
      cloudProvider: 'kubernetes',
      credentials: serverGroup.account,
      account: serverGroup.account,
      region: serverGroup.region,
      serverGroupName: serverGroup.name,
    };
    angular.extend(job, params);

    return TaskExecutor.executeTask({
      application,
      description: 'Upsert autoscaler ' + serverGroup.name,
      job: [job],
    });
  }

  return { upsertAutoscaler };
});
