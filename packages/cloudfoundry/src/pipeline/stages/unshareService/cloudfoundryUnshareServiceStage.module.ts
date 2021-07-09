import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';

import { CloudfoundryUnshareServiceExecutionDetails } from './CloudfoundryUnshareServiceExecutionDetails';
import { CloudfoundryUnshareServiceStageConfig } from './CloudfoundryUnshareServiceStageConfig';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  provides: 'unshareService',
  key: 'unshareService',
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryUnshareServiceStageConfig,
  controller: 'cfUnshareServiceStageCtrl',
  executionDetailsSections: [CloudfoundryUnshareServiceExecutionDetails, ExecutionDetailsTasks],
  supportsCustomTimeout: true,
  validators: [
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
  ],
});
