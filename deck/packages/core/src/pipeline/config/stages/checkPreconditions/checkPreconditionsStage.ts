import { CheckPreconditionsExecutionDetails } from './CheckPreconditionsExecutionDetails';
import { CheckPreconditionsStageConfig } from './CheckPreconditionsStageConfig';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const checkPreconditionsStage: IStageTypeConfig = {
  label: 'Check Preconditions',
  description: 'Checks for preconditions before continuing',
  key: 'checkPreconditions',
  restartable: true,
  component: CheckPreconditionsStageConfig,
  executionDetailsSections: [CheckPreconditionsExecutionDetails, ExecutionDetailsTasks],
  strategy: true,
};

Registry.pipeline.registerStage(checkPreconditionsStage);
