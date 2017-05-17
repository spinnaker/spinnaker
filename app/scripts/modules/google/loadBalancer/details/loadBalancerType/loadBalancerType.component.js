'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.loadBalancer.loadBalancerType', [])
  .component('gceLoadBalancerType', {
    template: '<span>{{ $ctrl.type }}</span>',
    bindings: {
      loadBalancer: '='
    },
    controller: function () {
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
    }
  });
