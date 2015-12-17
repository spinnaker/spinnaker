'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.capacity.targetHealthyPercentageSelector.directive', [
  ])
  .directive('awsTargetHealthyPercentageSelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./targetHealthyPercentageSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
