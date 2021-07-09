import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';

import { CloudfoundryShareServiceExecutionDetails } from './CloudfoundryShareServiceExecutionDetails';
import { CloudfoundryShareServiceStageConfig } from './CloudfoundryShareServiceStageConfig';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  provides: 'shareService',
  key: 'shareService',
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryShareServiceStageConfig,
  executionDetailsSections: [CloudfoundryShareServiceExecutionDetails, ExecutionDetailsTasks],
  supportsCustomTimeout: true,
  validators: [
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
  ],
});
