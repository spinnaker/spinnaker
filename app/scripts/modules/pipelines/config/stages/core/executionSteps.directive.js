'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stages.core.executionStepDetails', [
  require('../../pipelineConfigProvider.js'),
])
  .directive('executionStepDetails', function() {
    return {
      restrict: 'E',
      scope: {
        item: '='
      },
      templateUrl: require('./executionStepDetails.html'),
      controller: 'ExecutionStepDetailsCtrl',
      controllerAs: 'executionStepDetailsCtrl'
    };
  })
  .controller('ExecutionStepDetailsCtrl', function() {

  })
  .name;

