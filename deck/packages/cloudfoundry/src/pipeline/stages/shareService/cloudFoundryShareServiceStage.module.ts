import type { IStage } from '@spinnaker/core';
import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { CloudFoundryShareServiceExecutionDetails } from './CloudFoundryShareServiceExecutionDetails';
import { CloudFoundryShareServiceStageConfig } from './CloudFoundryShareServiceStageConfig';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  provides: 'shareService',
  key: 'shareService',
  cloudProvider: 'cloudfoundry',
  component: CloudFoundryShareServiceStageConfig,
  executionDetailsSections: [CloudFoundryShareServiceExecutionDetails, ExecutionDetailsTasks],
  supportsCustomTimeout: true,
  validators: [
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
  ],
});
