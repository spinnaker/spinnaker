import { AdditionalFields } from './AdditionalFields';
import { DeploymentStrategyRegistry } from '../../deploymentStrategy.registry';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Rolling Red/Black (Experimental)',
  description: `Creates a new version of this server group, then incrementally resizes the new server group while disabling the same percentage in the previous server group.`,
  key: 'rollingredblack',
  providerRestricted: true,
  additionalFields: ['targetPercentages', 'scaleDown'],
  AdditionalFieldsComponent: AdditionalFields,
  initializationMethod: (command) => {
    if (!command.targetPercentages) {
      command.targetPercentages = [50, 100];
    }
    command.delayBeforeDisableSec = command.delayBeforeDisableSec || 0;

    if (!command.pipelineBeforeCleanup) {
      command.pipelineBeforeCleanup = {
        application: command.application,
      };
    }
  },
});
