import { DeployServiceExecutionDetails } from './DeployServiceExecutionDetails';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';

Registry.pipeline.registerStage({
  executionDetailsSections: [DeployServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'deployService',
  label: 'Deploy Service',
  description: 'Deploy a service',
  strategy: true,
});
