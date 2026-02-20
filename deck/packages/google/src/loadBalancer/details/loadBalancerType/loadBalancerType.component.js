'use strict';

import { module } from 'angular';
import _ from 'lodash';

export const GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERTYPE_LOADBALANCERTYPE_COMPONENT =
  'spinnaker.deck.gce.loadBalancer.loadBalancerType';
export const name = GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERTYPE_LOADBALANCERTYPE_COMPONENT; // for backwards compatibility
module(GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERTYPE_LOADBALANCERTYPE_COMPONENT, []).component('gceLoadBalancerType', {
  template: '<span>{{ $ctrl.type }}</span>',
  bindings: {
    loadBalancer: '=',
  },
  controller: function () {
    this.$onInit = () => {
      this.type = (function (lb) {
        if (lb.loadBalancerType === 'HTTP') {
          const hasCertificate = _.isString(lb.certificate) && !_.isEmpty(lb.certificate);
          const hasCertificateMap = _.isString(lb.certificateMap) && !_.isEmpty(lb.certificateMap);
          if (hasCertificate || hasCertificateMap) {
            return 'HTTPS';
          } else {
            return 'HTTP';
          }
        } else {
          return lb.loadBalancerType;
        }
      })(this.loadBalancer);
    };
  },
});
