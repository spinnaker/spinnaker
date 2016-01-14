'use strict';

describe('Controller: pipelineExecutions', function () {

  const angular = require('angular');

  var controller;
  var scope;
  var $state;
  var pipelineConfigService;
  var $q;
  var $timeout;
  var rx;

  beforeEach(
    window.module(
      require('./executions.controller'),
      require('../../utils/rx')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _$state_, _pipelineConfigService_, _$q_, _$timeout_, _rx_) {
      scope = $rootScope.$new();
      $state = { go: angular.noop };
      pipelineConfigService = _pipelineConfigService_;
      $q = _$q_;
      $timeout = _$timeout_;
      rx = _rx_;

      this.initializeController = function (application) {
        scope.application = application;
        controller = $controller('ExecutionsCtrl', {
          $scope: scope,
          $state: $state,
          pipelineConfigService: pipelineConfigService,
        });
      };
    })
  );

  it('should not set loading flag to false until executions and pipeline configs have been loaded', function () {
    var executionRefreshStream = new rx.Subject(),
        pipelineConfigRefreshStream = new rx.Subject();
    var application = {
      name: 'foo',
      executionsLoaded: false,
      pipelineConfigsLoaded: false,
      executionRefreshStream: executionRefreshStream,
      pipelineConfigRefreshStream: pipelineConfigRefreshStream,
      reloadExecutions: () => executionRefreshStream.onNext(),
      reloadPipelineConfigs: () => pipelineConfigRefreshStream.onNext(),
    };
    spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when({ plain: function () {
      return [];
    } }));
    this.initializeController(application);
    scope.$digest();

    expect(controller.viewState.loading).toBe(true);

    executionRefreshStream.onNext();
    pipelineConfigRefreshStream.onNext();
    scope.$digest();
    $timeout.flush();

    expect(controller.viewState.loading).toBe(false);
  });

  it('should update execution name when pipelineConfigId is present and name differs in config', function () {
    var executionRefreshStream = new rx.Subject(),
        pipelineConfigRefreshStream = new rx.Subject();
    var application = {
      name: 'foo',
      pipelineConfigsLoading: false,
      executionsLoaded: true,
      executionRefreshStream: executionRefreshStream,
      pipelineConfigRefreshStream: pipelineConfigRefreshStream,
      reloadExecutions: () => executionRefreshStream.onNext(),
      reloadPipelineConfigs: () => pipelineConfigRefreshStream.onNext(),
      pipelineConfigs: [
        {
          id: 'a1',
          name: 'updated name',
        },
        {
          id: 'a2',
          name: 'unchanged',
        },
      ],
      executions: [
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
      ],
    };
    this.initializeController(application);

    executionRefreshStream.onNext();

    expect(application.executions[0].name).toBe('updated name');
    expect(application.executions[1].name).toBe('unchanged');
    expect(application.executions[2].name).toBe('no longer configured');
  });
});

