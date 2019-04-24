import { CloudfoundryCreateServiceKeyStageConfig } from './CloudfoundryCreateServiceKeyStageConfig';
import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { CloudfoundryCreateServiceKeyExecutionDetails } from './CloudfoundryCreateServiceKeyExecutionDetails';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => stage.context.credentials,
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryCreateServiceKeyStageConfig,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  controller: 'BaseProviderStageCtrl as baseProviderStageCtrl',
  description: 'Create a service key',
  executionDetailsSections: [CloudfoundryCreateServiceKeyExecutionDetails, ExecutionDetailsTasks],
  key: 'createServiceKey',
  label: 'Create Service Key',
  validators: [
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', fieldName: 'region', preventSave: true },
    { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
    { type: 'requiredField', fieldName: 'serviceKeyName', preventSave: true },
  ],
});
