import { Registry } from 'core/registry';

import { UnshareServiceExecutionDetails } from './UnshareServiceExecutionDetails';
import { ExecutionDetailsTasks } from '../common';

Registry.pipeline.registerStage({
  executionDetailsSections: [UnshareServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'unshareService',
  label: 'Unshare Service',
  description: 'Unshare a service',
  strategy: true,
});
