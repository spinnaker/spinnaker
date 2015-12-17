'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.details.autoscaling.process', [])
  .factory('azureAutoScalingProcessService', function() {
    function listProcesses() {
      return [
        {
          name: 'Launch',
          description: 'Controls if new instances should be launched into the ASG. If this is disabled, scale-up ' +
            'events will not produce new instances.',
        },
        {
          name: 'Terminate',
          description: 'Controls if instances should be terminated during a scale-down event.',
        },
        {
          name: 'AddToLoadBalancer',
          description: 'Controls if new instances should be added to the ASG’s ELB.',
        },
        {
          name: 'AlarmNotification',
          description: 'This disables autoscaling.',
        },
        {
          name: 'AZRebalance',
          description: 'Controls whether Azure should attempt to maintain an even distribution of instances across all ' +
          'healthy Availability Zones configured for the ASG.',
        },
        {
          name: 'HealthCheck',
          description: 'If disabled, the instance’s health will no longer be reported to the autoscaling processor.',
        },
        {
          name: 'ReplaceUnhealthy',
          description: 'Controls whether instances should be replaced if they failed the health check.',
        },
        {
          name: 'ScheduledActions',
          description: 'Controls whether scheduled actions should be executed.',
        },
      ];
    }

    return {
      listProcesses: listProcesses,
    };
  });
