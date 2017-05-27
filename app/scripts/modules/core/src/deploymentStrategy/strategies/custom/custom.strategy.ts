import { DeploymentStrategyRegistry } from 'core/deploymentStrategy/deploymentStrategy.registry';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Custom',
  description: 'Runs a custom deployment strategy',
  key: 'custom',
  additionalFields: [],
  additionalFieldsTemplateUrl: require('./additionalFields.html'),
});
