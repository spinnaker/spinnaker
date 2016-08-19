'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.serverGroup.configure.advancedCapacitySelector.component', [])
  .component('gceServerGroupAdvancedCapacitySelector', {
    bindings: {
      command: '='
    },
    templateUrl: require('./advancedCapacitySelector.component.html')
  });
