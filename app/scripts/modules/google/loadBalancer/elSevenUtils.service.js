'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.elSevenUtils.service', [])
  .factory('elSevenUtils', function () {
    const region = 'global';

    function isElSeven (lb) {
      return (lb.provider === 'gce' || lb.type === 'gce') && (lb.loadBalancerType === 'HTTP' || lb.region === region);
    }

    function isHttps (lb) {
      return lb.certificate;
    }

    function getElSevenRegion () {
      return region;
    }

    return { isElSeven, getElSevenRegion, isHttps };
  });
