'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions.enableParallel', [
  require('../../services/pipelineConfigService.js'),
  require('../../../../utils/lodash.js'),
])
  .controller('EnableParallelModalCtrl', function($scope, pipeline, _, $modalInstance, pipelineConfigService) {

    this.cancel = $modalInstance.dismiss;

    $scope.pipeline = pipeline;

    this.makeParallel = function() {
      pipelineConfigService.enableParallelExecution(pipeline);
      $modalInstance.close();
    };

  });
