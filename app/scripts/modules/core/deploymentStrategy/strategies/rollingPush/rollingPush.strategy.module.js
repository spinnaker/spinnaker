'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.rollingPush', [])
  .config(function(deploymentStrategyConfigProvider) {
    deploymentStrategyConfigProvider.registerStrategy({
      label: 'Rolling Push (deprecated)',
      description: 'Updates the launch configuration for this server group, then terminates instances incrementally, replacing them with instances launched with the updated configuration',
      key: 'rollingpush',
      providers: ['aws'],
      additionalFields: ['termination.totalRelaunches', 'termination.concurrentRelaunches', 'termination.order', 'termination.relaunchAllInstances'],
      additionalFieldsTemplateUrl: require('./additionalFields.html'),
      initializationMethod: function(command) {
        command.termination = command.termination || {
          order: 'oldest',
          relaunchAllInstances: true,
          concurrentRelaunches: 1,
          totalRelaunches: command.capacity.max
        };
      }
    });
  });
