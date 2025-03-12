import type { IStage } from '@spinnaker/core';
import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';
import { CloudFoundryLoadBalancersExecutionDetails, CloudFoundryLoadBalancersStageConfig } from '../../../presentation';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudFoundryLoadBalancersStageConfig,
  description: 'Unmap a load balancer',
  executionDetailsSections: [CloudFoundryLoadBalancersExecutionDetails, ExecutionDetailsTasks],
  key: 'unmapLoadBalancers',
  label: 'Unmap Load Balancer',
  validators: [
    { type: 'requiredField', preventSave: true, fieldName: 'cluster' },
    { type: 'requiredField', preventSave: true, fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', preventSave: true, fieldName: 'region' },
    { type: 'requiredField', preventSave: true, fieldName: 'target' },
    { type: 'cfRequiredRoutesField', preventSave: true, fieldName: 'loadBalancerNames' },
  ],
});
