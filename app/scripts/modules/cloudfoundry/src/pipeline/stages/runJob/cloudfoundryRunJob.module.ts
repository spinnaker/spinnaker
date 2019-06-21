import { CloudfoundryRunJobStageConfig } from './CloudfoundryRunJobStageConfig';
import { IStage, Registry } from '@spinnaker/core';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => stage.context.credentials,
  component: CloudfoundryRunJobStageConfig,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  cloudProvider: 'cloudfoundry',
  key: 'runJob',
  provides: 'runJob',
  restartable: true,
  defaultTimeoutMs: 2 * 60 * 60 * 1000, // 2 hours
  validators: [
    { type: 'requiredField', fieldName: 'credentials' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'command' },
    { type: 'requiredField', fieldName: 'jobName' },
  ],
});
