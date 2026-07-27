import { DisableClusterExecutionDetails } from './DisableClusterExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const disableClusterStage: IStageTypeConfig = {
  useBaseProvider: true,
  key: 'disableCluster',
  label: 'Disable Cluster',
  description: 'Disables a cluster',
  component: NoConfigurationStageConfig,
  executionDetailsSections: [DisableClusterExecutionDetails, ExecutionDetailsTasks],
  strategy: true,
};

Registry.pipeline.registerStage(disableClusterStage);
