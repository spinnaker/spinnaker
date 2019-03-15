import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { UnshareServiceExecutionDetails } from './UnshareServiceExecutionDetails';

Registry.pipeline.registerStage({
  executionDetailsSections: [UnshareServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'unshareService',
  label: 'Unshare Service',
  description: 'Unshare a service',
  strategy: true,
});
