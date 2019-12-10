import { KUBERNETES_V1_CLUSTER_CONFIGURE_COMMANDBUILDER } from './configure/CommandBuilder';
('use strict');

import { module } from 'angular';

export const KUBERNETES_V1_CLUSTER_CLUSTER_KUBERNETES_MODULE = 'spinnaker.cluster.kubernetes';
export const name = KUBERNETES_V1_CLUSTER_CLUSTER_KUBERNETES_MODULE; // for backwards compatibility
module(KUBERNETES_V1_CLUSTER_CLUSTER_KUBERNETES_MODULE, [KUBERNETES_V1_CLUSTER_CONFIGURE_COMMANDBUILDER]);
