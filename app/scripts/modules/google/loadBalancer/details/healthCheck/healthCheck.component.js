'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.loadBalancer.details.healthCheck.component', [])
  .component('gceHealthCheck', {
    bindings: {
      healthCheck: '='
    },
    templateUrl: require('./healthCheck.component.html')
  });
