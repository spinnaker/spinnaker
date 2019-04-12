import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { ExecutionWindowExecutionDetails } from './ExecutionWindowExecutionDetails';
import { ExecutionWindowsTransformer } from './executionWindows.transformer';

Registry.pipeline.registerStage({
  label: 'Restrict Execution During',
  synthetic: true,
  description: 'Restricts execution of stage during specified period of time',
  key: 'restrictExecutionDuringTimeWindow',
  executionDetailsSections: [ExecutionWindowExecutionDetails, ExecutionDetailsTasks],
});

Registry.pipeline.registerTransformer(new ExecutionWindowsTransformer());
