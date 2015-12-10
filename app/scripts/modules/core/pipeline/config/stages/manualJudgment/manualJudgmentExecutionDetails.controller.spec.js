'use strict';

describe('Controller: manualJudgmentExecutionDetails', function () {

  var $scope, controller, $q, manualJudgmentService;

  beforeEach(window.module(require('./manualJudgmentExecutionDetails.controller')));

  beforeEach(window.inject(function ($controller, $rootScope, _manualJudgmentService_, _executionDetailsSectionService_, _$q_) {
    $scope = $rootScope.$new();
    $q = _$q_;

    manualJudgmentService = _manualJudgmentService_;

    spyOn(_executionDetailsSectionService_, 'synchronizeSection').and.callFake(angular.noop);

    controller = $controller('ManualJudgmentExecutionDetailsCtrl', {
        $scope: $scope,
        manualJudgmentService: manualJudgmentService,
        executionDetailsSectionService: _executionDetailsSectionService_,
        $stateParams: { details: ''},
      });
    }));

    describe('provideJudgment', function () {

      it('sets view state flags on successful judgment, then reloads application executions', function () {
        let reloadCalled = false,
            viewState = $scope.viewState;
        $scope.application = {
          reloadExecutions: () => reloadCalled = true
        };

        spyOn(manualJudgmentService, 'provideJudgment').and.returnValue($q.when(null));

        controller.provideJudgment('continue');

        expect(viewState.submitting).toBe(true);
        expect(viewState.error).toBe(false);
        expect(viewState.judgmentDecision).toBe('continue');
        expect(reloadCalled).toBe(false);

        $scope.$digest();

        // submitting is left alone - application execution reload will clear it up
        expect(viewState.submitting).toBe(true);
        expect(viewState.error).toBe(false);
        expect(viewState.judgmentDecision).toBe('continue');
        expect(reloadCalled).toBe(true);
      });

      it('sets view state flags of failure, does not reload application executions', function () {
        let reloadCalled = false,
            viewState = $scope.viewState;
        $scope.application = {
          reloadExecutions: () => reloadCalled = true
        };

        spyOn(manualJudgmentService, 'provideJudgment').and.returnValue($q.reject(null));

        controller.provideJudgment('continue');

        expect(viewState.submitting).toBe(true);
        expect(viewState.error).toBe(false);
        expect(viewState.judgmentDecision).toBe('continue');
        expect(reloadCalled).toBe(false);

        $scope.$digest();

        expect(viewState.submitting).toBe(false);
        expect(viewState.error).toBe(true);
        expect(viewState.judgmentDecision).toBe('continue');
        expect(reloadCalled).toBe(false);
      });
    });
});
