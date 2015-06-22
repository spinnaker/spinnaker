'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.disableParallel', [
  require('../../services/pipelineConfigService.js'),
])
  .controller('DisableParallelModalCtrl', function($scope, pipeline, _, $modalInstance, pipelineConfigService) {

    this.cancel = $modalInstance.dismiss;

    $scope.pipeline = pipeline;

    this.disableParallel = function() {
      pipelineConfigService.disableParallelExecution(pipeline);
      $modalInstance.close();
    };

  });
