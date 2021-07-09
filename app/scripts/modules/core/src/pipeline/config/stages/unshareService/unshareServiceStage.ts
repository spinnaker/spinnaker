import { UnshareServiceExecutionDetails } from './UnshareServiceExecutionDetails';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';

Registry.pipeline.registerStage({
  executionDetailsSections: [UnshareServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'unshareService',
  label: 'Unshare Service',
  description: 'Unshare a service',
  strategy: true,
});
