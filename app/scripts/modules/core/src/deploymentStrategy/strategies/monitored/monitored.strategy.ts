import { DeploymentStrategyRegistry } from 'core/deploymentStrategy/deploymentStrategy.registry';

import { AdditionalFields } from './AdditionalFields';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Monitored Deploy',
  description: `Creates a new version of this server group, then incrementally resizes the new server group while monitoring progress using a deployment monitor.`,
  key: 'monitored',
  providerRestricted: true,
  additionalFields: ['deploySteps', 'scaleDown'],
  AdditionalFieldsComponent: AdditionalFields,
  initializationMethod: command => {
    if (!command.deploySteps) {
      command.deploySteps = [10, 40, 100];
      command.rollback.onFailure = true;
      command.deploymentMonitor = { id: '' };
      command.maxRemainingAsgs = 2;
    }
  },
});
