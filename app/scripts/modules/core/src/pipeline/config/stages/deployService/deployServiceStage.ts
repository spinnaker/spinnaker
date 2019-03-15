import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { DeployServiceExecutionDetails } from './DeployServiceExecutionDetails';

Registry.pipeline.registerStage({
  executionDetailsSections: [DeployServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'deployService',
  label: 'Deploy Service',
  description: 'Deploy a service',
  strategy: true,
});
