import { FindArtifactFromExecutionExecutionDetails } from './FindArtifactFromExecutionExecutionDetails';
import { FindArtifactFromExecutionStageConfig } from './FindArtifactFromExecutionStageConfig';
import { ExecutionArtifactTab } from '../../../../artifact/react/ExecutionArtifactTab';
import { ExecutionDetailsTasks } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export { FindArtifactFromExecutionStageConfig } from './FindArtifactFromExecutionStageConfig';

export const findArtifactFromExecutionStage: IStageTypeConfig = {
  label: 'Find Artifacts From Execution',
  description: 'Find and bind artifacts from another execution',
  key: 'findArtifactFromExecution',
  component: FindArtifactFromExecutionStageConfig,
  executionDetailsSections: [FindArtifactFromExecutionExecutionDetails, ExecutionDetailsTasks, ExecutionArtifactTab],
  validators: [
    { type: 'requiredField', fieldName: 'pipeline', fieldLabel: 'Pipeline' },
    { type: 'requiredField', fieldName: 'application', fieldLabel: 'Application' },
  ],
  producesArtifacts: true,
};

Registry.pipeline.registerStage(findArtifactFromExecutionStage);
