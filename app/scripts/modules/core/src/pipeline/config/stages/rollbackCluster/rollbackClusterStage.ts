import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import { RollbackClusterExecutionDetails } from './RollbackClusterExecutionDetails';
import { ExecutionDetailsTasks } from '../core/ExecutionDetailsTasks';

export const ROLLBACK_CLUSTER_STAGE = 'spinnaker.core.pipeline.stage.rollbackClusterStage';
module(ROLLBACK_CLUSTER_STAGE, [
  PIPELINE_CONFIG_PROVIDER
])
  .config(function(pipelineConfigProvider: PipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'rollbackCluster',
      label: 'Rollback Cluster',
      description: 'Rollback one or more regions in a cluster',
      executionDetailsSections: [RollbackClusterExecutionDetails, ExecutionDetailsTasks]
    });
  });
