import { CloneServerGroupExecutionDetails } from './CloneServerGroupExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const cloneServerGroupStage: IStageTypeConfig = {
  useBaseProvider: true,
  key: 'cloneServerGroup',
  label: 'Clone Server Group',
  component: NoConfigurationStageConfig,
  executionDetailsSections: [CloneServerGroupExecutionDetails, ExecutionDetailsTasks],
  description: 'Clones a server group',
  strategy: false,
};

Registry.pipeline.registerStage(cloneServerGroupStage);
