import { ExecutionArtifactTab } from '../../../../artifact/react/ExecutionArtifactTab';
import { Registry } from '../../../../registry';
import { ExecutionDetailsTasks } from '../common';
import { FindArtifactFromExecutionExecutionDetails } from './FindArtifactFromExecutionExecutionDetails';
import * as findArtifactFromExecutionStageModule from './findArtifactFromExecutionStage';

describe('findArtifactFromExecutionStage', () => {
  beforeEach(() => Registry.reinitialize());

  it('registers the Find Artifacts From Execution stage as a React stage config', () => {
    const {
      FindArtifactFromExecutionStageConfig,
      findArtifactFromExecutionStage,
    } = findArtifactFromExecutionStageModule as any;

    expect(findArtifactFromExecutionStage).toEqual(
      jasmine.objectContaining({
        label: 'Find Artifacts From Execution',
        description: 'Find and bind artifacts from another execution',
        key: 'findArtifactFromExecution',
        component: FindArtifactFromExecutionStageConfig,
        executionDetailsSections: [
          FindArtifactFromExecutionExecutionDetails,
          ExecutionDetailsTasks,
          ExecutionArtifactTab,
        ],
        validators: [
          { type: 'requiredField', fieldName: 'pipeline', fieldLabel: 'Pipeline' },
          { type: 'requiredField', fieldName: 'application', fieldLabel: 'Application' },
        ],
        producesArtifacts: true,
      }),
    );
    expect(findArtifactFromExecutionStage.templateUrl).toBeUndefined();
    expect(findArtifactFromExecutionStage.controller).toBeUndefined();
    expect(findArtifactFromExecutionStage.controllerAs).toBeUndefined();
  });
});
