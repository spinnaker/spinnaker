import { DeploymentStrategyRegistry } from '../../deploymentStrategy.registry';
import { AdditionalFields } from './AdditionalFields';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Custom',
  description: 'Runs a custom deployment strategy',
  key: 'custom',
  additionalFields: ['pipelineParameters', 'strategyApplication', 'strategyPipeline'],
  AdditionalFieldsComponent: AdditionalFields,
});
