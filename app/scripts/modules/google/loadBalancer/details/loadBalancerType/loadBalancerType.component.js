'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.loadBalancer.loadBalancerType', [
    require('../../../../core/utils/lodash.js')
  ])
  .component('gceLoadBalancerType', {
    template: '<span>{{ $ctrl.type }}</span>',
    bindings: {
      loadBalancer: '='
    },
    controller: function(_) {
      this.type = (function(lb) {
        if (lb.loadBalancerType === 'NETWORK') {
          return 'NETWORK';
        }

        if (lb.loadBalancerType === 'HTTP') {
          if (_.isString(lb.certificate)) {
            return 'HTTPS';
          } else {
            return 'HTTP';
          }
        }
      })(this.loadBalancer);
    }
  });
