'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.gce.serverGroup.details.scalingPolicy.write.service', [
    require('../../core/task/taskExecutor.js')
  ])
  .factory('gceAutoscalingPolicyWriter', function(taskExecutor) {

    function upsertAutoscalingPolicy(application, serverGroup, policy, params = {}) {
      let job = {
        type: 'upsertScalingPolicy',
        cloudProvider: serverGroup.type,
        credentials: serverGroup.account,
        region: serverGroup.region,
        serverGroupName: serverGroup.name,
        autoscalingPolicy: policy
      };
      angular.extend(job, params);

      return taskExecutor.executeTask({
        application,
        description: 'Upsert scaling policy ' + serverGroup.name,
        job: [job]
      });
    }

    function deleteAutoscalingPolicy(application, serverGroup) {
      return taskExecutor.executeTask({
        application,
        description: 'Delete scaling policy ' + serverGroup.name,
        job: [
          {
            type: 'deleteScalingPolicy',
            cloudProvider: serverGroup.type,
            credentials: serverGroup.account,
            region: serverGroup.region,
            serverGroupName: serverGroup.name
          }
        ]
      });
    }

    return { upsertAutoscalingPolicy, deleteAutoscalingPolicy };
  });
