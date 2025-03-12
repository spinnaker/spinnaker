import { AdditionalFields } from './AdditionalFields';
import { DeploymentStrategyRegistry } from '../../deploymentStrategy.registry';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Custom',
  description: 'Runs a custom deployment strategy',
  key: 'custom',
  additionalFields: ['pipelineParameters', 'strategyApplication', 'strategyPipeline'],
  AdditionalFieldsComponent: AdditionalFields,
});
