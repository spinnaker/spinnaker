'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.gce.serverGroup.details.scalingPolicy.write.service', [
    require('../../../../core/task/taskExecutor.js')
  ])
  .factory('gceScalingPolicyWriter', function(taskExecutor) {

    function upsertScalingPolicy(application, serverGroup, policy) {
      return taskExecutor.executeTask({
        application,
        description: 'Upsert scaling policy ' + serverGroup.name,
        job: [
          {
            type: 'upsertScalingPolicy',
            cloudProvider: serverGroup.type,
            credentials: serverGroup.account,
            region: serverGroup.region,
            serverGroupName: serverGroup.name,
            autoscalingPolicy: policy
          }
        ]
      });
    }

    function deleteScalingPolicy(application, serverGroup) {
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

    return { upsertScalingPolicy, deleteScalingPolicy };
  });
