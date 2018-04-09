import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import { DisableClusterExecutionDetails } from './DisableClusterExecutionDetails';
import { ExecutionDetailsTasks } from '../core/ExecutionDetailsTasks';

export const DISABLE_CLUSTER_STAGE = 'spinnaker.core.pipeline.stage.disableClusterStage';
module(DISABLE_CLUSTER_STAGE, [PIPELINE_CONFIG_PROVIDER]).config(function(
  pipelineConfigProvider: PipelineConfigProvider,
) {
  pipelineConfigProvider.registerStage({
    useBaseProvider: true,
    key: 'disableCluster',
    label: 'Disable Cluster',
    description: 'Disables a cluster',
    executionDetailsSections: [DisableClusterExecutionDetails, ExecutionDetailsTasks],
    strategy: true,
  });
});
