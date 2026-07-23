import { module } from 'angular';

import { ShrinkClusterExecutionDetails } from './ShrinkClusterExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const SHRINK_CLUSTER_STAGE = 'spinnaker.core.pipeline.stage.shrinkClusterStage';

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
module(SHRINK_CLUSTER_STAGE, []);
