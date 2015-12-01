'use strict';

describe('Controller: PipelineConfigCtrl', function () {

  var controller;
  var scope;
  var rx;

  beforeEach(
    window.module(
      require('./pipelineConfig.controller.js'),
      require('../../utils/rx.js')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _rx_) {
      scope = $rootScope.$new();
      controller = $controller;
      rx = _rx_;
    })
  );

  it('should initialize immediately if pipeline configs are already present', function () {
    scope.application = {
      pipelineConfigs: [
        { id: 'a'}
      ]
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
    var pipelineConfigRefreshStream = new rx.Subject();
    scope.application = {
      pipelineConfigRefreshStream: pipelineConfigRefreshStream,
    };
    pipelineConfigRefreshStream.onNext();
    let vm = controller('PipelineConfigCtrl', {
      $scope: scope,
      $stateParams: {
        pipelineId: 'a'
      }
    });

    scope.application.pipelineConfigs = [ {id: 'a'} ];
    pipelineConfigRefreshStream.onNext();

    expect(vm.state.pipelinesLoaded).toBe(true);
  });
});

