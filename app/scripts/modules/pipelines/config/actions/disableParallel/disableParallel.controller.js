'use strict';

angular.module('spinnaker.pipelines.disableParallel', [
  'spinnaker.pipelines.config.service',
])
  .controller('DisableParallelModalCtrl', function($scope, pipeline, _, $modalInstance, pipelineConfigService) {

    this.cancel = $modalInstance.dismiss;

    $scope.pipeline = pipeline;

    this.disableParallel = function() {
      pipelineConfigService.disableParallelExecution(pipeline);
      $modalInstance.close();
    };

  });
