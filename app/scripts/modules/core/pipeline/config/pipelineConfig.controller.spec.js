'use strict';

describe('Controller: PipelineConfigCtrl', function () {

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./pipelineConfig.controller.js')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller;
    })
  );

  it('should reload pipeline configs if not present on application', function () {
    scope.application = { reloadPipelineConfigs: angular.noop };
    spyOn(scope.application, 'reloadPipelineConfigs');
    controller('PipelineConfigCtrl', {
      $scope: scope
    });
    expect(scope.application.reloadPipelineConfigs.calls.count()).toBe(1);
  });
});

