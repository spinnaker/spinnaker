'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.details.resize.capacity.directive', [
  ])
  .directive('awsResizeCapacity', function () {
    return {
      restrict: 'E',
      templateUrl: require('./resizeCapacity.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
        currentSize: '='
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
