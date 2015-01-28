'use strict';

angular.module('deckApp.serverGroup.details.aws.autoscaling.process', [])
  .factory('autoScalingProcessService', function() {
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
          description: 'Controls whether instances in an Availability Zone should be rebalanced to another zone when ' +
            'the AZ becomes unhealthy.',
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
