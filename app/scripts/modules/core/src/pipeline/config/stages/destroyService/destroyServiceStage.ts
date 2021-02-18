import { Registry } from 'core/registry';

import { DestroyServiceExecutionDetails } from './DestroyServiceExecutionDetails';
import { ExecutionDetailsTasks } from '../common';

Registry.pipeline.registerStage({
  executionDetailsSections: [DestroyServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'destroyService',
  label: 'Destroy Service',
  description: 'Destroys a service',
  strategy: true,
});
