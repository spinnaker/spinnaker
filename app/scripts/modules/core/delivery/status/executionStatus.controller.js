'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.delivery.executionStatus.controller', [])
  .controller('executionStatus', function($scope) {
    const strategyExclusions = [
      'parentPipelineId',
      'strategy',
      'parentStageId',
      'deploymentDetails',
      'cloudProvider'
    ];

    if ($scope.execution.trigger && $scope.execution.trigger.parameters) {
      this.parameters = Object.keys($scope.execution.trigger.parameters).sort()
        .filter((paramKey) => $scope.execution.isStrategy ? strategyExclusions.indexOf(paramKey) < 0 : true)
        .map((paramKey) => {
          return { key: paramKey, value: $scope.execution.trigger.parameters[paramKey] };
      });
    }
  });
