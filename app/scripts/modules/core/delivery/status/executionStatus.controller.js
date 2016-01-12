'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.delivery.executionStatus.controller', [])
  .controller('executionStatus', function($scope) {
    if ($scope.execution.trigger && $scope.execution.trigger.parameters) {
      this.parameters = Object.keys($scope.execution.trigger.parameters).sort().map((paramKey) => {
        return { key: paramKey, value: $scope.execution.trigger.parameters[paramKey] };
      });
    }
  });
