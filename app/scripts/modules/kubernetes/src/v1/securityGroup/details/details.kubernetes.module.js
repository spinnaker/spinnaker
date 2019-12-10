'use strict';

const angular = require('angular');

export const KUBERNETES_V1_SECURITYGROUP_DETAILS_DETAILS_KUBERNETES_MODULE =
  'spinnaker.securityGroup.details.kubernetes';
export const name = KUBERNETES_V1_SECURITYGROUP_DETAILS_DETAILS_KUBERNETES_MODULE; // for backwards compatibility
angular.module(KUBERNETES_V1_SECURITYGROUP_DETAILS_DETAILS_KUBERNETES_MODULE, [require('./details.controller').name]);
