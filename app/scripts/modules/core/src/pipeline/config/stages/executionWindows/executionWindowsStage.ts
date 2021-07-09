import { ExecutionWindowExecutionDetails } from './ExecutionWindowExecutionDetails';
import { ExecutionDetailsTasks } from '../common';
import { ExecutionWindowsTransformer } from './executionWindows.transformer';
import { Registry } from '../../../../registry';

Registry.pipeline.registerStage({
  label: 'Restrict Execution During',
  synthetic: true,
  description: 'Restricts execution of stage during specified period of time',
  key: 'restrictExecutionDuringTimeWindow',
  executionDetailsSections: [ExecutionWindowExecutionDetails, ExecutionDetailsTasks],
});

Registry.pipeline.registerTransformer(new ExecutionWindowsTransformer());
