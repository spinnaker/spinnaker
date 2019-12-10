'use strict';

const angular = require('angular');

export const KUBERNETES_V1_SECURITYGROUP_CONFIGURE_WIZARD_TLS_CONTROLLER =
  'spinnaker.securityGroup.configure.kubernetes.tls';
export const name = KUBERNETES_V1_SECURITYGROUP_CONFIGURE_WIZARD_TLS_CONTROLLER; // for backwards compatibility
angular
  .module(KUBERNETES_V1_SECURITYGROUP_CONFIGURE_WIZARD_TLS_CONTROLLER, [require('../../transformer').name])
  .controller('kubernetesSecurityGroupTLSController', [
    '$scope',
    'kubernetesSecurityGroupTransformer',
    function($scope, kubernetesSecurityGroupTransformer) {
      this.addTLSEntry = function() {
        $scope.securityGroup.tls.push(kubernetesSecurityGroupTransformer.constructNewIngressTLS());
      };

      this.removeTLSEntry = function(i) {
        $scope.securityGroup.tls.splice(i, 1);
      };
    },
  ]);
