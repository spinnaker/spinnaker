import { DeploymentStrategyRegistry } from 'core/deploymentStrategy/deploymentStrategy.registry';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Red/Black',
  description: 'Disables <i>all</i> previous server groups in the cluster as soon as new server group passes health checks',
  key: 'redblack',
  providerRestricted: true,
  additionalFields: ['scaleDown', 'maxRemainingAsgs'],
  additionalFieldsTemplateUrl: require('./additionalFields.html'),
});
