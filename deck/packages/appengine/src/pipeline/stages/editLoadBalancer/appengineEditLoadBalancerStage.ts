import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { AppengineEditLoadBalancerStageConfig } from './AppengineEditLoadBalancerStageConfig';
import { AppengineEditLoadBalancerExecutionDetails } from '../AppengineExecutionDetails';

Registry.pipeline.registerStage({
  label: 'Edit Load Balancer',
  description: 'Edits a load balancer',
  key: 'upsertAppEngineLoadBalancers',
  cloudProvider: 'appengine',
  component: AppengineEditLoadBalancerStageConfig,
  executionDetailsSections: [AppengineEditLoadBalancerExecutionDetails, ExecutionDetailsTasks],
  validators: [],
});
