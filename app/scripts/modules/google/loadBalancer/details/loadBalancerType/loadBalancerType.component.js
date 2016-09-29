'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.loadBalancer.loadBalancerType', [])
  .component('gceLoadBalancerType', {
    template: '<span>{{ $ctrl.type }}</span>',
    bindings: {
      loadBalancer: '='
    },
    controller: function () {
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
