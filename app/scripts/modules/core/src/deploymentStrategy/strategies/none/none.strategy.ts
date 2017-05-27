import { DeploymentStrategyRegistry } from 'core/deploymentStrategy/deploymentStrategy.registry';

DeploymentStrategyRegistry.registerStrategy({
  label: 'None',
  description: 'Creates the next server group with no impact on existing server groups',
  key: '',
  providerRestricted: false,
});
