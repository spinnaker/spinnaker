'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stages.core.executionStepDetails', [
  PIPELINE_CONFIG_PROVIDER,
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

  });

