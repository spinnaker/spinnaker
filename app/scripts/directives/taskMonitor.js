'use strict';


angular.module('deckApp')
  .directive('taskMonitor', function () {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/taskMonitor.html',
      scope: {
        taskMonitor: '=monitor'
      }
    };
  }
);
