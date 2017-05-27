import { DeploymentStrategyRegistry } from '@spinnaker/core';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Rolling Push (deprecated)',
  description: 'Updates the launch configuration for this server group, then terminates instances incrementally, replacing them with instances launched with the updated configuration',
  key: 'rollingpush',
  additionalFields: ['termination.totalRelaunches', 'termination.concurrentRelaunches', 'termination.order', 'termination.relaunchAllInstances'],
  additionalFieldsTemplateUrl: require('./additionalFields.html'),
  initializationMethod: (command) => {
    command.termination = command.termination || {
        order: 'oldest',
        relaunchAllInstances: true,
        concurrentRelaunches: 1,
        totalRelaunches: command.capacity.max
      };
  }
});
