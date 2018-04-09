'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup.configure.kubernetes.advancedSettings', [])
  .controller('kubernetesServerGroupAdvancedSettingsController', function($scope) {
    if (!$scope.command.dnsPolicy) {
      $scope.command.dnsPolicy = 'ClusterFirst';
    }

    this.policies = ['ClusterFirst', 'Default', 'ClusterFirstWithHostNet'];
  });
