'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.oraclebmcs.serverGroup.configure.wizard.capacity.selector.component', [
  ])
  .component('oracleBmcsServerGroupCapacitySelector', {
      templateUrl: require('./capacitySelector.component.html'),
      bindings: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: angular.noop
    });
