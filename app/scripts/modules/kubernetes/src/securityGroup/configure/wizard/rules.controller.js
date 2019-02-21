'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.securityGroup.configure.kubernetes.ports', [require('../../transformer').name])
  .controller('kubernetesSecurityGroupRulesController', ['$scope', 'kubernetesSecurityGroupTransformer', function($scope, kubernetesSecurityGroupTransformer) {
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
  }]);
