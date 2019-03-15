import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { ShareServiceExecutionDetails } from './ShareServiceExecutionDetails';

Registry.pipeline.registerStage({
  executionDetailsSections: [ShareServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'shareService',
  label: 'Share Service',
  description: 'Share a service',
  strategy: true,
});
