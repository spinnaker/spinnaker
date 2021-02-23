import { Registry } from 'core/registry';

import { DeployServiceExecutionDetails } from './DeployServiceExecutionDetails';
import { ExecutionDetailsTasks } from '../common';

Registry.pipeline.registerStage({
  executionDetailsSections: [DeployServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'deployService',
  label: 'Deploy Service',
  description: 'Deploy a service',
  strategy: true,
});
