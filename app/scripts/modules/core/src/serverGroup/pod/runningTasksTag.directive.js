'use strict';

import { module } from 'angular';

export const CORE_SERVERGROUP_POD_RUNNINGTASKSTAG_DIRECTIVE = 'spinnaker.core.serverGroup.display.tasks.tag';
export const name = CORE_SERVERGROUP_POD_RUNNINGTASKSTAG_DIRECTIVE; // for backwards compatibility
module(CORE_SERVERGROUP_POD_RUNNINGTASKSTAG_DIRECTIVE, [])
  .directive('runningTasksTag', function () {
    return {
      restrict: 'E',
      scope: {
        application: '=',
        tasks: '=',
        executions: '=',
      },
      templateUrl: require('./runningTasksTag.html'),
      controller: 'RunningTaskTagController',
    };
  })
  .controller('RunningTaskTagController', [
    '$scope',
    function ($scope) {
      $scope.popoverTemplate = require('./runningTasksPopover.html');
      $scope.popover = { show: false };
      $scope.runningExecutions = function () {
        return ($scope.executions || []).filter((e) => e.isRunning || e.hasNotStarted);
      };
    },
  ]);
