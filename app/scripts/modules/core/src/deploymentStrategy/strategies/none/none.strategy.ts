import { DeploymentStrategyRegistry } from '../../deploymentStrategy.registry';

DeploymentStrategyRegistry.registerStrategy({
  label: 'None',
  description: 'Creates the next server group with no impact on existing server groups',
  key: '',
  providerRestricted: false,
});
