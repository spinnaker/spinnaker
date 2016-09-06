'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.container.configurer.directive', [])
  .directive('kubernetesContainerConfigurer', function () {
    return {
      restrict: 'E',
      templateUrl: require('./configurer.directive.html'),
      scope: {
        container: '=',
        index: '=',
        command: '=',
      }
    };
  })
  .controller('kubernetesContainerConfigurerController', function($scope) {
    this.cpuPattern = /^\d+(m)?$/;
    this.memoryPattern = /^\d+(Mi|Gi)?$/;
    this.pullPolicies = ['IFNOTPRESENT', 'ALWAYS', 'NEVER'];

    this.removePort = function(index) {
      $scope.container.ports.splice(index, 1);
    };

    this.addPort = function() {
      $scope.container.ports.push({ protocol: 'TCP' });
    };

    this.removeMount = function(index) {
      $scope.container.volumeMounts.splice(index, 1);
    };

    this.addMount = function() {
      $scope.container.volumeMounts.push({ name: '', readOnly: false, mountPath: '/', });
    };

    this.removeEnvVar = function(index) {
      $scope.container.envVars.splice(index, 1);
    };

    this.addEnvVar = function() {
      $scope.container.envVars.push({ name: '', value: '', });
    };

    this.removeCommand = function(index) {
      $scope.container.command.splice(index, 1);
    };

    this.addCommand = function() {
      $scope.container.command.push('');
    };

    this.removeArg = function(index) {
      $scope.container.args.splice(index, 1);
    };

    this.addArg = function() {
      $scope.container.args.push('');
    };

    this.protocols = ['TCP', 'UDP'];
    this.maxPort = 65535;
  });
