'use strict';

describe('Controller: pipelineExecutions', function () {

  const angular = require('angular');

  var controller;
  var scope;
  var $state;
  var $stateParams;
  var $timeout;
  var rx;
  var scrollToService;

  beforeEach(
    window.module(
      require('./executions.controller'),
      require('../../utils/rx')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _$timeout_, _rx_, _scrollToService_) {
      scope = $rootScope.$new();
      $state = { go: angular.noop };
      $stateParams = {};
      $timeout = _$timeout_;
      rx = _rx_;
      scrollToService = _scrollToService_;

      this.initializeController = function (application) {
        scope.application = application;
        application.executions.refreshStream = new rx.Subject();
        application.executions.onRefresh = function($scope, method) { return application.executions.refreshStream.subscribe(method); };
        application.executions.onNextRefresh = function($scope, method) { return application.executions.refreshStream.take(1).subscribe(method); };
        application.pipelineConfigs.refreshStream = new rx.Subject();
        application.pipelineConfigs.onRefresh = function($scope, method) { return application.pipelineConfigs.refreshStream.subscribe(method); };
        application.pipelineConfigs.onNextRefresh = function($scope, method) { return application.pipelineConfigs.refreshStream.take(1).subscribe(method); };

        controller = $controller('ExecutionsCtrl', {
          $scope: scope,
          $state: $state,
          $stateParams: $stateParams,
          scrollToService: _scrollToService_,
        });
      };
    })
  );

  it('should not set loading flag to false until executions and pipeline configs have been loaded', function () {
    var application = {
      name: 'foo',
      executions: { refresh: angular.noop, },
      pipelineConfigs: { refresh: angular.noop, },
    };
    this.initializeController(application);
    scope.$digest();

    expect(controller.viewState.loading).toBe(true);

    application.executions.refreshStream.onNext();
    application.pipelineConfigs.refreshStream.onNext();
    scope.$digest();
    $timeout.flush();

    expect(controller.viewState.loading).toBe(false);
  });

  it('should update execution name when pipelineConfigId is present and name differs in config', function () {
    var application = {
      name: 'foo',
      pipelineConfigs: { data: [
        {
          id: 'a1',
          name: 'updated name',
        },
        {
          id: 'a2',
          name: 'unchanged',
        },
      ], loaded: true},
      executions: { data: [
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
      ], loaded: true},
    };
    this.initializeController(application);
    $timeout.flush();

    expect(application.executions.data[0].name).toBe('updated name');
    expect(application.executions.data[1].name).toBe('unchanged');
    expect(application.executions.data[2].name).toBe('no longer configured');
  });

  describe('auto-scrolling behavior', function () {

    var application;

    beforeEach(function () {
      spyOn(scrollToService, 'scrollTo');
      application = {
        name: 'foo',
        executions: { data: [], loaded: true },
        pipelineConfigs: { data: [], loaded: true },
      };
    });

    it('should scroll execution into view on initialization if an execution is present in state params', function () {
      $stateParams.executionId = 'a';

      this.initializeController(application);
      scope.$digest();

      expect(scrollToService.scrollTo.calls.count()).toBe(1);
    });

    it('should NOT scroll execution into view on initialization if none present in state params', function () {
      this.initializeController(application);
      scope.$digest();

      expect(scrollToService.scrollTo.calls.count()).toBe(0);
    });

    it('should scroll execution into view on state change success if no execution id in state params', function () {
      this.initializeController(application);
      scope.$digest();

      expect(scrollToService.scrollTo.calls.count()).toBe(0);

      scope.$broadcast('$stateChangeSuccess', { name: 'executions.execution' }, { executionId: 'a' }, { name: 'executions' }, {});
      expect(scrollToService.scrollTo.calls.count()).toBe(1);
    });

    it('should scroll execution into view on state change success if execution id changes', function () {
      this.initializeController(application);
      scope.$digest();

      expect(scrollToService.scrollTo.calls.count()).toBe(0);

      scope.$broadcast('$stateChangeSuccess', { name: 'executions.execution' }, { executionId: 'a' }, { name: 'executions.execution' }, { executionId: 'b' });
      expect(scrollToService.scrollTo.calls.count()).toBe(1);
    });

    it('should scroll into view if no params change, because the user clicked on a link somewhere else in the page', function () {
      let params = { executionId: 'a', step: 'b', stage: 'c', details: 'd' };

      this.initializeController(application);
      scope.$digest();

      expect(scrollToService.scrollTo.calls.count()).toBe(0);

      scope.$broadcast('$stateChangeSuccess', { name: 'executions.execution' }, params, { name: 'executions.execution' }, params);
      expect(scrollToService.scrollTo.calls.count()).toBe(1);
    });

    it('should NOT scroll into view if step changes', function () {
      let toParams = { executionId: 'a', step: 'b', stage: 'c', details: 'd' },
          fromParams = { executionId: 'a', step: 'c', stage: 'c', details: 'd' };

      this.initializeController(application);
      scope.$digest();
      scope.$broadcast('$stateChangeSuccess', { name: 'executions' }, toParams, { name: 'executions' }, fromParams);
      expect(scrollToService.scrollTo.calls.count()).toBe(0);
    });

    it('should NOT scroll into view if stage changes', function () {
      let toParams = { executionId: 'a', step: 'b', stage: 'c', details: 'd' },
          fromParams = { executionId: 'a', step: 'b', stage: 'e', details: 'd' };

      this.initializeController(application);
      scope.$digest();
      scope.$broadcast('$stateChangeSuccess', { name: 'executions' }, toParams, { name: 'executions' }, fromParams);
      expect(scrollToService.scrollTo.calls.count()).toBe(0);
    });

    it('should NOT scroll into view if detail changes', function () {
      let toParams = { executionId: 'a', step: 'b', stage: 'c', details: 'd' },
          fromParams = { executionId: 'a', step: 'b', stage: 'c', details: 'e' };

      this.initializeController(application);
      scope.$digest();
      scope.$broadcast('$stateChangeSuccess', { name: 'executions' }, toParams, { name: 'executions' }, fromParams);
      expect(scrollToService.scrollTo.calls.count()).toBe(0);
    });

  });

});

