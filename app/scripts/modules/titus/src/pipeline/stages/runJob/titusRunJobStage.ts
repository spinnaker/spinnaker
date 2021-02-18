import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';

import { RunJobExecutionDetails } from './RunJobExecutionDetails';
import { TitusRunJobStageConfig } from './TitusRunJobStageConfig';

Registry.pipeline.registerStage({
  provides: 'runJob',
  useBaseProvider: true,
  restartable: true,
  key: 'runJob',
  cloudProvider: 'titus',
  providesFor: ['aws', 'titus'],
  component: TitusRunJobStageConfig,
  executionDetailsSections: [RunJobExecutionDetails, ExecutionDetailsTasks],
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  supportsCustomTimeout: true,
  validators: [
    { type: 'requiredField', fieldName: 'cluster.iamProfile' },
    { type: 'requiredField', fieldName: 'cluster.imageId' },
    { type: 'requiredField', fieldName: 'credentials' },
    { type: 'requiredField', fieldName: 'cluster.region' },
    { type: 'requiredField', fieldName: 'cluster.resources.cpu' },
    { type: 'requiredField', fieldName: 'cluster.resources.gpu' },
    { type: 'requiredField', fieldName: 'cluster.resources.memory' },
    { type: 'requiredField', fieldName: 'cluster.resources.disk' },
    { type: 'requiredField', fieldName: 'cluster.runtimeLimitSecs' },
  ],
});
