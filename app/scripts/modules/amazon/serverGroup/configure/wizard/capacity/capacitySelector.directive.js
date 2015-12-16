'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.capacity.selector.directive', [
  ])
  .directive('awsServerGroupCapacitySelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./capacitySelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: 'awsServerGroupCapacitySelectorCtrl',
    };
  }).controller('awsServerGroupCapacitySelectorCtrl', function () {
    this.setSimpleCapacity = (simpleCapacity) => {
      this.command.viewState.useSimpleCapacity = simpleCapacity;
      this.command.useSourceCapacity = false;
      this.setMinMax(this.command.capacity.desired);
    };

    this.setMinMax = (newVal) => {
      if (this.command.viewState.useSimpleCapacity) {
        this.command.capacity.min = newVal;
        this.command.capacity.max = newVal;
        this.command.useSourceCapacity = false;
      }
    };
  });
