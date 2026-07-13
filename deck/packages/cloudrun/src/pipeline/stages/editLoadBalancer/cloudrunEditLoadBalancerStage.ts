import type { IStageTypeConfig } from '@spinnaker/core';
import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { CloudrunEditLoadBalancerExecutionDetails } from './CloudrunEditLoadBalancerExecutionDetails';
import { CloudrunEditLoadBalancerStageConfig } from './CloudrunEditLoadBalancerStageConfig';

export const CLOUDRUN_EDIT_LOAD_BALANCER_STAGE_CONFIG: IStageTypeConfig = {
  label: 'Edit Load Balancer (Cloud Run)',
  description: 'Edits a load balancer',
  key: 'upsertCloudrunLoadBalancers',
  cloudProvider: 'cloudrun',
  component: CloudrunEditLoadBalancerStageConfig,
  executionDetailsSections: [CloudrunEditLoadBalancerExecutionDetails, ExecutionDetailsTasks],
  executionConfigSections: ['editLoadBalancerConfig', 'taskStatus'],
  validators: [],
};

Registry.pipeline.registerStage(CLOUDRUN_EDIT_LOAD_BALANCER_STAGE_CONFIG);
