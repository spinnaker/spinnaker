import type { IStage } from '@spinnaker/core';
import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { CloudFoundryRunJobStageConfig } from './CloudFoundryRunJobStageConfig';
import { RunJobExecutionDetails } from './RunJobExecutionDetails';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => stage.context.credentials,
  component: CloudFoundryRunJobStageConfig,
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
