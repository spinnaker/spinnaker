import { DeploymentStrategyRegistry } from 'core/deploymentStrategy/deploymentStrategy.registry';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Highlander',
  description: 'Destroys <i>all</i> previous server groups in the cluster as soon as new server group passes health checks',
  key: 'highlander',
});
