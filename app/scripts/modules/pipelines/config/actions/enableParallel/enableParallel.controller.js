'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.config.actions.enableParallel', [
  require('../../services/pipelineConfigService.js'),
  require('utils/lodash.js'),
  require('angular-bootstrap'),
])
  .controller('EnableParallelModalCtrl', function($scope, pipeline, _, $modalInstance, pipelineConfigService) {

    this.cancel = $modalInstance.dismiss;

    $scope.pipeline = pipeline;

    this.makeParallel = function() {
      pipelineConfigService.enableParallelExecution(pipeline);
      $modalInstance.close();
    };

  }).name;
