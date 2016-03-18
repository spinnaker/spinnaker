'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.capacity.selector.directive', [
  ])
  .directive('gceServerGroupCapacitySelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./capacitySelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: 'gceServerGroupCapacitySelectorCtrl',
    };
  }).controller('gceServerGroupCapacitySelectorCtrl', function () {
    this.setMinMax = (newVal) => {
      this.command.capacity.min = newVal;
      this.command.capacity.max = newVal;
    };
  });
