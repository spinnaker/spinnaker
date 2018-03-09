'use strict';

describe('Pipeline Trigger: PipelineTriggerOptionsCtrl', function() {

  var $scope, executionService, executionsTransformer, ctrl, $q, command;

  beforeEach(
    window.module(
      require('./pipelineTriggerOptions.directive.js').name
    )
  );

  // https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$q
  beforeEach(
    window.module(($qProvider) => {
      $qProvider.errorOnUnhandledRejections(false);
  }));

  beforeEach(window.inject(function($rootScope, _executionService_, _executionsTransformer_, $controller, _$q_) {
    $scope = $rootScope.$new();
    executionService = _executionService_;
    executionsTransformer = _executionsTransformer_;
    $q = _$q_;

    command = {
      trigger: {
        type: 'pipeline',
        application: 'a',
        pipeline: 'b'
      }
    };

    this.initialize = function() {
      ctrl = $controller('PipelineTriggerOptionsCtrl', {
        executionService: executionService,
        executionsTransformer: executionsTransformer,
        $scope: $scope,
      }, { command: command });
      ctrl.$onInit();
    };
  }));

  it('loads executions on initialization, setting state flags', function () {
    let executions = [];
    spyOn(executionService, 'getExecutionsForConfigIds').and.returnValue($q.when(executions));
    spyOn(executionsTransformer, 'addBuildInfo');

    this.initialize();
    expect(ctrl.viewState.executionsLoading).toBe(true);
    $scope.$digest();
    expect(ctrl.viewState.executionsLoading).toBe(false);
    expect(ctrl.viewState.loadError).toBe(false);
    expect(ctrl.executions).toEqual(executions);
    expect(ctrl.viewState.selectedExecution).toBe(null);
  });

  it('sets execution to first one available when returned on initialization', function () {
    let executions = [
      { pipelineConfigId: 'b', buildTime: 3, id: 'b-3', application: 'a' },
      { pipelineConfigId: 'b', buildTime: 1, id: 'b-1', application: 'a' },
    ];
    spyOn(executionService, 'getExecutionsForConfigIds').and.returnValue($q.when(executions));
    spyOn(executionsTransformer, 'addBuildInfo');

    this.initialize();
    expect(ctrl.viewState.executionsLoading).toBe(true);
    $scope.$digest();
    expect(ctrl.viewState.executionsLoading).toBe(false);
    expect(ctrl.viewState.loadError).toBe(false);
    expect(ctrl.executions).toEqual([executions[0], executions[1]]);
    expect(ctrl.viewState.selectedExecution).toBe(executions[0]);
    expect(command.extraFields.parentPipelineId).toBe('b-3');
    expect(command.extraFields.parentPipelineApplication).toBe('a');
  });

  it('sets flags when execution load fails', function () {
    spyOn(executionService, 'getExecutionsForConfigIds').and.returnValue($q.reject('does not matter'));
    spyOn(executionsTransformer, 'addBuildInfo');

    this.initialize();
    expect(ctrl.viewState.executionsLoading).toBe(true);
    $scope.$digest();
    expect(ctrl.viewState.executionsLoading).toBe(false);
    expect(ctrl.viewState.loadError).toBe(true);
    expect(ctrl.executions).toBeUndefined();
    expect(ctrl.viewState.selectedExecution).toBe(null);
    expect(command.extraFields.parentPipelineId).toBeUndefined();
    expect(command.extraFields.parentPipelineApplication).toBeUndefined();
  });

  it('re-initializes when trigger changes', function () {
    let firstExecution = { pipelineConfigId: 'b', buildTime: 1, id: 'b-1', application: 'a' },
        secondExecution = { pipelineConfigId: 'c', buildTime: 3, id: 'c-3', application: 'b' },
        secondTrigger = { type: 'pipeline', application: 'b', pipeline: 'c'};

    spyOn(executionService, 'getExecutionsForConfigIds').and.callFake((configIds) => {
      let executions = [];
      if (configIds[0] === 'b') {
        executions = [firstExecution];
      }
      if (configIds[0] === 'c') {
        executions = [secondExecution];
      }
      return $q.when(executions);
    });
    spyOn(executionsTransformer, "addBuildInfo");

    this.initialize();
    $scope.$digest();

    expect(ctrl.executions).toEqual([firstExecution]);
    expect(ctrl.viewState.selectedExecution).toBe(firstExecution);
    expect(command.extraFields.parentPipelineApplication).toBe('a');
    expect(command.extraFields.parentPipelineId).toBe('b-1');

    command.trigger = secondTrigger;
    $scope.$digest();

    expect(ctrl.executions).toEqual([secondExecution]);
    expect(ctrl.viewState.selectedExecution).toBe(secondExecution);
    expect(command.extraFields.parentPipelineApplication).toBe('b');
    expect(command.extraFields.parentPipelineId).toBe('c-3');
  });

});
