'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.task.reason.directive', [])
  .directive('taskReason', function () {
    return {
      restrict: 'E',
      bindToController: {
        command: '=',
      },
      scope: {},
      controller: angular.noop,
      controllerAs: 'vm',
      templateUrl: require('./reason.directive.html')
    };
  });
