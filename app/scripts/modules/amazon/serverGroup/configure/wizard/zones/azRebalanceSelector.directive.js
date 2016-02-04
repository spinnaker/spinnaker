'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.capacity.azRebalance.directive', [
  ])
  .directive('azRebalanceSelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./azRebalanceSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
