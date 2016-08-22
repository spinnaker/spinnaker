'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.serverGroup.dirtyInstanceTypeNotification.component', [])
  .component('dirtyInstanceTypeNotification', {
    bindings: {
      command: '='
    },
    templateUrl: require('./dirtyInstanceTypeNotification.component.html')
  });
