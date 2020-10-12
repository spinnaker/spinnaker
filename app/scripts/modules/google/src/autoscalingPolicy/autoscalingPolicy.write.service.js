'use strict';

import * as angular from 'angular';

import { TaskExecutor } from '@spinnaker/core';

export const GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE =
  'spinnaker.gce.serverGroup.details.scalingPolicy.write.service';
export const name = GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE; // for backwards compatibility
angular
  .module(GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE, [])
  .factory('gceAutoscalingPolicyWriter', function () {
    function upsertAutoscalingPolicy(application, serverGroup, policy, params = {}) {
      const job = {
        type: 'upsertScalingPolicy',
        cloudProvider: serverGroup.type,
        credentials: serverGroup.account,
        region: serverGroup.region,
        serverGroupName: serverGroup.name,
        autoscalingPolicy: policy,
      };
      angular.extend(job, params);

      return TaskExecutor.executeTask({
        application,
        description: 'Upsert scaling policy ' + serverGroup.name,
        job: [job],
      });
    }

    function deleteAutoscalingPolicy(application, serverGroup) {
      return TaskExecutor.executeTask({
        application,
        description: 'Delete scaling policy ' + serverGroup.name,
        job: [
          {
            type: 'deleteScalingPolicy',
            cloudProvider: serverGroup.type,
            credentials: serverGroup.account,
            region: serverGroup.region,
            serverGroupName: serverGroup.name,
          },
        ],
      });
    }

    function upsertAutoHealingPolicy(application, serverGroup, policy, params = {}) {
      const job = {
        type: 'upsertScalingPolicy',
        cloudProvider: serverGroup.type,
        credentials: serverGroup.account,
        region: serverGroup.region,
        serverGroupName: serverGroup.name,
        autoHealingPolicy: policy,
      };
      angular.extend(job, params);

      return TaskExecutor.executeTask({
        application,
        description: 'Upsert autohealing policy ' + serverGroup.name,
        job: [job],
      });
    }

    function deleteAutoHealingPolicy(application, serverGroup) {
      return TaskExecutor.executeTask({
        application,
        description: 'Delete autohealing policy ' + serverGroup.name,
        job: [
          {
            type: 'deleteScalingPolicy',
            cloudProvider: serverGroup.type,
            credentials: serverGroup.account,
            region: serverGroup.region,
            serverGroupName: serverGroup.name,
            deleteAutoHealingPolicy: true,
          },
        ],
      });
    }

    return { upsertAutoscalingPolicy, deleteAutoscalingPolicy, upsertAutoHealingPolicy, deleteAutoHealingPolicy };
  });
