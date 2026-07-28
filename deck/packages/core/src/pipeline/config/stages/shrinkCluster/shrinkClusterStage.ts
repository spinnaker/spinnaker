import { ShrinkClusterExecutionDetails } from './ShrinkClusterExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const shrinkClusterStage: IStageTypeConfig = {
  executionDetailsSections: [ShrinkClusterExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'shrinkCluster',
  label: 'Shrink Cluster',
  description: 'Shrinks a cluster',
  component: NoConfigurationStageConfig,
  strategy: true,
};

Registry.pipeline.registerStage(shrinkClusterStage);
