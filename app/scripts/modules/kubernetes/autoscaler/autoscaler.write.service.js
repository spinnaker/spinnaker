'use strict';

import {TASK_EXECUTOR} from 'core/task/taskExecutor';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.serverGroup.details.autoscaler.write.service', [
    TASK_EXECUTOR,
  ])
  .factory('kubernetesAutoscalerWriter', function(taskExecutor) {
    function upsertAutoscaler(application, serverGroup, params = {}) {
      let job = {
        type: 'upsertScalingPolicy',
        cloudProvider: 'kubernetes',
        credentials: serverGroup.account,
        account: serverGroup.account,
        region: serverGroup.region,
        serverGroupName: serverGroup.name,
      };
      angular.extend(job, params);

      return taskExecutor.executeTask({
        application,
        description: 'Upsert autoscaler ' + serverGroup.name,
        job: [job]
      });
    }

    return { upsertAutoscaler };
  });
