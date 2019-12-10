'use strict';

const angular = require('angular');

export const KUBERNETES_V1_LOADBALANCER_DETAILS_DETAILS_KUBERNETES_MODULE = 'spinnaker.loadBalancer.details.kubernetes';
export const name = KUBERNETES_V1_LOADBALANCER_DETAILS_DETAILS_KUBERNETES_MODULE; // for backwards compatibility
angular.module(KUBERNETES_V1_LOADBALANCER_DETAILS_DETAILS_KUBERNETES_MODULE, [require('./details.controller').name]);
