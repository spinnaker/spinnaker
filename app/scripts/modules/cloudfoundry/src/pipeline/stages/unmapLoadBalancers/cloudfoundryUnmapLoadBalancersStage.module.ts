import { CloudfoundryUnmapLoadBalancersStageConfig } from './CloudfoundryUnmapLoadBalancersStageConfig';
import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { CloudfoundryUnmapLoadBalancersExecutionDetails } from './CloudfoundryUnmapLoadBalancersExecutionDetails';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => stage.context.credentials,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryUnmapLoadBalancersStageConfig,
  controller: 'BaseProviderStageCtrl as baseProviderStageCtrl',
  description: 'Unmap a load balancer',
  executionDetailsSections: [CloudfoundryUnmapLoadBalancersExecutionDetails, ExecutionDetailsTasks],
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
