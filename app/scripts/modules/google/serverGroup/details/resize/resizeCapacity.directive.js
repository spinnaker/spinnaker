'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.google.serverGroup.details.resize.capacity.directive', [
  ])
  .directive('gceResizeCapacity', function () {
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
