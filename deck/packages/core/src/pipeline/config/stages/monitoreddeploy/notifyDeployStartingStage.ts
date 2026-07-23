import { module } from 'angular';

import { DeploymentMonitorExecutionDetails } from './DeploymentMonitorExecutionDetails';
import { NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const NOTIFY_DEPLOY_STARTING_STAGE = 'spinnaker.core.pipeline.stage.monitored.notifydeploystartingstage';

export const notifyDeployStartingStage: IStageTypeConfig = {
  synthetic: true,
  key: 'notifyDeployStarting',
  component: NoConfigurationStageConfig,
  executionDetailsSections: [DeploymentMonitorExecutionDetails],
};

Registry.pipeline.registerStage(notifyDeployStartingStage);
module(NOTIFY_DEPLOY_STARTING_STAGE, []);
