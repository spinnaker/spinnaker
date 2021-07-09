import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';

import { CloudfoundryRunJobStageConfig } from './CloudfoundryRunJobStageConfig';
import { RunJobExecutionDetails } from './RunJobExecutionDetails';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => stage.context.credentials,
  component: CloudfoundryRunJobStageConfig,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  cloudProvider: 'cloudfoundry',
  key: 'runJob',
  provides: 'runJob',
  restartable: true,
  executionDetailsSections: [ExecutionDetailsTasks, RunJobExecutionDetails],
  supportsCustomTimeout: true,
  validators: [
    { type: 'requiredField', fieldName: 'credentials' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'command' },
  ],
});
