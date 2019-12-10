'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_SECURITYGROUP_CONFIGURE_WIZARD_BACKEND_CONTROLLER =
  'spinnaker.securityGroup.configure.kubernetes.backend';
export const name = KUBERNETES_V1_SECURITYGROUP_CONFIGURE_WIZARD_BACKEND_CONTROLLER; // for backwards compatibility
module(KUBERNETES_V1_SECURITYGROUP_CONFIGURE_WIZARD_BACKEND_CONTROLLER, []).controller(
  'kubernetesSecurityGroupBackendController',
  function() {
    this.maxPort = 65535;
  },
);
