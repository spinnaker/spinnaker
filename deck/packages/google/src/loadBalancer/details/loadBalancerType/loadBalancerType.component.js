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
        if (lb.loadBalancerType === 'HTTP' || lb.loadBalancerType === 'EXTERNAL_MANAGED') {
          const hasCertificate =
            (_.isString(lb.certificate) && !_.isEmpty(lb.certificate)) ||
            _.some(lb.listeners, (listener) => _.isString(listener.certificate) && !_.isEmpty(listener.certificate));
          const hasCertificateMap =
            (_.isString(lb.certificateMap) && !_.isEmpty(lb.certificateMap)) ||
            _.some(
              lb.listeners,
              (listener) => _.isString(listener.certificateMap) && !_.isEmpty(listener.certificateMap),
            );
          const prefix = lb.loadBalancerType === 'EXTERNAL_MANAGED' ? 'Regional External ' : '';
          if (hasCertificate || hasCertificateMap) {
            return `${prefix}HTTPS`;
          } else {
            return `${prefix}HTTP`;
          }
        } else {
          return lb.loadBalancerType;
        }
      })(this.loadBalancer);
    };
  },
});
