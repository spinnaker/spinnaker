'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.enableParallel', [
  require('../../services/pipelineConfigService.js'),
])
  .controller('EnableParallelModalCtrl', function($scope, pipeline, _, $modalInstance, pipelineConfigService) {

    this.cancel = $modalInstance.dismiss;

    $scope.pipeline = pipeline;

    this.makeParallel = function() {
      pipelineConfigService.enableParallelExecution(pipeline);
      $modalInstance.close();
    };

  });
