import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export const FIND_AMI_STAGE = 'spinnaker.core.pipeline.stage.findAmiStage';

module(FIND_AMI_STAGE, [
  PIPELINE_CONFIG_PROVIDER
])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      executionDetailsUrl: require('./findAmiExecutionDetails.html'),
      useBaseProvider: true,
      key: 'findImage',
      label: 'Find Image from Cluster',
      description: 'Finds an image to deploy from an existing cluster'
    });
  });

