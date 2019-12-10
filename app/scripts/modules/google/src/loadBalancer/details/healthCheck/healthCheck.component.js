'use strict';

const angular = require('angular');

export const GOOGLE_LOADBALANCER_DETAILS_HEALTHCHECK_HEALTHCHECK_COMPONENT =
  'spinnaker.deck.gce.loadBalancer.details.healthCheck.component';
export const name = GOOGLE_LOADBALANCER_DETAILS_HEALTHCHECK_HEALTHCHECK_COMPONENT; // for backwards compatibility
angular.module(GOOGLE_LOADBALANCER_DETAILS_HEALTHCHECK_HEALTHCHECK_COMPONENT, []).component('gceHealthCheck', {
  bindings: {
    healthCheck: '=',
  },
  templateUrl: require('./healthCheck.component.html'),
});
