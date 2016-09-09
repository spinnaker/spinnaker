'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.elSevenUtils.service', [])
  .factory('elSevenUtils', function () {
    const region = 'global';

    function isElSeven (lb) {
      return lb.loadBalancerType === 'HTTP';
    }

    function isHttps (lb) {
      return lb.certificate;
    }

    function getElSevenRegion () {
      return region;
    }

    return { isElSeven, getElSevenRegion, isHttps };
  });
