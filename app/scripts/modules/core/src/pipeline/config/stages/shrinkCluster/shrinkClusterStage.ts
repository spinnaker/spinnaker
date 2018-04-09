import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import { ShrinkClusterExecutionDetails } from './ShrinkClusterExecutionDetails';
import { ExecutionDetailsTasks } from '../core/ExecutionDetailsTasks';

export const SHRINK_CLUSTER_STAGE = 'spinnaker.core.pipeline.stage.shrinkClusterStage';

module(SHRINK_CLUSTER_STAGE, [PIPELINE_CONFIG_PROVIDER]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerStage({
    executionDetailsSections: [ShrinkClusterExecutionDetails, ExecutionDetailsTasks],
    useBaseProvider: true,
    key: 'shrinkCluster',
    label: 'Shrink Cluster',
    description: 'Shrinks a cluster',
    strategy: true,
  });
});
