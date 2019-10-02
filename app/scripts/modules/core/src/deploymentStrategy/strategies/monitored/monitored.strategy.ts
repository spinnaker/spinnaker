import { isEmpty, defaultsDeep } from 'lodash';
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
    defaultsDeep(command, {
      rollback: { onFailure: true },
      deploymentMonitor: { id: '' },
      maxRemainingAsgs: 2,
    });

    // defaultsDeep will merge the arrays, which we don't want hence check deploySteps separately
    if (isEmpty(command.deploySteps)) {
      command.deploySteps = [10, 40, 100];
    }
  },
});
