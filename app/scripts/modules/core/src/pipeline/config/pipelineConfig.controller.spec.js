import { APPLICATION_MODEL_BUILDER } from 'core/application/applicationModel.builder';

describe('Controller: PipelineConfigCtrl', function() {
  var controller;
  var scope;
  var applicationModelBuilder;

  beforeEach(window.module(require('./pipelineConfig.controller').name, APPLICATION_MODEL_BUILDER));

  beforeEach(
    window.inject(function($rootScope, $controller, _applicationModelBuilder_) {
      scope = $rootScope.$new();
      controller = $controller;
      applicationModelBuilder = _applicationModelBuilder_;
    }),
  );

  it('should initialize immediately if pipeline configs are already present', function() {
    const application = applicationModelBuilder.createApplicationForTests('app', {
      key: 'pipelineConfigs',
      lazy: true,
    });
    application.pipelineConfigs.data = [{ id: 'a' }];
    application.pipelineConfigs.loaded = true;

    let vm = controller('PipelineConfigCtrl', {
      $scope: scope,
      $stateParams: {
        pipelineId: 'a',
      },
      app: application,
    });
    scope.$digest();
    expect(vm.state.pipelinesLoaded).toBe(true);
  });

  it('should wait until pipeline configs are loaded before initializing', function() {
    const application = applicationModelBuilder.createApplicationForTests('app', {
      key: 'pipelineConfigs',
      lazy: true,
    });
    spyOn(application.pipelineConfigs, 'activate').and.callFake(angular.noop);
    let vm = controller('PipelineConfigCtrl', {
      $scope: scope,
      $stateParams: {
        pipelineId: 'a',
      },
      app: application,
    });

    application.pipelineConfigs.data.push({ id: 'a' });
    application.pipelineConfigs.dataUpdated();
    scope.$digest();

    expect(vm.state.pipelinesLoaded).toBe(true);
    expect(application.pipelineConfigs.activate.calls.count()).toBe(1);
  });
});
