import { DeploymentStrategyRegistry } from 'core/deploymentStrategy/deploymentStrategy.registry';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Custom',
  description: 'Runs a custom deployment strategy',
  key: 'custom',
  additionalFields: ['pipelineParameters', 'strategyApplication', 'strategyPipeline'],
  additionalFieldsTemplateUrl: require('./additionalFields.html'),
});
