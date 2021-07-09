import { IScope, IControllerService, IRootScopeService, mock } from 'angular';

import {
  WERCKER_EXECUTION_DETAILS_CONTROLLER,
  WerckerExecutionDetailsCtrl,
} from './werckerExecutionDetails.controller';

describe('Wercker Execution Details Controller:', () => {
  let $scope: IScope, $ctrl: IControllerService;

  beforeEach(mock.module(WERCKER_EXECUTION_DETAILS_CONTROLLER));

  beforeEach(
    mock.inject(($controller: IControllerService, $rootScope: IRootScopeService) => {
      $ctrl = $controller;
      $scope = $rootScope.$new();
    }),
  );

  const initializeController = (stage: any): WerckerExecutionDetailsCtrl => {
    $scope.stage = stage;
    return $ctrl(WerckerExecutionDetailsCtrl, {
      $scope,
      executionDetailsSectionService: { synchronizeSection: ({}, fn: () => any) => fn() },
    });
  };

  describe('getting failure message', () => {
    it('should count number of failing tests', () => {
      const stage = {
        context: {
          buildInfo: {
            testResults: [{ failCount: 0 }, { failCount: 3 }, { failCount: 2 }],
          },
        },
      };

      const controller = initializeController(stage);

      expect(controller.failureMessage).toBe('5 tests failed.');
    });

    it('should fall back to "build failed" message when no failed tests found, but result is "FAILURE"', () => {
      let stage = {
        context: {
          buildInfo: {
            result: 'FAILURE',
            testResults: [] as any,
          },
        },
      };

      let controller = initializeController(stage);

      expect(controller.failureMessage).toBe('Build failed.');

      stage = {
        context: {
          buildInfo: {
            result: 'FAILURE',
            testResults: [{ failCount: 0 }],
          },
        },
      };

      controller = initializeController(stage);

      expect(controller.failureMessage).toBe('Build failed.');
    });

    it('should set failureMessage to undefined when not failing', function () {
      const controller = initializeController({});
      expect(controller.failureMessage).toBeUndefined();
    });
  });
});
