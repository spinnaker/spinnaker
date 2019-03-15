import { CloudfoundryUnshareServiceStageConfig } from './CloudfoundryUnshareServiceStageConfig';
import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { CloudfoundryServiceExecutionDetails } from 'cloudfoundry/presentation';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => stage.context.credentials,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  provides: 'unshareService',
  key: 'unshareService',
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryUnshareServiceStageConfig,
  templateUrl: require('./cloudfoundryUnshareServiceStage.html'),
  controller: 'cfUnshareServiceStageCtrl',
  executionDetailsSections: [CloudfoundryServiceExecutionDetails, ExecutionDetailsTasks],
  defaultTimeoutMs: 30 * 60 * 1000,
  validators: [
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
  ],
});
