import { CloudfoundryDestroyServiceStageConfig } from './CloudfoundryDestroyServiceStageConfig';
import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { CloudfoundryServiceExecutionDetails } from 'cloudfoundry/presentation';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => stage.context.credentials,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryDestroyServiceStageConfig,
  controller: 'cfDestroyServiceStageCtrl',
  defaultTimeoutMs: 30 * 60 * 1000,
  executionDetailsSections: [CloudfoundryServiceExecutionDetails, ExecutionDetailsTasks],
  key: 'destroyService',
  provides: 'destroyService',
  validators: [
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
