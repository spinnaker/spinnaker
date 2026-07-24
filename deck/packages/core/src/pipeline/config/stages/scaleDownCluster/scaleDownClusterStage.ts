import { ScaleDownClusterExecutionDetails } from './ScaleDownClusterExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const scaleDownClusterStage: IStageTypeConfig = {
  executionDetailsSections: [ScaleDownClusterExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'scaleDownCluster',
  label: 'Scale Down Cluster',
  description: 'Scales down a cluster',
  component: NoConfigurationStageConfig,
  strategy: true,
};

Registry.pipeline.registerStage(scaleDownClusterStage);
