'use strict';

import _ from 'lodash';

const angular = require('angular');

export const GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERTYPE_LOADBALANCERTYPE_COMPONENT =
  'spinnaker.deck.gce.loadBalancer.loadBalancerType';
export const name = GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERTYPE_LOADBALANCERTYPE_COMPONENT; // for backwards compatibility
angular
  .module(GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERTYPE_LOADBALANCERTYPE_COMPONENT, [])
  .component('gceLoadBalancerType', {
    template: '<span>{{ $ctrl.type }}</span>',
    bindings: {
      loadBalancer: '=',
    },
    controller: function() {
      this.type = (function(lb) {
        if (lb.loadBalancerType === 'HTTP') {
          if (_.isString(lb.certificate)) {
            return 'HTTPS';
          } else {
            return 'HTTP';
          }
        } else {
          return lb.loadBalancerType;
        }
      })(this.loadBalancer);
    },
  });
