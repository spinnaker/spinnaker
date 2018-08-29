'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.oracle.serverGroup.configure.wizard.capacity.selector.component', [])
  .component('oracleServerGroupCapacitySelector', {
    templateUrl: require('./capacitySelector.component.html'),
    bindings: {
      command: '=',
    },
    controllerAs: 'vm',
    controller: angular.noop,
  });
