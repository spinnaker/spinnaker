import { DestroyAsgExecutionDetails } from './DestroyAsgExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const destroyAsgStage: IStageTypeConfig = {
  executionDetailsSections: [DestroyAsgExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'destroyServerGroup',
  label: 'Destroy Server Group',
  description: 'Destroys a server group',
  component: NoConfigurationStageConfig,
  strategy: true,
};

Registry.pipeline.registerStage(destroyAsgStage);
