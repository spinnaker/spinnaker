import { DisableAsgExecutionDetails } from './DisableAsgExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const disableAsgStage: IStageTypeConfig = {
  useBaseProvider: true,
  executionDetailsSections: [DisableAsgExecutionDetails, ExecutionDetailsTasks],
  key: 'disableServerGroup',
  label: 'Disable Server Group',
  description: 'Disables a server group',
  component: NoConfigurationStageConfig,
  strategy: true,
};

Registry.pipeline.registerStage(disableAsgStage);
