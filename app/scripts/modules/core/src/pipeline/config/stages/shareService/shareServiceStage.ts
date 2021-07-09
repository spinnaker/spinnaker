import { ShareServiceExecutionDetails } from './ShareServiceExecutionDetails';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';

Registry.pipeline.registerStage({
  executionDetailsSections: [ShareServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'shareService',
  label: 'Share Service',
  description: 'Share a service',
  strategy: true,
});
