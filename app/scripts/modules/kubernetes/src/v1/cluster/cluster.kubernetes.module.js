'use strict';

const angular = require('angular');

export const KUBERNETES_V1_CLUSTER_CLUSTER_KUBERNETES_MODULE = 'spinnaker.cluster.kubernetes';
export const name = KUBERNETES_V1_CLUSTER_CLUSTER_KUBERNETES_MODULE; // for backwards compatibility
angular.module(KUBERNETES_V1_CLUSTER_CLUSTER_KUBERNETES_MODULE, [require('./configure/CommandBuilder').name]);
