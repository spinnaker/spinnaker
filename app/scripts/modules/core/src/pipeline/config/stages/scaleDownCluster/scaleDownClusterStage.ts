import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export const SCALE_DOWN_CLUSTER_STAGE = 'spinnaker.core.pipeline.stage.scaleDownClusterStage';
module(SCALE_DOWN_CLUSTER_STAGE, [
  PIPELINE_CONFIG_PROVIDER
])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      executionConfigSections: ['scaleDownClusterConfig', 'taskStatus'],
      executionDetailsUrl: require('./scaleDownClusterExecutionDetails.html'),
      useBaseProvider: true,
      key: 'scaleDownCluster',
      label: 'Scale Down Cluster',
      description: 'Scales down a cluster',
      strategy: true,
    });
  });

