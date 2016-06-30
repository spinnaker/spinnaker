'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.configure.openstack.ports', [
    require('../../transformer.js'),
])
  .controller('openstackSecurityGroupRulesController', function($scope, openstackSecurityGroupTransformer) {
    this.addRule = function() {
      $scope.securityGroup.rules.push(openstackSecurityGroupTransformer.constructNewIngressRule());
    };

    this.removeRule = function(i) {
      $scope.securityGroup.rules.splice(i, 1);
    };
  });
