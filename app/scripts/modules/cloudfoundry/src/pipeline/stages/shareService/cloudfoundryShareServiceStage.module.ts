import { CloudfoundryShareServiceStageConfig } from './CloudfoundryShareServiceStageConfig';
import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { CloudfoundryShareServiceExecutionDetails } from 'cloudfoundry/presentation';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => stage.context.credentials,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  provides: 'shareService',
  key: 'shareService',
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryShareServiceStageConfig,
  executionDetailsSections: [CloudfoundryShareServiceExecutionDetails, ExecutionDetailsTasks],
  defaultTimeoutMs: 30 * 60 * 1000,
  validators: [
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
  ],
});
