import { FindAmiExecutionDetails } from './FindAmiExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export interface IFindAmiStageContext {
  region: string;
  imageId: string;
  imageName: string;
}

export const findAmiStage: IStageTypeConfig = {
  executionDetailsSections: [FindAmiExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'findImage',
  label: 'Find Image from Cluster',
  description: 'Finds an image to deploy from an existing cluster',
  component: NoConfigurationStageConfig,
};

Registry.pipeline.registerStage(findAmiStage);
