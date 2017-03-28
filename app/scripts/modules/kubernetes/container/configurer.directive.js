'use strict';

let angular = require('angular');

import {KUBERNETES_LIFECYCLE_HOOK_CONFIGURER} from './lifecycleHook.component';

module.exports = angular.module('spinnaker.kubernetes.container.configurer.directive', [
  KUBERNETES_LIFECYCLE_HOOK_CONFIGURER,
]).directive('kubernetesContainerConfigurer', function () {
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

    this.setPostStartHandler = function(handler) {
      if (!$scope.container.lifecycle) {
        $scope.container.lifecycle = {};
      }
      $scope.container.lifecycle.postStart = handler;
    };

    this.setPreStopHandler = function(handler) {
      if (!$scope.container.lifecycle) {
        $scope.container.lifecycle = {};
      }
      $scope.container.lifecycle.preStop = handler;
    };
  });
