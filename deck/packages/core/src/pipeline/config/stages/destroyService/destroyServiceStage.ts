import { DestroyServiceExecutionDetails } from './DestroyServiceExecutionDetails';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';

Registry.pipeline.registerStage({
  executionDetailsSections: [DestroyServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'destroyService',
  label: 'Destroy Service',
  description: 'Destroys a service',
  strategy: true,
});
