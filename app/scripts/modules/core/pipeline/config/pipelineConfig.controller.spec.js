import {APPLICATION_MODEL_BUILDER} from 'core/application/applicationModel.builder';

describe('Controller: PipelineConfigCtrl', function () {

  var controller;
  var scope;
  var applicationModelBuilder;

  beforeEach(
    window.module(
      require('./pipelineConfig.controller.js'),
      APPLICATION_MODEL_BUILDER
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _applicationModelBuilder_) {
      scope = $rootScope.$new();
      controller = $controller;
      applicationModelBuilder = _applicationModelBuilder_;
    })
  );

  it('should initialize immediately if pipeline configs are already present', function () {
    scope.application = applicationModelBuilder.createApplication({key: 'pipelineConfigs', lazy: true});
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
    scope.application = applicationModelBuilder.createApplication({key: 'pipelineConfigs', lazy: true});
    spyOn(scope.application.pipelineConfigs, 'activate').and.callFake(angular.noop);
    let vm = controller('PipelineConfigCtrl', {
      $scope: scope,
      $stateParams: {
        pipelineId: 'a'
      }
    });

    scope.application.pipelineConfigs.data.push({id: 'a'});
    scope.application.pipelineConfigs.dataUpdated();
    scope.$digest();

    expect(vm.state.pipelinesLoaded).toBe(true);
    expect(scope.application.pipelineConfigs.activate.calls.count()).toBe(1);
  });
});

