import { KUBERNETES_V1_SECURITYGROUP_TRANSFORMER } from '../../transformer';
('use strict');

import { module } from 'angular';

export const KUBERNETES_V1_SECURITYGROUP_CONFIGURE_WIZARD_RULES_CONTROLLER =
  'spinnaker.securityGroup.configure.kubernetes.ports';
export const name = KUBERNETES_V1_SECURITYGROUP_CONFIGURE_WIZARD_RULES_CONTROLLER; // for backwards compatibility
module(KUBERNETES_V1_SECURITYGROUP_CONFIGURE_WIZARD_RULES_CONTROLLER, [
  KUBERNETES_V1_SECURITYGROUP_TRANSFORMER,
]).controller('kubernetesSecurityGroupRulesController', [
  '$scope',
  'kubernetesSecurityGroupTransformer',
  function($scope, kubernetesSecurityGroupTransformer) {
    this.addRule = function() {
      $scope.securityGroup.rules.push(kubernetesSecurityGroupTransformer.constructNewIngressRule());
    };

    this.removeRule = function(i) {
      $scope.securityGroup.rules.splice(i, 1);
    };

    this.addPath = function(rule) {
      rule.value.http.paths.push(kubernetesSecurityGroupTransformer.constructNewIngressPath());
    };

    this.removePath = function(rule, i) {
      rule.value.http.paths.splice(i, 1);
    };

    this.maxPort = 65535;
  },
]);
