import { ExecutionWindowExecutionDetails } from './ExecutionWindowExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import { ExecutionWindowsTransformer } from './executionWindows.transformer';
import { Registry } from '../../../../registry';

export const executionWindowsStage = {
  label: 'Restrict Execution During',
  synthetic: true,
  description: 'Restricts execution of stage during specified period of time',
  key: 'restrictExecutionDuringTimeWindow',
  component: NoConfigurationStageConfig,
  executionDetailsSections: [ExecutionWindowExecutionDetails, ExecutionDetailsTasks],
};

Registry.pipeline.registerStage(executionWindowsStage);

Registry.pipeline.registerTransformer(new ExecutionWindowsTransformer());
