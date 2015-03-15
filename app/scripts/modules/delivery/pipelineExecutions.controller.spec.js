'use strict';

describe('Controller: pipelineExecutions', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;
  var rootScope;
  var $state;
  var pipelineConfigService;
  var executionsService;
  var $q;

  beforeEach(
    module('deckApp.delivery.pipelineExecutions.controller')
  );

  beforeEach(
    inject(function ($rootScope, $controller, _$state_, _pipelineConfigService_, _$q_) {
      rootScope = $rootScope;
      scope = $rootScope.$new();
      $state = { go: angular.noop };
      pipelineConfigService = _pipelineConfigService_;
      $q = _$q_;

      this.initializeController = function(application) {
        scope.application = application;
        controller = $controller('pipelineExecutions', {
          $scope: scope,
          $state: $state,
          pipelineConfigService: pipelineConfigService,
        });
      };
    })
  );

  it('should reroute to pipeline config when no execution history or configurations', function () {
    var application = {
      name: 'foo',
      executionsLoaded: true,
      executions: []
    };
    spyOn($state, 'go');
    spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when({ plain: function() { return []; } }));
    this.initializeController(application);
    scope.$digest();

    expect($state.go).toHaveBeenCalledWith('^.pipelineConfig');
  });

  it('should not set loading flag to false until executions have been loaded', function() {
    var application = {
      name: 'foo',
      executionsLoaded: false
    };
    spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when({ plain: function() { return []; } }));
    this.initializeController(application);
    scope.$digest();

    expect(scope.viewState.loading).toBe(true);

    rootScope.$broadcast('executions-loaded');
    expect(scope.viewState.loading).toBe(false);
  });
});

