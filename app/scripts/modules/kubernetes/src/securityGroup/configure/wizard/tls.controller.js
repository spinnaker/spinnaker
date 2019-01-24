'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.securityGroup.configure.kubernetes.tls', [require('../../transformer').name])
  .controller('kubernetesSecurityGroupTLSController', function($scope, kubernetesSecurityGroupTransformer) {
    this.addTLSEntry = function() {
      $scope.securityGroup.tls.push(kubernetesSecurityGroupTransformer.constructNewIngressTLS());
    };

    this.removeTLSEntry = function(i) {
      $scope.securityGroup.tls.splice(i, 1);
    };
  });
