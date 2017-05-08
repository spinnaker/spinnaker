import {APPLICATION_MODEL_BUILDER} from 'core/application/applicationModel.builder';

describe('Controller: pipelineExecutions', function () {

  var controller;
  var scope;
  var $state;
  var $stateParams;
  var $timeout;
  var scrollToService;
  var application;

  beforeEach(
    window.module(
      require('./executions.controller'),
      APPLICATION_MODEL_BUILDER
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _$timeout_, _scrollToService_, applicationModelBuilder, executionFilterModel) {
      scope = $rootScope.$new();
      $state = {go: angular.noop};
      $stateParams = {};
      $timeout = _$timeout_;
      scrollToService = _scrollToService_;
      spyOn(executionFilterModel.asFilterModel, 'applyParamsToUrl').and.callFake(() => {});
      application = applicationModelBuilder.createApplication({key: 'executions', lazy: true}, {key: 'pipelineConfigs', lazy: true});

      this.initializeController = function (data) {
        scope.application = application;
        application.executions.activate = angular.noop;
        application.pipelineConfigs.activate = angular.noop;
        if (data && data.executions) {
          application.executions.data = data.executions;
          application.executions.loaded = true;
        }
        if (data && data.pipelineConfigs) {
          application.pipelineConfigs.data = data.pipelineConfigs;
          application.pipelineConfigs.loaded = true;
        }

        controller = $controller('ExecutionsCtrl', {
          $scope: scope,
          $state: $state,
          $stateParams: $stateParams,
          scrollToService: _scrollToService_,
        });
        controller.$onInit();
      };
    })
  );

  it('should not set loading flag to false until executions and pipeline configs have been loaded', function () {
    this.initializeController();

    expect(controller.viewState.loading).toBe(true);

    application.executions.dataUpdated();
    application.pipelineConfigs.dataUpdated();
    scope.$digest();
    $timeout.flush();
    expect(controller.viewState.loading).toBe(false);
  });

  it('should update execution name when pipelineConfigId is present and name differs in config', function () {
    var pipelineConfigs = [
      {
        id: 'a1',
        name: 'updated name',
      },
      {
        id: 'a2',
        name: 'unchanged',
      },
    ];
    var executions = [
      {
        pipelineConfigId: 'a1',
        name: 'oldName',
        stageSummaries: [],
      },
      {
        pipelineConfigId: 'a2',
        name: 'unchanged',
        stageSummaries: [],
      },
      {
        pipelineConfigId: 'a3',
        name: 'no longer configured',
        stageSummaries: [],
      }
    ];

    this.initializeController({pipelineConfigs: pipelineConfigs, executions: executions});
    scope.$digest();
    $timeout.flush();

    expect(application.executions.data[0].name).toBe('updated name');
    expect(application.executions.data[1].name).toBe('unchanged');
    expect(application.executions.data[2].name).toBe('no longer configured');
  });

  describe('auto-scrolling behavior', function () {

    beforeEach(function () {
      spyOn(scrollToService, 'scrollTo');
    });

    it('should scroll execution into view on initialization if an execution is present in state params', function () {
      $stateParams.executionId = 'a';

      this.initializeController({ pipelineConfigs: [], executions: []});
      scope.$digest();

      expect(scrollToService.scrollTo.calls.count()).toBe(1);
    });

    it('should NOT scroll execution into view on initialization if none present in state params', function () {
      this.initializeController();
      scope.$digest();

      expect(scrollToService.scrollTo.calls.count()).toBe(0);
    });

    it('should scroll execution into view on state change success if no execution id in state params', function () {
      this.initializeController();
      scope.$digest();

      expect(scrollToService.scrollTo.calls.count()).toBe(0);

      scope.$broadcast('$stateChangeSuccess', {name: 'executions.execution'}, {executionId: 'a'}, {name: 'executions'}, {});
      expect(scrollToService.scrollTo.calls.count()).toBe(1);
    });

    it('should scroll execution into view on state change success if execution id changes', function () {
      this.initializeController();
      scope.$digest();

      expect(scrollToService.scrollTo.calls.count()).toBe(0);

      scope.$broadcast('$stateChangeSuccess', {name: 'executions.execution'}, {executionId: 'a'}, {name: 'executions.execution'}, {executionId: 'b'});
      expect(scrollToService.scrollTo.calls.count()).toBe(1);
    });

    it('should scroll into view if no params change, because the user clicked on a link somewhere else in the page', function () {
      let params = {executionId: 'a', step: 'b', stage: 'c', details: 'd'};

      this.initializeController();
      scope.$digest();

      expect(scrollToService.scrollTo.calls.count()).toBe(0);

      scope.$broadcast('$stateChangeSuccess', {name: 'executions.execution'}, params, {name: 'executions.execution'}, params);
      expect(scrollToService.scrollTo.calls.count()).toBe(1);
    });

    it('should NOT scroll into view if step changes', function () {
      let toParams = {executionId: 'a', step: 'b', stage: 'c', details: 'd'},
          fromParams = {executionId: 'a', step: 'c', stage: 'c', details: 'd'};

      this.initializeController();
      scope.$digest();
      scope.$broadcast('$stateChangeSuccess', {name: 'executions'}, toParams, {name: 'executions'}, fromParams);
      expect(scrollToService.scrollTo.calls.count()).toBe(0);
    });

    it('should NOT scroll into view if stage changes', function () {
      let toParams = {executionId: 'a', step: 'b', stage: 'c', details: 'd'},
          fromParams = {executionId: 'a', step: 'b', stage: 'e', details: 'd'};

      this.initializeController();
      scope.$digest();
      scope.$broadcast('$stateChangeSuccess', {name: 'executions'}, toParams, {name: 'executions'}, fromParams);
      expect(scrollToService.scrollTo.calls.count()).toBe(0);
    });

    it('should NOT scroll into view if detail changes', function () {
      let toParams = {executionId: 'a', step: 'b', stage: 'c', details: 'd'},
          fromParams = {executionId: 'a', step: 'b', stage: 'c', details: 'e'};

      this.initializeController();
      scope.$digest();
      scope.$broadcast('$stateChangeSuccess', {name: 'executions'}, toParams, {name: 'executions'}, fromParams);
      expect(scrollToService.scrollTo.calls.count()).toBe(0);
    });

  });

});

