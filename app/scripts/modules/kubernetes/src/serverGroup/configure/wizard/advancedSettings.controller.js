'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup.configure.kubernetes.advancedSettings', [])
  .controller('kubernetesServerGroupAdvancedSettingsController', ['$scope', function($scope) {
    if (!$scope.command.dnsPolicy) {
      $scope.command.dnsPolicy = 'ClusterFirst';
    }

    this.policies = ['ClusterFirst', 'Default', 'ClusterFirstWithHostNet'];

    this.onTolerationChange = tolerations => {
      $scope.command.tolerations = tolerations;
      $scope.$applyAsync();
    };
  }]);
