import { CreateLoadBalancerExecutionDetails } from './CreateLoadBalancerExecutionDetails';
import { CreateLoadBalancerStageConfig } from './CreateLoadBalancerStageConfig';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';
export { hasPipelineLoadBalancerModal, openLoadBalancerModal } from './openLoadBalancerModal';

export const upsertLoadBalancersStage = {
  key: 'upsertLoadBalancers',
  label: 'Create Load Balancers',
  description:
    'Creates one or more load balancers. If a load balancer exists with the same name, then that will be updated.',
  component: CreateLoadBalancerStageConfig,
  executionDetailsSections: [CreateLoadBalancerExecutionDetails, ExecutionDetailsTasks],
  supportsCustomTimeout: true,
  validators: [],
};

Registry.pipeline.registerStage(upsertLoadBalancersStage);
