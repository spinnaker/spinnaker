'use strict';

describe('Controller: PipelineConfigCtrl', function () {

  var controller;
  var scope;
  var rx;
  var applicationReader;

  beforeEach(
    window.module(
      require('./pipelineConfig.controller.js'),
      require('../../utils/rx.js'),
      require('../../application/service/applications.read.service.js')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _rx_, _applicationReader_) {
      scope = $rootScope.$new();
      controller = $controller;
      rx = _rx_;
      applicationReader = _applicationReader_;
    })
  );

  it('should initialize immediately if pipeline configs are already present', function () {
    scope.application = {
      pipelineConfigs: {
        loaded: true,
        data: [
          { id: 'a'}
        ]
      }
    };
    let vm = controller('PipelineConfigCtrl', {
      $scope: scope,
      $stateParams: {
        pipelineId: 'a'
      }
    });
    expect(vm.state.pipelinesLoaded).toBe(true);
  });

  it('should wait until pipeline configs are loaded before initializing', function () {
    scope.application = {};
    applicationReader.addSectionToApplication({key: 'pipelineConfigs', lazy: true}, scope.application);
    let vm = controller('PipelineConfigCtrl', {
      $scope: scope,
      $stateParams: {
        pipelineId: 'a'
      }
    });

    scope.application.pipelineConfigs.data.push({id: 'a'});
    scope.application.pipelineConfigs.refreshStream.onNext();

    expect(vm.state.pipelinesLoaded).toBe(true);
  });
});

