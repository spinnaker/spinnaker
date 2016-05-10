'use strict';

describe('Controller: PipelineConfigCtrl', function () {

  var controller;
  var scope;
  var applicationReader;

  beforeEach(
    window.module(
      require('./pipelineConfig.controller.js'),
      require('../../application/service/applications.read.service.js')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _applicationReader_) {
      scope = $rootScope.$new();
      controller = $controller;
      applicationReader = _applicationReader_;
    })
  );

  it('should initialize immediately if pipeline configs are already present', function () {
    scope.application = {};
    applicationReader.addSectionToApplication({key: 'pipelineConfigs', lazy: true}, scope.application);
    scope.application.pipelineConfigs.data = [ { id: 'a' } ];
    scope.application.pipelineConfigs.loaded = true;

    let vm = controller('PipelineConfigCtrl', {
      $scope: scope,
      $stateParams: {
        pipelineId: 'a'
      }
    });
    scope.$digest();
    expect(vm.state.pipelinesLoaded).toBe(true);
  });

  it('should wait until pipeline configs are loaded before initializing', function () {
    scope.application = {};
    applicationReader.addSectionToApplication({key: 'pipelineConfigs', lazy: true, loader: angular.noop}, scope.application);
    spyOn(scope.application.pipelineConfigs, 'activate').and.callFake(angular.noop);
    let vm = controller('PipelineConfigCtrl', {
      $scope: scope,
      $stateParams: {
        pipelineId: 'a'
      }
    });

    scope.application.pipelineConfigs.data.push({id: 'a'});
    scope.application.pipelineConfigs.refreshStream.onNext();
    scope.$digest();

    expect(vm.state.pipelinesLoaded).toBe(true);
    expect(scope.application.pipelineConfigs.activate.calls.count()).toBe(1);
  });
});

