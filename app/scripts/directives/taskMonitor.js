'use strict';

require('../app');
var angular = require('angular');

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
