import { CloudfoundryMapLoadBalancersStageConfig } from './CloudfoundryMapLoadBalancersStageConfig';
import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { CloudfoundryMapLoadBalancersExecutionDetails } from './CloudfoundryMapLoadBalancersExecutionDetails';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => stage.context.credentials,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryMapLoadBalancersStageConfig,
  controller: 'BaseProviderStageCtrl as baseProviderStageCtrl',
  description: 'Map a load balancer',
  executionDetailsSections: [CloudfoundryMapLoadBalancersExecutionDetails, ExecutionDetailsTasks],
  key: 'mapLoadBalancers',
  label: 'Map Load Balancer',
  validators: [
    { type: 'requiredField', preventSave: true, fieldName: 'cluster' },
    { type: 'requiredField', preventSave: true, fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', preventSave: true, fieldName: 'region' },
    { type: 'requiredField', preventSave: true, fieldName: 'target' },
    { type: 'cfRequiredRoutesField', preventSave: true, fieldName: 'loadBalancerNames' },
  ],
});
