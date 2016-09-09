'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.httpLoadBalancer.healthCheck.component', [])
  .component('gceHttpLoadBalancerHealthCheck', {
    bindings: {
      healthCheck: '=',
      deleteHealthCheck: '&',
      index: '='
    },
    templateUrl: require('./healthCheck.component.html'),
    controller: function () {
      this.max = Number.MAX_SAFE_INTEGER;
    }
  });
