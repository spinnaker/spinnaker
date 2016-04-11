'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions.disableParallel', [
  require('../../../../utils/lodash.js'),
  require('../../services/pipelineConfigService.js'),
])
  .controller('DisableParallelModalCtrl', function($scope, pipeline, _, $uibModalInstance, pipelineConfigService) {

    this.cancel = $uibModalInstance.dismiss;

    $scope.pipeline = pipeline;

    this.disableParallel = function() {
      pipelineConfigService.disableParallelExecution(pipeline);
      $uibModalInstance.close();
    };

  });
