import { RollbackClusterExecutionDetails } from './RollbackClusterExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const rollbackClusterStage: IStageTypeConfig = {
  useBaseProvider: true,
  key: 'rollbackCluster',
  label: 'Rollback Cluster',
  description: 'Rollback one or more regions in a cluster',
  component: NoConfigurationStageConfig,
  executionDetailsSections: [RollbackClusterExecutionDetails, ExecutionDetailsTasks],
};

Registry.pipeline.registerStage(rollbackClusterStage);
