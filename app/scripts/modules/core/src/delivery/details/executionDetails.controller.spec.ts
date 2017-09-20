import { IControllerService, IRootScopeService, IScope, auto, mock } from 'angular';
import { EXECUTION_DETAILS_CONTROLLER, ExecutionDetailsController } from './executionDetails.controller';

import { IStage } from 'core/domain';

describe('Controller: ExecutionDetails', () => {
  let $scope: IScope;
  let controller: ExecutionDetailsController;
  let pipelineConfig: any;

  beforeEach(mock.module(
    EXECUTION_DETAILS_CONTROLLER,
    ($provide: auto.IProvideService) => {
      $provide.service('pipelineConfig', () => {
        return { getStageConfig: (stage: IStage) => stage };
      });
    }
  ));

  beforeEach(mock.inject(($controller: IControllerService,
                          $rootScope: IRootScopeService,
                          _pipelineConfig_: any) => {
    $scope = $rootScope.$new();
    controller = $controller(ExecutionDetailsController, {
      $scope: $scope
    });
    controller.execution = { isRunning: false } as any;
    controller.application = { attributes: { enableRestartRunningExecutions: false } } as any;

    pipelineConfig = _pipelineConfig_;
  }));

  describe('isRestartable', () => {
    it('returns false when no stage config', () => {
      expect(controller.isRestartable()).toBe(false);
    });

    it('returns false when stage is not restartable', () => {
      expect(controller.isRestartable({restartable: false} as any)).toBe(false);
    });

    it('returns false when stage is already restarting', () => {
      expect(controller.isRestartable({restartable: true, isRestarting: true} as any)).toBe(false);
    });

    it('returns true when stage is restartable', () => {
      expect(controller.isRestartable({restartable: true} as any)).toBe(true);
    });

    it('returns true when stage is running, is restartable and enableRestartRunningExecutions=true', () => {
      controller.execution.isRunning = true;
      controller.application.attributes.enableRestartRunningExecutions = true;
      $scope.$digest();
      expect(controller.isRestartable({restartable: true} as any)).toBe(true);
    });
  });

});
