'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.simpleCapacity.selector.component', [
  ])
  .component('gceServerGroupSimpleCapacitySelector', {
    templateUrl: require('./simpleCapacitySelector.component.html'),
    bindings: {
      command: '=',
      setSimpleCapacity: '='
    },
    controller: 'gceServerGroupSimpleCapacitySelectorCtrl',
  }).controller('gceServerGroupSimpleCapacitySelectorCtrl', function () {
    this.setMinMax = (newVal) => {
      this.command.capacity.min = newVal;
      this.command.capacity.max = newVal;
    };
  });
