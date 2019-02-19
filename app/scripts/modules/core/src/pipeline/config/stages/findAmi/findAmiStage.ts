import { module } from 'angular';

import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { FindAmiExecutionDetails } from './FindAmiExecutionDetails';

export interface IFindAmiStageContext {
  region: string;
  imageId: string;
  imageName: string;
}

export const FIND_AMI_STAGE = 'spinnaker.core.pipeline.stage.findAmiStage';

module(FIND_AMI_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    executionDetailsSections: [FindAmiExecutionDetails, ExecutionDetailsTasks],
    useBaseProvider: true,
    key: 'findImage',
    label: 'Find Image from Cluster',
    description: 'Finds an image to deploy from an existing cluster',
  });
});
