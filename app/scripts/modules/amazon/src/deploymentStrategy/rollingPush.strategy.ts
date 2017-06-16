import { DeploymentStrategyRegistry } from '@spinnaker/core';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Rolling Push (not recommended)',
  description: `Updates the launch configuration for this server group, then terminates instances incrementally,
    replacing them with instances launched with the updated configuration. This is not a best practice - it goes against
    the principles of immutable infrastructure - but may be necessary in some cases.`,
  key: 'rollingpush',
  providerRestricted: true,
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
