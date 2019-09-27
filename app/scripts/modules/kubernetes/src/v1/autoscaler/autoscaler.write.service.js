'use strict';

const angular = require('angular');

import { TaskExecutor } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.kubernetes.serverGroup.details.autoscaler.write.service', [])
  .factory('kubernetesAutoscalerWriter', function() {
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

      return TaskExecutor.executeTask({
        application,
        description: 'Upsert autoscaler ' + serverGroup.name,
        job: [job],
      });
    }

    return { upsertAutoscaler };
  });
