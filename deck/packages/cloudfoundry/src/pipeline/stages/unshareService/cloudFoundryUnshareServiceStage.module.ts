import type { IStage } from '@spinnaker/core';
import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { CloudFoundryUnshareServiceExecutionDetails } from './CloudFoundryUnshareServiceExecutionDetails';
import { CloudFoundryUnshareServiceStageConfig } from './CloudFoundryUnshareServiceStageConfig';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  provides: 'unshareService',
  key: 'unshareService',
  cloudProvider: 'cloudfoundry',
  component: CloudFoundryUnshareServiceStageConfig,
  controller: 'cfUnshareServiceStageCtrl',
  executionDetailsSections: [CloudFoundryUnshareServiceExecutionDetails, ExecutionDetailsTasks],
  supportsCustomTimeout: true,
  validators: [
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
  ],
});
