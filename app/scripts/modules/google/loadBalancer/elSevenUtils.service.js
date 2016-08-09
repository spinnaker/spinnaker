'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.elSevenUtils.service', [])
  .factory('elSevenUtils', function () {
    function isElSeven (loadBalancer) {
      return loadBalancer.loadBalancerType === 'HTTP';
    }

    return { isElSeven };
  });
