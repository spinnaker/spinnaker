'use strict';

describe('Controller: pipelineExecutions', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;
  var $state;
  var pipelineConfigService;
  var executionsService;
  var $q;

  beforeEach(
    module('deckApp.delivery.pipelineExecutions.controller')
  );

  beforeEach(
    inject(function ($rootScope, $controller, _$state_, _pipelineConfigService_, _executionsService_, _$q_) {
      scope = $rootScope.$new();
      $state = { go: angular.noop };
      pipelineConfigService = _pipelineConfigService_;
      executionsService = _executionsService_;
      $q = _$q_;
      scope.application = {name: 'foo'};

      this.initializeController = function() {
        controller = $controller('pipelineExecutions', {
          $scope: scope,
          $state: $state,
          pipelineConfigService: pipelineConfigService,
          executionsService: executionsService,
        });
      };
    })
  );

  it('should reroute to pipeline config when no execution history or configurations', function () {
    spyOn($state, 'go');
    spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when({ plain: angular.noop, length: 0 }));
    spyOn(executionsService, 'getAll').and.returnValue($q.when([]));
    this.initializeController();
    scope.$digest();

    expect($state.go).toHaveBeenCalledWith('^.pipelineConfig');
  });
});

