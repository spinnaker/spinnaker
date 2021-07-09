'use strict';

import { IScope, mock } from 'angular';
import { FindArtifactFromExecutionCtrl } from './findArtifactFromExecution.controller';

describe('Find Artifact From Execution Controller:', function () {
  let ctrl: FindArtifactFromExecutionCtrl, $scope: IScope, initializeController: (stage: any) => void;

  beforeEach(
    mock.inject(($rootScope: IScope) => {
      $scope = $rootScope.$new();
      initializeController = (stage: any) => {
        $scope = $rootScope.$new();
        $scope.stage = stage;
        ctrl = new FindArtifactFromExecutionCtrl($scope);
      };
    }),
  );

  it('properly initializes an empty stage', () => {
    initializeController({});
    const expectedArtifact = ctrl.stage.expectedArtifacts[0];
    const executionOptions: any = ctrl.stage.executionOptions;

    expect(executionOptions).toBeDefined();

    expect(expectedArtifact).toBeDefined();
    expect(expectedArtifact.id).toBeDefined();
    expect(expectedArtifact.displayName).toBeDefined();
    expect(expectedArtifact.matchArtifact).toBeDefined();
    expect(expectedArtifact.defaultArtifact).toBeDefined();
    expect(expectedArtifact.useDefaultArtifact).toBeFalsy();
  });

  it('does not overwrite existing execution options', () => {
    const existingExecutionOptions = {
      successful: true,
      terminal: true,
    };
    initializeController({
      executionOptions: existingExecutionOptions,
    });

    const expectedArtifact = ctrl.stage.expectedArtifacts[0];
    const executionOptions: any = ctrl.stage.executionOptions;

    expect(executionOptions).toBeDefined();
    expect(executionOptions).toEqual(existingExecutionOptions);

    expect(expectedArtifact).toBeDefined();
    expect(expectedArtifact.id).toBeDefined();
    expect(expectedArtifact.displayName).toBeDefined();
    expect(expectedArtifact.matchArtifact).toBeDefined();
    expect(expectedArtifact.defaultArtifact).toBeDefined();
    expect(expectedArtifact.useDefaultArtifact).toBeFalsy();
  });

  it('adds missing fields to partly-initialized expected artifact', () => {
    const existingExpectedArtifact = {
      id: 'e3125b4d-6358-4fe2-a4c4-1379d1fe7d03',
      matchArtifact: {
        name: 'gcr.io/test',
        type: 'docker/image',
      },
    };
    initializeController({
      expectedArtifact: existingExpectedArtifact,
    });

    const expectedArtifact: any = ctrl.stage.expectedArtifacts[0];

    expect(expectedArtifact).toBeDefined();
    expect(expectedArtifact.id).toEqual(existingExpectedArtifact.id);
    expect(expectedArtifact.displayName).toBeDefined();
    expect(expectedArtifact.matchArtifact).toEqual(existingExpectedArtifact.matchArtifact);
    expect(expectedArtifact.defaultArtifact).toBeDefined();
    expect(expectedArtifact.useDefaultArtifact).toBeFalsy();
  });

  it('does not overwrite a fully-initialized expected artifact', () => {
    const existingExpectedArtifact = {
      id: '645f9710-1f93-4551-a73b-be1447c939bc',
      displayName: 'my-artifact',
      matchArtifact: {
        id: '6b8c2ed5-3460-4d18-b122-8fbe28bc4c2b',
        name: 'gcr.io/test',
        type: 'docker/image',
      },
      defaultArtifact: {
        id: 'febf5209-0469-4b46-9d4e-d1e99b4acf05',
        name: 'gcr.io/other-test',
        reference: 'gcr.io/other-test',
        type: 'docker/image',
      },
      useDefaultArtifact: true,
      usePriorArtifact: false,
    };
    initializeController({
      expectedArtifact: existingExpectedArtifact,
    });

    const expectedArtifact: any = ctrl.stage.expectedArtifacts[0];

    expect(expectedArtifact).toBeDefined();
    expect(expectedArtifact.id).toEqual(existingExpectedArtifact.id);
    expect(expectedArtifact.displayName).toEqual(expectedArtifact.displayName);
    expect(expectedArtifact.matchArtifact).toEqual(existingExpectedArtifact.matchArtifact);
    expect(expectedArtifact.defaultArtifact).toEqual(existingExpectedArtifact.defaultArtifact);
    expect(expectedArtifact.useDefaultArtifact).toBeTruthy();
  });
});
