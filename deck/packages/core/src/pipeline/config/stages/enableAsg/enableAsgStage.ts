import { module } from 'angular';

import { EnableAsgExecutionDetails } from './EnableAsgExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const ENABLE_ASG_STAGE = 'spinnaker.core.pipeline.stage.enableAsg';

export const enableAsgStage: IStageTypeConfig = {
  useBaseProvider: true,
  executionDetailsSections: [EnableAsgExecutionDetails, ExecutionDetailsTasks],
  key: 'enableServerGroup',
  label: 'Enable Server Group',
  description: 'Enables a server group',
  component: NoConfigurationStageConfig,
  strategy: true,
};

Registry.pipeline.registerStage(enableAsgStage);
module(ENABLE_ASG_STAGE, []);
