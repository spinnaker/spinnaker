'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.display.tasks.tag', [
  require('../configure/common/runningExecutions.service.js'),
])
  .directive('runningTasksTag', function() {
    return {
      restrict: 'E',
      scope: {
        application: '=',
        tasks: '=',
        executions: '='
      },
      templateUrl: require('./runningTasksTag.html'),
      controller: 'RunningTaskTagController',
    };
  })
  .controller('RunningTaskTagController', function ($scope, executionFilterService) {
    $scope.popover = { show : false };
    $scope.runningExecutions = function() {
      return executionFilterService.filterRunningExecutions($scope.executions);
    };

  });
