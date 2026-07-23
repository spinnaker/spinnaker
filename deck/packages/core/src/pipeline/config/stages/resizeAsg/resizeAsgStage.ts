import { module } from 'angular';

import { ResizeAsgExecutionDetails } from './ResizeAsgExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const RESIZE_ASG_STAGE = 'spinnaker.core.pipeline.stage.resizeAsgStage';

export const resizeAsgStage: IStageTypeConfig = {
  executionDetailsSections: [ResizeAsgExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'resizeServerGroup',
  label: 'Resize Server Group',
  description: 'Resizes a server group',
  component: NoConfigurationStageConfig,
  strategy: true,
};

Registry.pipeline.registerStage(resizeAsgStage);
module(RESIZE_ASG_STAGE, []);
