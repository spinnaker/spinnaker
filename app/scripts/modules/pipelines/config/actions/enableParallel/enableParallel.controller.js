'use strict';

angular.module('spinnaker.pipelines.enableParallel', [
  'spinnaker.pipelines.config.service',
])
  .controller('EnableParallelModalCtrl', function($scope, pipeline, _, $modalInstance, pipelineConfigService) {

    this.cancel = $modalInstance.dismiss;

    $scope.pipeline = pipeline;

    this.makeParallel = function() {
      pipelineConfigService.enableParallelExecution(pipeline);
      $modalInstance.close();
    };

  });
