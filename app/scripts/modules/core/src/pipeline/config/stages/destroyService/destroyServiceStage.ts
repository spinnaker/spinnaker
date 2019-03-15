import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { DestroyServiceExecutionDetails } from './DestroyServiceExecutionDetails';

Registry.pipeline.registerStage({
  executionDetailsSections: [DestroyServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'destroyService',
  label: 'Destroy Service',
  description: 'Destroys a service',
  strategy: true,
});
