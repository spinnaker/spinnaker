'use strict';

describe('Controller: pipelineExecutions', function () {

  const angular = require('angular');

  var controller;
  var scope;
  var rootScope;
  var $state;
  var pipelineConfigService;
  var $q;

  beforeEach(
    window.module(
      require('./executions.controller')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _$state_, _pipelineConfigService_, _$q_) {
      rootScope = $rootScope;
      scope = $rootScope.$new();
      $state = { go: angular.noop };
      pipelineConfigService = _pipelineConfigService_;
      $q = _$q_;

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
    var application = {
      name: 'foo',
      executionsLoaded: false,
      pipelineConfigsLoaded: false,
    };
    spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when({ plain: function () {
      return [];
    } }));
    this.initializeController(application);
    scope.$digest();

    expect(controller.viewState.loading).toBe(true);

    rootScope.$broadcast('executions-loaded');
    rootScope.$broadcast('pipelineConfigs-loaded');
    scope.$digest();
    expect(controller.viewState.loading).toBe(false);
  });

  it('should update execution name when pipelineConfigId is present and name differs in config', function () {
    var application = {
      name: 'foo',
      pipelineConfigsLoading: false,
      executionsLoaded: true,
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
    scope.$digest();

    expect(application.executions[0].name).toBe('updated name');
    expect(application.executions[1].name).toBe('unchanged');
    expect(application.executions[2].name).toBe('no longer configured');
  });
});

