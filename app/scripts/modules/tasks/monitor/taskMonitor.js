'use strict';


angular.module('deckApp.tasks.monitor')
  .directive('taskMonitor', function () {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'scripts/modules/tasks/monitor/taskMonitor.html',
      scope: {
        taskMonitor: '=monitor'
      }
    };
  }
);
