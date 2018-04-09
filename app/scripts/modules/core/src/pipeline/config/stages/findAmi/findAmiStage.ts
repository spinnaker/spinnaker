import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

import { ExecutionDetailsTasks } from '../core';
import { FindAmiExecutionDetails } from './FindAmiExecutionDetails';

export interface IFindAmiStageContext {
  region: string;
  imageId: string;
  imageName: string;
}

export const FIND_AMI_STAGE = 'spinnaker.core.pipeline.stage.findAmiStage';

module(FIND_AMI_STAGE, [PIPELINE_CONFIG_PROVIDER]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerStage({
    executionDetailsSections: [FindAmiExecutionDetails, ExecutionDetailsTasks],
    useBaseProvider: true,
    key: 'findImage',
    label: 'Find Image from Cluster',
    description: 'Finds an image to deploy from an existing cluster',
  });
});
