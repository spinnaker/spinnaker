import { DeploymentStrategyRegistry } from 'core/deploymentStrategy/deploymentStrategy.registry';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Rolling Red/Black (Experimental)',
  description: `Creates a new version of this server group, then incrementally resizes the new server group while disabling the same percentage in the previous server group.`,
  key: 'rollingredblack',
  providerRestricted: true,
  additionalFields: ['targetPercentages'],
  additionalFieldsTemplateUrl: require('./additionalFields.html'),
  initializationMethod: command => {
    if (!command.targetPercentages) {
      command.targetPercentages = [50, 100];
    }

    if (!command.pipelineBeforeCleanup) {
      command.pipelineBeforeCleanup = {
        application: command.application,
      };
    }
  },
});
